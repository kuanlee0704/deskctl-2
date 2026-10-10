package tw.deskctl

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.autostart(context) || !Prefs.wasStarted(context)) return
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            AppLog.init(context)
            AppLog.i("BOOT", "開機自動啟動服務")
            ContextCompat.startForegroundService(
                context, Intent(context, BridgeService::class.java).setAction(BridgeService.ACTION_START)
            )
        } catch (e: Exception) {
            AppLog.e("BOOT", "自動啟動失敗: " + e)
        }
    }
}
