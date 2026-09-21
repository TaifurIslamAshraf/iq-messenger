package com.iqsocial.iq_message.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iqsocial.iq_message.data.Country
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit
) {
    val d = IqTheme.dimens
    val repo = rememberAuthRepository()
    val scope = rememberCoroutineScope()

    var country by remember { mutableStateOf(Country.BANGLADESH) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun submit() {
        if (loading) return
        error = null
        loading = true
        scope.launch {
            when (val result = repo.login(country.composeForApi(phone), password)) {
                is ApiResult.Success -> {
                    loading = false
                    onLoggedIn()
                }
                is ApiResult.Failure -> {
                    loading = false
                    error = result.message
                }
            }
        }
    }

    AuthScaffold(
        title = "Welcome back",
        subtitle = "Sign in to pick up where you left off.",
        onBack = null
    ) {
        Text(
            "Log in",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink
        )
        Spacer(Modifier.height(d.gapSm))
        Text(
            "Use the phone number you registered with.",
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

        Spacer(Modifier.height(d.gapLg))
        AuthField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            placeholder = "Your password",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            enabled = !loading
        )

        Spacer(Modifier.height(d.gapSm))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                "Forgot password?",
                style = MaterialTheme.typography.titleSmall,
                color = Crimson,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(d.radiusSm))
                    .clickable(enabled = !loading, onClick = onForgotPassword)
                    .padding(horizontal = d.gapSm, vertical = d.gapSm)
            )
        }

        if (error != null) {
            Spacer(Modifier.height(d.gapSm))
            AuthError(error)
        }

        Spacer(Modifier.height(d.gapLg))
        AuthButton(text = "Log in", onClick = ::submit, loading = loading)

        Spacer(Modifier.height(d.gapLg))
        AuthSwitchRow(
            prompt = "Don't have an account?",
            action = "Sign up",
            onClick = { if (!loading) onSignUp() }
        )
    }
}
