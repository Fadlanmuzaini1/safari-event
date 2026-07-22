package com.github.fadlanmuzaini1.safarievent.region

import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.event.TurnManager
import net.minecraft.network.packet.s2c.play.PositionFlag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.slf4j.LoggerFactory

/**
 * Berkala memeriksa semua pemain online: kalau ada yang berada di dalam sebuah region Safari
 * TAPI bukan giliran grupnya saat ini, teleport keluar ke titik antrian region tersebut.
 *
 * Hanya aktif kalau TurnManager.isActive == true (ada giliran sedang berjalan) -- di luar
 * itu (event IDLE atau baru selesai), region bebas keluar-masuk siapa saja, sesuai keputusan
 * desain. Class ini murni enforcement -- tidak tahu apa-apa soal leaderboard atau scoring.
 *
 * CATATAN VERSI: signature `ServerPlayerEntity.teleport(ServerWorld, double, double, double,
 * Set<PositionFlag>, float, float)` (7 parameter, TANPA boolean resetCamera di akhir) sudah
 * dikonfirmasi dari dokumentasi Yarn resmi untuk rentang 1.21-1.21.3. Parameter boolean
 * resetCamera baru ditambahkan mulai 1.21.4 -- untuk 1.21.1 (target mod ini) signature 7
 * parameter ini yang benar.
 */
class SafariAccessGuard(
    private val configManager: ConfigManager,
    private val regionManager: RegionManager,
    private val turnManager: TurnManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/SafariAccessGuard")

    // Supaya warning "queuePoint belum diatur" tidak spam tiap kali dicek, cuma dicatat SEKALI
    // per region per rangkaian giliran -- direset lewat resetWarnings() saat giliran baru mulai.
    private val warnedMissingQueuePoint = mutableSetOf<String>()

    private var tickCounter = 0

    /**
     * Dipanggil dari ServerTickEvents.END_SERVER_TICK (didaftarkan sekali secara global di
     * SafariEventMod). Aman dipanggil tiap tick -- early return kalau tidak ada giliran aktif,
     * dan hanya benar-benar memeriksa pemain tiap `accessCheckIntervalTicks` (config).
     */
    fun tick(server: MinecraftServer) {
        if (!turnManager.isActive) return

        tickCounter++
        val interval = configManager.current.session.accessCheckIntervalTicks.coerceAtLeast(1)
        if (tickCounter < interval) return
        tickCounter = 0

        for (player in server.playerManager.playerList) {
            enforce(player)
        }
    }

    /** Dipanggil TurnManager setiap kali rangkaian giliran baru dimulai, supaya warning region bisa muncul lagi kalau memang masih relevan. */
    fun resetWarnings() {
        warnedMissingQueuePoint.clear()
    }

    private fun enforce(player: ServerPlayerEntity) {
        val region = regionManager.findRegion(player) ?: return
        if (turnManager.isPlayerAllowedNow(player.uuid)) return

        val queuePoint = region.queuePoint
        if (queuePoint == null) {
            if (warnedMissingQueuePoint.add(region.id)) {
                logger.warn(
                    "Region '{}' tidak punya titik antrian (queueX/Y/Z) di config. Enforcement " +
                        "DILEWATI untuk region ini -- pemain di luar giliran tetap bisa berada " +
                        "di dalamnya sampai admin mengisi titik antrian dan /safari reload.",
                    region.id
                )
            }
            return
        }

        player.teleport(
            player.serverWorld,
            queuePoint.x,
            queuePoint.y,
            queuePoint.z,
            emptySet<PositionFlag>(),
            player.yaw,
            player.pitch
        )
        player.sendMessage(
            Text.literal("Bukan giliran grupmu sekarang -- kamu dikeluarkan dari area Safari."),
            false
        )
    }
}
