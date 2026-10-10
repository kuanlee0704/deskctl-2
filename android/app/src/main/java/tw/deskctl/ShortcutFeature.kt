package tw.deskctl

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

/**
 * 捷徑: 把名單傳給控制器; 控制器送 {"c":"run","i":編號} 時, 依序執行該捷徑的步驟。
 * 執行結果以 {"k":"act","s":"run|done|fail|busy","n":名稱,"m":訊息} 回報, 控制器會跳提示。
 */
class ShortcutFeature(host: FeatureHost) : Feature(host) {
    override val id = "shortcut"

    private val ctx: Context = host.context
    private var lastVersion = -1
    private var busy = false
    private var lastInfo = ""

    override fun info(): String = lastInfo

    override fun onReady() {
        pushList()
    }

    override fun onTick(n: Int) {
        if (Shortcuts.version != lastVersion) pushList()
    }

    private fun pushList() {
        lastVersion = Shortcuts.version
        val arr = JSONArray()
        for (sc in Shortcuts.load(ctx).take(16)) arr.put(sc.name.take(24))
        host.send(JSONObject().put("k", "acts").put("l", arr))
    }

    private fun report(state: String, name: String, msg: String = "") {
        host.send(JSONObject().put("k", "act").put("s", state).put("n", name.take(24)).put("m", msg.take(30)))
    }

    override fun onCommand(c: String, o: JSONObject): Boolean {
        if (c != "run") return false
        val list = Shortcuts.load(ctx)
        val i = o.optInt("i", -1)
        if (i < 0 || i >= list.size) {
            report("fail", "", "沒有這個捷徑")
            return true
        }
        val sc = list[i]
        if (busy) {
            report("busy", sc.name)
            return true
        }
        run(sc)
        return true
    }

    /** 也給手機畫面上的「測試執行」按鈕使用 */
    fun run(sc: Shortcut) {
        busy = true
        AppLog.i("SHORTCUT", "執行「" + sc.name + "」(" + sc.steps.size + " 步)")
        lastInfo = "▶ 執行捷徑: " + sc.name
        report("run", sc.name)
        step(sc, 0)
    }

    private fun finish(sc: Shortcut, ok: Boolean, msg: String) {
        busy = false
        lastInfo = ""
        if (ok) {
            AppLog.i("SHORTCUT", "「" + sc.name + "」完成")
            report("done", sc.name)
        } else {
            AppLog.w("SHORTCUT", "「" + sc.name + "」失敗: " + msg)
            report("fail", sc.name, msg)
        }
    }

    private fun step(sc: Shortcut, idx: Int) {
        if (idx >= sc.steps.size) {
            finish(sc, true, "")
            return
        }
        val st = sc.steps[idx]
        val label = Shortcuts.describe(ctx, st)
        AppLog.i("SHORTCUT", "步驟 " + (idx + 1) + "/" + sc.steps.size + ": " + label)
        var delay = 500L
        var ok = true
        var msg = label
        try {
            when (st.type) {
                "app" -> {
                    val i = ctx.packageManager.getLaunchIntentForPackage(st.a)
                    if (i == null) {
                        ok = false
                        msg = "找不到 App"
                    } else {
                        ok = Launcher.start(ctx, i)
                    }
                }
                "url" -> {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(st.a))
                    if (st.b.isNotEmpty()) i.setPackage(st.b)
                    ok = Launcher.start(ctx, i)
                }
                "wait" -> {
                    val sec = st.a.toDoubleOrNull() ?: 1.0
                    delay = (sec * 1000).toLong().coerceIn(0L, 60000L)
                }
                "tap" -> {
                    val p = st.a.split(",")
                    val x = p.getOrNull(0)?.trim()?.toFloatOrNull() ?: 50f
                    val y = p.getOrNull(1)?.trim()?.toFloatOrNull() ?: 50f
                    ok = DeskAccess.tap(x, y)
                    if (!ok) msg = "需要開啟無障礙服務"
                }
                "text" -> {
                    // 頁面可能還在載入, 最多重試 8 秒
                    retryClick(sc, idx, st.a, 8)
                    return
                }
                "media" -> ok = mediaKey(st.a)
                "back" -> {
                    ok = DeskAccess.back()
                    if (!ok) msg = "需要開啟無障礙服務"
                }
                else -> {
                    ok = false
                    msg = "未知步驟 " + st.type
                }
            }
        } catch (e: Exception) {
            ok = false
            msg = "錯誤 " + e.javaClass.simpleName
        }
        if (!ok) {
            finish(sc, false, msg)
            return
        }
        host.handler.postDelayed({ step(sc, idx + 1) }, delay)
    }

    private fun retryClick(sc: Shortcut, idx: Int, text: String, left: Int) {
        if (DeskAccess.clickText(text)) {
            host.handler.postDelayed({ step(sc, idx + 1) }, 600L)
            return
        }
        if (left <= 0) {
            val m = if (DeskAccess.instance == null) "需要開啟無障礙服務" else "找不到「" + text + "」"
            finish(sc, false, m)
            return
        }
        host.handler.postDelayed({ retryClick(sc, idx, text, left - 1) }, 1000L)
    }

    private fun mediaKey(kind: String): Boolean {
        val code = when (kind) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return true
    }
}
