# Safari Event Framework

Mod server-side Fabric untuk **Minecraft 1.21.1 + Cobblemon 1.7.x** yang menyelenggarakan
event Safari ("Most Pokémon Caught") dengan arsitektur modular, sehingga mode event lain
(Most Shiny, Most Legendary, Most Water Type, dst.) bisa ditambahkan di masa depan tanpa
mengubah sistem inti.

Selain penghitungan tangkapan dasar, mod ini mendukung **pendaftaran pemain dengan
penempatan grup langsung** dan **giliran per-grup yang di-start manual oleh admin** (bukan
otomatis berurutan): pemain daftar kapan saja lewat `/safari register` dan langsung masuk
grup (diberi tahu grup mana), lalu admin men-start giliran grup satu per satu lewat
`/safari start <grup> <durasi>`, dengan enforcement otomatis lewat teleport untuk pemain
yang bukan gilirannya. Admin juga bisa men-spawn paksa Pokemon acak "common" ke area Safari
lewat `/safari forcespawn`.

> **Catatan snapshot**: ini adalah rekonstruksi source code pada titik rilis **v0.4.0**.
> Fitur spawn Pokemon OTOMATIS berkala, teleport masuk/keluar area Safari, HUD khusus
> per-grup, Safari Ball berkala, dan pembersihan Pokemon liar antar giliran (ditambahkan di
> v0.5.0 ke atas) TIDAK ada di sini -- Pokemon liar hanya muncul lewat spawning alami
> Cobblemon atau lewat `/safari forcespawn` manual.

**Status pada snapshot ini: fitur inti + grup manual + forcespawn manual lengkap & sudah
berhasil di-build.** `CatchRateBooster` (bonus catch rate Safari Ball) sengaja tidak
diimplementasikan.

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
/safari register          # daftar (langsung dapat balasan "Kamu terdaftar di Grup 1")
/safari register          # ulangi dari akun lain -- kalau maxGroupSize sudah tercapai, masuk Grup 2
/safari start group1 60   # mulai giliran Grup 1 SAJA, 60 detik (durasi pendek untuk testing)
/safari forcespawn 3      # spawn paksa 3 Pokemon acak "common" di area Safari (bisa kapan saja)
```

Yang perlu dicek:
- `/safari register` langsung membalas grup mana pemain itu masuk.
- Broadcast "Giliran Grup 1 Dimulai!" beserta daftar nama pemain di grup itu muncul ke
  semua pemain.
- BossBar/ActionBar tampil ke SEMUA pemain online (kalau diaktifkan di config) -- pada
  snapshot ini belum dibatasi hanya ke anggota grup aktif.
- Tangkap Pokémon liar (baik dari spawning alami Cobblemon maupun hasil `/safari forcespawn`)
  di dalam area Safari → `Caught: X` di HUD naik.
- `/safari leaderboard` dan `/safari stats [player]` bisa dicek kapan saja.
- **Validasi negatif** (harus TIDAK menambah skor): tangkap lewat command (`/pokegive` dst),
  atau menangkap di LUAR koordinat region.
- **Kalau ada ≥2 grup**: pemain Grup 2 yang masuk area saat giliran Grup 1 masih berjalan
  harus otomatis diteleport ke `queuePoint`.
- Setelah giliran habis: broadcast leaderboard sementara. TIDAK ada auto-lanjut ke grup
  berikutnya — jalankan `/safari start group2 60` secara manual untuk melanjutkan.
- `/safari stop` untuk tes jalur giliran dihentikan paksa. `/safari reset` untuk tes
  mengakhiri seluruh sesi (broadcast hasil akhir top-3 + bersihkan semua pendaftaran/grup).

Log server ada di `run/logs/latest.log` kalau ada error yang perlu ditelusuri.

## Daftar Command

| Command | Siapa | Keterangan |
|---|---|---|
| `/safari register` | Pemain | Daftar; langsung ditempatkan ke grup (diberi tahu grup mana) |
| `/safari unregister` | Pemain | Batalkan pendaftaran (hanya kalau grupnya belum pernah di-start) |
| `/safari start <grup> <durasi> [mode]` | Admin (level 2) | Mulai giliran SATU grup, mis. `/safari start group1 60`. Durasi: `1800`, `30m`, `2h`. Label grup fleksibel (`group1`/`grup1`/`1`). Mode default: `most_catch`, terkunci untuk seluruh sesi setelah grup pertama dimulai |
| `/safari stop` | Admin (level 2) | Hentikan giliran grup yang SEDANG AKTIF sekarang juga |
| `/safari list` | Admin (level 2) | Lihat semua grup beserta anggotanya & status locked (sudah/belum di-start) |
| `/safari forcespawn [jumlah]` | Admin (level 2) | Spawn paksa `jumlah` (default 1, maks 50) Pokemon acak "common" di titik acak dalam area Safari |
| `/safari reset` | Admin (level 2) | Akhiri sesi: broadcast hasil akhir, lalu hapus semua pendaftaran/grup/leaderboard untuk sesi baru |
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
- **`session`**: `maxGroupSize` (ukuran maksimal 1 grup -- pemain langsung masuk grup ini
  saat /safari register, grup baru dibuka otomatis kalau grup sebelumnya sudah penuh atau
  sudah pernah di-start) dan `accessCheckIntervalTicks` (seberapa sering `SafariAccessGuard`
  memeriksa pemain di region).
- **`safariBall`**: reserved, belum aktif — menunggu `CatchRateBooster` yang sengaja belum
  diimplementasikan.
- **`forceSpawn`**: `level` (level Pokemon hasil `/safari forcespawn`), `excludedLabels`
  (label Cobblemon yang membuat sebuah spesies dikecualikan dari hasil acak "common" --
  default `legendary`, `mythical`, `ultra_beast`, `paradox`), `maxSpeciesRerollAttempts`
  (batas percobaan cari ulang spesies kalau kena filter, mencegah infinite loop).

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
├── session/                   Pendaftaran pemain SEKALIGUS penempatan langsung ke grup
│   ├── RegistrationManager.kt   register() langsung assign+lock grup, tidak ada GroupManager terpisah
│   └── Group.kt                  snapshot immutable satu grup, dibekukan saat giliran di-start
├── event/                     Lifecycle giliran (per grup, manual) & strategi scoring
│   ├── TimerManager.kt          countdown generik berbasis tick server
│   ├── TurnManager.kt           SATU giliran grup pada satu waktu, dimulai manual oleh admin
│   ├── EventManager.kt          orkestrator utama (startGroup, stop, resetSession)
│   └── scoring/                  interface EventScoringStrategy + implementasinya
│       ├── EventScoringStrategy.kt
│       ├── MostCatchStrategy.kt
│       └── EventScoringStrategyRegistry.kt
├── leaderboard/                Ranking in-memory, satu papan gabungan lintas grup
│   ├── LeaderboardEntry.kt
│   └── LeaderboardManager.kt
├── capture/                    Deteksi tangkapan valid via CobblemonEvents.POKEMON_CAPTURED
│   └── CatchTracker.kt
├── spawn/                      Force-spawn manual admin
│   └── ForceSpawnManager.kt
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
  boolean terpisah, menghindari dua sumber kebenaran yang bisa tidak sinkron. Berarti "ada
  SATU giliran grup yang sedang berjalan", bukan "seluruh event" (konsep itu sudah tidak ada
  sejak giliran di-start manual per grup).
- **Pemain masuk grup LANGSUNG saat `/safari register`** (grup 1 diisi sampai
  `maxGroupSize`, baru grup 2 dibuka, dst) — bukan lagi dibentuk sesudah pendaftaran
  ditutup. Grup dibekukan (`locked`) TEPAT SAAT admin men-start gilirannya lewat
  `/safari start <grup> <durasi>`; sebelum itu grup masih bisa kemasukan anggota baru. Sekali
  dibekukan, grup itu tidak akan pernah bisa di-start ulang (mencegah giliran ganda).
- **Tidak ada auto-lanjut ke grup berikutnya** — admin harus menjalankan
  `/safari start group2 <durasi>` secara eksplisit untuk tiap grup. `/safari stop` hanya
  menghentikan giliran yang SEDANG AKTIF (bukan seluruh sesi).
- **`/safari reset`** ditambahkan sebagai konsekuensi struktural dari hilangnya satu momen
  tunggal "mulai/selesai event": tanpa ini, leaderboard & pendaftaran akan menumpuk selamanya
  tanpa cara memulai sesi baru dari nol.
- **Leaderboard TIDAK direset otomatis di antara giliran grup** — tetap satu papan gabungan
  lintas grup sampai admin eksplisit `/safari reset`.
- **`/safari forcespawn` memakai pola resmi command `/pokespawn` bawaan Cobblemon**
  (`PokemonProperties.parse(...).createEntity(world)`, diverifikasi langsung dari source
  `SpawnPokemon.kt` milik Cobblemon), bukan API yang dikarang. Filter "common" bekerja di
  level `Species.labels` (SEBELUM Pokemon dibuat) terhadap `forceSpawn.excludedLabels` --
  lebih murah daripada membuat objek `Pokemon` penuh dulu baru dibuang kalau ternyata
  legendary/mythical. Pokemon yang muncul adalah Pokemon liar SUNGGUHAN, sehingga tangkapan
  atasnya lolos `CatchTracker` seperti tangkapan alami biasa.
- **BossBar dibuat satu instance per pemain** (bukan dibagi bersama), karena "Caught: XX" itu
  personal sedangkan satu `ServerBossBar` vanilla cuma bisa menampilkan satu baris teks yang
  sama untuk semua viewer-nya.
- **Entrypoint di `fabric.mod.json` WAJIB pakai `"adapter": "kotlin"`** karena `SafariEventMod`
  adalah Kotlin `object` (singleton) yang dikompilasi jadi class dengan constructor *private*.
  Default Java language adapter Fabric Loader mencoba `Constructor.newInstance()` langsung dan
  gagal dengan `IllegalAccessException`. Format entrypoint harus object (bukan string polos):
  `{"adapter": "kotlin", "value": "..."}` — berlaku untuk SETIAP entrypoint Kotlin `object`
  yang ditambahkan di masa depan, bukan cuma `SafariEventMod`.

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

## Changelog

Lihat [CHANGELOG.md](CHANGELOG.md) untuk riwayat rilis sampai dengan v0.4.0 (snapshot ini).

## Lisensi

MIT — lihat [LICENSE](LICENSE).
