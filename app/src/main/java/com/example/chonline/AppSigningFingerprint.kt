package com.example.chonline

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * SHA-256 отпечаток подписи установленного APK (формат с двоеточиями, как в консоли Ru Store / keytool).
 *
 * Важно: для API 28+ нужно брать [android.content.pm.SigningInfo.getApkContentsSigners] — подпись **текущего** APK.
 * [SigningInfo.signingCertificateHistory.first] — это **самый старый** сертификат в цепочке (v3), из‑за этого
 * в UI мог показываться другой отпечаток, чем в ошибке Ru Store `pub_key '…' doesn't exist`.
 */
object AppSigningFingerprint {

    /** Отпечаток, которым реально подписано содержимое APK (как ожидает Ru Store). */
    fun sha256ColonHex(context: Context): String? {
        val bytes = signingCertBytes(context) ?: return null
        return hashSha256Colon(bytes)
    }

    /** Все различные SHA-256 по цепочке (если сомневаетесь — добавьте в консоль все). */
    fun allSha256ColonHex(context: Context): List<String> {
        val pm = context.packageManager
        val pkg = context.packageName
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val si = pi.signingInfo ?: return emptyList()
                val seen = linkedSetOf<String>()
                for (s in si.apkContentsSigners) {
                    seen.add(hashSha256Colon(s.toByteArray()))
                }
                for (s in si.signingCertificateHistory) {
                    seen.add(hashSha256Colon(s.toByteArray()))
                }
                seen.toList()
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val sig = pi.signatures?.firstOrNull() ?: return emptyList()
                listOf(hashSha256Colon(sig.toByteArray()))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun signingCertBytes(context: Context): ByteArray? {
        val pm = context.packageManager
        val pkg = context.packageName
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
                val si = pi.signingInfo ?: return null
                val sig =
                    si.apkContentsSigners.firstOrNull()
                        ?: si.signingCertificateHistory.lastOrNull()
                        ?: si.signingCertificateHistory.firstOrNull()
                        ?: return null
                sig.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                pi.signatures?.firstOrNull()?.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hashSha256Colon(certBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
        return digest.joinToString(":") { b -> "%02X".format(b) }
    }
}
