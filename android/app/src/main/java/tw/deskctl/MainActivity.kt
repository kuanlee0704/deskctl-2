package tw.deskctl

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 三個分頁:
 *   主頁 — 平常只會用到這裡: 狀態、啟動/停止、重新連線
 *   記錄 — 所有連線與錯誤記錄集中在這裡 (可複製/分享/清除)
 *   設定 — 初次設定(做完會自動收合)、偏好、控制器管理、關於
 */
class MainActivity : Activity() {

    private companion object {
        const val C_BG = 0xFFF2F4F7.toInt()
        const val C_CARD = 0xFFFFFFFF.toInt()
        const val C_PRIMARY = 0xFF1565C0.toInt()
        const val C_OK = 0xFF2E7D32.toInt()
        const val C_WARN = 0xFFEF6C00.toInt()
        const val C_BAD = 0xFFC62828.toInt()
        const val C_GREY = 0xFF78909C.toInt()
        const val C_TEXT = 0xFF212121.toInt()
        const val C_SUB = 0xFF616161.toInt()
    }

    private val h = Handler(Looper.getMainLooper())
    private var pendingStart = false
    private var tab = 0

    // 版面
    private lateinit var content: FrameLayout
    private val pages = ArrayList<View>()
    private val tabViews = ArrayList<TextView>()

    // 主頁
    private lateinit var statusCard: LinearLayout
    private lateinit var tvState: TextView
    private lateinit var tvDetail: TextView
    private lateinit var tvDevice: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnReconnect: Button
    private lateinit var tvInfo: TextView
    private lateinit var setupBanner: LinearLayout
    private lateinit var tvBanner: TextView

    // 記錄
    private lateinit var logScroll: ScrollView
    private lateinit var tvLog: TextView
    private lateinit var btnFilter: Button
    private var onlyProblems = false
    private var lastLogVersion = -1
    private var lastLogFilter = false

    // 設定
    private class SetupItem(
        val title: String, val hint: String, val done: () -> Boolean,
        val btn: String, val action: () -> Unit,
        val btn2: String? = null, val action2: (() -> Unit)? = null
    ) {
        lateinit var row: LinearLayout
        lateinit var mark: TextView
    }

    private val setupItems = ArrayList<SetupItem>()
    private lateinit var setupHeader: TextView
    private lateinit var setupBody: LinearLayout
    private var setupOpen: Boolean? = null       // null = 自動 (全部完成就收合)
    private lateinit var tvMac: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(C_BG)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        pages.add(buildHome())
        pages.add(buildLog())
        pages.add(buildSettings())
        for (p in pages) content.addView(p, FrameLayout.LayoutParams(-1, -1))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(C_CARD)
        bar.elevation = dp(8).toFloat()
        val names = arrayOf("主頁", "記錄", "設定")
        for (i in names.indices) {
            val t = TextView(this)
            t.text = names[i]
            t.textSize = 15f
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(14), 0, dp(14))
            t.setOnClickListener { select(i) }
            tabViews.add(t)
            bar.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        select(0)
        if (Prefs.autoOpen(this) && !BridgeService.running && hasBtPerms()) startSvc(BridgeService.ACTION_START)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        h.removeCallbacksAndMessages(null)
    }

    private fun select(i: Int) {
        tab = i
        for (k in pages.indices) pages[k].visibility = if (k == i) View.VISIBLE else View.GONE
        for (k in tabViews.indices) {
            val on = k == i
            tabViews[k].setTextColor(if (on) C_PRIMARY else C_SUB)
            tabViews[k].setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
        refresh()
    }

    // ============================================================
    // 小工具
    private fun card(parent: LinearLayout, color: Int = C_CARD): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(color)
        bg.cornerRadius = dp(14).toFloat()
        c.background = bg
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 0, 0, dp(12))
        parent.addView(c, lp)
        return c
    }

    private fun text(parent: ViewGroup, t: String, size: Float = 14f, color: Int = C_TEXT, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.setTypeface(null, Typeface.BOLD)
        parent.addView(v, LinearLayout.LayoutParams(-1, -2))
        return v
    }

    private fun button(parent: ViewGroup, t: String, primary: Boolean = false, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        if (primary) {
            b.setTextColor(Color.WHITE)
            val bg = GradientDrawable()
            bg.setColor(C_PRIMARY)
            bg.cornerRadius = dp(12).toFloat()
            b.background = bg
        }
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, dp(6), 0, 0)
        parent.addView(b, lp)
        return b
    }

    private fun page(): Pair<ScrollView, LinearLayout> {
        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(28), dp(16), dp(16))
        sv.addView(col, ViewGroup.LayoutParams(-1, -2))
        return Pair(sv, col)
    }

    private fun tint(v: View, color: Int) {
        val bg = v.background
        if (bg is GradientDrawable) bg.setColor(color)
    }

    // ============================================================
    // 主頁
    private fun buildHome(): View {
        val (sv, col) = page()
        text(col, "桌邊控制器", 24f, C_TEXT, true)
        text(col, "ESP32 桌邊藍牙控制器的手機端", 13f, C_SUB).setPadding(0, 0, 0, dp(14))

        setupBanner = card(col, 0xFFFFF3E0.toInt())
        tvBanner = text(setupBanner, "", 14f, C_TEXT)
        button(setupBanner, "前往設定") { select(2) }

        statusCard = card(col, C_GREY)
        tvState = text(statusCard, "", 26f, Color.WHITE, true)
        tvDetail = text(statusCard, "", 14f, Color.WHITE)
        tvDevice = text(statusCard, "", 13f, 0xFFE0E0E0.toInt())

        btnToggle = button(col, "啟動", true) { toggleService() }
        btnToggle.textSize = 18f
        btnToggle.setPadding(0, dp(14), 0, dp(14))
        btnReconnect = button(col, "重新連線") { startSvc(BridgeService.ACTION_RECONNECT) }

        val info = card(col)
        tvInfo = text(info, "", 14f, C_TEXT)
        info.setPadding(dp(16), dp(14), dp(16), dp(14))
        (info.layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(14), 0, 0)
        return sv
    }

    private fun toggleService() {
        if (BridgeService.running) {
            startSvc(BridgeService.ACTION_STOP)
        } else {
            startSvc(BridgeService.ACTION_START)
        }
        h.postDelayed({ refresh() }, 300)
    }

    private fun updateHome() {
        val running = BridgeService.running
        val connected = BridgeService.connected
        val color: Int
        if (!running) {
            color = C_GREY
            tvState.text = "已停止"
            tvDetail.text = "按下方「啟動」開始連線"
            tvDevice.text = ""
        } else if (!connected) {
            color = C_WARN
            tvState.text = "連線中…"
            tvDetail.text = BridgeService.status
            tvDevice.text = Prefs.mac(this)?.let { "控制器 $it" } ?: "尚未配對, 搜尋中 (請確認控制器已開機)"
        } else {
            color = C_OK
            tvState.text = "已連線"
            tvDetail.text = BridgeService.status
            val mins = (SystemClock.elapsedRealtime() - BridgeService.connectedSince) / 60000
            val dur = if (mins < 1) "剛連上" else if (mins < 60) "已連線 $mins 分鐘" else "已連線 ${mins / 60} 小時 ${mins % 60} 分"
            tvDevice.text = (Prefs.mac(this) ?: "") + "  ·  " + dur
        }
        tint(statusCard, color)
        btnToggle.text = if (running) "停止" else "啟動"
        val bg = btnToggle.background
        if (bg is GradientDrawable) bg.setColor(if (running) C_BAD else C_PRIMARY)
        btnReconnect.visibility = if (running) View.VISIBLE else View.GONE

        val missing = setupItems.filter { !it.done() }
        if (missing.isEmpty()) {
            setupBanner.visibility = View.GONE
        } else {
            setupBanner.visibility = View.VISIBLE
            tvBanner.text = "⚠ 還有 ${missing.size} 項初次設定沒完成:\n" + missing.joinToString("\n") { "• " + it.title }
        }
        val lines = BridgeService.infoLines
        tvInfo.visibility = if (running && lines.isNotEmpty()) View.VISIBLE else View.GONE
        (tvInfo.parent as View).visibility = tvInfo.visibility
        tvInfo.text = lines.joinToString("\n")
    }

    // ============================================================
    // 記錄
    private fun buildLog(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(28), dp(12), dp(8))

        text(root, "記錄", 22f, C_TEXT, true).setPadding(dp(4), 0, 0, dp(8))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        fun small(t: String, f: () -> Unit): Button {
            val b = Button(this)
            b.text = t
            b.isAllCaps = false
            b.textSize = 13f
            b.setOnClickListener { f() }
            row.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
            return b
        }
        small("複製") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("DeskCtl log", AppLog.text(false)))
            Toast.makeText(this, "已複製", Toast.LENGTH_SHORT).show()
        }
        small("分享") {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, AppLog.text(false))
            startActivity(Intent.createChooser(i, "分享記錄"))
        }
        small("清除") { AppLog.clear(); refresh() }
        btnFilter = small("只看異常") {
            onlyProblems = !onlyProblems
            refresh()
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2))

        logScroll = ScrollView(this)
        tvLog = TextView(this)
        tvLog.typeface = Typeface.MONOSPACE
        tvLog.textSize = 11.5f
        tvLog.setTextColor(C_TEXT)
        tvLog.setTextIsSelectable(true)
        tvLog.setPadding(dp(6), dp(6), dp(6), dp(6))
        logScroll.addView(tvLog, ViewGroup.LayoutParams(-1, -2))
        val bg = GradientDrawable()
        bg.setColor(C_CARD)
        bg.cornerRadius = dp(10).toFloat()
        logScroll.background = bg
        val lp = LinearLayout.LayoutParams(-1, 0, 1f)
        lp.setMargins(0, dp(8), 0, 0)
        root.addView(logScroll, lp)
        return root
    }

    private fun updateLog() {
        if (AppLog.version == lastLogVersion && onlyProblems == lastLogFilter) return
        lastLogVersion = AppLog.version
        lastLogFilter = onlyProblems
        btnFilter.text = if (onlyProblems) "顯示全部" else "只看異常"
        val child = logScroll.getChildAt(0)
        val atBottom = child == null || child.bottom - (logScroll.height + logScroll.scrollY) < dp(48)
        val t = AppLog.text(onlyProblems)
        tvLog.text = if (t.isEmpty()) "(沒有記錄)" else t
        if (atBottom) logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ============================================================
    // 設定
    private fun buildSettings(): View {
        val (sv, col) = page()
        text(col, "設定", 22f, C_TEXT, true).setPadding(0, 0, 0, dp(12))

        // ---- 初次設定 ----
        val setup = card(col)
        setupHeader = text(setup, "", 16f, C_TEXT, true)
        setupHeader.setOnClickListener {
            setupOpen = !(setupOpen ?: setupItems.any { !it.done() })
            refresh()
        }
        setupBody = LinearLayout(this)
        setupBody.orientation = LinearLayout.VERTICAL
        setup.addView(setupBody, LinearLayout.LayoutParams(-1, -2))

        setupItems.add(
            SetupItem("藍牙與通知權限", "讓 App 能連接控制器並顯示常駐通知。", { hasBtPerms() }, "授權", { requestPerms() })
        )
        setupItems.add(
            SetupItem(
                "通知存取 (讀取音樂資訊)", "開啟後才讀得到歌名與播放狀態。若無法勾選, 請先按下方「允許受限設定」。",
                { NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName) },
                "開啟通知存取",
                { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                "允許受限設定 (App 資訊 → ⋮)",
                { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            )
        )
        setupItems.add(
            SetupItem(
                "允許背景執行 (電池不受限)", "避免系統在螢幕關閉後把連線殺掉。",
                {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    pm.isIgnoringBatteryOptimizations(packageName)
                },
                "開啟",
                { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            )
        )
        for (it in setupItems) addSetupRow(it)

        // ---- 偏好 ----
        val pref = card(col)
        text(pref, "偏好", 16f, C_TEXT, true)
        val s1 = Switch(this)
        s1.text = "手機開機後自動啟動"
        s1.isChecked = Prefs.autostart(this)
        s1.setOnCheckedChangeListener { _, c -> Prefs.setAutostart(this, c) }
        pref.addView(s1, LinearLayout.LayoutParams(-1, -2))
        val s2 = Switch(this)
        s2.text = "開啟 App 時自動啟動"
        s2.isChecked = Prefs.autoOpen(this)
        s2.setOnCheckedChangeListener { _, c -> Prefs.setAutoOpen(this, c) }
        pref.addView(s2, LinearLayout.LayoutParams(-1, -2))

        // ---- 控制器 ----
        val dev = card(col)
        text(dev, "控制器", 16f, C_TEXT, true)
        tvMac = text(dev, "", 14f, C_SUB)
        button(dev, "向控制器重新同步") { startSvc(BridgeService.ACTION_SYNC) }
        button(dev, "忘記已記住的控制器 (重新搜尋)") {
            startSvc(BridgeService.ACTION_FORGET)
            Toast.makeText(this, "已忘記, 會重新搜尋", Toast.LENGTH_SHORT).show()
            refresh()
        }

        // ---- 關於 ----
        val about = card(col)
        text(about, "關於", 16f, C_TEXT, true)
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
        text(about, "桌邊控制器 App v$ver", 14f, C_SUB)
        text(about, "已載入功能: " + Features.registry.joinToString("、") { it.title }, 14f, C_SUB)
        text(about, "新增功能的方法見 README「手機 App 新增功能」", 12f, C_GREY)
        return sv
    }

    private fun addSetupRow(it: SetupItem) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(0, dp(10), 0, dp(4))
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        val mark = TextView(this)
        mark.textSize = 18f
        top.addView(mark, LinearLayout.LayoutParams(dp(30), -2))
        val t = TextView(this)
        t.text = it.title
        t.textSize = 15f
        t.setTextColor(C_TEXT)
        top.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(top, LinearLayout.LayoutParams(-1, -2))
        val hint = TextView(this)
        hint.text = it.hint
        hint.textSize = 12f
        hint.setTextColor(C_SUB)
        hint.setPadding(dp(30), 0, 0, 0)
        row.addView(hint, LinearLayout.LayoutParams(-1, -2))
        val b = Button(this)
        b.text = it.btn
        b.isAllCaps = false
        b.setOnClickListener { _ -> it.action(); }
        row.addView(b, LinearLayout.LayoutParams(-1, -2))
        if (it.btn2 != null && it.action2 != null) {
            val b2 = Button(this)
            b2.text = it.btn2
            b2.isAllCaps = false
            b2.setOnClickListener { _ -> it.action2.invoke() }
            row.addView(b2, LinearLayout.LayoutParams(-1, -2))
        }
        it.row = row
        it.mark = mark
        setupBody.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun updateSettings() {
        val doneCount = setupItems.count { it.done() }
        val all = doneCount == setupItems.size
        val open = setupOpen ?: !all
        setupHeader.text = (if (all) "✔ 初次設定已完成" else "初次設定 ($doneCount/${setupItems.size})") +
            (if (open) "  ▲" else "  ▼ 點開檢查")
        setupBody.visibility = if (open) View.VISIBLE else View.GONE
        for (it in setupItems) {
            val ok = it.done()
            it.mark.text = if (ok) "✔" else "✘"
            it.mark.setTextColor(if (ok) C_OK else C_BAD)
            // 做完的項目把按鈕收起來, 版面更清爽 (按鈕在 row 的第 3 個之後)
            for (k in 2 until it.row.childCount) it.row.getChildAt(k).visibility = if (ok) View.GONE else View.VISIBLE
        }
        tvMac.text = Prefs.mac(this)?.let { "已記住: $it" } ?: "尚未配對 (啟動後會自動搜尋 DeskCtl)"
    }

    // ============================================================
    // 權限與服務
    private fun startSvc(action: String) {
        if (action == BridgeService.ACTION_START) {
            if (!hasBtPerms()) {
                pendingStart = true
                requestPerms()
                return
            }
            try {
                ContextCompat.startForegroundService(this, Intent(this, BridgeService::class.java).setAction(action))
            } catch (e: Exception) {
                AppLog.e("APP", "啟動服務失敗: " + e)
                Toast.makeText(this, "啟動失敗: " + e.message, Toast.LENGTH_LONG).show()
            }
            return
        }
        if (!BridgeService.running) {
            // 服務沒在執行: 只有「忘記控制器」有意義, 直接清掉設定
            if (action == BridgeService.ACTION_FORGET) Prefs.setMac(this, null)
            return
        }
        try {
            startService(Intent(this, BridgeService::class.java).setAction(action))
        } catch (e: Exception) {
            AppLog.e("APP", "指令失敗: " + e)
        }
    }

    private fun neededPerms(): Array<String> {
        val l = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            l.add(Manifest.permission.BLUETOOTH_SCAN)
            l.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            l.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l.toTypedArray()
    }

    private fun hasBtPerms(): Boolean = neededPerms().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPerms() {
        requestPermissions(neededPerms(), 1)
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r)
        if (pendingStart && hasBtPerms()) {
            pendingStart = false
            startSvc(BridgeService.ACTION_START)
        }
        pendingStart = false
        refresh()
    }

    // ============================================================
    private fun refresh() {
        updateHome()
        updateSettings()
        if (tab == 1) updateLog()
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ refresh() }, 1000)
    }
}
