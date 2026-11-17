package id.ac.tazkia.nightguard

import android.content.Context
import java.util.Calendar

/** Logika jadwal jam malam. */
object Guard {
    fun minutesNow(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    /** Mendukung jadwal yang melewati tengah malam, mis. 23:00–05:00. */
    fun inWindow(now: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> now in start until end
        else -> now >= start || now < end
    }

    fun isNightTime(c: Context) = inWindow(minutesNow(), Prefs.startMin(c), Prefs.endMin(c))

    fun shouldLock(c: Context): Boolean {
        if (Prefs.testLock(c)) return true
        if (!Prefs.enabled(c)) return false
        return isNightTime(c) && System.currentTimeMillis() >= Prefs.passUntil(c)
    }

    fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)

    /** "05:00" / "5.00" / "5:00" -> menit sejak 00:00 */
    fun parse(s: String): Int? {
        val m = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*$""").find(s) ?: return null
        val h = m.groupValues[1].toInt()
        val mm = m.groupValues[2].toInt()
        if (h > 23 || mm > 59) return null
        return h * 60 + mm
    }
}
