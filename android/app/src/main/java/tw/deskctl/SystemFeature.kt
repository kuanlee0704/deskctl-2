package tw.deskctl

import org.json.JSONObject

/** 每 10 秒把手機系統狀態 (見 PhoneStats) 傳給控制器的「手機狀態」頁。內容沒變就不重送。 */
class SystemFeature(host: FeatureHost) : Feature(host) {
    override val id = "system"

    override fun onReady() {
        push()
    }

    override fun onTick(n: Int) {
        if (n % 10 == 0) push()
    }

    private fun push() {
        val s = PhoneStats.collect(host.context)
        s.remove("ut")          // 開機分鐘每分鐘都變, 控制器不顯示, 不傳
        s.put("k", "sys")
        host.sendIfChanged("sys", s)
    }
}
