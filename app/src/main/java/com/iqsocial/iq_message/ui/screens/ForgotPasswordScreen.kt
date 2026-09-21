package com.iqsocial.iq_message.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.auth.AuthRepository
import com.iqsocial.iq_message.data.Country
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.CrimsonTint
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val OTP_SECONDS = 120

private enum class ResetStep { Phone, Code, NewPassword }

/**
 * Three steps behind one screen: ask for the number, verify the code that was
 * texted to it, then set a new password. Only this flow uses an OTP — signing up
 * does not.
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val d = IqTheme.dimens
    val repo = rememberAuthRepository()
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(ResetStep.Phone) }
    var country by remember { mutableStateOf(Country.BANGLADESH) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(0) }

    LaunchedEffect(step, secondsLeft) {
        if (step == ResetStep.Code && secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    fun <T> handle(result: ApiResult<T>, onSuccess: () -> Unit) {
        loading = false
        when (result) {
            is ApiResult.Success -> { error = null; onSuccess() }
            is ApiResult.Failure -> error = result.message
        }
    }

    fun sendCode() {
        if (loading) return
        error = null; loading = true
        scope.launch {
            handle(repo.requestPasswordOtp(country.composeForApi(phone))) {
                step = ResetStep.Code
                code = ""
                secondsLeft = OTP_SECONDS
            }
        }
    }

    fun verifyCode() {
        if (loading) return
        error = null; loading = true
        scope.launch {
            handle(repo.verifyPasswordOtp(country.composeForApi(phone), code)) {
                step = ResetStep.NewPassword
            }
        }
    }

    fun savePassword() {
        if (loading) return
        error = null; loading = true
        scope.launch {
            handle(
                repo.resetPassword(country.composeForApi(phone), code, password, confirm)
            ) { onDone() }
        }
    }

    fun stepBack() {
        error = null
        step = if (step == ResetStep.NewPassword) ResetStep.Code else ResetStep.Phone
    }

    // Same reason as signup: without this, system back leaves the flow entirely
    // rather than returning to the previous step.
    BackHandler(enabled = step != ResetStep.Phone && !loading) { stepBack() }

    AuthScaffold(
        title = when (step) {
            ResetStep.Phone -> "Forgot password"
            ResetStep.Code -> "Verify your number"
            ResetStep.NewPassword -> "New password"
        },
        subtitle = when (step) {
            ResetStep.Phone -> "We'll text you a one-time code."
            ResetStep.Code -> "A secure code was sent to your phone."
            ResetStep.NewPassword -> "Choose something only you know."
        },
        onBack = {
            if (!loading) {
                if (step == ResetStep.Phone) onBack() else stepBack()
            }
        },
        trailing = { StepPill("Step ${step.ordinal + 1} of 3") }
    ) {
        when (step) {
            ResetStep.Phone -> {
                Text("Reset password", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(d.gapSm))
                Text(
                    "Enter the phone number on your account.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkMuted
                )
                Spacer(Modifier.height(d.gapXl))
                PhoneNumberField(
                    country = country,
                    onCountryChange = { country = it },
                    value = phone,
                    onValueChange = { phone = it },
                    enabled = !loading
                )
                if (error != null) {
                    Spacer(Modifier.height(d.gapMd)); AuthError(error)
                }
                Spacer(Modifier.height(d.gapXl))
                AuthButton(text = "Send code", onClick = ::sendCode, loading = loading)
            }

            ResetStep.Code -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(d.scaled(52))
                            .clip(CircleShape)
                            .background(CrimsonTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(IqIcons.ShieldCheck, null, Modifier.size(d.iconLg), tint = Crimson)
                    }
                    Spacer(Modifier.width(d.gapMd))
                    Text(
                        "Enter Verification Code",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(d.gapLg))
                Text(
                    "We sent a ${AuthRepository.OTP_LENGTH}-digit code to",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkMuted
                )
                Spacer(Modifier.height(d.gapXs))
                Text(
                    country.composeForApi(phone),
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(d.gapLg))
                OtpInput(value = code, onValueChange = { code = it }, enabled = !loading)

                Spacer(Modifier.height(d.gapLg))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(IqIcons.Clock, null, Modifier.size(d.iconSm), tint = Ink)
                    Spacer(Modifier.width(d.gapSm))
                    Text(
                        if (secondsLeft > 0) "Expires in ${formatMmSs(secondsLeft)}"
                        else "Code expired",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkMuted,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Resend Code",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (secondsLeft > 0) InkMuted else Crimson,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(d.radiusSm))
                            .clickable(enabled = secondsLeft == 0 && !loading) { sendCode() }
                            .padding(horizontal = d.gapXs, vertical = d.gapXs)
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(d.gapMd)); AuthError(error)
                }
                Spacer(Modifier.height(d.gapXl))
                AuthButton(
                    text = "Verify",
                    onClick = ::verifyCode,
                    loading = loading,
                    enabled = code.length == AuthRepository.OTP_LENGTH
                )
            }

            ResetStep.NewPassword -> {
                Text("Set a new password", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(d.gapSm))
                Text(
                    "You'll use this to log in from now on.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = InkMuted
                )
                Spacer(Modifier.height(d.gapXl))
                AuthField(
                    label = "New password",
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
                    Spacer(Modifier.height(d.gapMd)); AuthError(error)
                }
                Spacer(Modifier.height(d.gapXl))
                AuthButton(text = "Save password", onClick = ::savePassword, loading = loading)
            }
        }
    }
}

/** Six tap-to-focus cells backed by one hidden field. */
@Composable
private fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    val d = IqTheme.dimens
    val focusRequester = remember { FocusRequester() }

    Box {
        BasicTextField(
            value = value,
            onValueChange = {
                onValueChange(it.filter(Char::isDigit).take(AuthRepository.OTP_LENGTH))
            },
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester)
                .alpha(0f)
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(d.gapSm)
        ) {
            repeat(AuthRepository.OTP_LENGTH) { index ->
                val focused = index == value.length.coerceAtMost(AuthRepository.OTP_LENGTH - 1)
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(0.78f)
                        .clip(RoundedCornerShape(d.radiusSm))
                        .background(Color.White)
                        .border(
                            width = if (focused) 2.dp else 1.dp,
                            color = if (focused) Crimson else Hairline,
                            shape = RoundedCornerShape(d.radiusSm)
                        )
                        .clickable(enabled = enabled) { focusRequester.requestFocus() },
                    contentAlignment = Alignment.Center
                ) {
                    val digit = value.getOrNull(index)
                    if (digit != null) {
                        Text(
                            digit.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Normal,
                            color = Ink
                        )
                    } else if (focused) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(d.scaled(26))
                                .clip(CircleShape)
                                .background(Crimson)
                        )
                    }
                }
            }
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
