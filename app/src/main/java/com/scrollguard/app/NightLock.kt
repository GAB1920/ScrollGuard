package com.scrollguard.app

import java.time.LocalTime
import java.time.format.DateTimeFormatter

object NightLock {
    /** True when the lock is switched on and the current time is inside the window. */
    fun isActive(prefs: Prefs, now: LocalTime = LocalTime.now()): Boolean =
        prefs.nightLockEnabled && inWindow(prefs, now)

    fun inWindow(prefs: Prefs, now: LocalTime = LocalTime.now()): Boolean {
        val m = now.hour * 60 + now.minute
        val start = prefs.lockStartMinutes
        val end = prefs.lockEndMinutes
        return when {
            start == end -> false
            start < end -> m in start until end          // e.g. 13:00–17:00
            else -> m >= start || m < end                 // crosses midnight, e.g. 23:00–09:00
        }
    }

    fun label(minutes: Int): String =
        LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern("h:mm a"))
}
