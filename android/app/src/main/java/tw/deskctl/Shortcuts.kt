package tw.deskctl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 捷徑 = 一串步驟, 控制器按下後由手機依序執行。
 * 步驟類型 (type):
 *   app   a=套件名稱            開啟 App
 *   url   a=網址 b=指定瀏覽器套件  用瀏覽器 (或指定 App) 開網址
 *   wait  a=秒數                等待
 *   tap   a="x,y" (畫面百分比)   點擊畫面 (需無障礙服務)
 *   text  a=文字                點擊畫面上含此文字的按鈕 (需無障礙服務)
 *   media a=play|pause|toggle|next|prev   送出媒體鍵
 *   back                        按「返回」(需無障礙服務)
 * 要新增步驟類型: 在 ShortcutFeature.step() 加一個分支, 並在 ShortcutsPage 的選單加一項。
 */
class Step(val type: String, val a: String = "", val b: String = "")

class Shortcut(var name: String, val steps: MutableList<Step> = ArrayList())

object Shortcuts {
    /** 每次儲存就 +1, ShortcutFeature 看到變動就把名單重新傳給控制器 */
    @Volatile var version: Int = 0

    fun load(c: Context): MutableList<Shortcut> {
        val out = ArrayList<Shortcut>()
        try {
            val arr = JSONArray(Prefs.shortcuts(c))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val sc = Shortcut(o.optString("n", "捷徑"))
                val ss = o.optJSONArray("s")
                if (ss != null) {
                    for (k in 0 until ss.length()) {
                        val so = ss.getJSONObject(k)
                        sc.steps.add(Step(so.optString("t"), so.optString("a"), so.optString("b")))
                    }
                }
                out.add(sc)
            }
        } catch (e: Exception) {
            AppLog.e("SHORTCUT", "讀取捷徑失敗: " + e)
        }
        return out
    }

    fun save(c: Context, list: List<Shortcut>) {
        val arr = JSONArray()
        for (sc in list) {
            val o = JSONObject()
            o.put("n", sc.name)
            val ss = JSONArray()
            for (st in sc.steps) {
                ss.put(JSONObject().put("t", st.type).put("a", st.a).put("b", st.b))
            }
            o.put("s", ss)
            arr.put(o)
        }
        Prefs.setShortcuts(c, arr.toString())
        version++
    }

    fun appLabel(c: Context, pkg: String): String = try {
        c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    fun mediaLabel(a: String): String = when (a) {
        "play" -> "播放"
        "pause" -> "暫停"
        "next" -> "下一首"
        "prev" -> "上一首"
        else -> "播放/暫停"
    }

    fun describe(c: Context, s: Step): String = when (s.type) {
        "app" -> "開啟 " + appLabel(c, s.a)
        "url" -> "開網址 " + s.a + (if (s.b.isNotEmpty()) "  (用 " + appLabel(c, s.b) + ")" else "")
        "wait" -> "等待 " + s.a + " 秒"
        "tap" -> "點擊畫面 (" + s.a + " %)"
        "text" -> "點擊文字「" + s.a + "」"
        "media" -> "媒體鍵: " + mediaLabel(s.a)
        "back" -> "按返回"
        "refresh" -> "重新整理目前頁面"
        "wake" -> "喚醒螢幕"
        else -> s.type
    }
}
