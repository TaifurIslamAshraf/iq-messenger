package com.iqsocial.iq_message.data.remote

import org.json.JSONObject

/**
 * The signed-in user, as `/v1/auth` answers it. The token is the main backend's own shape, so it works there too.
 */
data class AuthUser(
    val uid: String,
    val fullname: String,
    val phone: String,
    val profilePicture: String,
    val refCode: String,
    val token: String
) {
    companion object {
        fun from(json: JSONObject): AuthUser = AuthUser(
            uid = json.string("uid"),
            fullname = json.string("fullname"),
            phone = json.string("phone_number"),
            profilePicture = json.string("profile_picture"),
            refCode = json.string("ref_code"),
            token = json.string("token")
        )
    }
}

/**
 * Signing in, on the messenger server's own `/v1/auth` - the same accounts as the IQ Social app, so one password
 * works in both. Password reset is the exception and still goes to the main backend (below).
 */
object AuthApi {

    /** The account API, for password reset only. */
    private suspend fun accountPost(path: String, body: JSONObject) =
        ApiClient.post(path, body, baseUrl = ApiConfig.BASE_URL)

    /**
     * Whether a number can sign up on this device, before the signup form. 409 for a number or a device already in
     * use, 403 for a banned device, each with the server's own wording.
     */
    suspend fun checkPhone(phone: String, deviceId: String): ApiResult<Unit> =
        ApiClient.post(
            "/v1/auth/check-phone",
            JSONObject().put("phone", phone).put("device_id", deviceId)
        ).map { }

    suspend fun login(phone: String, password: String, deviceId: String): ApiResult<AuthUser> =
        ApiClient.post(
            "/v1/auth/login",
            JSONObject()
                .put("phone", phone)
                .put("password", password)
                .put("device_id", deviceId)
        ).map(AuthUser::from)

    /** No OTP step - the server creates the account, with the name, and answers its session. */
    suspend fun register(
        fullname: String,
        phone: String,
        password: String,
        country: String,
        deviceId: String
    ): ApiResult<AuthUser> =
        ApiClient.post(
            "/v1/auth/signup",
            JSONObject()
                .put("fullname", fullname)
                .put("phone", phone)
                .put("password", password)
                .put("country", country)
                .put("device_id", deviceId)
        ).map(AuthUser::from)

    /**
     * Trades the IQ Social app's token, handed over on this phone ([com.iqsocial.iq_message.data.auth.MainAppAccount]),
     * for this app's own session.
     */
    suspend fun connect(mainAppToken: String, deviceId: String): ApiResult<AuthUser> =
        ApiClient.post(
            "/v1/auth/connect",
            JSONObject().put("device_id", deviceId),
            token = mainAppToken
        ).map(AuthUser::from)

    /* -- password reset ------------------------------------------------------ */

    /*
     * These three are snake_case, unlike the rest of the auth surface — see
     * `routes/app.routes.js` in the main backend:
     *
     *   /send_otp_code  -> userController.sendOtp        (number must EXIST)
     *   /verify_code    -> userController.verifyCode
     *   /reset_password -> userController.forgetPassword
     *
     * Not to be confused with `/send_otp`, which is the *signup* OTP and refuses a
     * number that is already registered ("This phone number has been taken").
     */

    suspend fun requestPasswordOtp(phone: String): ApiResult<Unit> =
        accountPost(
            "/send_otp_code",
            JSONObject().put("phone", phone)
        ).map { }

    suspend fun verifyPasswordOtp(phone: String, otp: String): ApiResult<Unit> =
        accountPost(
            "/verify_code",
            JSONObject().put("phone", phone).put("code", otp)
        ).map { }

    /**
     * Note the server takes `password` here, not `newPassword`, and does **not**
     * re-check the OTP — `verifyPasswordOtp` is what gates this step.
     */
    suspend fun resetPassword(phone: String, otp: String, password: String): ApiResult<Unit> =
        accountPost(
            "/reset_password",
            JSONObject()
                .put("phone", phone)
                .put("password", password)
        ).map { }
}

private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data), message)
    is ApiResult.Failure -> this
}
