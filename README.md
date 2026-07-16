# Safari Event Framework

Mod server-side Fabric untuk Minecraft 1.21.1 + Cobblemon 1.7.x yang menyelenggarakan
event Safari ("Most Pokémon Caught") dengan arsitektur modular, sehingga mode event lain
(Most Shiny, Most Legendary, Most Water Type, dst.) bisa ditambahkan di masa depan tanpa
mengubah sistem inti.

**Status: fitur inti lengkap.** Seluruh manager sudah terhubung end-to-end. `CatchRateBooster`
(bonus catch rate Safari Ball) sengaja TIDAK diimplementasikan -- dikesampingkan atas
keputusan eksplisit selama pengembangan.

## Requirement

- Minecraft 1.21.1
- Fabric Loader
- Fabric API
- Fabric Language Kotlin
- Cobblemon 1.7.x
- Server-side only (tidak ada component client)

## Progres Implementasi

- [x] `ConfigManager` + `SafariEventConfig` — load/reload `config/safari-event.json`
- [x] `RegionManager` — deteksi player di dalam area Safari
- [x] `EventScoringStrategy` + `MostCatchStrategy` — strategi scoring, dibuat extensible
- [x] `RegisteredPlayer` + `RegistrationManager` — pendaftaran pemain sebelum event mulai
- [x] `Group` + `GroupManager` — pembagian pendaftar ke grup ukuran tetap
- [x] `TimerManager` — countdown generik berbasis tick server
- [x] `TurnManager` — rangkaian giliran grup berurutan
- [x] `SafariAccessGuard` — teleport keluar pemain yang bukan gilirannya
- [x] `LeaderboardManager` — leaderboard in-memory, satu papan gabungan lintas grup
- [x] `EventManager` — orkestrator: menyatukan semua manager di atas
- [x] `CatchTracker` — subscribe `CobblemonEvents.POKEMON_CAPTURED`
- [x] `HudManager` — BossBar (per-pemain) & ActionBar
- [x] `CommandManager` — semua subcommand `/safari`
- [ ] ~~`CatchRateBooster`~~ — **sengaja tidak diimplementasikan** (dikesampingkan atas keputusan eksplisit). Source `CatchRateModifier.kt`/`CatchRateModifiers.kt` Cobblemon 1.7.x sudah pernah dibaca kalau suatu saat ingin dilanjutkan.

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

## Verifikasi Terhadap Source Cobblemon

Beberapa keputusan desain di atas diverifikasi langsung dengan membaca source Cobblemon 1.7.x
(bukan tebakan/dokumentasi pihak ketiga):

- `CobblemonEvents.POKEMON_CAPTURED` hanya di-*post* dari satu tempat: `EmptyPokeBallEntity
  .shakeBall()`, yang mensyaratkan `owner` bola adalah `ServerPlayer` asli. Command, trade,
  breeding, dan hadiah NPC tidak pernah melewati jalur ini, sehingga tidak mungkin memicu
  event ini secara keliru.
- `party.add(pokemon)` bawaan Cobblemon **tidak otomatis melimpah ke PC** saat party penuh
  (bisa mengembalikan `false` tanpa fallback). Karena itu `CatchTracker` menambahkan lapis
  verifikasi tambahan lewat `Pokemon.belongsTo(player)` (method resmi Cobblemon) sebelum
  menghitung tangkapan, memastikan requirement "harus masuk ke Party atau PC" benar terpenuhi.

## Alur Registrasi & Giliran Grup

Selain event berjalan terus-menerus, mod ini mendukung mode "giliran bergantian":
pemain **mendaftar** dulu selagi event belum dimulai, lalu saat admin menjalankan
`/safari start <detikPerGiliran>`, pendaftaran ditutup dan seluruh pendaftar dibagi
menjadi beberapa grup berukuran maksimal tetap (`session.maxGroupSize` di config).
Grup-grup itu lalu main **berurutan** (bukan bersamaan) selama `<detikPerGiliran>` masing-masing;
`SafariAccessGuard` men-teleport keluar siapa pun yang bukan gilirannya tapi nekat masuk region.
Leaderboard tetap **satu papan gabungan** untuk seluruh grup, bukan papan terpisah per grup.

**Broadcast leaderboard sementara**: setiap kali satu giliran grup selesai, seluruh server
otomatis menerima broadcast leaderboard sementara (top-N sesuai `leaderboard.topNPerBroadcast`
di config). Ini DIPICU OLEH SELESAINYA GILIRAN, bukan interval waktu tetap -- keputusan desain
final, menggantikan rencana awal "broadcast tiap N detik" yang sempat ada di config tapi
tidak pernah benar-benar dipakai.

## Arsitektur

Setiap komponen punya tanggung jawab tunggal dan tidak saling mencampuri detail
implementasi komponen lain:

```
com.github.fadlanmuzaini1.safarievent
├── SafariEventMod.kt        Entry point, bootstrap semua manager
├── config/                  Load & simpan config/safari-event.json
├── region/                  Deteksi region Safari + enforcement akses giliran
│   ├── SafariRegion.kt
│   ├── RegionManager.kt
│   └── SafariAccessGuard.kt   teleport keluar pemain yang bukan gilirannya
├── session/                 Pendaftaran pemain & pembagian grup
│   ├── RegisteredPlayer.kt
│   ├── RegistrationManager.kt
│   ├── Group.kt
│   └── GroupManager.kt
├── event/                   Lifecycle event, timer, giliran, dan strategi scoring
│   ├── TimerManager.kt        countdown generik
│   ├── TurnManager.kt         rangkaian giliran grup berurutan
│   ├── EventManager.kt        orkestrator utama
│   └── scoring/                interface EventScoringStrategy + implementasinya
├── leaderboard/              Ranking in-memory, satu papan gabungan lintas grup
├── capture/                  Deteksi tangkapan valid via CobblemonEvents.POKEMON_CAPTURED
├── hud/                      BossBar (per-pemain) / ActionBar
└── command/                  Semua subcommand /safari
```

Penambahan mode event baru = membuat class baru yang mengimplementasikan
`EventScoringStrategy`, tanpa menyentuh `EventManager` atau class lain.

## Build

> **Catatan:** repo ini menyertakan `gradle/wrapper/gradle-wrapper.properties` (menunjuk Gradle 8.9)
> dan `.github/workflows/build.yml`, tapi **belum** menyertakan `gradlew`, `gradlew.bat`, dan
> `gradle-wrapper.jar` — file-file itu berupa script/binary yang idealnya digenerate langsung dari
> mesin Anda (saya tidak mengunduh biner dari luar domain yang di-whitelist di sandbox saya).
> Jalankan sekali di root project:
> ```bash
> gradle wrapper --gradle-version 8.9
> ```
> (butuh Gradle terpasang sekali saja secara lokal/via IDE), lalu commit hasilnya. Setelah itu
> CI di `.github/workflows/build.yml` akan langsung bisa jalan.

```bash
./gradlew build
```

> Sebelum build pertama, cek versi dependency di `gradle.properties`
> (loader Fabric, Fabric API, Fabric Language Kotlin, dan version-id Cobblemon di Modrinth)
> agar sesuai dengan target server Anda.

## Konfigurasi

Dibuat otomatis di `config/safari-event.json` saat mod pertama kali dijalankan, berisi:
regions (dengan titik antrian `queueX/Y/Z` opsional), pengaturan HUD (BossBar/ActionBar),
`leaderboard.topNPerBroadcast` (jumlah entri teratas yang ditampilkan tiap broadcast),
`session` (ukuran grup & interval enforcement), dan `safariBall` (**reserved, belum aktif**
-- menunggu `CatchRateBooster` yang sengaja belum diimplementasikan).

## Lisensi

MIT — lihat [LICENSE](LICENSE).
