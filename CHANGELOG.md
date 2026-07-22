# Changelog

Semua perubahan penting pada project ini dicatat di file ini. Format mengikuti
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) dan versi mengikuti
[Semantic Versioning](https://semver.org/). Changelog ini mulai dicatat dari rilis pertama
mod ini dan seterusnya.

## [0.7.0] - 2026-07-22

### Added
- Anggota grup yang sedang giliran sekarang menerima **20 Safari Ball setiap 20 detik**
  (BUKAN 1 buah tiap 1 detik) -- pemberian pertama terjadi tepat saat giliran di-start,
  lalu berulang tiap interval selama giliran masih berjalan.
- `item/SafariBallSupplier.kt` -- memakai `CobblemonItems.SAFARI_BALL` (konstanta resmi
  Cobblemon, diverifikasi dari source `CobblemonItems.kt`) dan `PlayerEntity.giveItemStack()`
  vanilla untuk memberi item ke inventory pemain.
- Semua Pokemon LIAR (bukan milik pemain) di dalam area Safari sekarang otomatis dihapus
  setiap SATU giliran grup selesai -- baik selesai wajar (timer habis) maupun dihentikan
  paksa admin (`/safari stop`).
- `spawn/WildPokemonCleaner.kt` -- memindai SEMUA region Safari terdaftar lewat
  `World.getEntitiesByClass(PokemonEntity::class.java, region.toBox()) { it.pokemon.isWild() }`,
  hanya menghapus Pokemon yang `pokemon.isWild()` (dikonfirmasi dari source Cobblemon:
  `storeCoordinates.get() == null`) -- tidak pernah menghapus Pokemon milik pemain (mis.
  yang sedang follow di luar Poke Ball).
- `SafariRegion.toBox()` -- konversi AABB region ke `Box` vanilla, dipakai
  `WildPokemonCleaner` untuk query entity. `RegionManager.allRegions()` -- salinan read-only
  semua region terdaftar.
- `SafariBallSupplyConfig` (`safariBallSupply` di `safari-event.json`): `enabled`, `amount`
  (default `20`), `intervalTicks` (default `400` = 20 detik).
- `WildCleanupConfig` (`wildCleanup.enabled` di `safari-event.json`, default `true`).

### Changed
- `EventManager` menerima 2 dependency baru: `SafariBallSupplier` dan `WildPokemonCleaner`.
  `startGroup()` memanggil `safariBallSupplier.grantImmediate()` tepat setelah teleport
  masuk; `handleGroupTurnFinished()` memanggil `wildPokemonCleaner.removeAllWildPokemon()`
  setelah teleport keluar, sebelum broadcast hasil giliran.
- `SafariBallSupplier.tick()` didelegasikan lewat `EventManager.tick()` (bukan pendaftaran
  `ServerTickEvents` terpisah) karena `EventManager` sudah memegang dependency ke situ untuk
  keperluan `grantImmediate()`.

## [0.6.0] - 2026-07-22

### Added
- Pemain sekarang OTOMATIS diteleport ke titik acak di dalam area Safari tepat saat
  gilirannya di-start (`/safari start <grup> <durasi>`), dan dikembalikan PERSIS ke lokasi
  asal mereka (sebelum teleport masuk) tepat saat giliran itu selesai -- baik selesai wajar
  (timer habis) maupun dihentikan paksa admin (`/safari stop`).
- `region/SafariTeleporter.kt` -- mengelola teleport masuk/keluar, termasuk menangani pemain
  yang sempat OFFLINE di momen krusial: login SELAGI gilirannya sendiri masih aktif dan
  belum pernah di-teleport masuk -> langsung diteleport masuk saat itu; login SETELAH
  gilirannya sudah selesai (offline tepat saat seharusnya dikembalikan) -> langsung
  dikembalikan ke lokasi semula saat login, mencegah pemain "terjebak" permanen di area
  Safari. Didaftarkan lewat `ServerPlayConnectionEvents.JOIN`.
- `region/RegionPositionFinder.kt` -- utility "cari titik acak di atas permukaan tanah dalam
  sebuah region", diekstrak supaya dipakai bersama oleh `ForceSpawnManager` (spawn) dan
  `SafariTeleporter` (teleport pemain) tanpa duplikasi logic.

### Changed
- **BossBar/ActionBar sekarang HANYA ditampilkan ke anggota grup yang sedang giliran**
  (`TurnManager.currentGroup()`), bukan lagi ke semua pemain online. Pemain lain yang
  online tapi bukan anggota grup aktif (belum mulai, grup lain, atau sudah selesai) tidak
  melihat bar ini sama sekali. Logic pruning `HudManager` diubah dari "berbasis
  online/offline" menjadi "berbasis keanggotaan grup aktif saat ini" (lebih umum, otomatis
  turut menangani kasus disconnect juga).
- `ForceSpawnManager` direfactor untuk memakai `RegionPositionFinder` (menghapus duplikasi
  logic pencarian titik acak + permukaan tanah yang sebelumnya inline di dalamnya).
- `EventManager` menerima dependency baru `SafariTeleporter`; `startGroup()` memanggil
  `teleportGroupIn()` tepat setelah grup dikunci, `handleGroupTurnFinished()` memanggil
  `teleportGroupOut()` sebelum broadcast hasil giliran.

## [0.5.0] - 2026-07-21

### Added
- Spawn Pokemon OTOMATIS & berkala (default tiap 1 detik) di titik acak dalam area Safari,
  aktif otomatis selama ada giliran grup yang berjalan (dimulai begitu `/safari start`
  dijalankan, berhenti otomatis begitu giliran selesai/dihentikan) -- tidak perlu command
  admin sama sekali.
- `spawn/PassiveSpawnScheduler.kt` -- memakai `ForceSpawnManager` yang sama persis dengan
  `/safari forcespawn` (tidak ada logic spawn yang diduplikasi), dipicu terus-menerus lewat
  timer selama `EventManager.isRunning`.
- `PassiveSpawnConfig` (`passiveSpawn` di `safari-event.json`): `enabled`, `intervalTicks`
  (default `20` = 1 detik), `maxConcurrentWildSpawns` (default `20`).

### Changed
- `ForceSpawnManager.ForceSpawnResult.Success` sekarang menyertakan `uuid` dan `worldKey`
  dari entity yang di-spawn, dibutuhkan `PassiveSpawnScheduler` untuk melacak apakah
  Pokemon hasil spawn otomatis masih hidup (lewat `World.getEntity(uuid)`).

### Fixed / Safety
- `maxConcurrentWildSpawns` ditambahkan sebagai pengaman struktural (bukan diminta
  eksplisit): tanpa batas ini, spawn 1 Pokemon/detik selama giliran 30 menit berpotensi
  menumpuk sampai 1800 entity liar dan membebani server secara serius. Begitu Pokemon
  yang ter-track ditangkap/despawn, slot spawn berikutnya otomatis terbuka lagi.

## [0.4.0] - 2026-07-21

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
