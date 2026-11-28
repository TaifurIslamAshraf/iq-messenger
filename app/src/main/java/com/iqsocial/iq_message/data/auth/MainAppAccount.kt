package com.iqsocial.iq_message.data.auth

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.iqsocial.iq_message.R
import java.security.MessageDigest

/**
 * The IQ Social app on this phone, and who is signed in to it.
 *
 * When someone is, the login screen offers to continue as them: one tap takes that app's token and trades it for a
 * Messenger session (`POST /v1/auth/connect`). The other app answers only the real Messenger, and this side asks only
 * the real IQ Social - same package, signed with this app's key or with one in `R.array.iq_social_certs` - so a look-
 * alike app cannot hand over an account of its choosing.
 *
 * Both calls read another app's provider: run them off the main thread.
 */
object MainAppAccount {

    const val PACKAGE = "com.iqama.newsocial"
    private const val AUTHORITY = "$PACKAGE.messengerconnect"
    private val ACCOUNT: Uri = Uri.parse("content://$AUTHORITY/account")

    data class Account(val uid: String, val fullname: String, val profilePicture: String)

    /** Null when IQ Social isn't installed, nobody is signed in to it, or it isn't the real one. */
    fun find(context: Context): Account? {
        if (!genuine(context)) return null
        return runCatching {
            context.contentResolver.query(ACCOUNT, null, null, null, null)?.use { row ->
                if (!row.moveToFirst()) return@use null
                Account(row.getString(0).orEmpty(), row.getString(1).orEmpty(), row.getString(2).orEmpty())
            }
        }.getOrNull()?.takeIf { it.uid.isNotBlank() }
    }

    /** The signed-in account's token, handed over once for `/v1/auth/connect`. Never kept. */
    fun token(context: Context): String? {
        if (!genuine(context)) return null
        return runCatching {
            context.contentResolver.call(ACCOUNT, "token", null, null)?.getString("token")
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun genuine(context: Context): Boolean {
        val pm = context.packageManager
        val owner = runCatching { pm.resolveContentProvider(AUTHORITY, 0)?.packageName }.getOrNull()
        if (owner != PACKAGE) return false
        if (pm.checkSignatures(context.packageName, PACKAGE) == PackageManager.SIGNATURE_MATCH) return true
        val allowed = context.resources.getStringArray(R.array.iq_social_certs)
            .map { it.replace(":", "").trim().lowercase() }
            .filter { it.length == 64 }
        if (allowed.isEmpty()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                allowed.any { pm.hasSigningCertificate(PACKAGE, hexBytes(it), PackageManager.CERT_INPUT_SHA256) }
            } else {
                @Suppress("DEPRECATION")
                val signatures = pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNATURES).signatures.orEmpty()
                signatures.map { sha256Hex(it.toByteArray()) }.any { it in allowed }
            }
        }.getOrDefault(false)
    }

    private fun hexBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
