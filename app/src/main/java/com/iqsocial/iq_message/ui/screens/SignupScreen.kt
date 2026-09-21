package com.iqsocial.iq_message.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.iqsocial.iq_message.data.Country
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.launch

private enum class SignupStep { Phone, Details }

/**
 * Signing up in two steps: the number is checked against the server before the
 * rest of the form appears.
 *
 * The check is what makes this worth splitting. `/check_phone` knows two things
 * the app can't — whether the number is already registered, and whether this
 * device is tied to another account — and either one makes the whole form a
 * waste of typing. Asking first turns that into one tap.
 *
 * Still no OTP here: the check leaves a code on the server but signup never asks
 * for it, so step two goes straight to creating the account.
 */
@Composable
fun SignupScreen(
    onRegistered: () -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit
) {
    val d = IqTheme.dimens
    val repo = rememberAuthRepository()
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(SignupStep.Phone) }
    var country by remember { mutableStateOf(Country.BANGLADESH) }
    var phone by remember { mutableStateOf("") }
    var fullname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    /** Step one: ask the server whether this number can be signed up on this device. */
    fun checkNumber() {
        if (loading) return
        error = null
        loading = true
        scope.launch {
            val result = repo.checkPhoneAvailable(country.composeForApi(phone))
            loading = false
            when (result) {
                // The server's own wording is the useful part: "This phone number
                // has been taken" and "This device is already registered to another
                // account" are different problems with different fixes.
                is ApiResult.Failure -> error = result.message
                is ApiResult.Success -> step = SignupStep.Details
            }
        }
    }

    /** Step two: the number is known good, so only the form is left to fail on. */
    fun submit() {
        if (loading) return
        error = null
        loading = true
        scope.launch {
            val result = repo.register(
                fullname = fullname,
                phone = country.composeForApi(phone),
                password = password,
                confirmPassword = confirm,
                countryCode = country.iso
            )
            loading = false
            when (result) {
                is ApiResult.Success -> onRegistered()
                is ApiResult.Failure -> error = result.message
            }
        }
    }

    fun backToPhone() {
        error = null
        step = SignupStep.Phone
    }

    // The navigator's own BackHandler pops the whole route, which from step two
    // would throw away a number the server has already cleared. Step back instead.
    BackHandler(enabled = step == SignupStep.Details && !loading) { backToPhone() }

    AuthScaffold(
        title = "Create account",
        subtitle = when (step) {
            SignupStep.Phone -> "Secure messaging starts with your number."
            SignupStep.Details -> "Just a few details and you're in."
        },
        onBack = {
            if (!loading) when (step) {
                SignupStep.Phone -> onBack()
                SignupStep.Details -> backToPhone()
            }
        },
        trailing = { StepPill("Step ${step.ordinal + 1} of 2") }
    ) {
        when (step) {
            SignupStep.Phone -> {
                Text("Sign up", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(d.gapSm))
                Text(
                    "Enter your phone number to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkMuted
                )

                Spacer(Modifier.height(d.gapMd))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(IqIcons.Shield, null, Modifier.size(d.iconSm), tint = Ink)
                    Spacer(Modifier.width(d.gapSm))
                    Text(
                        "Private & secure",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                }

                Spacer(Modifier.height(d.gapXl))
                PhoneNumberField(
                    country = country,
                    onCountryChange = { country = it },
                    value = phone,
                    onValueChange = { phone = it },
                    enabled = !loading
                )

                if (error != null) {
                    Spacer(Modifier.height(d.gapMd))
                    AuthError(error)
                }

                Spacer(Modifier.height(d.gapXl))
                AuthButton(text = "Continue", onClick = ::checkNumber, loading = loading)

                Spacer(Modifier.height(d.gapLg))
                AuthSwitchRow(
                    prompt = "Already have an account?",
                    action = "Log in",
                    onClick = { if (!loading) onLogin() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            SignupStep.Details -> {
                Text("Almost there", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(d.gapSm))
                Text(
                    "No verification code needed — you're in straight away.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkMuted
                )

                // The number that just cleared the check, with a way back to it:
                // the point of step one is that this is what gets registered.
                Spacer(Modifier.height(d.gapMd))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(IqIcons.Check, null, Modifier.size(d.iconSm), tint = Crimson)
                    Spacer(Modifier.width(d.gapSm))
                    Text(
                        country.composeForApi(phone),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ink
                    )
                    Spacer(Modifier.width(d.gapMd))
                    Text(
                        "Change",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Crimson,
                        modifier = Modifier.clickable(enabled = !loading) { backToPhone() }
                    )
                }

                Spacer(Modifier.height(d.gapXl))
                AuthField(
                    label = "Full name",
                    value = fullname,
                    onValueChange = { fullname = it.take(50) },
                    placeholder = "Your name",
                    enabled = !loading
                )

                Spacer(Modifier.height(d.gapLg))
                AuthField(
                    label = "Password",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "At least 6 characters",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    enabled = !loading
                )

                Spacer(Modifier.height(d.gapLg))
                AuthField(
                    label = "Confirm password",
                    value = confirm,
                    onValueChange = { confirm = it },
                    placeholder = "Repeat your password",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    enabled = !loading
                )

                if (error != null) {
                    Spacer(Modifier.height(d.gapMd))
                    AuthError(error)
                }

                Spacer(Modifier.height(d.gapXl))
                AuthButton(text = "Create account", onClick = ::submit, loading = loading)

                Spacer(Modifier.height(d.gapLg))
                AuthSwitchRow(
                    prompt = "Already have an account?",
                    action = "Log in",
                    onClick = { if (!loading) onLogin() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
