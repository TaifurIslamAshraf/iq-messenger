package com.iqsocial.iq_message.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A rounded 24dp icon family drawn in-project.
 *
 * Everything is built from circles, capsules and round-capped strokes — no square
 * terminals and no hard corners anywhere — so the set reads as one rounded system.
 * It lives here because the extended Material artifact is not on the classpath and
 * several glyphs the mockups need (waveform, flip camera, QR, double-tick) are not
 * in the core set either.
 */
object IqIcons {

    /* -- builders --------------------------------------------------------- */

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply(block).build()

    /** Solid shape. */
    private fun ImageVector.Builder.fill(
        fillType: PathFillType = PathFillType.NonZero,
        block: PathBuilder.() -> Unit
    ) {
        path(fill = SolidColor(Color.Black), pathFillType = fillType) { block() }
    }

    /** Round-capped, round-joined stroke — the backbone of the family. */
    private fun ImageVector.Builder.line(
        width: Float = 2f,
        block: PathBuilder.() -> Unit
    ) {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) { block() }
    }

    /* -- geometry helpers -------------------------------------------------- */

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    private fun PathBuilder.ellipse(cx: Float, cy: Float, rx: Float, ry: Float) {
        moveTo(cx - rx, cy)
        arcToRelative(rx, ry, 0f, true, true, 2 * rx, 0f)
        arcToRelative(rx, ry, 0f, true, true, -2 * rx, 0f)
        close()
    }

    private fun PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, cr: Float) {
        moveTo(l + cr, t)
        lineTo(r - cr, t)
        arcToRelative(cr, cr, 0f, false, true, cr, cr)
        lineTo(r, b - cr)
        arcToRelative(cr, cr, 0f, false, true, -cr, cr)
        lineTo(l + cr, b)
        arcToRelative(cr, cr, 0f, false, true, -cr, -cr)
        lineTo(l, t + cr)
        arcToRelative(cr, cr, 0f, false, true, cr, -cr)
        close()
    }

    /** Speech-bubble silhouette with a soft tail at the bottom-left. */
    private fun PathBuilder.bubble() {
        moveTo(8f, 3.8f)
        lineTo(16f, 3.8f)
        arcToRelative(4.6f, 4.6f, 0f, false, true, 4.6f, 4.6f)
        lineTo(20.6f, 12.6f)
        arcToRelative(4.6f, 4.6f, 0f, false, true, -4.6f, 4.6f)
        lineTo(9.4f, 17.2f)
        lineTo(4.9f, 20.6f)
        lineTo(3.4f, 15.4f)
        lineTo(3.4f, 8.4f)
        arcToRelative(4.6f, 4.6f, 0f, false, true, 4.6f, -4.6f)
        close()
    }

    /* -- navigation -------------------------------------------------------- */

    val ArrowBack = icon("ArrowBack") {
        line {
            moveTo(19.2f, 12f); lineTo(4.8f, 12f)
            moveTo(10.8f, 18f); lineTo(4.8f, 12f); lineTo(10.8f, 6f)
        }
    }

    val ArrowForward = icon("ArrowForward") {
        line {
            moveTo(4.8f, 12f); lineTo(19.2f, 12f)
            moveTo(13.2f, 6f); lineTo(19.2f, 12f); lineTo(13.2f, 18f)
        }
    }

    val ChevronRight = icon("ChevronRight") {
        line { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) }
    }

    val ChevronDown = icon("ChevronDown") {
        line { moveTo(5.5f, 9.5f); lineTo(12f, 16f); lineTo(18.5f, 9.5f) }
    }

    val MoreVert = icon("MoreVert") {
        fill {
            circle(12f, 4.9f, 1.85f)
            circle(12f, 12f, 1.85f)
            circle(12f, 19.1f, 1.85f)
        }
    }

    val Close = icon("Close") {
        line {
            moveTo(6.3f, 6.3f); lineTo(17.7f, 17.7f)
            moveTo(17.7f, 6.3f); lineTo(6.3f, 17.7f)
        }
    }

    val Search = icon("Search") {
        line {
            circle(10.6f, 10.6f, 6.9f)
            moveTo(15.7f, 15.7f); lineTo(20.4f, 20.4f)
        }
    }

    val Add = icon("Add") {
        line {
            moveTo(12f, 5.2f); lineTo(12f, 18.8f)
            moveTo(5.2f, 12f); lineTo(18.8f, 12f)
        }
    }

    val Check = icon("Check") {
        line(2.2f) { moveTo(5f, 12.6f); lineTo(9.6f, 17.2f); lineTo(19f, 7f) }
    }

    val DoneAll = icon("DoneAll") {
        line(1.9f) {
            moveTo(1.8f, 12.8f); lineTo(5.9f, 16.9f); lineTo(12.4f, 10.4f)
            moveTo(8.6f, 12.8f); lineTo(12.7f, 16.9f); lineTo(22.2f, 7.4f)
        }
    }

    /* -- tab bar ----------------------------------------------------------- */

    val ChatOutline = icon("ChatOutline") { line { bubble() } }

    val ChatFilled = icon("ChatFilled") { fill { bubble() } }

    val ChatCompose = icon("ChatCompose") {
        line {
            bubble()
            moveTo(9.2f, 13.6f); lineTo(9.2f, 11.6f); lineTo(13.4f, 7.4f)
            lineTo(15.4f, 9.4f); lineTo(11.2f, 13.6f); close()
        }
    }

    /** Outlined handset used by the tab bar. */
    val PhoneOutline = icon("PhoneOutline") {
        line {
            moveTo(20.6f, 16.9f)
            verticalLineTo(19.4f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, -2.1f, 1.9f)
            curveTo(10.4f, 20.4f, 3.6f, 13.6f, 2.7f, 5.5f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, 1.9f, -2.1f)
            horizontalLineTo(7.1f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, 1.9f, 1.6f)
            curveToRelative(0.1f, 0.9f, 0.4f, 1.8f, 0.7f, 2.7f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, -0.4f, 2f)
            lineTo(8f, 10.9f)
            arcToRelative(15.2f, 15.2f, 0f, false, false, 5.1f, 5.1f)
            lineTo(14.3f, 14.7f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, 2f, -0.4f)
            curveToRelative(0.9f, 0.3f, 1.8f, 0.6f, 2.7f, 0.7f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, 1.6f, 1.9f)
            close()
        }
    }

    /** Solid handset — call buttons, and rotated for hang-up. */
    val Phone = icon("Phone") {
        fill {
            moveTo(20.6f, 16.9f)
            verticalLineTo(19.4f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, -2.1f, 1.9f)
            curveTo(10.4f, 20.4f, 3.6f, 13.6f, 2.7f, 5.5f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, 1.9f, -2.1f)
            horizontalLineTo(7.1f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, 1.9f, 1.6f)
            curveToRelative(0.1f, 0.9f, 0.4f, 1.8f, 0.7f, 2.7f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, -0.4f, 2f)
            lineTo(8f, 10.9f)
            arcToRelative(15.2f, 15.2f, 0f, false, false, 5.1f, 5.1f)
            lineTo(14.3f, 14.7f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, 2f, -0.4f)
            curveToRelative(0.9f, 0.3f, 1.8f, 0.6f, 2.7f, 0.7f)
            arcToRelative(1.9f, 1.9f, 0f, false, true, 1.6f, 1.9f)
            close()
        }
    }

    val CallEnd = Phone

    val PersonOutline = icon("PersonOutline") {
        line {
            circle(12f, 8.2f, 3.7f)
            moveTo(4.9f, 20f)
            curveTo(4.9f, 16.4f, 8.1f, 14.6f, 12f, 14.6f)
            curveTo(15.9f, 14.6f, 19.1f, 16.4f, 19.1f, 20f)
        }
    }

    val Person = icon("Person") {
        fill {
            circle(12f, 8.2f, 4f)
            moveTo(4.4f, 20.4f)
            curveTo(4.4f, 16.4f, 8f, 14.4f, 12f, 14.4f)
            curveTo(16f, 14.4f, 19.6f, 16.4f, 19.6f, 20.4f)
            arcToRelative(1.2f, 1.2f, 0f, false, true, -1.2f, 1.2f)
            horizontalLineTo(5.6f)
            arcToRelative(1.2f, 1.2f, 0f, false, true, -1.2f, -1.2f)
            close()
        }
    }

    val People = icon("People") {
        line {
            circle(9f, 8.2f, 3.4f)
            moveTo(2.6f, 19.8f)
            curveTo(2.6f, 16.4f, 5.6f, 14.7f, 9f, 14.7f)
            curveTo(12.4f, 14.7f, 15.4f, 16.4f, 15.4f, 19.8f)
            moveTo(16f, 5.3f)
            arcToRelative(3.4f, 3.4f, 0f, false, true, 0f, 5.9f)
            moveTo(17.4f, 15.1f)
            curveTo(20f, 15.7f, 21.6f, 17.3f, 21.6f, 19.8f)
        }
    }

    val PeopleFilled = icon("PeopleFilled") {
        fill {
            circle(9f, 8.2f, 3.7f)
            moveTo(2.4f, 20.3f)
            curveTo(2.4f, 16.5f, 5.5f, 14.6f, 9f, 14.6f)
            curveTo(12.5f, 14.6f, 15.6f, 16.5f, 15.6f, 20.3f)
            arcToRelative(1.1f, 1.1f, 0f, false, true, -1.1f, 1.1f)
            horizontalLineTo(3.5f)
            arcToRelative(1.1f, 1.1f, 0f, false, true, -1.1f, -1.1f)
            close()
            circle(17.2f, 8.2f, 3.1f)
            moveTo(17.4f, 14.7f)
            curveTo(20.1f, 15.2f, 21.7f, 17f, 21.7f, 20.3f)
            arcToRelative(1.1f, 1.1f, 0f, false, true, -1.1f, 1.1f)
            horizontalLineTo(17.4f)
            close()
        }
    }

    val PersonAdd = icon("PersonAdd") {
        line {
            circle(10f, 8.2f, 3.5f)
            moveTo(3.4f, 20f)
            curveTo(3.4f, 16.5f, 6.4f, 14.7f, 10f, 14.7f)
            curveTo(11.2f, 14.7f, 12.3f, 14.9f, 13.3f, 15.2f)
            moveTo(18.4f, 14.6f); lineTo(18.4f, 21f)
            moveTo(15.2f, 17.8f); lineTo(21.6f, 17.8f)
        }
    }

    /** Rounded gear: a ring, eight round-capped teeth and a centre dot. */
    val SettingsOutline = icon("SettingsOutline") { gear(2f) }
    val Settings = icon("Settings") { gear(2.6f) }

    private fun ImageVector.Builder.gear(width: Float) {
        line(width) {
            circle(12f, 12f, 6.4f)
            circle(12f, 12f, 2.5f)
            // eight teeth, at 45 degree steps
            moveTo(12f, 5.6f); lineTo(12f, 2.9f)
            moveTo(12f, 18.4f); lineTo(12f, 21.1f)
            moveTo(5.6f, 12f); lineTo(2.9f, 12f)
            moveTo(18.4f, 12f); lineTo(21.1f, 12f)
            moveTo(7.5f, 7.5f); lineTo(5.6f, 5.6f)
            moveTo(16.5f, 16.5f); lineTo(18.4f, 18.4f)
            moveTo(7.5f, 16.5f); lineTo(5.6f, 18.4f)
            moveTo(16.5f, 7.5f); lineTo(18.4f, 5.6f)
        }
    }

    /* -- calls ------------------------------------------------------------- */

    val Videocam = icon("Videocam") {
        fill {
            roundRect(2.6f, 6.2f, 15.6f, 17.8f, 3.4f)
            moveTo(17.2f, 11.1f)
            lineTo(20.3f, 8.4f)
            arcToRelative(1.1f, 1.1f, 0f, false, true, 1.8f, 0.8f)
            verticalLineTo(14.8f)
            arcToRelative(1.1f, 1.1f, 0f, false, true, -1.8f, 0.8f)
            lineTo(17.2f, 12.9f)
            close()
        }
    }

    val VideocamOutline = icon("VideocamOutline") {
        line {
            roundRect(2.9f, 6.5f, 15.3f, 17.5f, 3.4f)
            moveTo(17.2f, 11.2f)
            lineTo(20.3f, 8.6f)
            arcToRelative(1f, 1f, 0f, false, true, 1.7f, 0.8f)
            verticalLineTo(14.6f)
            arcToRelative(1f, 1f, 0f, false, true, -1.7f, 0.8f)
            lineTo(17.2f, 12.8f)
            close()
        }
    }

    val Mic = icon("Mic") {
        fill { roundRect(8.7f, 2.2f, 15.3f, 14.4f, 3.3f) }
        line {
            moveTo(5.4f, 11.2f)
            curveTo(5.4f, 14.9f, 8.4f, 17.8f, 12f, 17.8f)
            curveTo(15.6f, 17.8f, 18.6f, 14.9f, 18.6f, 11.2f)
            moveTo(12f, 17.8f); lineTo(12f, 21.4f)
        }
    }

    val MicOff = icon("MicOff") {
        fill { roundRect(8.7f, 2.2f, 15.3f, 14.4f, 3.3f) }
        line {
            moveTo(5.4f, 11.2f)
            curveTo(5.4f, 14.9f, 8.4f, 17.8f, 12f, 17.8f)
            curveTo(15.6f, 17.8f, 18.6f, 14.9f, 18.6f, 11.2f)
            moveTo(12f, 17.8f); lineTo(12f, 21.4f)
        }
        line(2.2f) { moveTo(3.6f, 3.2f); lineTo(20.4f, 20.8f) }
    }

    val Speaker = icon("Speaker") {
        fill {
            moveTo(11.2f, 4.7f)
            arcToRelative(1.4f, 1.4f, 0f, false, true, 2.3f, 1.1f)
            verticalLineTo(18.2f)
            arcToRelative(1.4f, 1.4f, 0f, false, true, -2.3f, 1.1f)
            lineTo(7.1f, 15.9f)
            horizontalLineTo(4.6f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, -1.8f, -1.8f)
            verticalLineTo(9.9f)
            arcToRelative(1.8f, 1.8f, 0f, false, true, 1.8f, -1.8f)
            horizontalLineTo(7.1f)
            close()
        }
        line {
            moveTo(16.6f, 9.2f); curveTo(17.8f, 10.2f, 17.8f, 13.8f, 16.6f, 14.8f)
            moveTo(19.3f, 6.4f); curveTo(21.6f, 8.5f, 21.6f, 15.5f, 19.3f, 17.6f)
        }
    }

    val FlipCamera = icon("FlipCamera") {
        line {
            roundRect(2.9f, 6.6f, 21.1f, 20.2f, 3.8f)
            moveTo(8.6f, 6.6f); lineTo(9.9f, 4.2f); lineTo(14.1f, 4.2f); lineTo(15.4f, 6.6f)
            circle(12f, 13.4f, 3.3f)
            moveTo(13.6f, 10.5f); lineTo(15.4f, 10.2f); lineTo(15.2f, 12f)
        }
    }

    val CallMade = icon("CallMade") {
        line {
            moveTo(6f, 18f); lineTo(17.6f, 6.4f)
            moveTo(9.6f, 6.4f); lineTo(17.6f, 6.4f); lineTo(17.6f, 14.4f)
        }
    }

    val CallReceived = icon("CallReceived") {
        line {
            moveTo(18f, 6f); lineTo(6.4f, 17.6f)
            moveTo(14.4f, 17.6f); lineTo(6.4f, 17.6f); lineTo(6.4f, 9.6f)
        }
    }

    val CallMissed = icon("CallMissed") {
        line {
            moveTo(20f, 7f); lineTo(12f, 15f); lineTo(8f, 11f); lineTo(4f, 15f)
            moveTo(20f, 12.4f); lineTo(20f, 7f); lineTo(14.6f, 7f)
        }
    }

    /* -- composer ---------------------------------------------------------- */

    val AttachFile = icon("AttachFile") {
        line {
            moveTo(16.6f, 7.4f)
            verticalLineTo(16.8f)
            curveTo(16.6f, 19.4f, 14.5f, 21.5f, 12f, 21.5f)
            curveTo(9.5f, 21.5f, 7.4f, 19.4f, 7.4f, 16.8f)
            verticalLineTo(6.3f)
            curveTo(7.4f, 4.5f, 8.8f, 3.1f, 10.5f, 3.1f)
            curveTo(12.2f, 3.1f, 13.6f, 4.5f, 13.6f, 6.3f)
            verticalLineTo(16.4f)
            curveTo(13.6f, 17.3f, 12.9f, 18f, 12f, 18f)
            curveTo(11.1f, 18f, 10.4f, 17.3f, 10.4f, 16.4f)
            verticalLineTo(7.4f)
        }
    }

    val Emoji = icon("Emoji") {
        line { circle(12f, 12f, 9f) }
        fill {
            circle(9.1f, 10f, 1.2f)
            circle(14.9f, 10f, 1.2f)
        }
        line {
            moveTo(8.2f, 14.4f)
            curveTo(9.1f, 15.9f, 10.4f, 16.7f, 12f, 16.7f)
            curveTo(13.6f, 16.7f, 14.9f, 15.9f, 15.8f, 14.4f)
        }
    }

    val CameraOutline = icon("CameraOutline") {
        line {
            roundRect(2.6f, 6.6f, 21.4f, 20.2f, 3.9f)
            moveTo(8.6f, 6.6f); lineTo(9.9f, 4.2f); lineTo(14.1f, 4.2f); lineTo(15.4f, 6.6f)
            circle(12f, 13.4f, 3.6f)
        }
    }

    val Camera = icon("Camera") {
        fill(PathFillType.EvenOdd) {
            // EvenOdd so the lens reads as a hole rather than a solid blob.
            circle(12f, 13.4f, 3.6f)
            moveTo(9.3f, 3.2f)
            horizontalLineTo(14.7f)
            lineTo(16.2f, 6f)
            horizontalLineTo(19.8f)
            arcToRelative(3.4f, 3.4f, 0f, false, true, 3.4f, 3.4f)
            verticalLineTo(17.4f)
            arcToRelative(3.4f, 3.4f, 0f, false, true, -3.4f, 3.4f)
            horizontalLineTo(4.2f)
            arcToRelative(3.4f, 3.4f, 0f, false, true, -3.4f, -3.4f)
            verticalLineTo(9.4f)
            arcToRelative(3.4f, 3.4f, 0f, false, true, 3.4f, -3.4f)
            horizontalLineTo(7.8f)
            close()
        }
    }

    val Send = icon("Send") {
        fill {
            moveTo(19.3f, 3.3f)
            arcToRelative(1.3f, 1.3f, 0f, false, true, 1.4f, 1.6f)
            lineTo(15.4f, 20.5f)
            arcToRelative(1.3f, 1.3f, 0f, false, true, -2.4f, 0.1f)
            lineTo(10.3f, 14.4f)
            lineTo(4.1f, 11.7f)
            arcToRelative(1.3f, 1.3f, 0f, false, true, 0.1f, -2.4f)
            close()
        }
    }

    val PaperPlane = Send

    val PlayArrow = icon("PlayArrow") {
        fill {
            moveTo(8.4f, 5.3f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, 2.3f, -1.3f)
            lineTo(18.5f, 10.7f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, 0f, 2.6f)
            lineTo(10.7f, 20f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, -2.3f, -1.3f)
            close()
        }
    }

    val Favorite = icon("Favorite") {
        fill {
            moveTo(12f, 20.7f)
            curveTo(6.2f, 16.4f, 2.8f, 13.2f, 2.8f, 9.1f)
            arcTo(4.9f, 4.9f, 0f, false, true, 12f, 6.4f)
            arcTo(4.9f, 4.9f, 0f, false, true, 21.2f, 9.1f)
            curveTo(21.2f, 13.2f, 17.8f, 16.4f, 12f, 20.7f)
            close()
        }
    }

    /* -- attachment sheet --------------------------------------------------- */

    val DocumentOutline = icon("DocumentOutline") {
        line {
            moveTo(13.6f, 2.8f)
            horizontalLineTo(7.6f)
            arcToRelative(3f, 3f, 0f, false, false, -3f, 3f)
            verticalLineTo(18.2f)
            arcToRelative(3f, 3f, 0f, false, false, 3f, 3f)
            horizontalLineTo(16.4f)
            arcToRelative(3f, 3f, 0f, false, false, 3f, -3f)
            verticalLineTo(8.6f)
            close()
            moveTo(13.6f, 2.8f); lineTo(13.6f, 8.6f); lineTo(19.4f, 8.6f)
            moveTo(8.6f, 13.4f); lineTo(15.4f, 13.4f)
            moveTo(8.6f, 17f); lineTo(13.4f, 17f)
        }
    }

    val Document = DocumentOutline

    val LocationPin = icon("LocationPin") {
        line {
            moveTo(12f, 21.2f)
            curveTo(16.5f, 16.6f, 19f, 13.1f, 19f, 10f)
            arcTo(7f, 7f, 0f, false, false, 5f, 10f)
            curveTo(5f, 13.1f, 7.5f, 16.6f, 12f, 21.2f)
            close()
            circle(12f, 9.9f, 2.6f)
        }
    }

    val Waveform = icon("Waveform") {
        line(2.4f) {
            moveTo(3.6f, 10f); lineTo(3.6f, 14f)
            moveTo(7.8f, 6.8f); lineTo(7.8f, 17.2f)
            moveTo(12f, 3.4f); lineTo(12f, 20.6f)
            moveTo(16.2f, 6.8f); lineTo(16.2f, 17.2f)
            moveTo(20.4f, 10f); lineTo(20.4f, 14f)
        }
    }

    val Gallery = icon("Gallery") {
        line {
            roundRect(3f, 3.8f, 21f, 20.2f, 4f)
            moveTo(3.6f, 17.4f); lineTo(9f, 12.4f); lineTo(13.4f, 16.3f)
            lineTo(16.2f, 13.8f); lineTo(20.4f, 17.8f)
        }
        fill { circle(8.9f, 9.4f, 1.7f) }
    }

    /* -- settings & trust ---------------------------------------------------- */

    private fun PathBuilder.shield() {
        moveTo(12f, 2.2f)
        curveToRelative(-0.3f, 0f, -0.5f, 0.1f, -0.7f, 0.2f)
        lineTo(5.2f, 5f)
        curveTo(4.5f, 5.3f, 4.1f, 6f, 4.1f, 6.7f)
        verticalLineTo(11.4f)
        curveTo(4.1f, 16.3f, 7.4f, 20.7f, 11.6f, 21.7f)
        curveToRelative(0.3f, 0.1f, 0.5f, 0.1f, 0.8f, 0f)
        curveTo(16.6f, 20.7f, 19.9f, 16.3f, 19.9f, 11.4f)
        verticalLineTo(6.7f)
        curveToRelative(0f, -0.7f, -0.4f, -1.4f, -1.1f, -1.7f)
        lineTo(12.7f, 2.4f)
        curveToRelative(-0.2f, -0.1f, -0.4f, -0.2f, -0.7f, -0.2f)
        close()
    }

    val Shield = icon("Shield") { fill { shield() } }

    val ShieldOutline = icon("ShieldOutline") { line { shield() } }

    val ShieldCheck = icon("ShieldCheck") {
        line { shield() }
        line(2.2f) { moveTo(8.6f, 11.8f); lineTo(11.2f, 14.4f); lineTo(15.6f, 10f) }
    }

    val Bell = icon("Bell") {
        line {
            moveTo(18.6f, 17.4f)
            verticalLineTo(11f)
            curveTo(18.6f, 7.6f, 16.3f, 5.1f, 13.2f, 4.5f)
            verticalLineTo(3.7f)
            arcToRelative(1.2f, 1.2f, 0f, false, false, -2.4f, 0f)
            verticalLineTo(4.5f)
            curveTo(7.7f, 5.1f, 5.4f, 7.6f, 5.4f, 11f)
            verticalLineTo(17.4f)
            lineTo(4f, 18.9f)
            horizontalLineTo(20f)
            close()
            moveTo(9.8f, 19.6f)
            curveTo(10.1f, 20.9f, 11f, 21.7f, 12f, 21.7f)
            curveTo(13f, 21.7f, 13.9f, 20.9f, 14.2f, 19.6f)
        }
    }

    val Storage = icon("Storage") {
        line {
            ellipse(12f, 5.6f, 7.8f, 2.7f)
            moveTo(4.2f, 5.6f)
            verticalLineTo(18.4f)
            curveTo(4.2f, 19.9f, 7.7f, 21.1f, 12f, 21.1f)
            curveTo(16.3f, 21.1f, 19.8f, 19.9f, 19.8f, 18.4f)
            verticalLineTo(5.6f)
            moveTo(4.2f, 12f)
            curveTo(4.2f, 13.5f, 7.7f, 14.7f, 12f, 14.7f)
            curveTo(16.3f, 14.7f, 19.8f, 13.5f, 19.8f, 12f)
        }
    }

    val HelpCircle = icon("HelpCircle") {
        line {
            circle(12f, 12f, 9f)
            moveTo(9.3f, 9.4f)
            curveTo(9.3f, 7.9f, 10.5f, 6.8f, 12f, 6.8f)
            curveTo(13.6f, 6.8f, 14.8f, 7.9f, 14.8f, 9.4f)
            curveTo(14.8f, 11.5f, 12f, 11.5f, 12f, 14.1f)
        }
        fill { circle(12f, 17.3f, 1.15f) }
    }

    val Logout = icon("Logout") {
        line {
            moveTo(14.4f, 3.4f)
            horizontalLineTo(6.6f)
            arcToRelative(2.6f, 2.6f, 0f, false, false, -2.6f, 2.6f)
            verticalLineTo(18f)
            arcToRelative(2.6f, 2.6f, 0f, false, false, 2.6f, 2.6f)
            horizontalLineTo(14.4f)
            moveTo(10.6f, 12f); lineTo(21f, 12f)
            moveTo(17.6f, 8.6f); lineTo(21f, 12f); lineTo(17.6f, 15.4f)
        }
    }

    val Edit = icon("Edit") {
        line {
            moveTo(4.2f, 19.8f)
            verticalLineTo(16.6f)
            lineTo(16.1f, 4.7f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, 2.3f, 0f)
            lineTo(19.3f, 5.6f)
            arcToRelative(1.6f, 1.6f, 0f, false, true, 0f, 2.3f)
            lineTo(7.4f, 19.8f)
            close()
            moveTo(14.4f, 6.4f); lineTo(17.6f, 9.6f)
        }
    }

    val Lock = icon("Lock") {
        line {
            roundRect(4.2f, 10.2f, 19.8f, 21.2f, 3.4f)
            moveTo(7.8f, 10.2f)
            verticalLineTo(7.4f)
            arcTo(4.2f, 4.2f, 0f, false, true, 16.2f, 7.4f)
            verticalLineTo(10.2f)
            moveTo(12f, 14.8f); lineTo(12f, 17.4f)
        }
    }

    val Clock = icon("Clock") {
        line {
            circle(12f, 12f, 9f)
            moveTo(12f, 6.9f); lineTo(12f, 12.2f); lineTo(15.6f, 14.4f)
        }
    }

    val Verified = icon("Verified") {
        fill(PathFillType.EvenOdd) {
            circle(12f, 12f, 9.6f)
            moveTo(10.6f, 16.3f)
            lineTo(6.5f, 12.2f)
            lineTo(8.3f, 10.4f)
            lineTo(10.6f, 12.7f)
            lineTo(15.8f, 7.5f)
            lineTo(17.6f, 9.3f)
            close()
        }
    }

    val QrCode = icon("QrCode") {
        line {
            roundRect(3.2f, 3.2f, 10f, 10f, 2f)
            roundRect(14f, 3.2f, 20.8f, 10f, 2f)
            roundRect(3.2f, 14f, 10f, 20.8f, 2f)
        }
        fill {
            circle(6.6f, 6.6f, 1.4f)
            circle(17.4f, 6.6f, 1.4f)
            circle(6.6f, 17.4f, 1.4f)
        }
        line(2.2f) {
            moveTo(14f, 14.2f); lineTo(14f, 16.4f)
            moveTo(17.4f, 14.2f); lineTo(20.8f, 14.2f)
            moveTo(20.8f, 17.6f); lineTo(20.8f, 20.6f)
            moveTo(14f, 20.6f); lineTo(17.2f, 20.6f)
            moveTo(17.3f, 17.6f); lineTo(17.4f, 17.6f)
        }
    }
}
