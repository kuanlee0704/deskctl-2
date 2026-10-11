package tw.deskctl

import android.app.Notification
import android.service.notification.StatusBarNotification
import org.json.JSONObject

/**
 * 手機訊息: 把手機收到的通知 (LINE、簡訊、Email…) 轉發到控制器的「手機訊息」頁。
 * 協定: 手機→控制器 {"k":"msg","i":編號,"a":App名,"t":標題,"x":內容}; 手機上通知被清除/已讀 → {"k":"msgrm","i":編號}。
 * 只轉發設定中勾選的 App; 常駐/進度/媒體播放器/群組摘要/本 App 的通知一律不轉發; 可在設定「隱藏內容」(只顯示 App 名)。
 */
class MessageFeature(host: FeatureHost) : Feature(host) {
    override val id = "message"

    private var sent = 0
    private val lastText = HashMap<String, String>()
    private val skipCats = arrayOf(
        Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_SYSTEM, Notification.CATEGORY_STATUS, Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_STOPWATCH
    )

    override fun start() {
        MsgBus.onPosted = { sbn -> host.handler.post { posted(sbn) } }
        MsgBus.onRemoved = { sbn -> host.handler.post { removed(sbn) } }
    }

    override fun stop() {
        MsgBus.onPosted = null
        MsgBus.onRemoved = null
        lastText.clear()
    }

    override fun info(): String {
        val ctx = host.context
        if (!Prefs.msgOn(ctx)) return ""
        val n = Prefs.msgAllow(ctx).size
        return if (n == 0) "訊息轉發: 尚未選擇要轉發的 App" else "訊息轉發: $n 個 App (本次 $sent 則)"
    }

    private fun key(sbn: StatusBarNotification) = sbn.packageName + ":" + sbn.id + ":" + (sbn.tag ?: "")

    private fun idOf(sbn: StatusBarNotification): Int = key(sbn).hashCode() and 0x7fffff

    private fun appLabel(pkg: String): String = try {
        val pm = host.context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun posted(sbn: StatusBarNotification) {
        val ctx = host.context
        if (!Prefs.msgOn(ctx) || !host.isReady) return
        val pkg = sbn.packageName
        if (pkg == ctx.packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category != null && n.category in skipCats) return
        if (!Prefs.msgAllow(ctx).contains(pkg)) return            // 只轉發有勾選的 App
        val ex = n.extras ?: return
        if ((ex.getString(Notification.EXTRA_TEMPLATE) ?: "").contains("MediaStyle")) return
        val title = (ex.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString().trim()
        var text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT) ?: "")
            .toString().trim()
        if (title.isEmpty() && text.isEmpty()) return
        val k = key(sbn)
        val sig = title + "\u0000" + text
        if (lastText[k] == sig) return                 // 同一則通知被重複更新, 不重送
        lastText[k] = sig
        if (lastText.size > 200) lastText.clear()
        val o = JSONObject().put("k", "msg").put("i", idOf(sbn)).put("a", appLabel(pkg).take(16))
        if (Prefs.msgHide(ctx)) {
            o.put("t", "新訊息").put("x", "")
        } else {
            text = text.replace('\n', ' ')
            o.put("t", title.take(40)).put("x", text.take(120))
        }
        host.send(o)
        sent++
        AppLog.i("MSG", "轉發 " + appLabel(pkg) + (if (Prefs.msgHide(ctx)) "" else ": " + title.take(12)))
    }

    private fun removed(sbn: StatusBarNotification) {
        if (!Prefs.msgOn(host.context) || !host.isReady) return
        val k = key(sbn)
        if (lastText.remove(k) == null) return          // 本來就沒轉發過的不用通知
        host.send(JSONObject().put("k", "msgrm").put("i", idOf(sbn)))
    }
}
