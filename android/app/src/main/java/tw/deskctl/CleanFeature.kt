package tw.deskctl

import org.json.JSONObject

/**
 * 控制器「手機狀態」頁中央鍵 → {"c":"clean"}: 結束背景 App + 清本 App 快取;
 * 中央雙擊 → {"c":"clean2"}: 再加清除「最近使用」清單 (需無障礙)。結果以 {"k":"act",...} 回報, 控制器會跳提示。
 */
class CleanFeature(host: FeatureHost) : Feature(host) {
    override val id = "clean"
    private var busy = false

    private fun report(state: String, msg: String = "") {
        host.send(JSONObject().put("k", "act").put("s", state).put("n", "清理").put("m", msg.take(30)))
    }

    override fun onCommand(c: String, o: JSONObject): Boolean {
        if (c != "clean" && c != "clean2") return false
        if (busy) {
            report("busy")
            return true
        }
        busy = true
        report("run")
        AppLog.i("CLEAN", if (c == "clean2") "清理背景 App + 最近使用清單" else "清理背景 App")
        Cleaner.cleanMemory(host.context, host.handler) { r ->
            val msg = "釋放約 ${r.freedMb}MB"
            AppLog.i("CLEAN", "已嘗試結束 ${r.tried} 個 App, 清快取 ${r.cacheKb}KB, $msg")
            if (c == "clean2") {
                Cleaner.clearRecents(host.handler) { ok, why ->
                    busy = false
                    if (ok) report("done", msg + ", 已清最近") else report("done", msg + " (最近:" + why + ")")
                }
            } else {
                busy = false
                report("done", msg)
            }
        }
        return true
    }
}
