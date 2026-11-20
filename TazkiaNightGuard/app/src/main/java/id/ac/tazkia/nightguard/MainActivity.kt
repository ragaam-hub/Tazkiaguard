package id.ac.tazkia.nightguard

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Panel admin: buat PIN, atur jadwal, izin, aktif/nonaktif, dan log. Semua dilindungi PIN. */
class MainActivity : Activity() {
    private lateinit var box: LinearLayout
    private var authorizedUntil = 0L

    private val authorized get() = System.currentTimeMillis() < authorizedUntil
    private val adminComponent by lazy { ComponentName(this, AdminReceiver::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG2
        window.navigationBarColor = Ui.BG
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.px(context, 20), Ui.px(context, 24), Ui.px(context, 20), Ui.px(context, 32))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            addView(box)
        })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(rc, p, r)
        render()
    }

    // ------------------------------------------------------------------
    private fun render() {
        box.removeAllViews()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(Ui.logo(context, 56))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.px(context, 14), 0, 0, 0)
                addView(Ui.text(context, "STMIK TAZKIA", 22f, Ui.TEXT, bold = true))
                addView(Ui.text(context, "Tazkia Night Guard — Jam Malam Mahasiswa", 13f, Ui.ACCENT))
            })
        }
        add(header, 0)
        when {
            !Prefs.hasPin(this) -> setupView()
            !authorized -> loginView()
            else -> panelView()
        }
    }

    private fun add(v: View, topDp: Int = 12) = box.addView(v, Ui.topMargin(this, topDp))
    private fun section(title: String) = add(Ui.text(this, title, 17f, Ui.ACCENT, bold = true), 28)
    private fun note(s: String, color: Int = Ui.MUTED) = add(Ui.text(this, s, 13f, color), 6)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun grantSession() { authorizedUntil = System.currentTimeMillis() + 10 * 60_000L }

    // ---------- Pertama kali: buat PIN ----------
    private fun setupView() {
        section("Buat PIN Darurat")
        note("PIN minimal 6 angka. Sebaiknya PIN dipegang pihak berwenang (wali, pengurus asrama, " +
            "atau bagian kemahasiswaan), bukan pemilik HP, agar jam malam efektif.")
        val p1 = Ui.pinField(this, "PIN baru")
        val p2 = Ui.pinField(this, "Ulangi PIN")
        add(p1); add(p2, 8)
        add(Ui.button(this, "Simpan PIN") {
            val a = p1.text.toString()
            val b = p2.text.toString()
            when {
                a.length < 6 || !a.all { it.isDigit() } -> toast("PIN minimal 6 angka")
                a != b -> toast("PIN tidak sama")
                else -> {
                    Prefs.setPin(this, a)
                    Prefs.log(this, "PIN darurat dibuat")
                    grantSession()
                    render()
                }
            }
        }, 16)
    }

    // ---------- Masuk panel ----------
    private fun loginView() {
        section("Panel Admin")
        note("Masukkan PIN darurat untuk membuka pengaturan.")
        val p = Ui.pinField(this, "PIN")
        add(p)
        add(Ui.button(this, "Masuk") {
            val now = System.currentTimeMillis()
            val until = Prefs.lockoutUntil(this)
            if (now < until) {
                toast("Terlalu banyak percobaan. Tunggu ${(until - now) / 1000 + 1} detik.")
                return@button
            }
            if (Prefs.checkPin(this, p.text.toString())) {
                Prefs.resetFails(this)
                grantSession()
                render()
            } else {
                Prefs.registerFail(this)
                Prefs.log(this, "PIN salah di panel admin")
                p.setText("")
                toast("PIN salah")
            }
        }, 12)
        statusCard()
    }

    // ---------- Panel utama ----------
    private fun panelView() {
        grantSession()
        statusCard()

        section("Perlindungan")
        if (Prefs.enabled(this)) {
            add(Ui.button(this, "Matikan perlindungan", danger = true) {
                Prefs.setEnabled(this, false)
                Watchdog.cancel(this)
                stopService(Intent(this, LockService::class.java))
                Prefs.log(this, "Perlindungan dimatikan dari panel admin")
                render()
            })
        } else {
            add(Ui.button(this, "Aktifkan perlindungan") {
                if (!Settings.canDrawOverlays(this)) {
                    toast("Berikan izin 'Tampil di atas aplikasi lain' dulu")
                    return@button
                }
                Prefs.setEnabled(this, true)
                LockService.start(this)
                Watchdog.schedule(this)
                Prefs.log(this, "Perlindungan diaktifkan")
                render()
            })
        }
        add(Ui.button(this, "Uji layar kunci sekarang", primary = false) {
            if (!Settings.canDrawOverlays(this)) {
                toast("Berikan izin 'Tampil di atas aplikasi lain' dulu")
                return@button
            }
            Prefs.setTestLock(this, true)
            Prefs.log(this, "Uji layar kunci dimulai")
            LockService.start(this)
        }, 8)
        note("Mode uji langsung menampilkan layar kunci. Keluar dengan PIN darurat.")

        permissionSection()
        scheduleSection()
        changePinSection()
        logSection()
    }

    private fun statusCard() {
        val on = Prefs.enabled(this)
        val night = Guard.isNightTime(this)
        val pass = Prefs.passUntil(this) - System.currentTimeMillis()
        val lines = buildString {
            append(if (on) "● AKTIF" else "○ NONAKTIF")
            append("\nJadwal kunci: ${Guard.fmt(Prefs.startMin(this@MainActivity))} – ${Guard.fmt(Prefs.endMin(this@MainActivity))}")
            append("\nSekarang: ${if (night) "di dalam jam kunci" else "di luar jam kunci"}")
            if (pass > 0) append("\nDibuka sementara: sisa ${pass / 60_000 + 1} menit")
        }
        add(Ui.text(this, lines, 15f, if (on) Ui.GREEN else Ui.TEXT).apply {
            background = Ui.rounded(context, Ui.CARD)
            setPadding(Ui.px(context, 16), Ui.px(context, 14), Ui.px(context, 16), Ui.px(context, 14))
            setLineSpacing(0f, 1.3f)
        }, 20)
    }

    private fun permissionSection() {
        section("Izin yang dibutuhkan")
        fun row(ok: Boolean, label: String, action: () -> Unit) {
            add(Ui.button(this, (if (ok) "✅  " else "⚠️  ") + label, primary = false) { action() }, 8)
        }

        row(Settings.canDrawOverlays(this), "Tampil di atas aplikasi lain (wajib)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            row(ok, "Notifikasi status") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        row(Emergency.hasUsageAccess(this), "Akses penggunaan (wajib, untuk tombol darurat)") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        val phoneOk = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        row(phoneOk, "Status panggilan (untuk tombol darurat)") {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), 2)
        }
        val pm = getSystemService(PowerManager::class.java)
        row(pm.isIgnoringBatteryOptimizations(packageName), "Bebas optimasi baterai") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) { openAppDetails() }
        }
        val dpm = getSystemService(DevicePolicyManager::class.java)
        row(dpm.isAdminActive(adminComponent), "Admin perangkat (cegah uninstall)") {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Agar aplikasi Jam Malam STMIK Tazkia tidak bisa di-uninstall tanpa menonaktifkan admin dulu."))
        }
        val brand = Build.MANUFACTURER.lowercase()
        if (brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
            add(Ui.button(this, "⚙️  Xiaomi: izinkan Autostart", primary = false) {
                try {
                    startActivity(Intent().setComponent(ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                } catch (_: Exception) { openAppDetails() }
            }, 8)
            add(Ui.button(this, "⚙️  Xiaomi: izin lainnya (pop-up latar belakang)", primary = false) {
                try {
                    startActivity(Intent("miui.intent.action.APP_PERM_EDITOR")
                        .setClassName("com.miui.securitycenter",
                            "com.miui.permcenter.permissions.PermissionsEditorActivity")
                        .putExtra("extra_pkgname", packageName))
                } catch (_: Exception) { openAppDetails() }
            }, 8)
        }
    }

    private fun openAppDetails() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun scheduleSection() {
        section("Jadwal")
        val start = Ui.field(this, "Mulai (HH:MM)", Guard.fmt(Prefs.startMin(this)))
        val end = Ui.field(this, "Selesai (HH:MM)", Guard.fmt(Prefs.endMin(this)))
        val dur = Ui.field(this, "Lama buka dengan PIN (menit)", Prefs.unlockMinutes(this).toString(), numeric = true)
        note("Jam mulai kunci"); add(start, 4)
        note("Jam selesai kunci"); add(end, 4)
        note("Lama HP terbuka setelah PIN darurat (menit)"); add(dur, 4)
        add(Ui.button(this, "Simpan jadwal") {
            val s = Guard.parse(start.text.toString())
            val e = Guard.parse(end.text.toString())
            val d = dur.text.toString().toIntOrNull()
            when {
                s == null || e == null -> toast("Format jam salah, contoh 00:00")
                s == e -> toast("Jam mulai dan selesai tidak boleh sama")
                d == null || d !in 1..300 -> toast("Durasi buka 1–300 menit")
                else -> {
                    Prefs.setSchedule(this, s, e)
                    Prefs.setUnlockMinutes(this, d)
                    Prefs.log(this, "Jadwal diubah: ${Guard.fmt(s)}–${Guard.fmt(e)}, buka $d menit")
                    toast("Jadwal disimpan")
                    render()
                }
            }
        }, 12)
    }

    private fun changePinSection() {
        section("Ganti PIN darurat")
        val p1 = Ui.pinField(this, "PIN baru")
        val p2 = Ui.pinField(this, "Ulangi PIN baru")
        add(p1, 8); add(p2, 8)
        add(Ui.button(this, "Ganti PIN", primary = false) {
            val a = p1.text.toString()
            when {
                a.length < 6 || !a.all { it.isDigit() } -> toast("PIN minimal 6 angka")
                a != p2.text.toString() -> toast("PIN tidak sama")
                else -> {
                    Prefs.setPin(this, a)
                    Prefs.log(this, "PIN darurat diganti")
                    toast("PIN diganti")
                    render()
                }
            }
        }, 12)
    }

    private fun logSection() {
        section("Riwayat")
        val logs = Prefs.logs(this).take(20)
        add(Ui.text(this, if (logs.isEmpty()) "Belum ada riwayat." else logs.joinToString("\n"), 13f, Ui.MUTED).apply {
            background = Ui.rounded(context, Ui.CARD)
            setPadding(Ui.px(context, 14), Ui.px(context, 12), Ui.px(context, 14), Ui.px(context, 12))
            setLineSpacing(0f, 1.25f)
        }, 8)
    }
}
