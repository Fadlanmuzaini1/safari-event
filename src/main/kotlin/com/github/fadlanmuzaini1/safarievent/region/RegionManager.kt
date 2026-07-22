package com.github.fadlanmuzaini1.safarievent.region

import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import net.minecraft.server.network.ServerPlayerEntity
import org.slf4j.LoggerFactory

/**
 * Menjawab satu pertanyaan saja: "apakah player ini sedang berada di dalam region Safari
 * (dan yang mana)?". Tidak tahu apa-apa soal event, scoring, atau capture -- murni geometri
 * + dunia. CatchTracker dan EventManager memakai class ini, bukan sebaliknya.
 *
 * Didesain mendukung banyak region sejak awal (lihat SafariRegionData/README), walau
 * requirement saat ini hanya butuh satu region aktif.
 */
class RegionManager(private val configManager: ConfigManager) {

    private val logger = LoggerFactory.getLogger("SafariEvent/RegionManager")

    private var regions: List<SafariRegion> = emptyList()

    /**
     * Dipanggil sekali saat mod initialize, dan lagi setiap kali /safari reload dijalankan
     * (dipanggil setelah ConfigManager.reload() supaya region baca config yang sudah fresh).
     */
    fun reload() {
        val compiled = mutableListOf<SafariRegion>()

        for (data in configManager.current.regions) {
            val region = SafariRegion.fromConfig(data)
            if (region == null) {
                logger.error(
                    "Region '{}' punya world identifier tidak valid: '{}'. Region ini DILEWATI, " +
                        "sisa region lain tetap dimuat normal.",
                    data.id,
                    data.world
                )
                continue
            }
            compiled.add(region)
        }

        regions = compiled
        logger.info("RegionManager memuat {} region Safari valid.", regions.size)
    }

    /** True jika player berada di dalam SALAH SATU region Safari yang terdaftar. */
    fun isPlayerInsideSafari(player: ServerPlayerEntity): Boolean {
        return findRegion(player) != null
    }

    /**
     * Mengembalikan region spesifik tempat player berada, atau null jika di luar semua region.
     * Diekspos terpisah dari isPlayerInsideSafari karena berguna di masa depan jika tiap
     * region ingin dikaitkan ke mode event yang berbeda (mis. region A = Most Catch,
     * region B = Most Shiny berjalan bersamaan).
     */
    fun findRegion(player: ServerPlayerEntity): SafariRegion? {
        val playerWorldKey = player.serverWorld.registryKey
        val pos = player.pos
        return regions.firstOrNull { region ->
            region.worldKey == playerWorldKey && region.contains(pos.x, pos.y, pos.z)
        }
    }

    /**
     * Pilih satu region SECARA ACAK dari semua region yang terdaftar. Null kalau belum ada
     * region valid sama sekali. Dipakai ForceSpawnManager -- setiap kali admin men-spawn
     * beberapa Pokemon sekaligus, tiap Pokemon independen memilih region-nya sendiri, jadi
     * kalau ada multi-region, hasilnya tersebar wajar di antara semuanya.
     */
    fun randomRegion(): SafariRegion? = regions.randomOrNull()
}
