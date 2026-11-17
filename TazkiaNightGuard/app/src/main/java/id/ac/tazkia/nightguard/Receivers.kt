package id.ac.tazkia.nightguard

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarm berkala untuk menghidupkan lagi LockService bila dimatikan sistem. */
object Watchdog {
    private fun pending(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 0, Intent(c, WatchdogReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(c: Context, delayMs: Long = 10 * 60_000L) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pending(c))
    }

    fun cancel(c: Context) {
        c.getSystemService(AlarmManager::class.java)?.cancel(pending(c))
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        if (Prefs.enabled(c)) {
            LockService.start(c)
            Watchdog.schedule(c)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        if (Prefs.enabled(c)) {
            Prefs.log(c, "HP dinyalakan ulang — perlindungan aktif kembali")
            LockService.start(c)
            Watchdog.schedule(c)
        }
    }
}

/** Admin perangkat: membuat aplikasi tidak bisa langsung di-uninstall. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(c: Context, i: Intent): CharSequence =
        "Menonaktifkan admin akan melemahkan Jam Malam STMIK Tazkia. Pastikan ini disetujui pihak berwenang."

    override fun onDisabled(c: Context, i: Intent) {
        Prefs.log(c, "Admin perangkat dinonaktifkan")
    }
}
