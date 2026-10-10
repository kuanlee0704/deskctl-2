package tw.deskctl

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.os.StatFs
import org.json.JSONObject
import java.io.File

/**
 * 蒐集手機的系統狀態, 回傳精簡 JSON (key 對應控制器「手機狀態」頁, 見 firmware/p_sys.py):
 *  pl 充電方式(0無 1AC 2USB 4無線)  t 電池溫度(0.1°C)  v 電壓(mV)  th 熱狀態(0-6)
 *  nt 網路(0無 1WiFi 2行動 3其他)  wf WiFi 強度(0-4)  bt 藍牙  sc 螢幕亮  ps 省電模式  ds Doze
 *  bk 待機分組(10活躍…50從未)  br 背景受限  ra RAM 已用%  rf RAM 剩餘MB  ap App 記憶體MB
 *  ca App 快取MB  sf 儲存剩餘(0.1GB)  ut 開機分鐘
 * 每一項都包 try/catch: 任何一項取不到就略過, 不影響其他項目。
 */
object PhoneStats {
    private var cacheMb = 0
    private var cacheAt = 0L

    fun collect(c: Context): JSONObject {
        val o = JSONObject()
        fun safe(f: () -> Unit) {
            try {
                f()
            } catch (_: Exception) {
            }
        }
        safe {
            val b = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (b != null) {
                o.put("pl", b.getIntExtra("plugged", 0))
                o.put("t", b.getIntExtra("temperature", 0))
                o.put("v", b.getIntExtra("voltage", 0))
            }
        }
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        safe {
            if (Build.VERSION.SDK_INT >= 29) o.put("th", pm.currentThermalStatus)
        }
        safe { o.put("ps", if (pm.isPowerSaveMode) 1 else 0) }
        safe { o.put("ds", if (pm.isDeviceIdleMode) 1 else 0) }
        safe { o.put("sc", if (pm.isInteractive) 1 else 0) }
        safe {
            val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val nt = when {
                caps == null -> 0
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 1
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
                else -> 3
            }
            o.put("nt", nt)
            if (nt == 1) {
                @Suppress("DEPRECATION")
                val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val rssi = wm.connectionInfo.rssi
                @Suppress("DEPRECATION")
                o.put("wf", WifiManager.calculateSignalLevel(rssi, 5))
            }
        }
        safe {
            val ad = (c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            o.put("bt", if (ad != null && ad.isEnabled) 1 else 0)
        }
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        safe {
            if (Build.VERSION.SDK_INT >= 28) o.put("br", if (am.isBackgroundRestricted) 1 else 0)
        }
        safe {
            if (Build.VERSION.SDK_INT >= 28) {
                val um = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                o.put("bk", um.appStandbyBucket)
            }
        }
        safe {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            o.put("ra", ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt())
            o.put("rf", (mi.availMem / 1048576).toInt())
        }
        safe {
            val pi = am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
            o.put("ap", pi[0].totalPss / 1024)
        }
        safe { o.put("ca", cacheSizeMb(c)) }
        safe {
            val st = StatFs(Environment.getDataDirectory().path)
            o.put("sf", (st.availableBytes / 107374182L).toInt())     // 0.1GB 為單位
        }
        o.put("ut", (SystemClock.elapsedRealtime() / 60000L).toInt())
        return o
    }

    /** 快取目錄大小 (每 30 秒才重算一次, 避免頻繁走檔案) */
    private fun cacheSizeMb(c: Context): Int {
        val now = SystemClock.elapsedRealtime()
        if (now - cacheAt < 30000L && cacheAt != 0L) return cacheMb
        var n = 0L
        fun walk(f: File?, depth: Int) {
            if (f == null || depth > 6) return
            val l = f.listFiles() ?: return
            for (x in l) {
                if (x.isDirectory) walk(x, depth + 1) else n += x.length()
            }
        }
        walk(c.cacheDir, 0)
        walk(c.externalCacheDir, 0)
        cacheMb = (n / 1048576L).toInt()
        cacheAt = now
        return cacheMb
    }

    // ---------- 給手機畫面顯示的文字 ----------
    private val NET = arrayOf("無網路", "Wi-Fi", "行動網路", "其他")
    private val THERMAL = arrayOf("正常", "輕微", "中等", "嚴重", "危急", "緊急", "關機")

    fun bucketName(b: Int): String = when {
        b <= 10 -> "活躍"
        b <= 20 -> "常用"
        b <= 30 -> "偶爾"
        b <= 40 -> "很少"
        b <= 45 -> "受限"
        else -> "從未使用"
    }

    fun lines(o: JSONObject): List<Pair<String, String>> {
        val l = ArrayList<Pair<String, String>>()
        fun yn(k: String) = if (o.optInt(k, 0) == 1) "是" else "否"
        val plug = when (o.optInt("pl", 0)) {
            1 -> "AC 充電"
            2 -> "USB 充電"
            4 -> "無線充電"
            else -> "未充電"
        }
        l.add("充電" to plug)
        l.add("電池溫度" to String.format("%.1f°C", o.optInt("t", 0) / 10.0))
        l.add("電池電壓" to String.format("%.2f V", o.optInt("v", 0) / 1000.0))
        l.add("熱狀態" to THERMAL[o.optInt("th", 0).coerceIn(0, 6)])
        val nt = o.optInt("nt", 0).coerceIn(0, 3)
        l.add("網路" to NET[nt] + (if (nt == 1) " (訊號 ${o.optInt("wf", 0)}/4)" else ""))
        l.add("藍牙" to (if (o.optInt("bt", 0) == 1) "開" else "關"))
        l.add("螢幕" to (if (o.optInt("sc", 1) == 1) "亮" else "滅"))
        l.add("省電模式" to yn("ps"))
        l.add("Doze 閒置模式" to yn("ds"))
        l.add("本 App 待機分組" to bucketName(o.optInt("bk", 10)))
        l.add("本 App 背景受限" to yn("br"))
        l.add("RAM 已用" to "${o.optInt("ra", 0)}%  (剩 ${o.optInt("rf", 0)} MB)")
        l.add("本 App 記憶體" to "${o.optInt("ap", 0)} MB")
        l.add("本 App 快取" to "${o.optInt("ca", 0)} MB")
        l.add("儲存剩餘" to String.format("%.1f GB", o.optInt("sf", 0) / 10.0))
        val ut = o.optInt("ut", 0)
        l.add("手機開機時間" to (if (ut >= 60) "${ut / 60} 小時 ${ut % 60} 分" else "$ut 分"))
        return l
    }
}
