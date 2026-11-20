package id.ac.tazkia.nightguard

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Penyimpanan pengaturan. PIN tidak disimpan mentah: hanya salt + hash SHA-256 berulang. */
object Prefs {
    private const val MAX_LOG = 60
    private const val MAX_FAIL = 5
    private const val LOCKOUT_MS = 60_000L

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences("night_guard", Context.MODE_PRIVATE)

    // ---------- PIN ----------
    fun hasPin(c: Context) = sp(c).contains("pin_hash")

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        sp(c).edit().putString("pin_salt", salt).putString("pin_hash", hash(salt, pin)).apply()
    }

    fun checkPin(c: Context, pin: String): Boolean {
        val salt = sp(c).getString("pin_salt", null) ?: return false
        val stored = sp(c).getString("pin_hash", null) ?: return false
        return MessageDigest.isEqual(stored.toByteArray(), hash(salt, pin).toByteArray())
    }

    private fun hash(salt: String, pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var d = (salt + pin).toByteArray()
        repeat(10_000) { d = md.digest(d) }
        return d.toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    // ---------- Percobaan PIN salah ----------
    fun lockoutUntil(c: Context) = sp(c).getLong("lockout_until", 0L)

    /** Catat PIN salah. Mengembalikan waktu akhir blokir (0 jika belum diblokir). */
    fun registerFail(c: Context): Long {
        val count = sp(c).getInt("fail_count", 0) + 1
        return if (count >= MAX_FAIL) {
            val until = System.currentTimeMillis() + LOCKOUT_MS
            sp(c).edit().putInt("fail_count", 0).putLong("lockout_until", until).apply()
            until
        } else {
            sp(c).edit().putInt("fail_count", count).apply()
            0L
        }
    }

    fun resetFails(c: Context) = sp(c).edit().putInt("fail_count", 0).apply()

    // ---------- Jadwal & status ----------
    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun startMin(c: Context) = sp(c).getInt("start_min", 0)          // 00:00
    fun endMin(c: Context) = sp(c).getInt("end_min", 5 * 60)         // 05:00
    fun setSchedule(c: Context, start: Int, end: Int) =
        sp(c).edit().putInt("start_min", start).putInt("end_min", end).apply()

    fun unlockMinutes(c: Context) = sp(c).getInt("unlock_min", 30)
    fun setUnlockMinutes(c: Context, m: Int) = sp(c).edit().putInt("unlock_min", m).apply()

    fun passUntil(c: Context) = sp(c).getLong("pass_until", 0L)
    fun setPassUntil(c: Context, t: Long) = sp(c).edit().putLong("pass_until", t).commit()

    fun testLock(c: Context) = sp(c).getBoolean("test_lock", false)
    fun setTestLock(c: Context, v: Boolean) = sp(c).edit().putBoolean("test_lock", v).commit()

    // ---------- Mode darurat ----------
    fun emergencyStart(c: Context) = sp(c).getLong("em_start", 0L)
    fun setEmergencyStart(c: Context, t: Long) = sp(c).edit().putLong("em_start", t).commit()
    fun emergencyCall(c: Context) = sp(c).getBoolean("em_call", false)
    fun setEmergencyCall(c: Context, v: Boolean) = sp(c).edit().putBoolean("em_call", v).commit()
    fun endEmergency(c: Context) = sp(c).edit().putLong("em_start", 0L).putBoolean("em_call", false).commit()

    // ---------- Log ----------
    fun log(c: Context, msg: String) {
        val time = SimpleDateFormat("dd/MM HH:mm", Locale("id", "ID")).format(Date())
        val lines = (listOf("$time  $msg") + logs(c)).take(MAX_LOG)
        sp(c).edit().putString("log", lines.joinToString("\n")).apply()
    }

    fun logs(c: Context): List<String> =
        sp(c).getString("log", "")!!.split("\n").filter { it.isNotBlank() }
}
