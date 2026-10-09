package tw.deskctl

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.TimeZone
import java.util.UUID

/**
 * 常駐服務: 與 ESP32 (DeskCtl) 建立 BLE 連線,
 * 把手機的時間/電量/音樂資訊/音量送過去, 並執行桌邊控制器傳來的指令。
 */
@SuppressLint("MissingPermission")
class BridgeService : Service() {

    companion object {
        const val ACTION_START = "tw.deskctl.START"
        const val ACTION_STOP = "tw.deskctl.STOP"
        const val ACTION_FORGET = "tw.deskctl.FORGET"
        private const val CHANNEL = "bridge"
        private const val NOTIF_ID = 1
        private const val DEVICE_NAME = "DeskCtl"

        val NUS: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val CHAR_RX: UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // 手機 -> 裝置 (寫入)
        val CHAR_TX: UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // 裝置 -> 手機 (通知)
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        @Volatile var status: String = "尚未啟動"
        @Volatile var running: Boolean = false
        @Volatile var mediaInfo: String = ""
    }

    private val h = Handler(Looper.getMainLooper())
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var am: AudioManager
    private var gatt: BluetoothGatt? = null
    private var rxChar: BluetoothGattCharacteristic? = null
    private var ready = false
    private var mtu = 23
    private var scanning = false
    private var inited = false
    private var tick = 0

    private val txQueue = ArrayDeque<ByteArray>()
    private var writing = false
    private val inBuf = StringBuilder()

    // 媒體
    private var msm: MediaSessionManager? = null
    private var listenerCn: ComponentName? = null
    private var controller: MediaController? = null
    private var compat: MediaControllerCompat? = null
    private val lastSent = HashMap<String, String>()

    // ---------------- 生命週期 ----------------
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                prefs = getSharedPreferences("deskctl", MODE_PRIVATE)
                prefs.edit().putBoolean("was_started", false).apply()
                shutdown()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_FORGET -> {
                getSharedPreferences("deskctl", MODE_PRIVATE).edit().remove("mac").apply()
                if (inited) { closeGatt(); h.post { connect() } }
            }
        }
        startAsForeground()
        if (!inited) init()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "桌邊控制器", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = buildNotif(status)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("桌邊控制器")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun setStatus(s: String) {
        status = s
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotif(s))
        } catch (_: Exception) {}
    }

    private fun init() {
        inited = true
        running = true
        prefs = getSharedPreferences("deskctl", MODE_PRIVATE)
        prefs.edit().putBoolean("was_started", true).apply()
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        ContextCompat.registerReceiver(
            this, battReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        setupMedia()
        connect()
        h.postDelayed(ticker, 1000)
    }

    private fun shutdown() {
        running = false
        inited = false
        h.removeCallbacksAndMessages(null)
        try { unregisterReceiver(battReceiver) } catch (_: Exception) {}
        controller?.unregisterCallback(mediaCb)
        stopScan()
        closeGatt()
        setStatus("已停止")
    }

    override fun onDestroy() {
        if (inited) shutdown()
        super.onDestroy()
    }

    // ---------------- 連線 ----------------
    private fun hasBt(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun adapter(): BluetoothAdapter? =
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private fun connect() {
        if (!inited) return
        val ad = adapter()
        if (ad == null || !ad.isEnabled) {
            setStatus("請先開啟手機藍牙")
            h.postDelayed({ connect() }, 4000)
            return
        }
        if (!hasBt()) {
            setStatus("缺少藍牙權限, 請開啟 App 授權")
            h.postDelayed({ connect() }, 5000)
            return
        }
        val mac = prefs.getString("mac", null)
        if (mac != null) {
            setStatus("連線中…")
            closeGatt()
            val dev = ad.getRemoteDevice(mac)
            gatt = dev.connectGatt(this, true, gattCb, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
        } else {
            startScan(ad)
        }
    }

    private fun startScan(ad: BluetoothAdapter) {
        if (scanning) return
        val sc = ad.bluetoothLeScanner ?: run {
            setStatus("無法掃描藍牙"); h.postDelayed({ connect() }, 4000); return
        }
        setStatus("搜尋 $DEVICE_NAME …")
        val filters = listOf(ScanFilter.Builder().setDeviceName(DEVICE_NAME).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanning = true
        sc.startScan(filters, settings, scanCb)
        h.postDelayed({
            if (scanning) { stopScan(); connect() }   // 逾時後重新掃描
        }, 20000)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try { adapter()?.bluetoothLeScanner?.stopScan(scanCb) } catch (_: Exception) {}
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            h.post {
                if (!scanning) return@post
                stopScan()
                prefs.edit().putString("mac", result.device.address).apply()
                setStatus("找到裝置, 連線中…")
                closeGatt()
                gatt = result.device.connectGatt(
                    this@BridgeService, false, gattCb, android.bluetooth.BluetoothDevice.TRANSPORT_LE
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            h.post { scanning = false; setStatus("掃描失敗($errorCode)"); h.postDelayed({ connect() }, 5000) }
        }
    }

    private fun closeGatt() {
        ready = false
        writing = false
        txQueue.clear()
        inBuf.setLength(0)
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        rxChar = null
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, newState: Int) {
            h.post {
                if (g != gatt) return@post
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    setStatus("已連上, 協商中…")
                    if (!g.requestMtu(247)) g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    setStatus("連線中斷, 重新連線…")
                    closeGatt()
                    h.postDelayed({ connect() }, 2000)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, m: Int, st: Int) {
            h.post { mtu = if (st == BluetoothGatt.GATT_SUCCESS) m else 23; g.discoverServices() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            h.post {
                val svc = g.getService(NUS)
                val tx = svc?.getCharacteristic(CHAR_TX)
                rxChar = svc?.getCharacteristic(CHAR_RX)
                if (tx == null || rxChar == null) {
                    setStatus("找不到控制器服務")
                    return@post
                }
                g.setCharacteristicNotification(tx, true)
                val d = tx.getDescriptor(CCCD)
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(d)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) {
            h.post {
                ready = true
                setStatus("已連線 DeskCtl")
                lastSent.clear()
                pushAll()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            h.post { writing = false; pump() }
        }

        // Android 13+ 走這個
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            h.post { onIncoming(String(value, Charsets.UTF_8)) }
        }

        // Android 12 以下走這個
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val bytes = c.value ?: return
            h.post { onIncoming(String(bytes, Charsets.UTF_8)) }
        }
    }

    // ---------------- 傳送 ----------------
    private fun send(o: JSONObject) {
        if (!ready) return
        val data = (o.toString() + "\n").toByteArray(Charsets.UTF_8)
        val size = maxOf(20, mtu - 3)
        var i = 0
        while (i < data.size) {
            val e = minOf(data.size, i + size)
            if (txQueue.size < 300) txQueue.addLast(data.copyOfRange(i, e))
            i = e
        }
        pump()
    }

    /** 內容沒變就不重送 (key 用來比對) */
    private fun sendIfChanged(key: String, o: JSONObject) {
        val s = o.toString()
        if (lastSent[key] == s) return
        lastSent[key] = s
        send(o)
    }

    private fun pump() {
        val g = gatt ?: return
        val c = rxChar ?: return
        if (!ready || writing || txQueue.isEmpty()) return
        val chunk = txQueue.removeFirst()
        c.value = chunk
        c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        writing = g.writeCharacteristic(c)
        if (!writing) {
            txQueue.addFirst(chunk)
            h.postDelayed({ pump() }, 60)
        }
    }

    // ---------------- 接收指令 ----------------
    private fun onIncoming(s: String) {
        inBuf.append(s)
        while (true) {
            val i = inBuf.indexOf("\n")
            if (i < 0) break
            val line = inBuf.substring(0, i).trim()
            inBuf.delete(0, i + 1)
            if (line.isEmpty()) continue
            try { handleCmd(JSONObject(line)) } catch (_: Exception) {}
        }
        if (inBuf.length > 2000) inBuf.setLength(0)
    }

    private fun handleCmd(o: JSONObject) {
        val tc = controller?.transportControls
        when (o.optString("c")) {
            "pp" -> {
                if (controller != null) {
                    if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) tc?.pause() else tc?.play()
                } else mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            "next" -> if (tc != null) tc.skipToNext() else mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "prev" -> if (tc != null) tc.skipToPrevious() else mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "seek" -> {
                val d = o.optLong("d", 0)
                val dur = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                var p = currentPos() + d
                if (p < 0) p = 0
                if (dur > 0 && p > dur) p = dur
                tc?.seekTo(p)
                h.postDelayed({ pushPlay() }, 250)
            }
            "vol" -> {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val nv = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + o.optInt("d", 0)).coerceIn(0, max)
                try { am.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0) } catch (_: SecurityException) {}
                h.postDelayed({ pushVol() }, 150)
            }
            "mute" -> {
                val flag = if (am.isStreamMute(AudioManager.STREAM_MUSIC)) AudioManager.ADJUST_UNMUTE
                else AudioManager.ADJUST_MUTE
                try { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, 0) } catch (_: SecurityException) {}
                h.postDelayed({ pushVol() }, 150)
            }
            "shuffle" -> compat?.transportControls?.setShuffleMode(
                if (o.optInt("v") == 1) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE
            )
            "repeat" -> compat?.transportControls?.setRepeatMode(
                when (o.optInt("v")) {
                    1 -> PlaybackStateCompat.REPEAT_MODE_ALL
                    2 -> PlaybackStateCompat.REPEAT_MODE_ONE
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                }
            )
            "sync" -> { lastSent.clear(); pushAll() }
            else -> {}
        }
    }

    private fun mediaKey(code: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---------------- 推送狀態 ----------------
    private fun pushAll() {
        pushTime(); pushBatt(); pushTrack(); pushPlay(); pushVol(); pushRoute(); pushMode()
    }

    private fun pushTime() {
        val now = System.currentTimeMillis()
        send(JSONObject().put("k", "time").put("t", now / 1000).put("z", TimeZone.getDefault().getOffset(now) / 60000))
    }

    private var battPct = -1
    private var battCharging = false
    private val battReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val lv = i.getIntExtra("level", -1)
            val sc = i.getIntExtra("scale", 100)
            battPct = if (lv >= 0 && sc > 0) lv * 100 / sc else -1
            val st = i.getIntExtra("status", -1)
            battCharging = st == 2 || st == 5   // CHARGING / FULL
            pushBatt()
        }
    }

    private fun pushBatt() {
        if (battPct < 0) return
        sendIfChanged("batt", JSONObject().put("k", "batt").put("p", battPct).put("ch", if (battCharging) 1 else 0))
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg }

    private fun pushTrack() {
        val md = controller?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val app = controller?.packageName?.let { appLabel(it) } ?: ""
        mediaInfo = if (controller == null) "沒有媒體工作階段" else "$app: $title"
        sendIfChanged(
            "track",
            JSONObject().put("k", "track").put("t", title).put("a", artist).put("b", album)
                .put("d", dur).put("app", app)
        )
    }

    private fun currentPos(): Long {
        val ps = controller?.playbackState ?: return 0
        var p = ps.position
        if (p < 0) return 0
        if (ps.state == PlaybackState.STATE_PLAYING) {
            p += ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
        }
        return p
    }

    private fun pushPlay() {
        val ps = controller?.playbackState
        val s = when (ps?.state) {
            PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> 1
            PlaybackState.STATE_PAUSED -> 2
            else -> 0
        }
        val sp = ps?.playbackSpeed ?: 1f
        val v = if (sp <= 0f) 100 else (sp * 100).toInt()
        send(JSONObject().put("k", "play").put("s", s).put("p", currentPos()).put("v", v))
    }

    private fun pushVol() {
        val o = JSONObject().put("k", "vol")
            .put("v", am.getStreamVolume(AudioManager.STREAM_MUSIC))
            .put("m", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            .put("mu", if (am.isStreamMute(AudioManager.STREAM_MUSIC)) 1 else 0)
        sendIfChanged("vol", o)
    }

    private fun pushRoute() {
        var r = "spk"
        var name = ""
        for (d in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            val t = d.type
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == 26 /*BLE_HEADSET*/ || t == 27 /*BLE_SPEAKER*/ ||
                t == AudioDeviceInfo.TYPE_HEARING_AID
            ) {
                r = "bt"; name = d.productName?.toString() ?: ""
                break
            }
            if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                t == AudioDeviceInfo.TYPE_USB_HEADSET
            ) {
                r = "wired"; name = d.productName?.toString() ?: ""
            }
        }
        sendIfChanged("route", JSONObject().put("k", "route").put("r", r).put("n", name))
    }

    private fun pushMode() {
        val c = compat ?: return
        val sh = c.shuffleMode
        val rp = c.repeatMode
        if (sh < 0 && rp < 0) return           // 播放器沒有回報, 讓裝置自己記
        val o = JSONObject().put("k", "mode")
        if (sh >= 0) o.put("sh", if (sh == PlaybackStateCompat.SHUFFLE_MODE_NONE) 0 else 1)
        if (rp >= 0) o.put(
            "rp",
            when (rp) {
                PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> 1
                PlaybackStateCompat.REPEAT_MODE_ONE -> 2
                else -> 0
            }
        )
        sendIfChanged("mode", o)
    }

    // ---------------- 媒體工作階段 ----------------
    private fun setupMedia() {
        try {
            val mgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(this, NotifListener::class.java)
            msm = mgr; listenerCn = cn
            mgr.addOnActiveSessionsChangedListener({ list -> pickController(list) }, cn, h)
            pickController(mgr.getActiveSessions(cn))
        } catch (e: SecurityException) {
            mediaInfo = "尚未授權「通知存取」, 讀不到音樂資訊"
        }
    }

    private fun pickController(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken && best != null) return
        controller?.unregisterCallback(mediaCb)
        controller = best
        compat = null
        if (best != null) {
            best.registerCallback(mediaCb, h)
            try {
                compat = MediaControllerCompat(this, MediaSessionCompat.Token.fromToken(best.sessionToken))
            } catch (_: Exception) {}
        }
        pushTrack(); pushPlay(); pushMode()
    }

    private val mediaCb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { pushTrack() }
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            pushPlay()
            // 另一個 App 開始播放時, 切換過去
            val mgr = msm; val cn = listenerCn
            if (mgr != null && cn != null && state?.state == PlaybackState.STATE_PLAYING) {
                try { pickController(mgr.getActiveSessions(cn)) } catch (_: Exception) {}
            }
        }
        override fun onSessionDestroyed() {
            val mgr = msm; val cn = listenerCn
            controller = null
            if (mgr != null && cn != null) {
                try { pickController(mgr.getActiveSessions(cn)) } catch (_: Exception) {}
            }
            pushTrack(); pushPlay()
        }
    }

    // ---------------- 每秒節拍 ----------------
    private val ticker = object : Runnable {
        override fun run() {
            tick++
            if (ready) {
                if (tick % 2 == 0) { pushVol(); pushRoute(); pushMode() }
                if (tick % 5 == 0) {
                    send(JSONObject().put("k", "ping"))
                    if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) pushPlay()
                }
                if (tick % 60 == 0) pushTime()
            }
            if (inited) h.postDelayed(this, 1000)
        }
    }
}
