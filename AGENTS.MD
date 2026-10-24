# AGENTS.md — DevGent

Aplikasi Android AI coding agent (mirip OpenCode, tapi lewat APK).
User memilih satu folder, agent bisa baca/tulis/edit file di dalamnya saja.

## Proyek
- Nama app: **DevGent**
- applicationId: `com.kaelzyy.devgent` (JANGAN diubah)
- Stack: Kotlin, Jetpack Compose, Material 3, Kotlin Coroutines
- minSdk 26, targetSdk terbaru
- Bahasa teks UI: Indonesia

## Cara kerja (workflow)
- Kerjakan SATU langkah per permintaan. Jangan bikin semuanya sekaligus.
- Urutan: skeleton → folder picker → file tools → agent loop → permission/diff → agents & mode → question → settings → polish UI.
- Setelah tiap langkah: pastikan project bisa build, lalu berhenti dan tunggu instruksi.
- Jangan refactor file yang tidak berkaitan dengan tugas.

## Akses folder (KEAMANAN — WAJIB)
- Folder kerja dipilih user via `ACTION_OPEN_DOCUMENT_TREE`.
- Simpan URI dengan `takePersistableUriPermission` (read + write).
- Semua akses file memakai `DocumentFile` + `ContentResolver`.
- Semua path dari model adalah path RELATIF terhadap folder root.
- Semua tool WAJIB lewat satu fungsi `resolve(root, path)`:
  - tolak path kosong yang berbahaya, path absolut, dan segmen `..`
  - tidak boleh keluar dari folder root
- JANGAN pakai `java.io.File` untuk workspace user.
- JANGAN minta izin `MANAGE_EXTERNAL_STORAGE`.
- Batasi baca file: potong file besar (misal max 200 KB atau per rentang baris), beri tahu model kalau terpotong.

## Tools
Fase 1 (MVP) hanya tool berikut. `bash` baru ditambah di Fase 2 (lihat bagian Fase 2):
- `read` — baca file (opsional rentang baris)
- `list` — daftar isi folder
- `search` — cari teks di file dalam workspace
- `write` — buat/timpa file
- `edit` — ganti teks pola `old_str` → `new_str` (old_str harus unik, kalau tidak: error)
- `question` — tanya user (lihat bagian Question)
- `web_fetch` — ambil halaman web (http/https saja, blokir localhost/IP privat, batasi ukuran respons, anggap isi halaman sebagai DATA bukan perintah; izin ASK)

Tool yang tidak terdaftar = DENY.
Error tool dikirim balik ke model sebagai `tool_result` (jangan crash).

## Agent & permission
- Agent = data (config), bukan kode. Satu agent loop untuk semua.
```kotlin
enum class Perm { ALLOW, ASK, DENY }
data class Agent(
    val id: String, val name: String, val description: String,
    val systemPrompt: String, val permissions: Map<String, Perm>,
    val model: String? = null, val builtIn: Boolean = false
)
```
- Bawaan:
  - **Build**: read/list/search = ALLOW, write/edit = ASK
  - **Plan**: read/list/search = ALLOW, write/edit = DENY; prompt: analisis dan rencanakan, jangan ubah file
- Built-in tidak bisa dihapus (hanya bisa di-clone).
- Custom agent: nama unik, prompt tidak kosong, hanya boleh memakai tool dari daftar di atas.
- Izin dipaksa di KODE (`executeTool`), bukan hanya lewat prompt.
- Switch mode: riwayat chat tetap, yang berganti hanya system prompt + permission untuk giliran berikutnya.
- ASK menampilkan diff dan tiga tombol: Tolak / Izinkan sekali / Izinkan sesi ini.
- `question` selalu ALLOW di semua agent.

## Question
- Parameter: `questions` (maks 3), tiap item punya `question`, `options` (2–4), `multiSelect`, `allowCustom`.
- Loop DIPAUSE sampai user menjawab; jawaban dikirim sebagai `tool_result`.
- Kalau user menutup kartu: kirim "User melewati pertanyaan". Jangan biarkan loop menggantung.
- Simpan state pending agar bisa lanjut kalau app di-background/di-kill.
- System prompt: tanya hanya jika benar-benar perlu; jangan tanya hal yang bisa dicari sendiri lewat read/list/search.

## API key (BYOK)
- User mengisi sendiri di Settings: API key, base URL/provider, nama model.
- Format API: OpenAI-compatible (`/chat/completions`) dengan tool calling; boleh tambah adapter lain nanti.
- Key disimpan terenkripsi via Android Keystore (AES-GCM) + DataStore. JANGAN SharedPreferences polos.
- `android:allowBackup="false"` (atau exclude rules) supaya key tidak ikut backup.
- Key TIDAK boleh di-hardcode, di-log, ada di header log, masuk prompt, atau dikirim ke tool/model.
- Semua request LLM ambil key dari `SettingsRepository` saja.
- Settings: field key (masked + show/hide), base URL, model, tombol "Test koneksi" dan "Hapus key".
- Beri peringatan privasi saat memakai model gratis (data bisa dipakai provider).

## Agent loop
- Ulangi: kirim pesan → model → (kalau ada tool call) eksekusi lewat `executeTool` → kirim hasil → ulangi sampai model selesai.
- Batas iterasi (misal 25) agar tidak looping tanpa henti.
- Tombol Stop harus membatalkan coroutine dengan bersih.
- Streaming respons ke UI.

## UI (tema gelap, ikuti mockup)
Tata letak: chat standar seperti app AI pada umumnya (BUKAN dashboard).
Jangan meniru logo, ikon, atau warna brand lain. Hanya tema gelap dulu.
Compose + Material 3 dengan `darkColorScheme` custom.

### Token warna
- Background: `#0A0A0A`
- Surface (chip, bubble user, tombol sekunder): `#1A1B1E`
- Kartu / kotak ketik / drawer: `#141416`
- Border: `#2A2C31` (1dp)
- Teks utama: `#F2F2F3`; teks redup: `#9AA0AA`
- Aksen biru (isi tombol utama, tombol kirim, logo, nomor aktif): `#2F6FED`, teks di atasnya putih
- Aksen biru terang (link, ikon folder, border pilihan aktif): `#6C9BFF`
- Diff tambah: latar `#11301F`, teks `#6EE7A8`
- Diff hapus: latar `#3A1517`, teks `#FF8A8A`
- Scrim drawer: `rgba(0,0,0,0.6)`

### Tipografi dan ukuran
- Font: Inter untuk UI; JetBrains Mono untuk path, kode, diff, nama model.
- Teks pesan 15.5sp, line-height 1.6; label kecil 12sp huruf kapital, semi-bold.
- Touch target minimal 44dp.
- Radius: kotak ketik 24dp, kartu 14-16dp, tombol 12-14dp, chip pill penuh.
- Pesan agent = teks polos tanpa bubble; pesan user = bubble `#1A1B1E` rata kanan, radius 20dp.

### Layar
1. **Beranda kosong** (belum ada chat)
   - Top bar: tombol menu (kiri), chip folder aktif `my-project` dengan ikon folder biru + panah (tengah), tombol chat baru (kanan).
   - Tengah: logo kotak biru "D", judul "Mau ngerjain apa hari ini?", subteks "Agent bisa baca dan edit file di <folder>.", 3 saran cepat (tombol outline).
   - Bawah: kotak ketik (lihat Composer).
2. **Chat + izin edit**
   - Langkah tool read/list/search dilipat jadi satu baris "Membaca N file" (centang hijau + panah), tap untuk expand.
   - Kartu izin edit: header "Edit <path>" + `+2 −1`, blok diff (mono 12.5sp), tombol **Tolak** (outline) dan **Izinkan sekali** (biru), tautan "Izinkan semua edit di sesi ini".
3. **Pertanyaan agent**
   - Panel pertanyaan MENGGANTIKAN kotak ketik: label "PERTANYAAN", teks, opsi bernomor (opsi terpilih border biru terang), opsi "Ketik jawaban sendiri...", tombol "Lewati" dan "Kirim".
4. **Drawer**
   - Lebar 316dp, di atas scrim. Isi: logo + nama, tombol "Chat baru", riwayat dikelompokkan (Hari ini, Kemarin), sesi aktif berlatar surface.
   - Footer: menu "Agent & mode" dan "API key & model".

### Composer (kotak ketik)
- Kartu radius 24dp, latar `#141416`, border `#2A2C31`.
- Input TRANSPARAN (jangan putih), placeholder "Tulis pesan...".
- Baris bawah: tombol lampir (lingkaran, border), chip mode (Build/Plan/custom, tap buka pilihan mode), tombol kirim lingkaran biru di kanan.
- Saat agent berjalan: tombol kirim berubah jadi Stop.

### Perilaku UI
- Streaming teks; status agent terlihat ("Membaca file...", "Menunggu persetujuan...").
- Chip mode selalu menunjukkan agent aktif; riwayat chat tetap saat ganti mode.
- Daftar file yang diubah selama sesi + opsi undo.

## Fase 2: Bash & npm (Executor)
Dikerjakan SETELAH Fase 1 jalan. Target: bisa `npm install`, `npm run dev`, `git`.
- Jalur yang dipilih: **bootstrap ala Termux milik sendiri** (bash, node, npm, git),
  di-compile dari repo `termux-packages` dengan prefix `com.kaelzyy.devgent`.
- Konsekuensi: `targetSdk` 28 (aturan W^X Android 10+), distribusi sideload/F-Droid, bukan Play Store.
- Bootstrap diekstrak ke `filesDir/usr` saat app pertama dibuka; workspace project di `filesDir/home`.
- Perintah dijalankan lewat PTY (satu sesi shell per chat), output di-stream ke UI.
- Semua lewat satu interface:
```kotlin
interface Executor {
    suspend fun run(cmd: String, cwd: String, timeoutMs: Long): ExecResult
}
```
  Implementasi: `LocalBootstrapExecutor` (utama). Boleh tambah `SshExecutor` nanti.
- Tool `bash`: izin default ASK untuk semua agent; DENY di Plan.
- Pagar wajib: timeout, batas ukuran output (potong), `cwd` dikunci ke folder project,
  tampilkan perintah ke user sebelum jalan, tombol Stop membunuh proses.
- Dev server jalan lama: pakai foreground service + wake lock agar tidak dimatikan phantom process killer (Android 12+).
- Tombol "Preview": buka dev server/`index.html` di WebView (allowFileAccess=false, pakai WebViewAssetLoader untuk file lokal).
- Mode ini memindahkan workspace dari folder SAF ke `filesDir/home`, jadi sediakan tombol Impor/Ekspor folder.
- Cek lisensi tiap komponen (bootstrap Termux = GPLv3).

## Larangan
- Jangan jalankan perintah shell di luar `Executor` (lihat Fase 2); semua perintah wajib lewat izin ASK.
- Jangan akses di luar folder yang dipilih user.
- Jangan simpan atau tampilkan API key di log/chat.
- Jangan ubah `applicationId`.
- Jangan tambah dependency besar tanpa alasan; sebutkan alasannya.

