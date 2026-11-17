package id.ac.tazkia.nightguard

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Layar kunci penuh yang tampil di atas semua aplikasi. */
class LockOverlay(private val ctx: Context, private val onDismiss: () -> Unit) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var info: TextView
    private lateinit var pin: EditText

    private val quotes = listOf(
        "Tidur cukup, kuliah fokus.",
        "Begadang hari ini, ngantuk di kelas besok.",
        "Istirahat juga bagian dari ikhtiar.",
        "Tugas bisa dilanjut setelah Subuh — lebih segar.",
        "Tubuhmu punya hak untuk istirahat."
    )

    private val clockTick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm:ss", Locale("id", "ID")).format(Date())
            date.text = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID")).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    fun show(): Boolean {
        val view = build()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        )
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        return try {
            wm.addView(view, lp)
            root = view
            handler.post(clockTick)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        root?.let {
            hideKeyboard()
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        root = null
    }

    private fun build(): View {
        val c = ctx
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(Ui.px(c, 28), Ui.px(c, 56), Ui.px(c, 28), Ui.px(c, 28))
        }

        col.addView(Ui.logo(c, 140).apply {
            // Muncul perlahan agar terasa lembut saat layar kunci tampil
            alpha = 0f; scaleX = 0.9f; scaleY = 0.9f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700).start()
        })
        col.addView(Ui.text(c, "STMIK TAZKIA", 28f, Ui.TEXT, bold = true, center = true).apply {
            letterSpacing = 0.12f
        }, Ui.topMargin(c, 16))
        col.addView(Ui.text(c, "Jam Istirahat Malam", 16f, Ui.MUTED, center = true))

        clock = Ui.text(c, "--:--:--", 58f, Ui.TEXT, center = true).apply {
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        }
        col.addView(clock, Ui.topMargin(c, 28))
        date = Ui.text(c, "", 14f, Ui.MUTED, center = true)
        col.addView(date)

        val status = if (Prefs.testLock(c)) {
            "MODE UJI — masukkan PIN untuk keluar"
        } else {
            "HP terkunci sampai pukul ${Guard.fmt(Prefs.endMin(c))}"
        }
        col.addView(Ui.text(c, status, 17f, Ui.TEXT, bold = true, center = true).apply {
            background = Ui.rounded(c, 0x22FFFFFF)
            setPadding(Ui.px(c, 16), Ui.px(c, 10), Ui.px(c, 16), Ui.px(c, 10))
        }, Ui.topMargin(c, 28))
        col.addView(Ui.text(c, "“${quotes.random()}”", 14f, Ui.MUTED, center = true).apply {
            setTypeface(typeface, Typeface.ITALIC)
        }, Ui.topMargin(c, 14))

        pin = Ui.pinField(c, "PIN darurat").apply {
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, _, _ -> tryUnlock(); true }
        }
        col.addView(pin, Ui.topMargin(c, 36))
        info = Ui.text(c, "", 13f, Ui.RED, center = true)
        col.addView(info, Ui.topMargin(c, 6))
        col.addView(Ui.button(c, "Buka dengan PIN darurat") { tryUnlock() }, Ui.topMargin(c, 8))
        col.addView(Ui.button(c, "📞  Panggilan Darurat 112", danger = true) { emergency() }, Ui.topMargin(c, 12))

        col.addView(Ui.text(c, "Program Jam Malam Mahasiswa STMIK Tazkia", 12f, Ui.MUTED, center = true),
            Ui.topMargin(c, 32))

        val scroll = ScrollView(c).apply {
            isFillViewport = true
            addView(col)
        }
        val frame = object : FrameLayout(c) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) return true // tombol Kembali tidak berfungsi
                return super.dispatchKeyEvent(event)
            }
        }
        frame.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Ui.BG2, Ui.BG, Ui.BG3)
        )
        frame.isClickable = true
        frame.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        return frame
    }

    private fun tryUnlock() {
        val now = System.currentTimeMillis()
        val until = Prefs.lockoutUntil(ctx)
        if (now < until) {
            info.text = "Terlalu banyak percobaan. Coba lagi dalam ${(until - now) / 1000 + 1} detik."
            return
        }
        val entered = pin.text.toString()
        if (entered.isEmpty()) return

        if (Prefs.checkPin(ctx, entered)) {
            Prefs.resetFails(ctx)
            if (Prefs.testLock(ctx)) {
                Prefs.setTestLock(ctx, false)
                Prefs.log(ctx, "Uji layar kunci selesai")
            } else {
                val m = Prefs.unlockMinutes(ctx)
                Prefs.setPassUntil(ctx, now + m * 60_000L)
                Prefs.log(ctx, "Dibuka dengan PIN darurat ($m menit)")
            }
            onDismiss()
        } else {
            pin.setText("")
            val lock = Prefs.registerFail(ctx)
            Prefs.log(ctx, "PIN salah dimasukkan")
            info.text = if (lock > now) "PIN salah 5 kali. Tunggu 60 detik." else "PIN salah."
        }
    }

    private fun emergency() {
        if (Prefs.testLock(ctx)) Prefs.setTestLock(ctx, false)
        val until = System.currentTimeMillis() + 3 * 60_000L
        if (Prefs.passUntil(ctx) < until) Prefs.setPassUntil(ctx, until)
        Prefs.log(ctx, "Tombol panggilan darurat dipakai (layar dibuka 3 menit)")
        onDismiss()
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }

    private fun hideKeyboard() {
        if (!::pin.isInitialized) return
        ctx.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(pin.windowToken, 0)
    }
}
