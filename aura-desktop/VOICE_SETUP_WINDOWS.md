# AURA Windows — pengaturan suara

## Aktivasi bebas tombol

Versi ini mencoba masuk ke mode siaga suara saat AURA dimulai. Ucapkan **“AURA”**; Anda juga dapat mengucapkan perintah dalam satu kalimat, misalnya **“AURA, bantu saya membuka dokumen.”** Setelah kata pemicu dikenali, tindak lanjut diterima selama 15 detik.

> Catatan privasi dan implementasi: pemicu “AURA” pada versi ini dikenali dari transkripsi cloud setelah segmentasi suara lokal. Audio yang terdeteksi sebagai ucapan dikirim untuk transkripsi ketika API key tersedia. Ini bukan detektor kata pemicu offline. Jika API key tidak dikonfigurasi, AURA tidak mengirim audio dan mode siaga suara tidak akan aktif.

## Menyiapkan API key

AURA tidak menyertakan API key di paket portable. Untuk memakai transkripsi dan fallback suara cloud, atur variabel lingkungan pengguna Windows bernama `AURA_SPEECH_API_KEY` menggunakan API key Anda sendiri. Tutup lalu buka kembali AURA setelah mengaturnya.

PowerShell (mengatur variabel untuk sesi terminal saat ini saja):

```powershell
$env:AURA_SPEECH_API_KEY = "API_KEY_MILIK_ANDA"
.AURA.exe
```

Untuk menyimpan variabel bagi sesi Windows berikutnya:

```powershell
[Environment]::SetEnvironmentVariable("AURA_SPEECH_API_KEY", "API_KEY_MILIK_ANDA", "User")
```

Setelah menyimpan variabel pengguna, tutup AURA sepenuhnya lalu jalankan ulang. Jangan membagikan API key atau menaruhnya di repositori.

## Bahasa Indonesia di Windows

- AURA meminta transkripsi dalam Bahasa Indonesia (`id`) secara default. `AURA_SPEECH_LANGUAGE` dapat digunakan untuk override bahasa.
- AURA terlebih dahulu menggunakan suara Windows SAPI yang kultur suaranya `id-ID`, jika terpasang.
- Jika suara `id-ID` tidak tersedia, AURA mencoba text-to-speech cloud dengan instruksi pelafalan Bahasa Indonesia, menggunakan API key yang sama.
- Fallback cloud membutuhkan koneksi internet dan dapat menimbulkan biaya API. Jika tidak ada suara `id-ID` maupun API key, AURA tidak seharusnya membaca teks Indonesia menggunakan suara Inggris.

## Jika mikrofon tidak aktif

Periksa izin mikrofon Windows, pilih perangkat input yang benar sebagai perangkat default, pastikan tidak ada aplikasi lain yang mengunci mikrofon, dan pastikan variabel API key sudah tersedia di proses AURA. Status AURA akan menunjukkan jika listener tidak dapat dimulai.
