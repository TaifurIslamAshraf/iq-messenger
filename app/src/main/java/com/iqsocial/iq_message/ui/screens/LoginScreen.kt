package com.iqsocial.iq_message.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.iqsocial.iq_message.data.Country
import com.iqsocial.iq_message.data.Person
import com.iqsocial.iq_message.data.auth.MainAppAccount
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.ui.components.Avatar
import com.iqsocial.iq_message.ui.theme.CanvasGrey
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onSignUp: () -> Unit,
    onForgotPassword: () -> Unit
) {
    val d = IqTheme.dimens
    val context = LocalContext.current
    val repo = rememberAuthRepository()
    val scope = rememberCoroutineScope()

    var country by remember { mutableStateOf(Country.BANGLADESH) }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    /** Whoever is signed in to IQ Social on this phone, offered as a one-tap sign-in. */
    var mainAccount by remember { mutableStateOf<MainAppAccount.Account?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        mainAccount = withContext(Dispatchers.IO) { MainAppAccount.find(context) }
    }

    fun connect() {
        if (loading || connecting) return
        connectError = null
        connecting = true
        scope.launch {
            val result = repo.connectMainApp(context)
            connecting = false
            when (result) {
                is ApiResult.Success -> onLoggedIn()
                is ApiResult.Failure -> connectError = result.message
            }
        }
    }

    fun submit() {
        if (loading || connecting) return
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
        // IQ Social answers only once its process has started, a second or more after this screen, so the card
        // slides in rather than pushing the form down in one jump.
        AnimatedVisibility(visible = mainAccount != null, enter = expandVertically() + fadeIn()) {
            Column { mainAccount?.let { account -> ContinueAs(account, connecting, loading, connectError, ::connect) } }
        }
        if (mainAccount == null) {
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
        }

        Spacer(Modifier.height(d.gapXl))
        PhoneNumberField(
            country = country,
            onCountryChange = { country = it },
            value = phone,
            onValueChange = { phone = it },
            enabled = !loading && !connecting
        )

        Spacer(Modifier.height(d.gapLg))
        AuthField(
            label = "Password",
            value = password,
            onValueChange = { password = it },
            placeholder = "Your password",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            enabled = !loading && !connecting
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

/** The heading over [ConnectCard], the card, and what went wrong with it. */
@Composable
private fun ContinueAs(
    account: MainAppAccount.Account,
    connecting: Boolean,
    loading: Boolean,
    error: String?,
    onConnect: () -> Unit
) {
    val d = IqTheme.dimens
    Text(
        "Continue with IQ Social",
        style = MaterialTheme.typography.headlineMedium,
        color = Ink
    )
    Spacer(Modifier.height(d.gapSm))
    Text(
        "You're signed in to IQ Social on this phone. One account works in both apps.",
        style = MaterialTheme.typography.bodyLarge,
        color = InkMuted
    )
    Spacer(Modifier.height(d.gapLg))
    ConnectCard(account = account, connecting = connecting, enabled = !loading, onConnect = onConnect)
    if (error != null) {
        Spacer(Modifier.height(d.gapSm))
        AuthError(error)
    }
    Spacer(Modifier.height(d.gapXl))
    Text(
        "Or log in with your phone number",
        style = MaterialTheme.typography.titleMedium,
        color = InkMuted
    )
}

/** The account signed in to IQ Social on this phone, and the one tap that signs in here as them. */
@Composable
private fun ConnectCard(
    account: MainAppAccount.Account,
    connecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit
) {
    val d = IqTheme.dimens
    val name = account.fullname.ifBlank { "Your IQ Social account" }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.radiusMd))
            .background(CanvasGrey)
            .padding(d.gapMd)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(
                person = Person(id = account.uid, name = name, handle = "", photo = account.profilePicture),
                size = d.avatarMd
            )
            Spacer(Modifier.width(d.gapMd))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Signed in to IQ Social",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkMuted,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(d.gapMd))
        AuthButton(
            text = "Connect",
            onClick = onConnect,
            loading = connecting,
            enabled = enabled
        )
    }
}
