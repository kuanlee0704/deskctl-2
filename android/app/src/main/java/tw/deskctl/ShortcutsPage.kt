package tw.deskctl

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * 「捷徑」分頁: 列出、新增(範本)、編輯、測試、刪除捷徑。
 * 控制器上的「捷徑」頁會顯示這裡的名單, 按中央就執行。
 */
class ShortcutsPage(private val act: Activity) {
    val view: ScrollView = ScrollView(act)
    private val col = LinearLayout(act)
    private var list: MutableList<Shortcut> = ArrayList()
    private var editing: Shortcut? = null
    private var editIndex = -1

    private val cText = 0xFF212121.toInt()
    private val cSub = 0xFF616161.toInt()
    private val cPrimary = 0xFF1565C0.toInt()
    private val cBad = 0xFFC62828.toInt()

    init {
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(28), dp(16), dp(16))
        view.addView(col, ViewGroup.LayoutParams(-1, -2))
        list = Shortcuts.load(act)
        render()
    }

    private fun dp(v: Int): Int = (v * act.resources.displayMetrics.density).toInt()

    // ---------------- 小工具 ----------------
    private fun tv(parent: ViewGroup, t: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val v = TextView(act)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.setTypeface(null, Typeface.BOLD)
        parent.addView(v, LinearLayout.LayoutParams(-1, -2))
        return v
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(act)
        c.orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable()
        bg.setColor(Color.WHITE)
        bg.cornerRadius = dp(14).toFloat()
        c.background = bg
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 0, 0, dp(12))
        col.addView(c, lp)
        return c
    }

    private fun btn(parent: ViewGroup, t: String, primary: Boolean = false, weight: Float = 0f, onClick: () -> Unit): Button {
        val b = Button(act)
        b.text = t
        b.isAllCaps = false
        if (primary) {
            b.setTextColor(Color.WHITE)
            val bg = GradientDrawable()
            bg.setColor(cPrimary)
            bg.cornerRadius = dp(12).toFloat()
            b.background = bg
        }
        b.setOnClickListener { onClick() }
        val lp = if (weight > 0f) LinearLayout.LayoutParams(0, -2, weight) else LinearLayout.LayoutParams(-1, -2)
        parent.addView(b, lp)
        return b
    }

    private fun row(parent: ViewGroup): LinearLayout {
        val r = LinearLayout(act)
        r.orientation = LinearLayout.HORIZONTAL
        parent.addView(r, LinearLayout.LayoutParams(-1, -2))
        return r
    }

    private fun save() {
        Shortcuts.save(act, list)
    }

    private fun toast(t: String) {
        Toast.makeText(act, t, Toast.LENGTH_SHORT).show()
    }

    // ---------------- 畫面 ----------------
    fun refresh() {
        // 這頁沒有需要每秒更新的內容; 切到此頁時重新載入
        if (editing == null) {
            list = Shortcuts.load(act)
            render()
        }
    }

    private fun render() {
        col.removeAllViews()
        if (editing != null) renderEditor() else renderList()
    }

    private fun renderList() {
        tv(col, "捷徑", 22f, cText, true)
        val sub = tv(col, "在這裡設定好, 控制器的「捷徑」頁就會出現同樣的名單, 按中央即可執行。", 13f, cSub)
        sub.setPadding(0, 0, 0, dp(12))

        val n = col.childCount
        btn(col, "＋ 新增捷徑", true) { chooseTemplate() }
        (col.getChildAt(n).layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, dp(12))

        if (list.isEmpty()) {
            val c = card()
            tv(c, "還沒有捷徑。例如:\n• 開啟 Samsung Music 並自動播放\n• 用 Brave 開啟 YouTube 並播放第一部影片", 14f, cSub)
        }
        for (i in list.indices) {
            val sc = list[i]
            val c = card()
            tv(c, (i + 1).toString() + ". " + sc.name, 17f, cText, true)
            for (st in sc.steps) tv(c, "  • " + Shortcuts.describe(act, st), 13f, cSub)
            val r = row(c)
            btn(r, "▶ 測試", false, 1f) { testRun(i) }
            btn(r, "編輯", false, 1f) { startEdit(i) }
            btn(r, "刪除", false, 1f) {
                confirm("刪除「" + sc.name + "」?") {
                    list.removeAt(i)
                    save()
                    render()
                }
            }
        }
        if (!Launcher.overlayOk(act) || !Launcher.accessibilityOn(act)) {
            val c = card()
            tv(c, "提醒: 到「設定」分頁的初次設定, 開啟「顯示在其他 App 上層」, 捷徑才能從背景開啟 App; 「點擊畫面/點擊文字/返回」步驟另需開啟「無障礙服務」。", 12f, cSub)
        }
    }

    private fun testRun(i: Int) {
        if (!BridgeService.running) {
            toast("請先到主頁按「啟動」")
            return
        }
        val it = Intent(act, BridgeService::class.java).setAction(BridgeService.ACTION_RUN).putExtra("i", i)
        try {
            ContextCompat.startForegroundService(act, it)
            toast("已送出, 到「記錄」看執行過程")
        } catch (e: Exception) {
            toast("失敗: " + e.message)
        }
    }

    // ---------------- 範本 ----------------
    private fun chooseTemplate() {
        val items = arrayOf(
            "開啟 App 並自動播放 (如 Samsung Music)",
            "用瀏覽器開網址 / 指定的 YouTube 影片或播放清單",
            "YouTube 搜尋並播放第一部影片 (需無障礙)",
            "重新整理目前的網頁 (瀏覽器卡住時用, 需無障礙)",
            "自訂 (自己加步驟)"
        )
        AlertDialog.Builder(act).setTitle("新增捷徑").setItems(items) { _, which ->
            when (which) {
                0 -> tplApp()
                1 -> tplUrl()
                2 -> tplYoutube()
                3 -> {
                    val sc = Shortcut("重新整理網頁")
                    sc.steps.add(Step("wake"))
                    sc.steps.add(Step("refresh"))
                    list.add(sc)
                    save()
                    startEdit(list.size - 1)
                }
                else -> {
                    list.add(Shortcut("新捷徑"))
                    startEdit(list.size - 1)
                }
            }
        }.show()
    }

    private fun tplApp() {
        pickApp("選擇要開啟的 App") { pkg ->
            val sc = Shortcut(Shortcuts.appLabel(act, pkg))
            sc.steps.add(Step("app", pkg))
            sc.steps.add(Step("wait", "4"))
            sc.steps.add(Step("media", "play"))
            list.add(sc)
            save()
            startEdit(list.size - 1)
        }
    }

    private fun tplUrl() {
        pickApp("用哪個 App 開啟? (例如 Brave)") { pkg ->
            ask("網址", "https://www.youtube.com/watch?v=...", "https://") { url ->
                val sc = Shortcut(Shortcuts.appLabel(act, pkg) + " 網頁")
                sc.steps.add(Step("url", url, pkg))
                list.add(sc)
                save()
                startEdit(list.size - 1)
            }
        }
    }

    private fun tplYoutube() {
        pickApp("用哪個瀏覽器? (例如 Brave)") { pkg ->
            ask("要搜尋的關鍵字", "例如 lofi 音樂", "") { kw ->
                val q = java.net.URLEncoder.encode(kw, "UTF-8")
                val sc = Shortcut("YouTube: " + kw)
                sc.steps.add(Step("url", "https://m.youtube.com/results?search_query=" + q, pkg))
                sc.steps.add(Step("wait", "6"))
                sc.steps.add(Step("firstvideo"))
                sc.steps.add(Step("wait", "3"))
                sc.steps.add(Step("ensureplay", "50,23"))
                list.add(sc)
                save()
                toast("會自動點第一部影片; 若點錯, 可把此步驟換成「點擊畫面」並用準星選位置")
                startEdit(list.size - 1)
            }
        }
    }

    // ---------------- 編輯 ----------------
    private fun startEdit(i: Int) {
        editIndex = i
        editing = list[i]
        render()
    }

    private fun renderEditor() {
        val sc = editing ?: return
        tv(col, "編輯捷徑", 22f, cText, true)

        val c0 = card()
        tv(c0, "名稱 (會顯示在控制器上)", 12f, cSub)
        val name = EditText(act)
        name.setText(sc.name)
        name.setSingleLine(true)
        c0.addView(name, LinearLayout.LayoutParams(-1, -2))
        name.setOnFocusChangeListener { _, has -> if (!has) sc.name = name.text.toString().trim().ifEmpty { "捷徑" } }

        val c1 = card()
        tv(c1, "步驟 (由上而下依序執行)", 12f, cSub)
        if (sc.steps.isEmpty()) tv(c1, "(還沒有步驟)", 14f, cSub)
        for (k in sc.steps.indices) {
            val st = sc.steps[k]
            val box = LinearLayout(act)
            box.orientation = LinearLayout.VERTICAL
            box.setPadding(0, dp(8), 0, dp(4))
            tv(box, (k + 1).toString() + ". " + Shortcuts.describe(act, st), 14f, cText)
            val r = row(box)
            btn(r, "↑", false, 1f) { if (k > 0) { sc.name = name.text.toString(); sc.steps.add(k - 1, sc.steps.removeAt(k)); render() } }
            btn(r, "↓", false, 1f) { if (k < sc.steps.size - 1) { sc.name = name.text.toString(); sc.steps.add(k + 1, sc.steps.removeAt(k)); render() } }
            btn(r, "改", false, 1f) { sc.name = name.text.toString(); editStep(st) { ns -> sc.steps[k] = ns; render() } }
            btn(r, "刪", false, 1f) { sc.name = name.text.toString(); sc.steps.removeAt(k); render() }
            c1.addView(box, LinearLayout.LayoutParams(-1, -2))
        }
        btn(c1, "＋ 加入步驟", false) { sc.name = name.text.toString(); addStep { ns -> sc.steps.add(ns); render() } }

        val r = row(col)
        btn(r, "完成", true, 1f) {
            sc.name = name.text.toString().trim().ifEmpty { "捷徑" }
            save()
            editing = null
            list = Shortcuts.load(act)
            render()
        }
        btn(r, "放棄", false, 1f) {
            editing = null
            list = Shortcuts.load(act)
            render()
        }
    }

    private val stepTypes = arrayOf(
        "開啟 App", "開網址", "等待幾秒", "媒體鍵 (播放/暫停/下一首…)",
        "點擊畫面位置 (需無障礙)", "點擊含某文字的按鈕 (需無障礙)", "按返回 (需無障礙)",
        "重新整理目前頁面 (需無障礙)", "喚醒螢幕",
        "點第一部影片 (搜尋結果頁, 需無障礙)",
        "確保播放中 (卡住/被靜音時補點擊, 需無障礙)"
    )

    private fun addStep(done: (Step) -> Unit) {
        AlertDialog.Builder(act).setTitle("加入步驟").setItems(stepTypes) { _, which ->
            editStep(Step(typeOf(which)), done)
        }.show()
    }

    private fun typeOf(which: Int): String = when (which) {
        0 -> "app"
        1 -> "url"
        2 -> "wait"
        3 -> "media"
        4 -> "tap"
        5 -> "text"
        6 -> "back"
        7 -> "refresh"
        8 -> "wake"
        9 -> "firstvideo"
        else -> "ensureplay"
    }

    private fun editStep(st: Step, done: (Step) -> Unit) {
        when (st.type) {
            "app" -> pickApp("選擇要開啟的 App") { pkg -> done(Step("app", pkg)) }
            "url" -> pickApp("用哪個 App 開啟網址?") { pkg ->
                ask("網址", "https://...", if (st.a.isEmpty()) "https://" else st.a) { url -> done(Step("url", url, pkg)) }
            }
            "wait" -> ask("等待幾秒", "例如 4", if (st.a.isEmpty()) "3" else st.a, true) { s -> done(Step("wait", s)) }
            "media" -> {
                val names = arrayOf("播放", "暫停", "播放/暫停", "下一首", "上一首")
                val keys = arrayOf("play", "pause", "toggle", "next", "prev")
                AlertDialog.Builder(act).setTitle("媒體鍵").setItems(names) { _, w -> done(Step("media", keys[w])) }.show()
            }
            "tap" -> askTap(if (st.a.isEmpty()) "50,40" else st.a) { s -> done(Step("tap", s)) }
            "ensureplay" -> askTap(if (st.a.isEmpty()) "50,23" else st.a) { s -> done(Step("ensureplay", s)) }
            "text" -> ask("按鈕上的文字", "例如 播放", st.a) { s -> done(Step("text", s)) }
            else -> done(Step(st.type))
        }
    }

    // ---------------- 對話框 ----------------
    private fun ask(title: String, hint: String, initial: String, number: Boolean = false, ok: (String) -> Unit) {
        val e = EditText(act)
        e.hint = hint
        e.setText(initial)
        if (number) e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        e.setSingleLine(true)
        AlertDialog.Builder(act).setTitle(title).setView(e)
            .setPositiveButton("確定") { _, _ ->
                val t = e.text.toString().trim()
                if (t.isNotEmpty()) ok(t)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 點擊位置: 可直接輸入「橫%,縱%」, 或按「在畫面上選取」用準星點選 */
    private fun askTap(initial: String, ok: (String) -> Unit) {
        val e = EditText(act)
        e.hint = "橫%,縱% 例如 50,40"
        e.setText(initial)
        e.setSingleLine(true)
        AlertDialog.Builder(act).setTitle("點擊位置")
            .setMessage("橫、縱都是 0~100 的百分比 (0,0=左上角, 100,100=右下角)。不想自己算就按「在畫面上選取」。")
            .setView(e)
            .setPositiveButton("確定") { _, _ ->
                val t = e.text.toString().trim()
                if (t.isNotEmpty()) ok(t)
            }
            .setNeutralButton("在畫面上選取") { _, _ -> pickOnScreen(e.text.toString(), ok) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickOnScreen(cur: String, ok: (String) -> Unit) {
        if (!Launcher.overlayOk(act)) {
            toast("請先到「設定」開啟「顯示在其他 App 上層」")
            return
        }
        val p = cur.split(",")
        val x0 = p.getOrNull(0)?.trim()?.toFloatOrNull() ?: 50f
        val y0 = p.getOrNull(1)?.trim()?.toFloatOrNull() ?: 40f
        val started = PosPicker.start(act, x0, y0) { x, y ->
            // 選好後把 App 帶回前景, 並用選到的值重新開啟對話框
            try {
                act.startActivity(
                    Intent(act, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            } catch (_: Exception) {
            }
            val v = String.format("%.0f,%.0f", x, y)
            act.window.decorView.postDelayed({ askTap(v, ok) }, 400)
        }
        if (!started) {
            toast("無法顯示選取準星")
            return
        }
        toast("切到要點的畫面, 拖曳準星後按「確定」")
        act.moveTaskToBack(true)
    }

    private fun confirm(msg: String, ok: () -> Unit) {
        AlertDialog.Builder(act).setMessage(msg)
            .setPositiveButton("確定") { _, _ -> ok() }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 列出手機上可從桌面開啟的 App 讓使用者挑 */
    private fun pickApp(title: String, ok: (String) -> Unit) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = act.packageManager
        val apps = pm.queryIntentActivities(i, 0)
            .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        if (apps.isEmpty()) {
            toast("找不到可開啟的 App")
            return
        }
        val names = apps.map { it.first }.toTypedArray()
        AlertDialog.Builder(act).setTitle(title).setItems(names) { _, which -> ok(apps[which].second) }.show()
    }
}
