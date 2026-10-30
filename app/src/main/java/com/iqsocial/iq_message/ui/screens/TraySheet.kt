package com.iqsocial.iq_message.ui.screens

/**
 * The photo tray's travel, as arithmetic.
 *
 * Pulled out of the composable because none of it is about Compose: it is a height,
 * two stops, and the rules for moving between them. Kept pure so the behaviour can be
 * checked without a device - a gesture that only exists inside a `@Composable` can
 * only ever be verified by hand.
 *
 * **Sign convention**, which is the thing that is easy to get backwards: every `delta`
 * and `velocity` here is a *screen* quantity, positive downwards, exactly as Compose
 * reports them. A finger moving **up** is therefore negative, and must make the sheet
 * **taller** - hence the subtraction in [heightAfterDrag].
 *
 * There are three stops, not two: closed, short and tall. Closed is a real stop
 * rather than a special case, so pushing the sheet down off the bottom is the same
 * gesture as moving it between the other two, and the sheet follows the finger the
 * whole way instead of refusing to move once it is at its smallest.
 */
internal object TraySheet {

    /** Past this speed the throw picks the stop; under it the position does. */
    const val FLING = 420f

    /** Where a drag of [delta] leaves a sheet currently [height] tall. */
    fun heightAfterDrag(height: Float, delta: Float, short: Float, tall: Float): Float =
        (height - delta).coerceIn(0f, tall)

    /**
     * Which stop a release at [height] travelling at [velocity] should land on.
     *
     * Zero means closed. A throw downwards closes from the short stop but only
     * shortens from the tall one: a flick from full height is far more likely to
     * mean "give me the conversation back" than "put this away entirely", and
     * closing on it would lose the photos in one careless movement.
     */
    fun stopFor(height: Float, velocity: Float, short: Float, tall: Float): Float = when {
        velocity < -FLING -> tall
        velocity > FLING -> if (height > short) short else 0f
        height > (short + tall) / 2f -> tall
        // Dragged more than a third of the way down from the short stop.
        height < short * CLOSE_AT -> 0f
        else -> short
    }

    /**
     * How much of a scroll the sheet takes *before* the grid gets it.
     *
     * Only upward movement, and only while there is room to grow: swiping up inside
     * the photos should raise the sheet first, and start scrolling the grid once it
     * cannot rise any further.
     */
    fun preScrollTaken(height: Float, delta: Float, short: Float, tall: Float): Float {
        if (delta >= 0f || height >= tall) return 0f
        return height - heightAfterDrag(height, delta, short, tall)
    }

    /** Past this far down from the short stop, letting go closes it. */
    private const val CLOSE_AT = 0.66f

    /**
     * How much of the left-over scroll the sheet takes *after* the grid has had it.
     *
     * Anything left over going down means the grid is already at its top, which is the
     * moment the sheet should start shrinking rather than the gesture doing nothing.
     */
    fun postScrollTaken(height: Float, delta: Float, short: Float, tall: Float): Float {
        if (delta <= 0f || height <= short) return 0f
        // Only down to the short stop. Below that the sheet is on its way out, and
        // that should take a deliberate hand on it rather than a flick of the photos.
        return height - heightAfterDrag(height, delta, short, tall).coerceAtLeast(short)
    }

    /**
     * Whether a fling belongs to the sheet rather than the grid.
     *
     * Mid-travel it does, and the grid must not get it as well, or the photos scroll
     * away underneath a sheet that is still moving.
     */
    fun ownsFling(height: Float, short: Float, tall: Float): Boolean =
        height > short && height < tall
}
