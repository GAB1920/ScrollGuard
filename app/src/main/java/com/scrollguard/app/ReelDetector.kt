package com.scrollguard.app

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Works out whether the reel/short player is on screen, and which reel it is.
 *
 * Instagram and YouTube are matched by the internal view IDs of their reel players.
 * These IDs can change when the apps update; if counting stops working for one app,
 * these lists are the place to fix it.
 * TikTok hides its IDs, so it's matched by shape: a scrollable, full-screen pager.
 */
object ReelDetector {
    /** Package name -> display name. */
    val APPS = mapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
    )

    /** Any of these on screen = we're in the reel player. */
    private val REEL_VIEW_IDS = mapOf(
        "com.instagram.android" to listOf(
            "clips_viewer_view_pager",
            "clips_viewer_root",
            "clips_video_container",
        ),
        "com.google.android.youtube" to listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
        ),
    )

    /** The vertical pager that holds one reel per page. */
    private val PAGER_IDS = mapOf(
        "com.instagram.android" to listOf("clips_viewer_view_pager"),
        "com.google.android.youtube" to listOf("reel_recycler"),
    )

    /** Description of the last pager inspected, for logs. */
    var lastDebug = ""

    fun isTikTok(pkg: String) = APPS[pkg] == "TikTok"

    fun isInReels(root: AccessibilityNodeInfo, pkg: String, screenW: Int, screenH: Int): Boolean {
        REEL_VIEW_IDS[pkg]?.let { ids ->
            return ids.any { id -> findVisible(root, pkg, id) != null }
        }
        return fullScreenScroller(root, screenW, screenH, 0) != null
    }

    /**
     * A key that identifies the reel currently filling the screen, e.g. "pos:12"
     * or a fingerprint of its caption/author text. When it changes, you've moved
     * to a new reel. Null if we can't tell.
     */
    fun currentReelKey(root: AccessibilityNodeInfo, pkg: String, w: Int, h: Int): String? {
        val pager = PAGER_IDS[pkg]?.firstNotNullOfOrNull { findVisible(root, pkg, it) }
            ?: fullScreenScroller(root, w, h, 0)
            ?: return null

        // The page that covers the middle of the screen is the one being watched.
        val cx = w / 2
        val cy = h / 2
        var page: AccessibilityNodeInfo? = null
        val dbg = StringBuilder("pager=${pager.viewIdResourceName} kids=${pager.childCount}")
        for (i in 0 until pager.childCount) {
            val child = pager.getChild(i) ?: continue
            val r = Rect().also { child.getBoundsInScreen(it) }
            val t = ArrayList<String>().also { collectText(child, it, 0) }
            dbg.append("\n   [$i] row=${child.collectionItemInfo?.rowIndex} vis=${child.isVisibleToUser} $r ${t.take(3)}")
            if (page == null && r.contains(cx, cy) && r.height() > h / 2) page = child
        }
        lastDebug = dbg.toString()
        page ?: return null

        // Position in the feed plus the author, so a feed that restarts its
        // numbering (e.g. reopening Shorts) doesn't look like new reels.
        val parts = ArrayList<String>()
        collectText(page, parts, 0)
        val pos = page.collectionItemInfo?.rowIndex
        return when {
            pos != null -> "$pkg|$pos|${parts.firstOrNull().orEmpty()}"
            parts.isNotEmpty() -> "$pkg|" + parts.joinToString("|")
            else -> null
        }
    }

    // Skips things that change while a reel plays: timers, like/view counts.
    private val VOLATILE = Regex("""^[\d\s:.,KMBkmb%/+\-]*$|\d+:\d{2}""")

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (out.size >= 6 || depth > 15) return
        for (s in listOf(node.text, node.contentDescription)) {
            val t = s?.toString()?.trim() ?: continue
            if (t.length >= 3 && !VOLATILE.containsMatchIn(t) && t !in out) out += t.take(60)
            if (out.size >= 6) return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out, depth + 1)
        }
    }

    private fun findVisible(root: AccessibilityNodeInfo, pkg: String, id: String) =
        root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").firstOrNull { it.isVisibleToUser }

    private fun fullScreenScroller(node: AccessibilityNodeInfo, w: Int, h: Int, depth: Int): AccessibilityNodeInfo? {
        if (depth > 20) return null
        if (node.isScrollable && node.isVisibleToUser) {
            val r = Rect().also { node.getBoundsInScreen(it) }
            if (r.height() >= h * 0.85 && r.width() >= w * 0.9) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            fullScreenScroller(child, w, h, depth + 1)?.let { return it }
        }
        return null
    }
}
