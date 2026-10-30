package com.iqsocial.iq_message.data.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * The three things a composition needs that a `WindowManager` window does not have.
 *
 * An overlay window is not an activity and not a fragment: it is a view handed to
 * the window manager, with no lifecycle, no view-model store and no saved-state
 * registry. A `ComposeView` wants all three from its view tree and fails outright
 * without them, which is why [ChatHeads] draws its circles by hand.
 *
 * A circle is one thing to draw by hand. A whole chat - a list, a keyboard, a
 * recorder, a dozen sheets - is not, so the owners are supplied here instead and the
 * real screen is composed into the window as it is anywhere else.
 *
 * Deliberately not restoring anything. [SavedStateRegistryController.performRestore]
 * is called with no state because there is none to bring back: the window is created
 * when it is opened and destroyed when it is closed, and a chat that reopened holding
 * a half-typed message from a previous session would be remembering something nobody
 * asked it to.
 */
internal class OverlayHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val saved = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    /** Straight to resumed: the window is only ever added when it is to be seen. */
    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    /**
     * Ends it properly, in that order.
     *
     * Destroying the lifecycle is what tells the composition to leave - every
     * `DisposableEffect` in the chat runs its `onDispose` here, which is what stops
     * the socket listeners and the voice player it started. Clearing the store after
     * is the other half: nothing is listening by then, so nothing is torn out from
     * under a running effect.
     */
    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
