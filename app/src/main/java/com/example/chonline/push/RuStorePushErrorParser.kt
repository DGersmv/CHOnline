package com.example.chonline.push

object RuStorePushErrorParser {
    private val pubKeyRegex = Regex("pub_key '([^']+)'")

    /** SHA-256 из текста ошибки Ru Store (то, что нужно добавить в консоль). */
    fun extractPubKeySha256(message: String?): String? {
        if (message.isNullOrBlank()) return null
        return pubKeyRegex.find(message)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
