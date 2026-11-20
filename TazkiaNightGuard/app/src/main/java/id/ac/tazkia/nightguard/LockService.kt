package id.ac.tazkia.nightguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings

/** Service latar depan yang mengecek jam tiap 2 detik dan memasang/melepas layar kunci. */
class LockService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: LockOverlay? = null

    private val tick = object : Runnable {
        override fun run() {
            evaluate()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Prefs.enabled(this)) Watchdog.schedule(this)
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun evaluate() {
        if (!Prefs.enabled(this) && !Prefs.testLock(this)) {
            hideOverlay()
            stopSelf()
            return
        }
        if (Guard.shouldLock(this)) showOverlay() else hideOverlay()
    }

    private fun showOverlay() {
        if (overlay != null) return
        if (!Settings.canDrawOverlays(this)) return
        val o = LockOverlay(this) { hideOverlay() }
        if (o.show()) overlay = o
    }

    private fun hideOverlay() {
        overlay?.hide()
        overlay = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (Prefs.enabled(this)) Watchdog.schedule(this, 5_000L)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        hideOverlay()
        if (Prefs.enabled(this)) Watchdog.schedule(this, 5_000L)
        super.onDestroy()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Jam Malam", NotificationManager.IMPORTANCE_LOW)
        ch.description = "Status perlindungan jam malam STMIK Tazkia"
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val range = "${Guard.fmt(Prefs.startMin(this))}–${Guard.fmt(Prefs.endMin(this))}"
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Jam Malam STMIK Tazkia aktif")
            .setContentText("HP akan terkunci pukul $range")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        private const val CHANNEL = "guard"
        private const val NOTIF_ID = 1

        fun start(c: Context) {
            val i = Intent(c, LockService::class.java)
            try {
                c.startForegroundService(i)
            } catch (_: Exception) {
                // Android 12+ bisa menolak start dari latar belakang; watchdog akan mencoba lagi.
            }
        }
    }
}
