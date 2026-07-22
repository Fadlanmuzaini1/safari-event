# Changelog

Semua perubahan penting pada project ini dicatat di file ini. Format mengikuti
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) dan versi mengikuti
[Semantic Versioning](https://semver.org/). Changelog ini mulai dicatat dari rilis pertama
mod ini dan seterusnya.

> **Catatan snapshot**: ini adalah rekonstruksi source code pada titik rilis **v0.4.0**
> (diminta ulang setelah rilis v0.5.0 ke atas sudah dikembangkan), disusun manual berdasarkan
> catatan detail di CHANGELOG proyek utama. Entri v0.5.0 ke atas (spawn otomatis berkala,
> teleport masuk/keluar, HUD per-grup, Safari Ball berkala, pembersihan Pokemon liar) TIDAK
> ada di kode ini.

## [0.4.0] - 2026-07-22

### Added
- `/safari forcespawn [jumlah]` (admin) -- spawn paksa `jumlah` (default 1, maks 50) Pokemon
  liar acak "common" (bukan legendary/mythical/ultra beast/paradox) di titik acak dalam
  salah satu region Safari yang terdaftar. Admin tidak perlu berada di lokasi tersebut.
  Pokemon yang muncul adalah Pokemon liar sungguhan -- tangkapan atasnya lolos `CatchTracker`
  seperti tangkapan alami biasa.
- `spawn/ForceSpawnManager.kt` -- mengimplementasikan spawn memakai pola resmi command
  `/pokespawn` bawaan Cobblemon (`PokemonProperties.parse(...).createEntity(world)`),
  diverifikasi langsung dari source `SpawnPokemon.kt` Cobblemon 1.7.x.
- `ForceSpawnConfig` (`forceSpawn` di `safari-event.json`): `level` (level Pokemon hasil
  spawn), `excludedLabels` (label Cobblemon yang mengecualikan spesies dari hasil acak,
  default `legendary`/`mythical`/`ultra_beast`/`paradox`), `maxSpeciesRerollAttempts`
  (batas percobaan cari ulang spesies, mencegah infinite loop kalau config terlalu ketat).
- `SafariRegion.randomXZ()` dan `RegionManager.randomRegion()` untuk mendukung pemilihan
  titik & region acak. `SafariRegion.minY`/`maxY` diubah dari `private` menjadi publik agar
  bisa dipakai meng-clamp hasil pencarian permukaan tanah (heightmap).

## [0.3.0] - 2026-07-20

### Added
- `/safari list` (admin) -- menampilkan seluruh grup beserta anggotanya dan status
  locked/belum locked (apakah giliran grup itu sudah pernah di-start).
- `RegistrationManager.GroupSummary` dan `listGroups()` untuk mendukung `/safari list`.

## [0.2.0] - 2026-07-19

### Added
- `/safari start <grup> <durasi> [mode]` -- admin men-start giliran SATU grup secara manual
  (mis. `/safari start group1 60`), menggantikan mekanisme lama yang menjalankan semua grup
  otomatis berurutan dari satu command `/safari start <durasi>`. Label grup fleksibel:
  `group1`, `grup1`, atau `1` semua diterima (parsing hanya mengambil digit dari string).
- Broadcast daftar nama pemain yang tergabung dalam grup, dikirim ke seluruh server setiap
  kali sebuah giliran grup di-start oleh admin.
- Player langsung diberi tahu grup mereka saat `/safari register` (mis. "Kamu terdaftar di
  Grup 1"), termasuk saat mencoba register ulang (`AlreadyRegistered` kini juga menyertakan
  info grup).
- `/safari reset` (admin) -- mengakhiri sesi secara eksplisit: broadcast hasil akhir top-N,
  lalu membersihkan seluruh pendaftaran/grup/leaderboard untuk sesi baru. Ditambahkan sebagai
  konsekuensi struktural: setelah `/safari start` tidak lagi jadi satu titik tunggal
  "mulai/selesai event", perlu ada cara eksplisit untuk mereset state.

### Changed
- **Penempatan grup kini terjadi LANGSUNG saat `/safari register`** (grup 1 diisi sampai
  `session.maxGroupSize`, baru grup 2 dibuka, dst) -- bukan lagi dibentuk sesudah pendaftaran
  ditutup secara massal.
- Pendaftaran (`/safari register`) **tidak pernah otomatis tertutup**. Sebelumnya tertutup
  begitu admin menjalankan `/safari start`; sekarang grup individual yang "dibekukan"
  (`locked`) tepat saat gilirannya di-start, sementara grup lain tetap bisa menerima
  pendaftar baru.
- `TurnManager` disederhanakan total: dari mengelola rangkaian banyak grup dengan
  auto-lanjut (`begin()`, `onTurnAdvanced`, `onAllTurnsFinished`) menjadi mengelola SATU
  giliran grup pada satu waktu (`startTurn()`), tanpa auto-lanjut ke grup berikutnya.
- `EventManager.start()` (multi-grup otomatis) diganti `EventManager.startGroup()` (satu
  grup per panggilan). `isRunning` sekarang berarti "ada satu giliran grup yang sedang
  berjalan", bukan lagi "seluruh event sedang berjalan" (konsep itu sudah tidak ada).
- `/safari stop` sekarang hanya menghentikan giliran grup yang SEDANG AKTIF, bukan
  menghentikan "seluruh event" (karena konsep itu sudah tidak ada -- lihat `/safari reset`
  untuk mengakhiri sesi sepenuhnya).
- Mode scoring strategy (`most_catch`, dst.) dikunci untuk seluruh sesi setelah grup
  pertama di-start; permintaan mode berbeda di tengah sesi diabaikan (dicatat sebagai
  warning di log, bukan error ke admin).
- `RegistrationManager` sekarang menerima `ConfigManager` di constructor (untuk membaca
  `session.maxGroupSize` langsung saat setiap `register()` dipanggil).

### Removed
- `session/GroupManager.kt` -- fungsinya (pembentukan grup dari snapshot pendaftar) pindah
  seluruhnya ke `RegistrationManager`, karena penempatan grup sekarang terjadi incremental
  saat registrasi, bukan sekali di akhir.
- `session/RegisteredPlayer.kt` -- tidak diperlukan lagi; grup menyimpan `UUID -> username`
  langsung tanpa perantara data class terpisah.
- `RegistrationManager.isOpen` / `RegistrationClosed` sebagai hasil dari `register()` /
  `unregister()` -- dihapus karena pendaftaran tidak lagi punya konsep "ditutup" secara
  keseluruhan (lihat locking per-grup di atas).
- `EventManager.StartResult`, `handleTurnAdvanced()`, `handleAllTurnsFinished()` -- diganti
  `StartGroupResult` dan `handleGroupTurnFinished()` yang scope-nya per satu giliran grup.

## [0.1.0] - 2026-07-19

Rilis pertama: framework event Safari lengkap untuk Fabric 1.21.1 + Cobblemon 1.7.x,
arsitektur modular, dengan giliran grup berurutan OTOMATIS (digantikan model manual per-grup
di v0.2.0).

### Added
- `ConfigManager` + `SafariEventConfig` -- load/reload `config/safari-event.json` (region,
  HUD, leaderboard, session, safariBall).
- `RegionManager` + `SafariRegion` -- deteksi AABB region Safari berbasis koordinat,
  mendukung multi-region, dengan titik antrian (`queueX/Y/Z`) opsional per region.
- `SafariAccessGuard` -- teleport otomatis keluar pemain yang berada di region saat bukan
  gilirannya.
- `RegistrationManager` + `GroupManager` -- pendaftaran pemain (`/safari register`/
  `unregister`) dan pembagian ke grup berukuran tetap (`session.maxGroupSize`) setelah
  pendaftaran ditutup.
- `TimerManager` -- countdown generik berbasis tick server.
- `TurnManager` -- menjalankan rangkaian giliran SEMUA grup secara otomatis berurutan
  (auto-lanjut ke grup berikutnya begitu giliran sebelumnya habis).
- `EventScoringStrategy` + `MostCatchStrategy` + `EventScoringStrategyRegistry` -- sistem
  strategi scoring yang extensible untuk mode event di masa depan (Most Shiny, Most
  Legendary, Most Water Type, dll.) tanpa mengubah `EventManager`.
- `LeaderboardManager` -- ranking in-memory, satu papan gabungan lintas grup, direset saat
  `/safari start`, broadcast top-N (`leaderboard.topNPerBroadcast`) setiap satu giliran grup
  selesai (bukan berdasarkan interval waktu tetap).
- `CatchTracker` -- subscribe `CobblemonEvents.POKEMON_CAPTURED`, diverifikasi langsung dari
  source Cobblemon 1.7.x bahwa event ini hanya fire dari lemparan Poké Ball asli pemain
  (bukan command/trade/breeding/hadiah NPC), plus verifikasi tambahan `Pokemon.belongsTo
  (player)` untuk memastikan tangkapan benar masuk party/PC.
- `HudManager` -- BossBar (satu instance per pemain) & ActionBar, update berkala tanpa spam
  chat.
- `CommandManager` -- `/safari register|unregister|start|stop|reload|leaderboard|stats`.
- `EventManager` -- orkestrator utama yang menyatukan seluruh manager di atas.

### Fixed
- Entrypoint `fabric.mod.json` diubah ke format `{"adapter": "kotlin", "value": "..."}` --
  `SafariEventMod` adalah Kotlin `object` (constructor *private*), default Java language
  adapter Fabric Loader gagal dengan `IllegalAccessException` tanpa adapter Kotlin.
- Versi `kotlin("jvm")` di `build.gradle.kts` dinaikkan ke `2.3.21` -- versi `kotlin-metadata
  -jvm` yang dipakai Fabric Loom untuk me-remap Cobblemon mengikuti versi Kotlin Gradle
  Plugin yang dideklarasikan project, bukan versi Gradle/Loom itu sendiri; versi lama
  menyebabkan error `kotlinx-metadata-jvm cannot write metadata for future compiler versions`
  saat build.
