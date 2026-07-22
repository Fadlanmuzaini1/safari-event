package com.github.fadlanmuzaini1.safarievent.spawn

import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.event.EventManager
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Memicu ForceSpawnManager SECARA OTOMATIS & BERKALA (default tiap 1 detik) selama ada
 * giliran grup yang sedang berjalan (`EventManager.isRunning`) -- berhenti otomatis begitu
 * giliran selesai/dihentikan, tanpa perlu command admin sama sekali.
 *
 * Membatasi jumlah wild Pokemon yang boleh hidup BERSAMAAN hasil spawn otomatis ini
 * (`passiveSpawn.maxConcurrentWildSpawns`) -- pengaman struktural supaya spawn 1
 * Pokemon/detik selama giliran panjang tidak menumpuk entity tanpa batas dan membebani
 * server. Begitu Pokemon yang di-track ditangkap/despawn (tidak lagi ditemukan lewat
 * `World.getEntity(uuid)`), slot itu otomatis terbuka lagi untuk spawn berikutnya.
 */
class PassiveSpawnScheduler(
    private val configManager: ConfigManager,
    private val eventManager: EventManager,
    private val forceSpawnManager: ForceSpawnManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/PassiveSpawnScheduler")

    private var tickCounter = 0

    // Pasangan (world, uuid) Pokemon yang di-spawn scheduler ini dan diasumsikan masih hidup,
    // sampai terbukti sebaliknya lewat pruneDeadSpawns(). Dipakai MURNI untuk membatasi
    // maxConcurrentWildSpawns -- tidak memengaruhi logic capture/leaderboard sama sekali.
    private val trackedSpawns = mutableListOf<Pair<RegistryKey<World>, UUID>>()

    /**
     * Dipanggil dari ServerTickEvents.END_SERVER_TICK (didelegasikan lewat SafariEventMod,
     * sejajar dengan EventManager.tick() dan HudManager.tick() -- SENGAJA tidak menjadi
     * dependency EventManager, supaya EventManager tidak perlu tahu apa-apa soal spawning).
     */
    fun tick(server: MinecraftServer) {
        val config = configManager.current.passiveSpawn
        if (!config.enabled) return

        if (!eventManager.isRunning) {
            tickCounter = 0
            return
        }

        tickCounter++
        val interval = config.intervalTicks.coerceAtLeast(1)
        if (tickCounter < interval) return
        tickCounter = 0

        pruneDeadSpawns(server)

        val cap = config.maxConcurrentWildSpawns.coerceAtLeast(0)
        if (trackedSpawns.size >= cap) {
            logger.debug("Batas maxConcurrentWildSpawns ({}) tercapai, spawn otomatis dilewati tick ini.", cap)
            return
        }

        when (val result = forceSpawnManager.forceSpawnOne(server)) {
            is ForceSpawnManager.ForceSpawnResult.Success -> {
                trackedSpawns.add(result.worldKey to result.uuid)
                logger.debug(
                    "Passive spawn: {} di ({}, {}, {}). Wild Pokemon ter-track sekarang: {}.",
                    result.speciesName,
                    result.x,
                    result.y,
                    result.z,
                    trackedSpawns.size
                )
            }
            // NoRegionConfigured/WorldNotLoaded/SpawnFailed sengaja tidak di-log di sini
            // (beda dari /safari forcespawn manual) -- kalau region belum dikonfigurasi,
            // ini akan terjadi TIAP interval selama giliran berjalan, dan sudah cukup
            // diketahui admin lewat log ForceSpawnManager sendiri kalau perlu; tidak perlu
            // duplikasi log setiap detik.
            else -> Unit
        }
    }

    private fun pruneDeadSpawns(server: MinecraftServer) {
        trackedSpawns.removeAll { (worldKey, uuid) ->
            val world = server.getWorld(worldKey)
            world == null || world.getEntity(uuid) == null
        }
    }
}
