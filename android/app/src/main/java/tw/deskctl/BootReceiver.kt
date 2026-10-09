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
        val prefs = context.getSharedPreferences("deskctl", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("autostart", true) || !prefs.getBoolean("was_started", false)) return
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            ContextCompat.startForegroundService(
                context, Intent(context, BridgeService::class.java).setAction(BridgeService.ACTION_START)
            )
        } catch (_: Exception) {
        }
    }
}
