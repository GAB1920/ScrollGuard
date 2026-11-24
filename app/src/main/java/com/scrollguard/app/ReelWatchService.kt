package com.scrollguard.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * The heart of the app. Android sends it screen events from every app; it
 *  - notices when the reel player of Instagram / YouTube / TikTok is on screen,
 *  - works out which reel is showing and counts one each time it changes,
 *  - shows the counter badge, the limit prompt, and enforces the night lock.
 */
class ReelWatchService : AccessibilityService() {

    companion object { private const val TAG = "ScrollGuard" }

    private lateinit var prefs: Prefs
    private lateinit var overlays: Overlays

    private var inReels = false
    private var currentPkg: String? = null
    private var lastCheckAt = 0L
    private var lastBlockAt = 0L

    /** Recently seen reels, so flicking back and forth doesn't double count. */
    private val recentKeys = ArrayDeque<String>()

    override fun onServiceConnected() {
        prefs = Prefs(this)
        overlays = Overlays(this, object : Overlays.Actions {
            override fun lockNow() {
                prefs.lockUntil = System.currentTimeMillis() + Prefs.QUICK_LOCK_MINUTES * 60_000L
                currentPkg?.let { block(it, SystemClock.elapsedRealtime()) }
            }
            override fun openApp() {
                startActivity(
                    Intent(this@ReelWatchService, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        })
        Log.i(TAG, "service connected")
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::overlays.isInitialized) overlays.removeAll()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::prefs.isInitialized) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg !in ReelDetector.APPS) {
            // A different app came to the front -> we left the reels.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isAppWindow(event)) {
                leaveReels()
                currentPkg = null
            }
            return
        }
        currentPkg = pkg

        // Videos playing fire lots of events; checking 4x a second is plenty.
        val now = SystemClock.elapsedRealtime()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastCheckAt < 250) return
        lastCheckAt = now
        check(pkg, now)
    }

    /** Ignore keyboards, notification shade, our own overlays etc. */
    private fun isAppWindow(event: AccessibilityEvent): Boolean {
        if (event.packageName == packageName || event.packageName == "com.android.systemui") return false
        val window = windows.firstOrNull { it.id == event.windowId } ?: return true
        return window.type == AccessibilityWindowInfo.TYPE_APPLICATION
    }

    private fun check(pkg: String, now: Long) {
        val root = rootInActiveWindow ?: return
        if (root.packageName != pkg) return
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels

        val was = inReels
        inReels = ReelDetector.isInReels(root, pkg, w, h)
        if (was != inReels) Log.i(TAG, "inReels=$inReels in $pkg")

        if (!inReels) {
            overlays.updateBadge(false, prefs)
            return
        }
        if (NightLock.isActive(prefs) || prefs.quickLockActive()) {
            block(pkg, now)
            return
        }

        val key = ReelDetector.currentReelKey(root, pkg, w, h)
        if (key != null && key !in recentKeys && !overlays.isPromptShowing) {
            recentKeys.addLast(key)
            if (recentKeys.size > 30) recentKeys.removeFirst()
            Log.i(TAG, "new reel: $key")
            prefs.recordReel(ReelDetector.APPS.getValue(pkg))
            overlays.updateBadge(true, prefs, ReelDetector.APPS.getValue(pkg))
            checkLimits(pkg)
        } else {
            overlays.updateBadge(true, prefs, ReelDetector.APPS.getValue(pkg))
        }
    }

    private fun leaveReels() {
        if (inReels) Log.i(TAG, "left reels")
        inReels = false
        overlays.updateBadge(false, prefs)
    }

    private fun checkLimits(pkg: String) {
        val appName = ReelDetector.APPS.getValue(pkg)
        val today = prefs.countToday()
        val lastHour = prefs.countLastHour()

        when {
            prefs.dailyLimit > 0 && today >= prefs.dailyAllowance() -> overlays.showLimitPanel(
                prefs,
                title = "Daily limit reached",
                appName = appName,
                onKeep = { prefs.grantDailyExtra(Prefs.SNOOZE_REELS) },
                onClose = { performGlobalAction(GLOBAL_ACTION_HOME); leaveReels() },
            )
            prefs.hourlyLimit > 0 && lastHour >= prefs.hourlyAllowance() -> overlays.showLimitPanel(
                prefs,
                title = "Hourly limit reached",
                appName = appName,
                onKeep = { prefs.grantHourlyExtra(Prefs.SNOOZE_REELS) },
                onClose = { performGlobalAction(GLOBAL_ACTION_HOME); leaveReels() },
            )
        }
    }

    // ---- Night lock / Lock it --------------------------------------------------------

    private fun block(pkg: String, now: Long) {
        if (now - lastBlockAt < 800) return
        // Back usually leaves Reels/Shorts. If we're still there soon after, or it's
        // TikTok (whole app is reels), go to the home screen instead.
        val goHome = ReelDetector.isTikTok(pkg) || now - lastBlockAt < 4000
        lastBlockAt = now
        performGlobalAction(if (goHome) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
        leaveReels()
        overlays.flashMessage(
            if (prefs.quickLockActive()) "🔒 Reels locked for ${prefs.quickLockMinutesLeft()} more min"
            else "🌙 Reels are locked until ${NightLock.label(prefs.lockEndMinutes)}",
        )
    }
}
