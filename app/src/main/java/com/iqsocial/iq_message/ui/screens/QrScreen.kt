package com.iqsocial.iq_message.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.iqsocial.iq_message.ui.components.shimmer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.iqsocial.iq_message.data.auth.SessionStore
import com.iqsocial.iq_message.data.chat.ChatRepository
import com.iqsocial.iq_message.data.chat.KnownPeople
import com.iqsocial.iq_message.data.remote.ApiResult
import com.iqsocial.iq_message.data.remote.QrApi
import com.iqsocial.iq_message.data.remote.QrCode
import com.iqsocial.iq_message.ui.components.CircleIconButton
import com.iqsocial.iq_message.ui.components.HairlineDivider
import com.iqsocial.iq_message.ui.icons.IqIcons
import com.iqsocial.iq_message.R
import com.iqsocial.iq_message.ui.theme.CanvasGrey
import com.iqsocial.iq_message.ui.theme.Crimson
import com.iqsocial.iq_message.ui.theme.FieldGrey
import com.iqsocial.iq_message.ui.theme.Hairline
import com.iqsocial.iq_message.ui.theme.Ink
import com.iqsocial.iq_message.ui.theme.InkFaint
import com.iqsocial.iq_message.ui.theme.InkMuted
import com.iqsocial.iq_message.ui.theme.IqTheme
import com.iqsocial.iq_message.ui.theme.SystemBarIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * This account's QR code, and reading somebody else's.
 *
 * Two people who have never exchanged numbers can still start a conversation: one
 * shows a code, the other points a phone at it, and the server does the rest -
 * `POST /v1/qr/scan` adds the person and opens the chat in one answer, so this
 * screen never has to create a conversation itself.
 *
 * **The code is also text.** A ten-character code is short enough to read out or
 * paste, and the server takes it either way - case, spaces and dashes ignored - so
 * the screen shows it as well as drawing it. That is what makes this work over a
 * phone call, or when one of the two phones has no working camera.
 */
@Composable
fun QrScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    SystemBarIcons(darkIcons = true)
    val d = IqTheme.dimens
    val context = LocalContext.current
    val token = remember { SessionStore(context).token }
    val scope = rememberCoroutineScope()

    var mine by remember { mutableStateOf<QrCode?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(token) {
        val auth = token ?: return@LaunchedEffect
        when (val result = QrApi.mine(auth)) {
            is ApiResult.Success -> mine = result.data
            is ApiResult.Failure -> note = result.message
        }
    }

    // Drawn off the main thread: encoding is a few milliseconds of Reed-Solomon
    // and mask scoring, which is a few milliseconds nobody should spend on the
    // thread that is drawing.
    LaunchedEffect(mine?.code) {
        val code = mine?.code?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        image = withContext(Dispatchers.Default) { draw(code) }
    }

    /** Hands a scanned or pasted code to the server and opens what comes back. */
    fun use(code: String) {
        val auth = token ?: return
        if (code.isBlank() || busy) return
        busy = true
        note = null
        scope.launch {
            when (val result = QrApi.scan(auth, code.trim())) {
                is ApiResult.Success -> {
                    // Both halves of the answer are worth keeping: the person, so
                    // the chat has a name and a face before it fetches anything,
                    // and the list, because a conversation that did not exist a
                    // moment ago is now in it.
                    KnownPeople.remember(result.data.conversation.id, result.data.person)
                    ChatRepository.invalidate()
                    busy = false
                    onOpenChat(result.data.conversation.id)
                }

                is ApiResult.Failure -> {
                    busy = false
                    note = result.message
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CanvasGrey)
            .imePadding()
    ) {
        Column(Modifier.fillMaxWidth().background(Color.White)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = d.gapXs, end = d.gutter, top = d.gapSm, bottom = d.gapSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(
                    icon = IqIcons.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
                Spacer(Modifier.width(d.gapXs))
                Text(
                    "Your QR code",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                    maxLines = 1
                )
            }
            HairlineDivider()
        }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = d.gutterTight)
                .padding(top = d.gapLg, bottom = d.gapXl)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(d.radiusXl))
                    .background(Color.White)
                    .border(1.dp, Hairline, RoundedCornerShape(d.radiusXl))
                    .padding(d.gapMd),
                contentAlignment = Alignment.Center
            ) {
                val drawn = image
                if (drawn == null) {
                    // The code is a square and its size is already known, so the
                    // square itself is the honest placeholder: when the code
                    // arrives it lands exactly here, at exactly this size.
                    Box(Modifier.fillMaxSize().shimmer(RoundedCornerShape(d.radiusMd)))
                } else {
                    Image(
                        bitmap = drawn,
                        contentDescription = "Your QR code",
                        modifier = Modifier.fillMaxSize()
                    )
                    // The app's mark in the middle, so a code seen across a table
                    // says whose app it belongs to before anyone reads a word.
                    //
                    // It sits on a white plate a few dp wider than itself: a
                    // scanner reads the squares around the logo, and without that
                    // gap the mark's own edge can be taken for one more module.
                    // The size is deliberate, not a guess - a fifth of the width
                    // is about 5% of the area, while [draw] now leaves 30% of the
                    // code recoverable, so there is a wide margin between what the
                    // logo hides and what the code can lose.
                    //
                    // Drawn from `drawable/iq_logo`, which is the launcher
                    // artwork itself rather than the mipmap entry: from Android 8
                    // `@mipmap/ic_launcher` is an adaptive icon - two layers and a
                    // mask the system owns - and composing those by hand did not
                    // give back the mark anybody recognises. The flat file is the
                    // logo as it is seen everywhere else, and it carries its own
                    // rounded corners and transparency, so the white plate shows
                    // through them.
                    Box(
                        Modifier
                            .fillMaxSize(LOGO_SHARE)
                            .clip(RoundedCornerShape(28))
                            .background(Color.White)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.iq_logo),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(Modifier.height(d.gapMd))
            mine?.let { code ->
                Text(
                    code.spaced,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(d.gapXs))
                Text(
                    "Valid until ${until(code.expiresAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkFaint
                )
                Spacer(Modifier.height(d.gapMd))
                Row(horizontalArrangement = Arrangement.spacedBy(d.gapMd)) {
                    Pill(IqIcons.Copy, "Copy") { copy(context, code.code) }
                    Pill(IqIcons.Clock, "New code") {
                        val auth = token ?: return@Pill
                        scope.launch {
                            note = null
                            when (val fresh = QrApi.reset(auth)) {
                                is ApiResult.Success -> mine = fresh.data
                                is ApiResult.Failure -> note = fresh.message
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(d.gapXl))
            Text(
                "Scan someone's code",
                style = MaterialTheme.typography.titleMedium,
                color = InkMuted,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(d.gapSm))
            WideAction(
                label = if (busy) "Opening…" else "Open the camera",
                enabled = !busy
            ) {
                scan(context, onFound = { use(it) }, onProblem = { note = it })
            }

            Spacer(Modifier.height(d.gapMd))
            Text(
                "Or type the code they read out to you.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkFaint,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(d.gapXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(d.radiusMd))
                        .background(Color.White)
                        .border(1.dp, Hairline, RoundedCornerShape(d.radiusMd))
                        .padding(horizontal = d.gapMd, vertical = d.gapMd)
                ) {
                    if (typed.isEmpty()) {
                        Text(
                            "K7M2-Q9XA-4T",
                            style = MaterialTheme.typography.bodyLarge,
                            color = InkFaint
                        )
                    }
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it.take(MAX_CODE) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        cursorBrush = SolidColor(Crimson),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(d.gapSm))
                Pill(IqIcons.ArrowForward, "Open", enabled = typed.isNotBlank() && !busy) {
                    use(typed)
                }
            }

            note?.let {
                Spacer(Modifier.height(d.gapMd))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Crimson,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Opens Google's scanner and hands back what it read.
 *
 * Its own screen, its own camera, and no camera permission of this app's - which
 * is the whole reason for choosing it: asking for the camera in order to read ten
 * characters is a large thing to ask for a small errand, and a permission this app
 * holds is a permission it has to justify.
 *
 * The module downloads on first use, so a failure here is ordinary - a phone
 * without Play services, or with no room to fetch it - and the typed code below is
 * the way through that, which is why it is not hidden behind the camera.
 */
private fun scan(
    context: Context,
    onFound: (String) -> Unit,
    onProblem: (String) -> Unit
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    runCatching {
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { found ->
                val value = found.rawValue.orEmpty()
                if (value.isBlank()) onProblem("That code could not be read.")
                else onFound(value)
            }
            .addOnCanceledListener { }
            .addOnFailureListener {
                onProblem("The scanner could not start. Type the code instead.")
            }
    }.onFailure {
        onProblem("The scanner could not start. Type the code instead.")
    }
}

/**
 * The code as a square of black and white, with room for the logo over it.
 *
 * Quiet zone of one module rather than the usual four: the card it sits on is
 * already white and already padded, and four modules of margin inside that reads
 * as a small code in a large box.
 *
 * Correction level **H** rather than M, which is the price of the mark in the
 * middle. A QR code carries its data more than once; H keeps about 30% of it
 * recoverable instead of 15%, so the modules the logo covers are read back from
 * the rest. It costs a denser code - more, smaller squares for the same text -
 * which is why the plate over it is kept small.
 */
private fun draw(code: String): ImageBitmap? = runCatching {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, SIZE, SIZE, hints)
    val pixels = IntArray(SIZE * SIZE)
    for (y in 0 until SIZE) {
        val row = y * SIZE
        for (x in 0 until SIZE) {
            pixels[row + x] = if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }
    Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()

private fun copy(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("IQ Message code", code))
}

private fun until(epochMs: Long): String =
    if (epochMs <= 0) "tomorrow"
    else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun Pill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .clip(RoundedCornerShape(d.radiusLg))
            .background(if (enabled) Color.White else FieldGrey)
            .border(1.dp, Hairline, RoundedCornerShape(d.radiusLg))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = d.gapLg, vertical = d.gapSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(d.iconSm), tint = if (enabled) Crimson else InkFaint)
        Spacer(Modifier.width(d.gapXs))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Ink else InkFaint
        )
    }
}

@Composable
private fun WideAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    val d = IqTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .height(d.buttonHeight)
            .clip(RoundedCornerShape(d.radiusMd))
            .background(if (enabled) Crimson else Crimson.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(IqIcons.QrCode, null, Modifier.size(d.icon), tint = Color.White)
        Spacer(Modifier.width(d.gapSm))
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Big enough that a phone camera reads it off a screen without fuss. */
private const val SIZE = 640

/** The logo plate, as a share of the code's width. See where it is drawn. */
private const val LOGO_SHARE = 0.21f


/** The server's own ceiling for a pasted code. */
private const val MAX_CODE = 32
