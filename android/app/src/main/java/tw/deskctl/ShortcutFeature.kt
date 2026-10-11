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
                "refresh" -> {
                    if (DeskAccess.instance == null) {
                        finish(sc, false, "需要開啟無障礙服務")
                    } else {
                        refreshStep(sc, idx, 0)
                    }
                    return
                }
                "killbg" -> {
                    Cleaner.cleanMemory(ctx, host.handler) { r ->
                        AppLog.i("SHORTCUT", "清理背景 App: 嘗試 ${r.tried} 個, 釋放約 ${r.freedMb}MB")
                        step(sc, idx + 1)
                    }
                    return
                }
                "clearrecents" -> {
                    Cleaner.clearRecents(host.handler) { ok, why ->
                        if (ok) step(sc, idx + 1) else finish(sc, false, why)
                    }
                    return
                }
                "ensureplay" -> {
                    ensurePlay(sc, idx, 3, st.a.ifEmpty { "50,23" })
                    return
                }
                "firstvideo" -> {
                    if (DeskAccess.instance == null) {
                        finish(sc, false, "需要開啟無障礙服務")
                    } else {
                        firstVideo(sc, idx, 10)
                    }
                    return
                }
                "wake" -> {
                    wakeScreen()
                    delay = 1200L
                }
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

    private val RELOAD = arrayOf("重新載入", "重新整理", "Reload", "Refresh")
    private val MENU = arrayOf("更多選項", "更多", "選單", "More options", "More", "Menu")

    /** 重新整理: 0 直接找「重新載入」鈕 → 1 打開選單再找 → 2 先捲到頂再慢速下拉 */
    private fun refreshStep(sc: Shortcut, idx: Int, stage: Int) {
        val next = { host.handler.postDelayed({ step(sc, idx + 1) }, 1500L) }
        when (stage) {
            0 -> {
                if (DeskAccess.clickAny(*RELOAD)) {
                    AppLog.i("SHORTCUT", "重新整理: 點了「重新載入」")
                    next()
                } else refreshStep(sc, idx, 1)
            }
            1 -> {
                if (DeskAccess.clickAny(*MENU)) {
                    host.handler.postDelayed({
                        if (DeskAccess.clickAny(*RELOAD)) {
                            AppLog.i("SHORTCUT", "重新整理: 從選單點了「重新載入」")
                            next()
                        } else {
                            DeskAccess.back()          // 關掉選單, 改用下拉
                            host.handler.postDelayed({ refreshStep(sc, idx, 2) }, 500L)
                        }
                    }, 800L)
                } else refreshStep(sc, idx, 2)
            }
            else -> {
                AppLog.i("SHORTCUT", "重新整理: 找不到按鈕, 改用下拉")
                DeskAccess.swipe(50f, 30f, 50f, 80f, 250L)           // 先捲到頁面最上面
                host.handler.postDelayed({
                    DeskAccess.swipe(50f, 30f, 50f, 80f, 250L)
                    host.handler.postDelayed({
                        val ok = DeskAccess.swipe(50f, 25f, 50f, 75f, 900L)    // 慢速下拉 = 重新整理
                        if (ok) next() else finish(sc, false, "下拉失敗")
                    }, 700L)
                }, 700L)
            }
        }
    }

    /** 點第一部影片: 頁面可能還在載入, 每秒重試; 找不到就點畫面 (50%,38%) */
    private fun firstVideo(sc: Shortcut, idx: Int, left: Int) {
        if (DeskAccess.clickFirstVideo()) {
            AppLog.i("SHORTCUT", "已點第一部影片")
            host.handler.postDelayed({ step(sc, idx + 1) }, 800L)
            return
        }
        if (left <= 0) {
            AppLog.w("SHORTCUT", "找不到影片項目, 改點畫面 (50%,38%)")
            DeskAccess.dump("畫面內容")
            val ok = DeskAccess.tap(50f, 38f)
            if (ok) host.handler.postDelayed({ step(sc, idx + 1) }, 800L) else finish(sc, false, "找不到影片")
            return
        }
        host.handler.postDelayed({ firstVideo(sc, idx, left - 1) }, 1000L)
    }

    private fun musicActive(): Boolean =
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive

    /**
     * 確保真的在播放: 瀏覽器在背景開啟的影片常卡在開頭或被自動靜音 (需要「使用者手勢」才解除)。
     * 每一輪依序: 點「取消靜音」並解除系統靜音 → 送播放鍵 → 用真實觸控點一下播放器 (這一下算使用者手勢),
     * 最多 3 輪; 只要偵測到有聲音在播放就結束。
     */
    private fun ensurePlay(sc: Shortcut, idx: Int, left: Int, pos: String) {
        val hd = host.handler
        val next = { hd.postDelayed({ step(sc, idx + 1) }, 500L); Unit }
        if (musicActive()) {
            AppLog.i("SHORTCUT", "已在播放")
            next()
            return
        }
        if (left <= 0) {
            DeskAccess.dump("畫面內容")
            finish(sc, false, "沒有開始播放")
            return
        }
        AppLog.i("SHORTCUT", "尚未播放, 嘗試喚醒 (剩 " + left + " 次)")
        if (left == 3) DeskAccess.dump("畫面內容")
        // 先找播放器上真正的「播放」按鈕 (精準比對), 找不到再補一個真實觸控
        if (DeskAccess.clickExact("播放", "播放影片", "Play", "Play video", "Resume")) {
            AppLog.i("SHORTCUT", "點了播放按鈕")
        }
        DeskAccess.clickAny("取消靜音", "解除靜音", "點按即可取消靜音", "Unmute", "Tap to unmute")
        try {
            (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        } catch (_: Exception) {
        }
        hd.postDelayed({
            if (musicActive()) {
                next()
            } else {
                mediaKey("play")
                hd.postDelayed({
                    if (musicActive()) {
                        next()
                    } else {
                        val p = pos.split(",")
                        val x = p.getOrNull(0)?.trim()?.toFloatOrNull() ?: 50f
                        val y = p.getOrNull(1)?.trim()?.toFloatOrNull() ?: 23f
                        DeskAccess.tap(x, y)
                        hd.postDelayed({ ensurePlay(sc, idx, left - 1, pos) }, 1500L)
                    }
                }, 1500L)
            }
        }, 1200L)
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

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val wl = pm.newWakeLock(
            android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "deskctl:wake"
        )
        wl.acquire(5000L)
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
