package com.iqsocial.iq_message.data.auth

import android.content.Context
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.AuthApi
import com.iqsocial.iq_message.data.remote.AuthUser
import com.iqsocial.iq_message.data.remote.routeMissing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Auth actions the UI calls, with the session persisted on success.
 *
 * Validation lives here rather than in each screen so login and signup can't drift
 * apart, and so the messages match what the backend would have said anyway.
 */
class AuthRepository(private val session: SessionStore) {

    fun currentUser(): AuthUser? = session.load()

    fun isSignedIn(): Boolean = session.isSignedIn

    suspend fun login(phone: String, password: String): ApiResult<AuthUser> {
        validatePhone(phone)?.let { return ApiResult.Failure(it) }
        if (password.isBlank()) return ApiResult.Failure("Password can't be empty")

        val result = AuthApi.login(phone.trim(), password, session.deviceId)
        if (result is ApiResult.Success) session.save(result.data)
        return result
    }

    /**
     * Signs in as whoever is signed in to the IQ Social app on this phone. Its token is used once, here, and
     * replaced by this app's own.
     */
    suspend fun connectMainApp(context: Context): ApiResult<AuthUser> {
        val token = withContext(Dispatchers.IO) { MainAppAccount.token(context) }
            ?: return ApiResult.Failure("Open IQ Social and sign in there first")
        var result = AuthApi.connect(token, session.deviceId)
        if (result.routeMissing) {
            // A messenger server from before /v1/auth/connect still takes IQ Social's token as it is: the same shape.
            val account = withContext(Dispatchers.IO) { MainAppAccount.find(context) }
                ?: return ApiResult.Failure("Open IQ Social and sign in there first")
            result = ApiResult.Success(
                AuthUser(
                    uid = account.uid,
                    fullname = account.fullname,
                    phone = "",
                    profilePicture = account.profilePicture,
                    refCode = "",
                    token = token
                )
            )
        }
        if (result is ApiResult.Success) session.save(result.data)
        return result
    }

    /** [countryCode] is the ISO code ("BD"); the server needs it to read a number typed without its code. */
    suspend fun register(
        fullname: String,
        phone: String,
        password: String,
        confirmPassword: String,
        countryCode: String
    ): ApiResult<AuthUser> {
        if (fullname.isBlank()) return ApiResult.Failure("Please enter your name")
        validatePhone(phone)?.let { return ApiResult.Failure(it) }
        validatePassword(password)?.let { return ApiResult.Failure(it) }
        if (password != confirmPassword) return ApiResult.Failure("Passwords don't match")
        if (countryCode.isBlank()) return ApiResult.Failure("Please pick your country")

        val result = AuthApi.register(
            fullname = fullname.trim(),
            phone = phone.trim(),
            password = password,
            country = countryCode,
            deviceId = session.deviceId
        )
        if (result is ApiResult.Success) session.save(result.data)
        return result
    }

    /**
     * Step one of signup: is this number free on this device? Runs before the rest of
     * the form is shown, so a taken number is caught immediately rather than after
     * the user has filled everything in.
     */
    suspend fun checkPhoneAvailable(phone: String): ApiResult<Unit> {
        validatePhone(phone)?.let { return ApiResult.Failure(it) }
        return AuthApi.checkPhone(phone.trim(), session.deviceId)
    }

    fun logout() = session.clear()

    /* -- password reset ------------------------------------------------------ */

    suspend fun requestPasswordOtp(phone: String): ApiResult<Unit> {
        validatePhone(phone)?.let { return ApiResult.Failure(it) }
        return AuthApi.requestPasswordOtp(phone.trim())
    }

    suspend fun verifyPasswordOtp(phone: String, otp: String): ApiResult<Unit> {
        if (otp.length < OTP_LENGTH) return ApiResult.Failure("Enter the $OTP_LENGTH-digit code")
        return AuthApi.verifyPasswordOtp(phone.trim(), otp)
    }

    suspend fun resetPassword(
        phone: String,
        otp: String,
        password: String,
        confirmPassword: String
    ): ApiResult<Unit> {
        validatePassword(password)?.let { return ApiResult.Failure(it) }
        if (password != confirmPassword) return ApiResult.Failure("Passwords don't match")
        return AuthApi.resetPassword(phone.trim(), otp, password)
    }

    private fun validatePhone(phone: String): String? {
        val digits = phone.filter(Char::isDigit)
        return when {
            digits.isEmpty() -> "Phone number can't be empty"
            digits.length < 6 -> "That phone number looks too short"
            else -> null
        }
    }

    private fun validatePassword(password: String): String? = when {
        password.isBlank() -> "Password can't be empty"
        password.length < 6 -> "Use at least 6 characters"
        else -> null
    }

    companion object {
        const val OTP_LENGTH = 6
    }
}
