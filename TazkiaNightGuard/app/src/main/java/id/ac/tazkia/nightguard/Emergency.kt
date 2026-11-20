package id.ac.tazkia.nightguard

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.telecom.TelecomManager

/**
 * Mode panggilan darurat.
 * Layar kunci hanya dibuka selama pengguna berada di aplikasi telepon atau sedang menelepon.
 * Begitu keluar dari aplikasi telepon / panggilan selesai, HP langsung terkunci lagi.
 */
object Emergency {
    private const val MAX_MS = 15 * 60_000L        // batas mutlak mode darurat
    private const val LAUNCH_GRACE_MS = 4_000L      // waktu untuk aplikasi telepon terbuka
    private const val NO_USAGE_GRACE_MS = 20_000L   // jika izin "Akses penggunaan" belum diberikan

    private var lastForeground: String? = null

    fun start(c: Context) {
        lastForeground = null
        Prefs.setEmergencyCall(c, false)
        Prefs.setEmergencyStart(c, System.currentTimeMillis())
        Prefs.log(c, "Tombol panggilan darurat ditekan")
    }

    fun active(c: Context) = Prefs.emergencyStart(c) > 0L

    fun end(c: Context, reason: String) {
        if (!active(c)) return
        Prefs.endEmergency(c)
        lastForeground = null
        Prefs.log(c, "Mode darurat selesai ($reason) — HP terkunci lagi")
    }

    /** true jika layar kunci boleh tetap terbuka karena mode darurat. */
    fun allowsOpen(c: Context): Boolean {
        val start = Prefs.emergencyStart(c)
        if (start == 0L) return false
        val age = System.currentTimeMillis() - start

        if (age > MAX_MS) { end(c, "batas 15 menit"); return false }

        if (inCall(c)) {
            if (!Prefs.emergencyCall(c)) {
                Prefs.setEmergencyCall(c, true)
                Prefs.log(c, "Panggilan darurat berlangsung")
            }
            return true
        }
        if (Prefs.emergencyCall(c)) { end(c, "panggilan selesai"); return false }

        if (age < LAUNCH_GRACE_MS) return true

        if (hasUsageAccess(c)) {
            val fg = foreground(c, start)
            return if (fg != null && fg in phonePackages(c)) {
                true
            } else {
                end(c, "keluar dari aplikasi telepon")
                false
            }
        }

        if (age < NO_USAGE_GRACE_MS) return true
        end(c, "tidak ada panggilan")
        return false
    }

    fun inCall(c: Context): Boolean {
        if (c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            c.getSystemService(TelecomManager::class.java)?.isInCall == true
        } catch (_: SecurityException) {
            false
        }
    }

    fun hasUsageAccess(c: Context): Boolean {
        val aom = c.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        } else {
            @Suppress("DEPRECATION")
            aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Aplikasi yang paling terakhir tampil di depan sejak mode darurat dimulai. */
    private fun foreground(c: Context, since: Long): String? {
        val usm = c.getSystemService(UsageStatsManager::class.java) ?: return lastForeground
        val events = usm.queryEvents(since - 2_000L, System.currentTimeMillis())
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) lastForeground = e.packageName
        }
        return lastForeground
    }

    private fun phonePackages(c: Context): Set<String> {
        val set = mutableSetOf(
            "com.android.phone", "com.android.server.telecom", "com.android.dialer",
            "com.google.android.dialer", "com.android.incallui", "com.android.contacts",
            "com.samsung.android.dialer", "com.samsung.android.incallui",
            "com.miui.phone", "com.oplus.dialer", "com.coloros.phone", "com.vivo.dialer"
        )
        try {
            c.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { set += it }
        } catch (_: Exception) {}
        return set
    }
}
