package com.iqsocial.iq_message.data.media

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * A screen's media state, independent of recycled rows. Access on the UI thread.
 * A row starts a load but does not own it; completed values and failures survive
 * that row leaving composition. Old results are evicted under a bounded budget.
 */
internal class RetainedMediaCache<K, V>(
    owner: CoroutineScope,
    private val maxBytes: Long,
    private val maxEntries: Int = 128,
    private val sizeOf: (V) -> Long,
    private val peek: (K) -> V?,
    private val fetch: suspend (K) -> V?
) : Closeable {
    private class Entry<V>(initial: V?) {
        val state = mutableStateOf(initial)
        var started = initial != null
        var complete = initial != null
    }

    private val scope = CoroutineScope(owner.coroutineContext + SupervisorJob(owner.coroutineContext[Job]))
    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)
    private var closed = false

    init {
        require(maxBytes > 0 && maxEntries > 0)
    }

    private fun entry(key: K): Entry<V> {
        check(!closed)
        return entries.getOrPut(key) { Entry(peek(key)) }
    }

    fun state(key: K): State<V?> = entry(key).state

    /** Call after composition. Returning or cancelling a row does not cancel this load. */
    fun load(key: K) {
        val entry = entry(key)
        if (entry.started) {
            trim()
            return
        }
        entry.started = true
        scope.launch {
            val result = try {
                fetch(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            ensureActive()
            entry.state.value = result
            entry.complete = true
            trim()
        }
    }

    private fun trim() {
        var bytes = entries.values.sumOf { it.state.value?.let(sizeOf) ?: 0L }
        val iterator = entries.entries.iterator()
        while ((bytes > maxBytes || entries.size > maxEntries) && iterator.hasNext()) {
            val entry = iterator.next().value
            // Keep a running request so recycling cannot start a second one.
            if (!entry.complete) continue
            bytes -= entry.state.value?.let(sizeOf) ?: 0L
            iterator.remove()
            // A visible row still owns its State; eviction must not blank that row.
        }
    }

    override fun close() {
        closed = true
        scope.cancel()
        entries.clear()
    }
}
