package com.example.chonline.data.remote

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.File

/**
 * [RequestBody] для multipart-загрузки с колбэком прогресса (байты / всего).
 */
class CountingFileRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (uploaded: Long, total: Long) -> Unit,
) : RequestBody() {

    override fun contentType() = contentType

    override fun contentLength() = file.length()

    override fun writeTo(sink: BufferedSink) {
        file.source().use { source ->
            val buffer = Buffer()
            var total = 0L
            val totalSize = contentLength()
            while (true) {
                val read = source.read(buffer, 8192)
                if (read == -1L) break
                sink.write(buffer, read)
                total += read
                onProgress(total, totalSize)
            }
        }
    }
}
