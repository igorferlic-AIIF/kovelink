package com.kove.mirror

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Locale

/**
 * Listens to the ongoing turn-by-turn notification of Google Maps (and, best effort, Waze)
 * and publishes the next manoeuvre to [ExternalNavState].
 *
 * Why: Google Maps cannot be launched on our VirtualDisplay (its activities are not
 * `allowEmbedded`), and MediaProjection stops delivering frames once the phone is locked.
 * The navigation notification, however, keeps updating in the background, so we read it and
 * draw the instruction on the TFT map ourselves. Routing stays Google's, rendering is ours.
 *
 * The user has to grant "Notification access" once (Settings → Notifications → Device & app
 * notifications). Nothing is stored and nothing leaves the phone except the bike link.
 */
class NavNotificationListener : NotificationListenerService() {

    companion object {
        const val PKG_GOOGLE_MAPS = "com.google.android.apps.maps"
        const val PKG_WAZE = "com.waze"

        private val WATCHED_PACKAGES = setOf(PKG_GOOGLE_MAPS, PKG_WAZE)

        // "200 m", "1,5 km", "0.3 mi", "500 ft" - locale aware decimal separator.
        private val DISTANCE_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|mi|ft|yd)\b""", RegexOption.IGNORE_CASE)

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, NavNotificationListener::class.java)
            return flat.split(':').any { entry ->
                val cn = ComponentName.unflattenFromString(entry)
                cn != null && cn == me
            }
        }

        /** Parse "1,5 km" / "200 m" / "0.3 mi" into metres, -1 when not parseable. */
        fun parseDistanceMeters(text: CharSequence?): Int {
            if (text.isNullOrBlank()) return -1
            val m = DISTANCE_REGEX.find(text) ?: return -1
            val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return -1
            return when (m.groupValues[2].lowercase(Locale.US)) {
                "km" -> (value * 1000).toInt()
                "m" -> value.toInt()
                "mi" -> (value * 1609.344).toInt()
                "ft" -> (value * 0.3048).toInt()
                "yd" -> (value * 0.9144).toInt()
                else -> -1
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        DebugLogger.info("🔔 Navigation notification listener connected")
        // A navigation may already be running when the user grants access.
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in WATCHED_PACKAGES) return
        if (ExternalNavState.current?.notificationKey == sbn.key) {
            DebugLogger.info("🔔 ${sbn.packageName} guidance notification removed → banner off")
            ExternalNavState.clear(sbn.key)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // We can no longer see updates or removals, so never show a possibly stale turn.
        DebugLogger.warning("🔔 Navigation notification listener disconnected")
        ExternalNavState.clear()
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg !in WATCHED_PACKAGES) return
        val n = sbn.notification ?: return

        // Only the ongoing guidance notification is interesting; traffic alerts, "share
        // location" prompts etc. are not ongoing and would flicker the banner.
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 || sbn.isOngoing
        if (!ongoing) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()

        if (title.isEmpty() && text.isEmpty()) return

        // Both apps post other ongoing notifications (location sharing, "Waze is active",
        // driving mode). Only real guidance carries CATEGORY_NAVIGATION or a distance.
        val isNavigationCategory = n.category == Notification.CATEGORY_NAVIGATION
        val hasDistance = DISTANCE_REGEX.containsMatchIn(title) || DISTANCE_REGEX.containsMatchIn(text)
        if (!isNavigationCategory && !hasDistance) return
        // A different notification from the same app must not replace live guidance.
        val cur = ExternalNavState.current
        if (cur != null && cur.notificationKey.isNotEmpty() && cur.notificationKey != sbn.key &&
            !isNavigationCategory && ExternalNavState.isActive
        ) return

        // Google Maps layouts seen in the wild (they change between versions):
        //   A) title = "200 m"              text = "Turn left onto Tržaška cesta"  sub = "12 min · 5,3 km · 09:41"
        //   B) title = "Turn left"          text = "200 m · Tržaška cesta"
        //   C) title = "200 m – Turn left"  text = "onto Tržaška cesta"
        // Strategy: find the distance anywhere, strip it, the remainder is instruction + road.
        val titleDist = DISTANCE_REGEX.find(title)
        val textDist = DISTANCE_REGEX.find(text)
        val distanceText = (titleDist ?: textDist)?.value.orEmpty()
        val distanceMeters = parseDistanceMeters(distanceText)

        fun stripDistance(s: String): String =
            s.replace(DISTANCE_REGEX, "")
                .replace(Regex("""^[\s·•\-–:,]+|[\s·•\-–:,]+$"""), "")
                .replace(Regex("""\s{2,}"""), " ")
                .trim()

        val titleRest = stripDistance(title)
        val textRest = stripDistance(text)

        val instruction: String
        val road: String
        when {
            titleRest.isNotEmpty() && textRest.isNotEmpty() -> {
                instruction = titleRest
                road = textRest
            }
            titleRest.isNotEmpty() -> {
                instruction = titleRest
                road = ""
            }
            else -> {
                // Split "Turn left onto X" / "Zavijte levo na X" at an "onto"-style word, but only
                // when a street name (capitalised or numbered) follows - "Gire a la derecha" must
                // stay one instruction.
                // No IGNORE_CASE: Java would then also case-fold \p{Lu} and defeat the lookahead.
                val split = Regex("""\s+(?:[oO]nto|[nN]a|[aA]uf|[vV]ers|[vV]erso)\s+(?=[\p{Lu}0-9])""").find(textRest)
                if (split != null) {
                    instruction = textRest.substring(0, split.range.first).trim()
                    road = textRest.substring(split.range.last + 1).trim()
                } else {
                    instruction = textRest
                    road = ""
                }
            }
        }

        val eta = if (subText.isNotEmpty()) subText else if (bigText.isNotEmpty() && bigText != text) bigText else ""

        val icon = try {
            loadLargeIcon(n)
        } catch (_: Exception) {
            null
        }

        val maneuver = ExternalNavState.Maneuver(
            sourcePackage = pkg,
            instruction = instruction.ifEmpty { title.ifEmpty { text } },
            road = road,
            distanceText = distanceText,
            distanceMeters = distanceMeters,
            etaText = eta,
            icon = icon,
            notificationKey = sbn.key ?: ""
        )

        val prev = ExternalNavState.current
        if (prev == null || prev.instruction != maneuver.instruction || prev.road != maneuver.road ||
            prev.distanceText != maneuver.distanceText || prev.etaText != maneuver.etaText
        ) {
            DebugLogger.info("🔔 Nav: ${maneuver.distanceText} | ${maneuver.instruction} | ${maneuver.road} | ${maneuver.etaText}")
        }
        ExternalNavState.update(maneuver)
    }

    private fun loadLargeIcon(n: Notification): Bitmap? {
        // minSdk is 26, so Notification.getLargeIcon() (API 23) is always available.
        val drawable: Drawable = n.getLargeIcon()?.loadDrawable(this) ?: return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }
}
