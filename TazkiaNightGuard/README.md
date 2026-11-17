# Tazkia Night Guard 🌙

Aplikasi Android **Jam Malam Mahasiswa STMIK Tazkia**: HP otomatis terkunci pukul **00:00–05:00**
supaya mahasiswa tidak begadang. Layar kunci hanya bisa dibuka dengan **PIN darurat**.

## Fitur
- Layar kunci penuh (di atas semua aplikasi) dengan jam, tanggal, branding STMIK Tazkia, dan pesan pengingat.
- Jadwal bisa diubah (default 00:00–05:00, mendukung jadwal lewat tengah malam seperti 23:00–05:00).
- **PIN darurat** (minimal 6 angka): membuka HP selama N menit (default 30), lalu terkunci lagi kalau masih jam malam.
- PIN disimpan sebagai hash SHA-256 + salt, bukan teks asli. Salah 5x → diblokir 60 detik.
- Tombol **Panggilan Darurat 112** selalu tersedia (membuka dialer selama 3 menit, tercatat di riwayat).
- Panel admin dilindungi PIN: jadwal, durasi buka, ganti PIN, aktif/nonaktif, riwayat.
- Tetap jalan setelah HP restart (boot receiver) + watchdog setiap ±10 menit.
- Admin perangkat (opsional) agar aplikasi tidak bisa langsung di-uninstall.
- Mode **Uji layar kunci** untuk demo kapan saja.

## Build APK lewat GitHub Actions
1. Buat repo baru di GitHub (mis. `TazkiaNightGuard`), upload semua isi folder ini.
2. Buka tab **Actions** → workflow **Build APK** jalan otomatis (atau klik *Run workflow*).
3. Setelah selesai, unduh artifact **TazkiaNightGuard-apk** → isinya `app-debug.apk`.

## Cara pasang di HP
1. Install APK, buka **Tazkia Night Guard**.
2. Buat PIN darurat (sebaiknya dipegang wali/pengurus, bukan pemilik HP).
3. Di panel admin, berikan izin:
   - **Tampil di atas aplikasi lain** (wajib)
   - Notifikasi, **Bebas optimasi baterai**, **Admin perangkat**
   - Xiaomi/Redmi/POCO: **Autostart** dan **Izin lainnya → Tampilkan jendela pop-up saat berjalan di latar belakang**
4. Tekan **Uji layar kunci sekarang** untuk mencoba, keluar dengan PIN.
5. Tekan **Aktifkan perlindungan**.

## Batasan (jujur)
Tanpa mode *Device Owner* (butuh HP di-reset / MDM kampus), aplikasi biasa tidak bisa 100% anti-bobol:
- **Safe mode** Android menonaktifkan aplikasi pihak ketiga.
- Panel notifikasi/quick settings masih bisa ditarik (tapi aplikasi lain tetap tertutup layar kunci).
- Tanpa izin admin perangkat, aplikasi bisa di-uninstall di luar jam malam.

Untuk penerapan resmi di kampus, sebaiknya pemasangan dilakukan **dengan persetujuan mahasiswa**
dan dijelaskan aturannya (jam, siapa pemegang PIN, kapan PIN boleh dipakai).

