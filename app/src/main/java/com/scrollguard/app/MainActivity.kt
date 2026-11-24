package com.scrollguard.app

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var setupCard: View
    private lateinit var heroBox: LinearLayout
    private lateinit var lockBox: LinearLayout
    private lateinit var dailyValue: TextView
    private lateinit var hourlyValue: TextView
    private lateinit var lockSwitch: Switch
    private lateinit var lockStart: TextView
    private lateinit var lockEnd: TextView
    private lateinit var lockStatus: TextView

    private val ticker = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 2000) }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.decorView.setBackgroundColor(Ui.BG)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(32))
        }
        val scroll = ScrollView(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Ui.BG)
            addView(col)
        }
        setContentView(scroll)
        scroll.descendantFocusability = ScrollView.FOCUS_BEFORE_DESCENDANTS
        scroll.isFocusableInTouchMode = true

        // Header
        col.addView(Ui.text(this, "ScrollGuard", 28f, Ui.TEXT, Ui.BOLD))
        col.addView(Ui.text(this, "Keep Blobby's brain from melting.", 15f, Ui.MUTED))

        // Setup card (only when the service is off)
        setupCard = card(col, top = 16, stroke = 0xFFFFB84D.toInt()).apply {
            addView(Ui.text(this@MainActivity, "Reel tracking is off", 18f, Ui.TEXT, Ui.BOLD))
            addView(Ui.text(this@MainActivity,
                "Turn on “ScrollGuard reel counter” in Accessibility settings so Blobby can count your reels.",
                14f, Ui.MUTED).apply { setPadding(0, dp(6), 0, 0) })
            addView(Ui.button(this@MainActivity, "Turn it on", "primary", 0xFFFFB84D.toInt()) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }

        // Hero: face + today's number
        heroBox = card(col, top = 16).apply { gravity = Gravity.CENTER_HORIZONTAL }

        // Active lock banner
        lockBox = card(col, top = 12, stroke = Ui.RED)

        // Limits
        section(col, "LIMITS")
        val limits = card(col)
        dailyValue = stepper(limits, "Reels per day", "Blobby is fully cooked at this many", 10, 1000,
            { prefs.dailyLimit }, { prefs.dailyLimit = it })
        divider(limits)
        hourlyValue = stepper(limits, "Reels per hour", "In any rolling 60 minutes", 5, 500,
            { prefs.hourlyLimit }, { prefs.hourlyLimit = it })

        // Night lock
        section(col, "NIGHT LOCK")
        val night = card(col)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(Ui.text(this, "🌙  Block reels at night", 17f, Ui.TEXT, Ui.BOLD))
        labels.addView(Ui.text(this, "Reels, Shorts and TikTok. DMs still work.", 13f, Ui.MUTED))
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        lockSwitch = Switch(this)
        row.addView(lockSwitch)
        night.addView(row)

        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        lockStart = timeChip()
        lockEnd = timeChip()
        times.addView(lockStart, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(Ui.text(this, "→", 18f, Ui.MUTED).apply { setPadding(dp(12), 0, dp(12), 0) })
        times.addView(lockEnd, LinearLayout.LayoutParams(0, -2, 1f))
        night.addView(times, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        lockStatus = Ui.text(this, "", 13f, Ui.MUTED).apply { setPadding(0, dp(10), 0, 0) }
        night.addView(lockStatus)

        lockSwitch.setOnClickListener {
            if (!lockSwitch.isChecked && NightLock.isActive(prefs)) {
                lockSwitch.isChecked = true
                showUnlockDialog("Turn off the night lock?") { prefs.nightLockEnabled = false }
            } else {
                prefs.nightLockEnabled = lockSwitch.isChecked
            }
            refresh()
        }
        lockStart.setOnClickListener { pickTime(prefs.lockStartMinutes) { prefs.lockStartMinutes = it } }
        lockEnd.setOnClickListener { pickTime(prefs.lockEndMinutes) { prefs.lockEndMinutes = it } }

        col.addView(Ui.button(this, "Reset today's count", "ghost") {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("Reset today's reel count to 0?")
                .setPositiveButton("Reset") { _, _ -> prefs.resetToday(); refresh() }
                .setNegativeButton("Cancel", null)
                .show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    // ---- Building blocks ---------------------------------------------------

    private fun card(parent: LinearLayout, top: Int = 0, stroke: Int? = null) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(18))
        background = Ui.rounded(this@MainActivity, Ui.CARD, 26, stroke)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(Ui.text(this, title, 12f, Ui.MUTED, Ui.BOLD).apply { letterSpacing = 0.15f },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(26); bottomMargin = dp(8); leftMargin = dp(6) })
    }

    private fun divider(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(Ui.CARD_2) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(14) })
    }

    private fun roundButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 22f
        typeface = Ui.BOLD
        gravity = Gravity.CENTER
        setTextColor(Ui.TEXT)
        background = Ui.rounded(this@MainActivity, Ui.CARD_2, 22)
        setOnClickListener { onClick() }
    }

    /** "Reels per day   [−] 100 [+]". Returns the value label. */
    private fun stepper(
        parent: LinearLayout, title: String, sub: String, step: Int, max: Int,
        get: () -> Int, set: (Int) -> Unit,
    ): TextView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(Ui.text(this, title, 17f, Ui.TEXT, Ui.BOLD))
        labels.addView(Ui.text(this, sub, 13f, Ui.MUTED))
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))

        val value = Ui.text(this, "", 20f, Ui.TEXT, Ui.BOLD).apply { gravity = Gravity.CENTER }
        row.addView(roundButton("−") { set((get() - step).coerceAtLeast(0)); refresh() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        row.addView(value, LinearLayout.LayoutParams(dp(64), -2))
        row.addView(roundButton("+") { set((get() + step).coerceAtMost(max)); refresh() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        parent.addView(row)
        return value
    }

    private fun timeChip() = Ui.text(this, "", 17f, Ui.TEXT, Ui.BOLD).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
        background = Ui.rounded(this@MainActivity, Ui.CARD_2, 16)
    }

    // ---- State -------------------------------------------------------------

    private fun refresh() {
        setupCard.visibility = if (serviceEnabled()) View.GONE else View.VISIBLE

        val d = Mood.dullness(prefs)
        val accent = Mood.accent(d)

        heroBox.removeAllViews()
        heroBox.addView(FaceView(this).apply { dullness = d }, LinearLayout.LayoutParams(dp(150), dp(150)))
        val wrap = { LinearLayout.LayoutParams(-2, -2) }
        heroBox.addView(Ui.text(this, Mood.label(d).uppercase(), 13f, accent, Ui.BOLD).apply { letterSpacing = 0.15f }, wrap())
        heroBox.addView(Ui.text(this, prefs.countToday().toString(), 72f, Ui.TEXT, Ui.BOLD), wrap())
        heroBox.addView(Ui.text(this, "reels today", 15f, Ui.MUTED), wrap())
        heroBox.addView(Ui.text(this, Mood.quip(d), 15f, Ui.TEXT).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0)
        }, wrap())
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("Instagram", "YouTube", "TikTok").forEach { app ->
            chips.addView(Ui.text(this, "$app  ${prefs.countToday(app)}", 13f, Ui.TEXT).apply {
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = Ui.rounded(this@MainActivity, Ui.CARD_2, 14)
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        heroBox.addView(chips, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(14) })
        heroBox.addView(Ui.progressRow(this, "Today", prefs.countToday(), prefs.dailyLimit, accent),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        heroBox.addView(Ui.progressRow(this, "This hour", prefs.countLastHour(), prefs.hourlyLimit, accent),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // Lock banner
        lockBox.removeAllViews()
        val quick = prefs.quickLockActive()
        lockBox.visibility = if (quick) View.VISIBLE else View.GONE
        if (quick) {
            lockBox.addView(Ui.text(this, "🔒  Reels locked", 18f, Ui.TEXT, Ui.BOLD))
            lockBox.addView(Ui.text(this, "${prefs.quickLockMinutesLeft()} min left. Blobby is recovering.", 14f, Ui.MUTED))
            lockBox.addView(Ui.button(this, "Unlock early", "danger") {
                showUnlockDialog("Unlock reels early?") { prefs.lockUntil = 0 }
            })
        }

        dailyValue.text = if (prefs.dailyLimit > 0) prefs.dailyLimit.toString() else "Off"
        hourlyValue.text = if (prefs.hourlyLimit > 0) prefs.hourlyLimit.toString() else "Off"

        val active = NightLock.isActive(prefs)
        lockSwitch.isChecked = prefs.nightLockEnabled
        lockStart.text = NightLock.label(prefs.lockStartMinutes)
        lockEnd.text = NightLock.label(prefs.lockEndMinutes)
        // No changing the hours while the lock is running (that would be an easy way out).
        lockStart.isEnabled = !active
        lockEnd.isEnabled = !active
        lockStart.alpha = if (active) 0.5f else 1f
        lockEnd.alpha = if (active) 0.5f else 1f
        lockStatus.text = when {
            active -> "Locked now until ${NightLock.label(prefs.lockEndMinutes)}. Turning it off takes a 60-second wait."
            prefs.nightLockEnabled -> "On. Starts at ${NightLock.label(prefs.lockStartMinutes)}."
            else -> "Off."
        }
    }

    private fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, h, m ->
            onPicked(h * 60 + m)
            refresh()
        }, current / 60, current % 60, false).show()
    }

    /** 60-second wait, then type a phrase, before a lock can be turned off. */
    private fun showUnlockDialog(title: String, onUnlock: () -> Unit) {
        val phrase = "I choose to scroll"
        val pad = dp(24)

        val info = TextView(this).apply { textSize = 16f }
        val input = EditText(this).apply { hint = phrase; isEnabled = false }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(info)
            addView(input)
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setView(box)
            .setNegativeButton("Stay locked", null)
            .setPositiveButton("Unlock", null)
            .create()
        dialog.show()

        val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        ok.isEnabled = false

        val timer = object : CountDownTimer(60_000, 1_000) {
            override fun onTick(ms: Long) {
                info.text = "Take a breath. Do you really want to scroll right now?\n\nYou can unlock in ${ms / 1000 + 1}s."
            }
            override fun onFinish() {
                info.text = "Type \"$phrase\" to unlock."
                input.isEnabled = true
                input.requestFocus()
            }
        }.start()

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                ok.isEnabled = input.isEnabled && s.toString().trim().equals(phrase, ignoreCase = true)
            }
        })
        ok.setOnClickListener {
            onUnlock()
            dialog.dismiss()
            refresh()
        }
        dialog.setOnDismissListener { timer.cancel() }
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(this, ReelWatchService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true)
        }
    }
}
