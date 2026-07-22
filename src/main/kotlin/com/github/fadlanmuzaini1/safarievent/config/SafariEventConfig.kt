package com.github.fadlanmuzaini1.safarievent.config

/**
 * Satu region Safari berupa Axis-Aligned Bounding Box di dalam satu dunia tertentu.
 *
 * Sengaja disimpan sebagai LIST sejak awal (bukan objek tunggal) walaupun requirement awal
 * hanya butuh satu region, supaya penambahan multi-region nanti hanya berarti menambah entri
 * baru di JSON -- tidak perlu mengubah format config atau kode RegionManager sama sekali.
 */
data class SafariRegionData(
    val id: String = "default",
    val world: String = "minecraft:overworld",
    val minX: Int = 0,
    val maxX: Int = 0,
    val minY: Int = -64,
    val maxY: Int = 320,
    val minZ: Int = 0,
    val maxZ: Int = 0,
    // Titik tempat pemain dari grup yang BUKAN sedang giliran akan diteleport keluar jika
    // nekat masuk region ini saat event RUNNING. Nullable karena admin mungkin belum
    // mengaturnya -- SafariAccessGuard wajib menangani null ini secara fail-safe (log
    // warning + lewati enforcement untuk region tsb), bukan crash atau menebak koordinat.
    val queueX: Double? = null,
    val queueY: Double? = null,
    val queueZ: Double? = null
)

/**
 * Konfigurasi BossBar. `color` dan `style` disimpan sebagai String di config (bukan enum)
 * supaya ramah ditulis manual di JSON; validasi ke enum BossBar.Color / BossBar.Style
 * dilakukan di HudManager saat dipakai, bukan di sini -- ConfigManager tidak boleh tahu
 * detail API BossBar vanilla, itu tanggung jawab HudManager.
 */
data class BossBarConfig(
    val enabled: Boolean = true,
    val color: String = "BLUE",
    val style: String = "PROGRESS",
    val title: String = "Safari Event"
)

data class ActionBarConfig(
    val enabled: Boolean = true
)

data class HudConfig(
    val bossbar: BossBarConfig = BossBarConfig(),
    val actionbar: ActionBarConfig = ActionBarConfig(),
    // Interval update HUD dalam tick server (20 tick = 1 detik).
    val updateIntervalTicks: Int = 20
)

data class LeaderboardConfig(
    // Berapa banyak entri teratas yang ditampilkan setiap kali broadcast leaderboard terjadi
    // -- baik saat SATU GILIRAN GRUP SELESAI (leaderboard sementara) maupun saat SELURUH
    // EVENT SELESAI (hasil akhir). Broadcast dipicu oleh selesainya giliran, BUKAN interval
    // waktu tetap -- keputusan desain final, menggantikan rencana awal "broadcast tiap N detik".
    val topNPerBroadcast: Int = 3
)

data class SafariBallConfig(
    val enabled: Boolean = true,
    val catchRateMultiplier: Double = 2.5
)

data class SessionConfig(
    // Ukuran maksimal 1 grup. Jumlah grup mengikuti jumlah pendaftar (dibulatkan ke atas),
    // bukan angka tetap -- sesuai keputusan desain: ukuran grup tetap, jumlah grup menyesuaikan.
    val maxGroupSize: Int = 10,
    // Seberapa sering (dalam tick server) SafariAccessGuard memeriksa & mengeluarkan pemain
    // yang bukan gilirannya dari region. Tidak perlu setiap tick demi performa.
    val accessCheckIntervalTicks: Int = 10
)

/**
 * Pengaturan untuk /safari forcespawn -- spawn paksa Pokemon ACAK "common" (bukan legendary/
 * mythical/ultra beast/paradox) di titik acak dalam area Safari.
 */
data class ForceSpawnConfig(
    val level: Int = 10,
    // Label Cobblemon yang membuat sebuah spesies DIKECUALIKAN dari hasil acak "common".
    // Nilai default mengikuti konstanta resmi Cobblemon (CobblemonPokemonLabels).
    val excludedLabels: List<String> = listOf("legendary", "mythical", "ultra_beast", "paradox"),
    // Batas percobaan mengambil spesies acak baru kalau hasil sebelumnya kena excludedLabels,
    // supaya tidak berpotensi infinite loop kalau config excludedLabels ditulis terlalu luas.
    val maxSpeciesRerollAttempts: Int = 50
)

/**
 * Pengaturan spawn OTOMATIS & BERKALA selama giliran grup berjalan -- berbeda dari
 * ForceSpawnConfig yang dipakai admin manual lewat /safari forcespawn. Memakai ForceSpawnManager
 * yang sama (spesies "common" acak) di belakang layar, cuma dipicu terus-menerus oleh timer,
 * bukan sekali per command.
 */
data class PassiveSpawnConfig(
    val enabled: Boolean = true,
    // 20 tick = 1 detik, sesuai permintaan default ("setiap 1 detik").
    val intervalTicks: Int = 20,
    // Batas jumlah wild Pokemon hasil spawn otomatis yang boleh hidup BERSAMAAN. Tanpa batas
    // ini, spawn 1 Pokemon/detik selama giliran 30 menit bisa menumpuk sampai 1800 entity dan
    // berpotensi membebani server secara serius -- ditambahkan sebagai pengaman struktural,
    // bukan permintaan eksplisit. Begitu ada Pokemon yang ditangkap/despawn (jumlah hidup
    // turun di bawah batas ini), spawn otomatis lanjut lagi.
    val maxConcurrentWildSpawns: Int = 20
)

/**
 * Pengaturan pemberian Safari Ball BERKALA ke anggota grup yang sedang giliran -- BEDA dari
 * `safariBall` di atas (yang reserved untuk CatchRateBooster yang belum diimplementasikan).
 * Pemberian PERTAMA terjadi tepat saat giliran di-start, lalu berulang tiap `intervalTicks`
 * selama giliran itu masih berjalan.
 */
data class SafariBallSupplyConfig(
    val enabled: Boolean = true,
    val amount: Int = 20,
    // 400 tick = 20 detik, sesuai permintaan eksplisit ("20 safari ball setiap 20 detik,
    // bukan 1 setiap 1 detik").
    val intervalTicks: Int = 400
)

/**
 * Pengaturan pembersihan Pokemon liar di area Safari setiap SATU giliran grup selesai.
 */
data class WildCleanupConfig(
    val enabled: Boolean = true
)

/**
 * Root object, dipetakan 1:1 dari config/safari-event.json.
 * Semua field punya nilai default supaya config yang belum lengkap/rusak sebagian tetap
 * bisa dimuat tanpa membuat seluruh mod crash -- ConfigManager yang menjaga kontrak ini.
 */
data class SafariEventConfig(
    val regions: List<SafariRegionData> = listOf(SafariRegionData()),
    val hud: HudConfig = HudConfig(),
    val leaderboard: LeaderboardConfig = LeaderboardConfig(),
    val safariBall: SafariBallConfig = SafariBallConfig(),
    val session: SessionConfig = SessionConfig(),
    val forceSpawn: ForceSpawnConfig = ForceSpawnConfig(),
    val passiveSpawn: PassiveSpawnConfig = PassiveSpawnConfig(),
    val safariBallSupply: SafariBallSupplyConfig = SafariBallSupplyConfig(),
    val wildCleanup: WildCleanupConfig = WildCleanupConfig()
)
