package com.scrollguard.app

import android.content.Context
import java.time.LocalDate

/** All settings and counts, stored on the phone in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("scrollguard", Context.MODE_PRIVATE)

    // ---- Settings ----------------------------------------------------------

    /** Max reels per day. 0 = no daily limit. */
    var dailyLimit: Int
        get() = sp.getInt("daily_limit", 100)
        set(v) = sp.edit().putInt("daily_limit", v.coerceAtLeast(0)).apply()

    /** Max reels in any rolling 60 minutes. 0 = no hourly limit. */
    var hourlyLimit: Int
        get() = sp.getInt("hourly_limit", 40)
        set(v) = sp.edit().putInt("hourly_limit", v.coerceAtLeast(0)).apply()

    var nightLockEnabled: Boolean
        get() = sp.getBoolean("night_lock", false)
        set(v) = sp.edit().putBoolean("night_lock", v).apply()

    /** Minutes after midnight. Default 23:00. */
    var lockStartMinutes: Int
        get() = sp.getInt("lock_start", 23 * 60)
        set(v) = sp.edit().putInt("lock_start", v).apply()

    /** Minutes after midnight. Default 09:00. */
    var lockEndMinutes: Int
        get() = sp.getInt("lock_end", 9 * 60)
        set(v) = sp.edit().putInt("lock_end", v).apply()

    /** "Lock it" from the pill: reels blocked until this time (ms). */
    var lockUntil: Long
        get() = sp.getLong("lock_until", 0)
        set(v) = sp.edit().putLong("lock_until", v).apply()

    fun quickLockActive(now: Long = System.currentTimeMillis()) = lockUntil > now

    fun quickLockMinutesLeft(now: Long = System.currentTimeMillis()) =
        ((lockUntil - now + 59_999) / 60_000).toInt().coerceAtLeast(0)

    // ---- Counts ------------------------------------------------------------

    private fun today() = LocalDate.now().toString()

    fun countToday(): Int = sp.getInt("count_${today()}", 0)

    fun countToday(app: String): Int = sp.getInt("count_${today()}_$app", 0)

    /** Timestamps (ms) of reels in the last hour, for the rolling-hour limit. */
    private fun recent(now: Long): List<Long> =
        sp.getString("recent", "")!!
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .filter { now - it < HOUR_MS }

    fun countLastHour(now: Long = System.currentTimeMillis()): Int = recent(now).size

    fun recordReel(app: String, now: Long = System.currentTimeMillis()) {
        val d = today()
        sp.edit()
            .putInt("count_$d", countToday() + 1)
            .putInt("count_${d}_$app", countToday(app) + 1)
            .putString("recent", (recent(now) + now).joinToString(","))
            .apply()
    }

    fun resetToday() {
        val d = today()
        val e = sp.edit().remove("recent").remove("hourly_extra").remove("hourly_extra_until")
        sp.all.keys.filter { it.startsWith("count_$d") || it == "daily_extra_$d" }.forEach { e.remove(it) }
        e.apply()
    }

    // ---- "Keep scrolling" allowances ---------------------------------------

    /** Daily limit plus any "keep scrolling" extras granted today. */
    fun dailyAllowance(): Int = dailyLimit + sp.getInt("daily_extra_${today()}", 0)

    fun grantDailyExtra(n: Int) {
        val key = "daily_extra_${today()}"
        sp.edit().putInt(key, sp.getInt(key, 0) + n).apply()
    }

    /** Hourly limit plus any extras granted in the last hour. */
    fun hourlyAllowance(now: Long = System.currentTimeMillis()): Int {
        val active = now < sp.getLong("hourly_extra_until", 0)
        return hourlyLimit + if (active) sp.getInt("hourly_extra", 0) else 0
    }

    fun grantHourlyExtra(n: Int, now: Long = System.currentTimeMillis()) {
        val current = if (now < sp.getLong("hourly_extra_until", 0)) sp.getInt("hourly_extra", 0) else 0
        sp.edit()
            .putInt("hourly_extra", current + n)
            .putLong("hourly_extra_until", now + HOUR_MS)
            .apply()
    }

    companion object {
        const val HOUR_MS = 60 * 60 * 1000L
        /** Reels granted each time you tap "Keep scrolling". */
        const val SNOOZE_REELS = 10
        /** How long "Lock it" blocks reels. */
        const val QUICK_LOCK_MINUTES = 30
    }
}
