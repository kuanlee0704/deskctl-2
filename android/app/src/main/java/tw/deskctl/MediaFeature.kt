package tw.deskctl

import android.content.ComponentName
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import org.json.JSONObject

/**
 * 音樂控制: 歌名/進度/播放狀態/音量/輸出裝置/隨機與循環 → 控制器;
 * 控制器的 pp / next / prev / seek / vol / mute / shuffle / repeat 指令 → 手機播放器。
 * 需要使用者開啟「通知存取」才能讀到其他 App 的媒體工作階段。
 */
class MediaFeature(host: FeatureHost) : Feature(host) {
    override val id = "media"

    private val ctx: Context = host.context
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var msm: MediaSessionManager? = null
    private var listenerCn: ComponentName? = null
    private var controller: MediaController? = null
    private var compat: MediaControllerCompat? = null
    private var mediaInfo = ""
    private var started = false

    private val sessionsCb = MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list) }

    override fun info(): String = mediaInfo

    // ---------------- 生命週期 ----------------
    override fun start() {
        if (started) return
        try {
            val mgr = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val cn = ComponentName(ctx, NotifListener::class.java)
            msm = mgr
            listenerCn = cn
            mgr.addOnActiveSessionsChangedListener(sessionsCb, cn, host.handler)
            started = true
            pickController(mgr.getActiveSessions(cn))
        } catch (e: SecurityException) {
            mediaInfo = "尚未授權「通知存取」, 讀不到音樂資訊"
            AppLog.w("MEDIA", mediaInfo)
        }
    }

    override fun stop() {
        try {
            controller?.unregisterCallback(mediaCb)
        } catch (_: Exception) {
        }
        try {
            msm?.removeOnActiveSessionsChangedListener(sessionsCb)
        } catch (_: Exception) {
        }
        controller = null
        compat = null
        started = false
    }

    override fun onReady() {
        // 通知存取可能是連線後才開, 沒啟動成功就再試一次
        if (!started) start()
        pushTrack(); pushPlay(); pushVol(); pushRoute(); pushMode()
    }

    override fun onTick(n: Int) {
        if (n % 2 == 0) { pushVol(); pushRoute(); pushMode() }
        if (n % 3 == 0 && controller?.playbackState?.state == PlaybackState.STATE_PLAYING) pushPlay()
    }

    // ---------------- 控制器指令 ----------------
    override fun onCommand(c: String, o: JSONObject): Boolean {
        val tc = controller?.transportControls
        when (c) {
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
                host.handler.postDelayed({ pushPlay() }, 250)
            }
            "vol" -> {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val nv = (am.getStreamVolume(AudioManager.STREAM_MUSIC) + o.optInt("d", 0)).coerceIn(0, max)
                try {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                } catch (_: SecurityException) {
                }
                host.handler.postDelayed({ pushVol() }, 150)
            }
            "mute" -> {
                val flag = if (am.isStreamMute(AudioManager.STREAM_MUSIC)) AudioManager.ADJUST_UNMUTE
                else AudioManager.ADJUST_MUTE
                try {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, flag, 0)
                } catch (_: SecurityException) {
                }
                host.handler.postDelayed({ pushVol() }, 150)
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
            else -> return false
        }
        return true
    }

    private fun mediaKey(code: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---------------- 推送狀態 ----------------
    private fun appLabel(pkg: String): String = try {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun pushTrack() {
        val md = controller?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val app = controller?.packageName?.let { appLabel(it) } ?: ""
        mediaInfo = if (controller == null) "沒有媒體工作階段 (先在手機播放音樂)" else "♪ $app: $title"
        host.sendIfChanged(
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
        host.send(JSONObject().put("k", "play").put("s", s).put("p", currentPos()).put("v", v))
    }

    private fun pushVol() {
        val o = JSONObject().put("k", "vol")
            .put("v", am.getStreamVolume(AudioManager.STREAM_MUSIC))
            .put("m", am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            .put("mu", if (am.isStreamMute(AudioManager.STREAM_MUSIC)) 1 else 0)
        host.sendIfChanged("vol", o)
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
        host.sendIfChanged("route", JSONObject().put("k", "route").put("r", r).put("n", name))
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
        host.sendIfChanged("mode", o)
    }

    // ---------------- 媒體工作階段 ----------------
    private fun pickController(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (best != null && best.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(mediaCb)
        controller = best
        compat = null
        if (best != null) {
            best.registerCallback(mediaCb, host.handler)
            try {
                compat = MediaControllerCompat(ctx, MediaSessionCompat.Token.fromToken(best.sessionToken))
            } catch (_: Exception) {
            }
            AppLog.i("MEDIA", "控制 " + appLabel(best.packageName))
        }
        pushTrack(); pushPlay(); pushMode()
    }

    private val mediaCb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            pushTrack()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            pushPlay()
            // 另一個 App 開始播放時, 切換過去
            val mgr = msm
            val cn = listenerCn
            if (mgr != null && cn != null && state?.state == PlaybackState.STATE_PLAYING) {
                try {
                    pickController(mgr.getActiveSessions(cn))
                } catch (_: Exception) {
                }
            }
        }

        override fun onSessionDestroyed() {
            val mgr = msm
            val cn = listenerCn
            controller = null
            if (mgr != null && cn != null) {
                try {
                    pickController(mgr.getActiveSessions(cn))
                } catch (_: Exception) {
                }
            }
            pushTrack(); pushPlay()
        }
    }
}
