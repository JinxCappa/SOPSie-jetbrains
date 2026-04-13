package com.sopsie.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Owns the scheduled executor used to debounce .sops.yaml change events.
 *
 * Lives as an application-level service so the platform disposes it on
 * dynamic plugin unload as well as IDE shutdown — registering against
 * `ApplicationManager.getApplication()` only fires at IDE shutdown, which
 * leaks the executor thread across plugin reloads.
 */
@Service(Service.Level.APP)
class SopsConfigDebouncer : Disposable {

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "SOPSie-ConfigDebounce").apply { isDaemon = true }
    }

    private val pendingEvents = ConcurrentHashMap<String, ScheduledFuture<*>>()

    /**
     * Cancel any pending action for [key] and schedule [action] to run
     * after [delayMs]. If a later call uses the same key before the
     * delay elapses, the earlier action is dropped.
     */
    fun debounce(key: String, delayMs: Long, action: () -> Unit) {
        pendingEvents[key]?.cancel(false)
        val future = scheduler.schedule({
            pendingEvents.remove(key)
            action()
        }, delayMs, TimeUnit.MILLISECONDS)
        pendingEvents[key] = future
    }

    override fun dispose() {
        pendingEvents.values.forEach { it.cancel(false) }
        pendingEvents.clear()
        scheduler.shutdownNow()
    }

    companion object {
        @JvmStatic
        fun getInstance(): SopsConfigDebouncer = service()
    }
}
