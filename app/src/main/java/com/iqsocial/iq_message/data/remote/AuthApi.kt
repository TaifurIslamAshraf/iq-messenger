package com.iqsocial.iq_message.data.remote

import org.json.JSONObject

/**
 * The signed-in user, as the backend returns it.
 *
 * `User.toJSON` renames `_id` to `uid` and strips the password, so these are the
 * fields the app can rely on.
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
            uid = json.optString("uid"),
            fullname = json.optString("fullname"),
            phone = json.optString("phone_number"),
            profilePicture = json.optString("profile_picture"),
            refCode = json.optString("ref_code"),
            token = json.optString("token")
        )
    }
}

/**
 * The user-facing auth endpoints on the live host, verified against it directly.
 *
 * Paths are the deployed ones — `/login` and `/signup` under `/v1/api` — which do
 * not match the local `iq-backend-web` router (`/api/auth/login`, `/api/auth/register`).
 * The deployed build is ahead of the checked-out repo; the request bodies are the
 * same, so `auth.controller.js` still describes the contract.
 */
object AuthApi {

    /**
     * Every call here goes to the **account** API, not the messenger host that the
     * rest of the app now defaults to.
     *
     * This is not a leftover. The messenger server exposes five routes — `/v1/me`,
     * two contacts routes and two health probes — and nothing else; login, signup
     * and the OTP routes all 404 there. Its own spec says its bearer token is the
     * one issued by this backend's `/v1/api/login`, so the two are complementary
     * by design. If auth ever lands on the messenger host, this helper is the only
     * thing that has to change.
     */
    private suspend fun accountPost(path: String, body: JSONObject) =
        ApiClient.post(path, body, baseUrl = ApiConfig.BASE_URL)

    /**
     * Availability check that gates the signup form.
     *
     * The live build answers a taken number with `status:"error"`, but the source in
     * `user.controller.js` uses `successMsg(null, "This phone number has been taken")`
     * — success carrying a rejection. Both are treated as taken so a redeploy of that
     * code path cannot let someone through to a signup that will fail.
     *
     * Side effect: on success the server stores a signup OTP. This app does not use
     * it — sign-up needs no code — so it is simply left unread.
     */
    suspend fun checkPhone(phone: String, deviceId: String): ApiResult<Unit> {
        val result = accountPost(
            "/check_phone",
            JSONObject().put("phone", phone).put("device_id", deviceId)
        )
        if (result is ApiResult.Success && result.message?.contains("taken", true) == true) {
            return ApiResult.Failure(result.message)
        }
        return result.map { }
    }

    suspend fun login(phone: String, password: String): ApiResult<AuthUser> =
        accountPost(
            "/login",
            JSONObject()
                .put("phone", phone)
                .put("password", password)
        ).map(AuthUser::from)

    /** No OTP step — the server creates the account and returns a token directly. */
    suspend fun register(
        fullname: String,
        phone: String,
        password: String,
        country: String = "",
        deviceId: String = ""
    ): ApiResult<AuthUser> =
        accountPost(
            "/signup",
            JSONObject()
                .put("fullname", fullname)
                .put("phone", phone)
                .put("password", password)
                .put("country", country)
                .put("device_id", deviceId)
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
