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

    /** The account API: password reset, and sign-in while the messenger server has no `/v1/auth` yet. */
    private suspend fun accountPost(path: String, body: JSONObject) =
        ApiClient.post(path, body, baseUrl = ApiConfig.BASE_URL)

    /**
     * A messenger server deployed before `/v1/auth` answers its routes with 404, and then the request goes to the main
     * backend's own endpoint, which takes the same accounts. So this app works on either server, whichever ships
     * first. Remove once every messenger server has `/v1/auth`.
     */
    private suspend fun authPost(path: String, legacyPath: String, body: JSONObject): ApiResult<JSONObject> {
        val result = ApiClient.post(path, body)
        return if (result.routeMissing) accountPost(legacyPath, body) else result
    }

    /**
     * Whether a number can sign up on this device, before the signup form. 409 for a number or a device already in
     * use, 403 for a banned device, each with the server's own wording.
     *
     * The main backend's `/check_phone` answers a taken number with **success** and the message "This phone number
     * has been taken", so a success saying "taken" is a refusal too.
     */
    suspend fun checkPhone(phone: String, deviceId: String): ApiResult<Unit> {
        val result = authPost(
            "/v1/auth/check-phone",
            "/check_phone",
            JSONObject().put("phone", phone).put("device_id", deviceId)
        )
        if (result is ApiResult.Success && result.message?.contains("taken", true) == true) {
            return ApiResult.Failure(result.message)
        }
        return result.map { }
    }

    suspend fun login(phone: String, password: String, deviceId: String): ApiResult<AuthUser> =
        authPost(
            "/v1/auth/login",
            "/login",
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
    ): ApiResult<AuthUser> {
        val body = JSONObject()
            .put("fullname", fullname)
            .put("phone", phone)
            .put("password", password)
            .put("country", country)
            .put("device_id", deviceId)
        val result = ApiClient.post("/v1/auth/signup", body)
        if (!result.routeMissing) return result.map(AuthUser::from)

        // The main backend's signup drops the name (BUG-095), so it is saved right after, on the new account.
        val created = accountPost("/signup", body).map(AuthUser::from)
        if (created !is ApiResult.Success) return created
        val named = ProfileApi.rename(created.data.token, fullname) is ApiResult.Success
        return if (named) ApiResult.Success(created.data.copy(fullname = fullname), created.message) else created
    }

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

/** The server has no such route: a messenger server deployed before it (see [AuthApi]). */
internal val ApiResult<*>.routeMissing: Boolean
    get() = this is ApiResult.Failure && notImplemented

private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data), message)
    is ApiResult.Failure -> this
}
