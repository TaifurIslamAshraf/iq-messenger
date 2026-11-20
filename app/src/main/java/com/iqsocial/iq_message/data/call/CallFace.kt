package com.iqsocial.iq_message.data.call

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.iqsocial.iq_message.R

/**
 * The face on a call notification, drawn the way the app draws every other face.
 *
 * Shared by the two call notifications - the one that rings and the one that stays -
 * because a caller whose picture is a circle while ringing and a rounded square once
 * answered is the same person drawn by two different apps.
 *
 * **The circle is cut here rather than left to the system.** A bitmap handed over as
 * an *adaptive* icon is masked by whatever shape the phone uses for app icons, and on
 * a good many of them - this one included - that shape is a rounded square. A plain
 * bitmap takes no mask at all, so the only shape it has is the one it arrives with:
 * this one, with the corners already transparent.
 */
object CallFace {

    /** Comfortably larger than the 64dp a notification draws it at. */
    private const val SIZE_PX = 192

    /** The picture as an icon, or the initial when there is no picture. */
    fun icon(context: Context, bitmap: Bitmap?, name: String): IconCompat? {
        val face = bitmap?.let(::circle) ?: initials(context, name) ?: return null
        return IconCompat.createWithBitmap(face)
    }

    /** Crops to the largest circle the picture holds, from its middle. */
    fun circle(source: Bitmap): Bitmap? = runCatching {
        val size = minOf(source.width, source.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                Matrix().apply {
                    setTranslate((size - source.width) / 2f, (size - source.height) / 2f)
                }
            )
        }
        Canvas(out).drawCircle(
            size / 2f,
            size / 2f,
            size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        )
        out
    }.getOrNull()

    /**
     * The same circle the app draws for someone with no picture.
     *
     * A notification with no face is a different shape from one with it, so without
     * this the call notice would fall into line only for the people who happen to
     * have a photograph.
     */
    fun initials(context: Context, name: String): Bitmap? = runCatching {
        val out = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val middle = SIZE_PX / 2f
        canvas.drawCircle(
            middle,
            middle,
            middle,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.notification_accent)
            }
        )
        val letter = name.trim().firstOrNull()?.uppercase() ?: return@runCatching out
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = SIZE_PX * 0.44f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Centred on the circle, not on the baseline: text is drawn from its
        // baseline, and a letter placed at the middle sits a third of itself low.
        canvas.drawText(letter, middle, middle - (ink.descent() + ink.ascent()) / 2f, ink)
        out
    }.getOrNull()
}
