package com.example.chonline.ui.chat

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.chonline.data.remote.FileAttachmentDto
import com.example.chonline.data.repo.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream

private const val PANO_HOST = "panorama.local"
private const val PANO_IMAGE_PATH = "/image"
private const val FAKE_IMAGE_URL = "https://$PANO_HOST$PANO_IMAGE_PATH"

/**
 * Сферический просмотр equirectangular: локальный кэш вложения отдаётся в WebView через shouldInterceptRequest
 * (без зависимости от CORS на S3).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PanoramaSphereWebView(
    modifier: Modifier = Modifier,
    baseUrl: String,
    messageId: String,
    file: FileAttachmentDto,
    isClient: Boolean,
    chatRepository: ChatRepository,
) {
    val context = LocalContext.current
    var cachedFile by remember(messageId, file) { mutableStateOf<File?>(null) }
    var loadErr by remember(messageId, file) { mutableStateOf<String?>(null) }

    LaunchedEffect(messageId, file) {
        cachedFile = null
        loadErr = null
        val r = withContext(Dispatchers.IO) {
            chatRepository.ensureChatAttachmentInCache(
                context,
                baseUrl,
                messageId,
                file,
                isClient,
            )
        }
        r.fold(
            onSuccess = { cachedFile = it },
            onFailure = { loadErr = it.message ?: "Не удалось подготовить файл" },
        )
    }

    when {
        loadErr != null -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(loadErr!!)
            }
        }
        cachedFile == null -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        else -> {
            val cf = cachedFile!!
            val mime = file.mime.ifBlank { "image/jpeg" }
            val quoted = JSONObject.quote(FAKE_IMAGE_URL)
            AndroidView(
                modifier = modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        @Suppress("DEPRECATION")
                        settings.allowFileAccessFromFileURLs = true
                        @Suppress("DEPRECATION")
                        settings.allowUniversalAccessFromFileURLs = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                val u = request.url
                                if (u.host == PANO_HOST && u.path == PANO_IMAGE_PATH) {
                                    return WebResourceResponse(mime, null, FileInputStream(cf))
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                if (url != null && url.contains("panorama-viewer.html")) {
                                    view.evaluateJavascript(
                                        "if(window.startViewer)window.startViewer($quoted)",
                                        null,
                                    )
                                }
                            }
                        }
                        loadUrl("file:///android_asset/panorama-viewer.html")
                    }
                },
                update = { },
            )
        }
    }
}
