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
 * Root object, dipetakan 1:1 dari config/safari-event.json.
 * Semua field punya nilai default supaya config yang belum lengkap/rusak sebagian tetap
 * bisa dimuat tanpa membuat seluruh mod crash -- ConfigManager yang menjaga kontrak ini.
 */
data class SafariEventConfig(
    val regions: List<SafariRegionData> = listOf(SafariRegionData()),
    val hud: HudConfig = HudConfig(),
    val leaderboard: LeaderboardConfig = LeaderboardConfig(),
    val safariBall: SafariBallConfig = SafariBallConfig(),
    val session: SessionConfig = SessionConfig()
)
