# Safari Event Framework

Mod server-side Fabric untuk **Minecraft 1.21.1 + Cobblemon 1.7.x** yang menyelenggarakan
event Safari ("Most Pokémon Caught") dengan arsitektur modular, sehingga mode event lain
(Most Shiny, Most Legendary, Most Water Type, dst.) bisa ditambahkan di masa depan tanpa
mengubah sistem inti.

Selain penghitungan tangkapan dasar, mod ini mendukung **pendaftaran pemain** dan **giliran
grup bergantian**: pemain daftar sebelum event dimulai, lalu dibagi ke grup berukuran tetap
yang main bergantian (bukan bersamaan), dengan enforcement otomatis lewat teleport.

**Status: selesai & sudah berhasil di-build.** Semua fitur inti terhubung end-to-end.
`CatchRateBooster` (bonus catch rate Safari Ball) sengaja tidak diimplementasikan.

## Requirement

- Minecraft 1.21.1
- Fabric Loader
- Fabric API
- Fabric Language Kotlin
- Cobblemon 1.7.x
- Java 21
- Server-side only (tidak ada component client)

## Quick Start

```bash
# Build
./gradlew build

# Jalankan server test (otomatis download Minecraft + set up folder run/)
./gradlew runServer
```

Saat pertama kali `runServer` dijalankan, server akan berhenti minta persetujuan EULA.
Buka `run/eula.txt`, ubah jadi `eula=true`, lalu jalankan `./gradlew runServer` lagi.

Config otomatis dibuat di `run/config/safari-event.json` (untuk server test) atau
`config/safari-event.json` (untuk server produksi). **Wajib isi `minX/maxX/minY/maxY/minZ/maxZ`
dan `queueX/Y/Z`** sesuai area di dunia Anda sebelum event bisa dipakai dengan benar —
cek koordinat lewat F3 di client, lalu `/safari reload` atau restart server.

### Alur Tes Fungsional

```
/safari register          # daftar (ulangi dari akun lain untuk tes multi-grup)
/safari start 60          # mulai event, 60 detik/giliran (durasi pendek untuk testing)
```

Yang perlu dicek:
- Broadcast "Safari Event Dimulai!" muncul ke semua pemain.
- BossBar/ActionBar langsung tampil (kalau diaktifkan di config).
- Tangkap Pokémon liar di dalam area Safari → `Caught: X` di HUD naik.
- `/safari leaderboard` dan `/safari stats [player]` bisa dicek kapan saja.
- **Validasi negatif** (harus TIDAK menambah skor): tangkap lewat command (`/pokegive` dst),
  atau menangkap di LUAR koordinat region.
- **Kalau ada ≥2 grup**: pemain grup lain yang masuk area saat bukan gilirannya harus otomatis
  diteleport ke `queuePoint`.
- Setelah giliran habis: broadcast leaderboard sementara (giliran berikutnya) atau hasil akhir
  top-3 (giliran terakhir). `/safari stop` untuk tes jalur dihentikan paksa oleh admin.

Log server ada di `run/logs/latest.log` kalau ada error yang perlu ditelusuri.

## Daftar Command

| Command | Siapa | Keterangan |
|---|---|---|
| `/safari register` | Pemain | Daftar untuk sesi event berikutnya (hanya selagi belum ada event berjalan) |
| `/safari unregister` | Pemain | Batalkan pendaftaran |
| `/safari start <durasi> [mode]` | Admin (level 2) | Mulai event. Durasi: `1800`, `30m`, atau `2h`. Mode default: `most_catch` |
| `/safari stop` | Admin (level 2) | Hentikan event paksa, langsung finalize seperti giliran terakhir selesai |
| `/safari reload` | Admin (level 2) | Muat ulang `config/safari-event.json` + region |
| `/safari leaderboard` | Siapa saja | Tampilkan top 10 leaderboard sesi berjalan/terakhir |
| `/safari stats [player]` | Siapa saja | Tampilkan skor diri sendiri atau pemain lain (berdasarkan nama) |

## Konfigurasi

Dibuat otomatis di `config/safari-event.json` saat mod pertama kali dijalankan:

- **`regions`**: list AABB (`minX/maxX/minY/maxY/minZ/maxZ`, `world`), plus `queueX/Y/Z`
  opsional (titik teleport untuk pemain yang bukan gilirannya). Mendukung multi-region.
- **`hud`**: `bossbar` (enabled, color, style, title) dan `actionbar` (enabled), plus
  `updateIntervalTicks`.
- **`leaderboard.topNPerBroadcast`**: jumlah entri teratas yang ditampilkan tiap broadcast
  (dipicu SELESAINYA GILIRAN GRUP, bukan interval waktu tetap).
- **`session`**: `maxGroupSize` (ukuran maksimal 1 grup, jumlah grup menyesuaikan jumlah
  pendaftar) dan `accessCheckIntervalTicks` (seberapa sering `SafariAccessGuard` memeriksa
  pemain di region).
- **`safariBall`**: reserved, belum aktif — menunggu `CatchRateBooster` yang sengaja belum
  diimplementasikan.

## Arsitektur

Setiap komponen punya tanggung jawab tunggal dan tidak saling mencampuri detail
implementasi komponen lain:

```
com.github.fadlanmuzaini1.safarievent
├── SafariEventMod.kt          Entry point, bootstrap semua manager
├── config/                    Load & simpan config/safari-event.json
│   ├── SafariEventConfig.kt
│   └── ConfigManager.kt
├── region/                    Deteksi region Safari + enforcement akses giliran
│   ├── SafariRegion.kt
│   ├── RegionManager.kt
│   └── SafariAccessGuard.kt     teleport keluar pemain yang bukan gilirannya
├── session/                   Pendaftaran pemain & pembagian grup
│   ├── RegisteredPlayer.kt
│   ├── RegistrationManager.kt
│   ├── Group.kt
│   └── GroupManager.kt
├── event/                     Lifecycle event, timer, giliran, dan strategi scoring
│   ├── TimerManager.kt          countdown generik berbasis tick server
│   ├── TurnManager.kt           rangkaian giliran grup berurutan
│   ├── EventManager.kt          orkestrator utama
│   └── scoring/                  interface EventScoringStrategy + implementasinya
│       ├── EventScoringStrategy.kt
│       ├── MostCatchStrategy.kt
│       └── EventScoringStrategyRegistry.kt
├── leaderboard/                Ranking in-memory, satu papan gabungan lintas grup
│   ├── LeaderboardEntry.kt
│   └── LeaderboardManager.kt
├── capture/                    Deteksi tangkapan valid via CobblemonEvents.POKEMON_CAPTURED
│   └── CatchTracker.kt
├── hud/                        BossBar (per-pemain) / ActionBar
│   └── HudManager.kt
└── command/                    Semua subcommand /safari
    └── CommandManager.kt
```

**Extensibility**: menambah mode event baru (Most Shiny, Most Legendary, dst.) = membuat
class baru yang mengimplementasikan `EventScoringStrategy` dan mendaftarkannya di
`EventScoringStrategyRegistry` — tanpa menyentuh `EventManager`, `CommandManager`, atau
class lain sama sekali.

## Keputusan Desain Penting

- **Deteksi tangkapan** memakai `CobblemonEvents.POKEMON_CAPTURED`, diverifikasi langsung
  dari source Cobblemon 1.7.x: event ini hanya di-*post* dari `EmptyPokeBallEntity.shakeBall()`
  dengan syarat `owner` adalah `ServerPlayer` asli — command, trade, breeding, dan hadiah NPC
  tidak pernah lewat jalur ini. `CatchTracker` menambah lapis verifikasi `Pokemon.belongsTo
  (player)` untuk memastikan Pokémon benar masuk party/PC (Cobblemon tidak otomatis melimpah
  ke PC saat party penuh).
- **`isRunning`** di `EventManager` disamakan dengan `TurnManager.isActive` — tidak ada flag
  boolean terpisah, menghindari dua sumber kebenaran yang bisa tidak sinkron.
- **`/safari stop`** memicu `TurnManager.stopNow()` yang secara sinkron memanggil
  `handleAllTurnsFinished()` — broadcast top-3 & reset registrasi pakai jalur logic yang SAMA
  baik event selesai wajar maupun dihentikan paksa, tidak ada duplikasi.
- **Leaderboard direset saat `/safari start`**, BUKAN saat event selesai — hasil tetap bisa
  dicek lewat `/safari leaderboard` sampai sesi berikutnya dimulai.
- **BossBar dibuat satu instance per pemain** (bukan dibagi bersama), karena "Caught: XX" itu
  personal sedangkan satu `ServerBossBar` vanilla cuma bisa menampilkan satu baris teks yang
  sama untuk semua viewer-nya.

## Build Environment (terverifikasi berhasil)

| Komponen | Versi |
|---|---|
| Fabric Loom | `1.17.14` |
| Gradle (wrapper) | `9.5.1` |
| Kotlin Gradle Plugin | `2.3.21` |
| Fabric Language Kotlin | `1.13.11+kotlin.2.3.21` |
| Java | `21` |

> **Kenapa `kotlin("jvm") version` di-set setinggi 2.3.21:** Cobblemon versi terbaru
> dikompilasi dengan Kotlin 2.2.0. Versi `kotlin-metadata-jvm` yang dipakai Fabric Loom untuk
> me-remap metadata Kotlin milik Cobblemon **diambil dari versi Kotlin Gradle Plugin yang
> dideklarasikan di project ini sendiri** (dikonfirmasi dari source `KotlinClasspathService
> .java` milik Fabric Loom) — bukan dari versi Gradle atau Loom itu sendiri. Kalau versi di
> sini lebih rendah dari Kotlin compiler yang dipakai Cobblemon, remap gagal dengan error
> `kotlinx-metadata-jvm cannot write metadata for future compiler versions`. Solusinya cukup
> menaikkan versi ini ke ≥ versi Kotlin yang dipakai Cobblemon — **bukan** mengejar versi
> Gradle/Loom terbaru (itu jalan buntu yang sempat dicoba dan malah butuh Java 25 untuk versi
> alpha Loom). Kalau Cobblemon naik versi Kotlin compiler lagi di masa depan, naikkan
> `kotlin("jvm") version` + `fabric_kotlin_version` bersamaan ke pasangan yang cocok.

Sebelum build pertama di server lain, tetap cek versi dependency di `gradle.properties`
(loader Fabric, Fabric API, dan version-id Cobblemon di Modrinth) agar sesuai target server.

## Lisensi

MIT — lihat [LICENSE](LICENSE).
