package com.kove.mirror

import android.graphics.Bitmap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Latest turn-by-turn instruction coming from an external navigation app
 * (Google Maps, Waze, ...) as read by [NavNotificationListener].
 *
 * The TFT presentation ([KovePresentation]) shows this in its navigation banner whenever
 * the app's own internal navigation is not running, which means Google Maps guidance keeps
 * working on the bike screen while the phone is locked in a pocket.
 */
object ExternalNavState {

    data class Maneuver(
        /** Package name of the app that produced it, e.g. com.google.android.apps.maps */
        val sourcePackage: String,
        /** Short instruction, e.g. "Turn left" or "Zavijte levo". */
        val instruction: String,
        /** Road / street the manoeuvre leads onto, may be empty. */
        val road: String,
        /** Distance to the manoeuvre as shown by the app, e.g. "200 m" (already localized). */
        val distanceText: String,
        /** Distance in metres if it could be parsed, otherwise -1. */
        val distanceMeters: Int,
        /** ETA / remaining line, e.g. "12 min · 5,3 km · 09:41", may be empty. */
        val etaText: String,
        /** Manoeuvre arrow rendered by the navigation app, may be null. */
        val icon: Bitmap?,
        val timestampMs: Long = System.currentTimeMillis()
    )

    interface Listener {
        fun onExternalNavChanged(maneuver: Maneuver?)
    }

    /**
     * Consider the data stale if the nav app has not updated it for this long. The primary
     * "navigation ended" signal is the notification being removed; this is only a safety net.
     */
    private const val STALE_AFTER_MS = 10 * 60_000L

    @Volatile var current: Maneuver? = null
        private set

    private val listeners = CopyOnWriteArrayList<Listener>()

    /** True while an external navigation session is active and fresh. */
    val isActive: Boolean
        get() {
            val m = current ?: return false
            return System.currentTimeMillis() - m.timestampMs < STALE_AFTER_MS
        }

    fun update(maneuver: Maneuver) {
        current = maneuver
        notifyListeners(maneuver)
    }

    fun clear(sourcePackage: String? = null) {
        val cur = current ?: return
        if (sourcePackage != null && cur.sourcePackage != sourcePackage) return
        current = null
        notifyListeners(null)
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(maneuver: Maneuver?) {
        for (l in listeners) {
            try {
                l.onExternalNavChanged(maneuver)
            } catch (_: Exception) {
            }
        }
    }
}
