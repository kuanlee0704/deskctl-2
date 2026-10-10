package tw.deskctl

import android.content.Context
import android.os.Handler
import org.json.JSONObject

/** 功能模組可用的「主機」能力 (由 BridgeService 提供)。 */
interface FeatureHost {
    val context: Context
    val handler: Handler          // 主執行緒
    val isReady: Boolean          // 已與控制器連線完成, 可以送資料
    fun send(o: JSONObject)
    /** 內容沒變就不重送; key 用來比對 */
    fun sendIfChanged(key: String, o: JSONObject)
}

/**
 * 一個「功能模組」 = 手機端的一塊功能 (時間、電量、音樂…)。
 * 新增功能的步驟:
 *   1. 新增一個繼承 Feature 的類別, 覆寫需要的函式
 *   2. 到 Features.kt 的 registry 加一行
 * 控制器 → 手機的指令格式為 {"c":"名稱", ...}, 在 onCommand 認領 (回傳 true 表示已處理)。
 * 手機 → 控制器用 host.send(...), 格式 {"k":"名稱", ...}; 控制器韌體端在 state.py 的 apply() 加對應處理。
 */
abstract class Feature(val host: FeatureHost) {
    abstract val id: String

    /** 服務啟動時 (還沒連線) */
    open fun start() {}

    /** 服務停止時, 釋放資源 */
    open fun stop() {}

    /** 與控制器連線完成 (或控制器要求重新同步) 時: 送出完整狀態 */
    open fun onReady() {}

    /** 連線期間每秒一次, n 為累計秒數 */
    open fun onTick(n: Int) {}

    /** 收到控制器指令; 是你的就處理並回傳 true */
    open fun onCommand(c: String, o: JSONObject): Boolean = false

    /** 顯示在主頁的一行說明 (沒有就回傳空字串) */
    open fun info(): String = ""
}
