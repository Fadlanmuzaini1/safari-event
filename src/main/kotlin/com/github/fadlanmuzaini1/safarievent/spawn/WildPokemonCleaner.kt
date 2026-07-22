package com.github.fadlanmuzaini1.safarievent.spawn

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.region.RegionManager
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

/**
 * Menghapus SEMUA Pokemon LIAR (bukan milik pemain manapun) yang sedang berada di dalam
 * region Safari mana pun, dipanggil EventManager tepat setiap SATU giliran grup selesai --
 * sesuai permintaan eksplisit "setiap grup selesai, semua Pokemon di dalam Safari dihapus".
 * Mencegah sisa Pokemon liar dari giliran sebelumnya menumpuk/menyeberang ke giliran
 * berikutnya (di luar mekanisme `maxConcurrentWildSpawns` milik PassiveSpawnScheduler, yang
 * hanya membatasi SELAMA satu giliran berjalan, bukan membersihkan ANTAR giliran).
 *
 * Sengaja HANYA menghapus Pokemon yang `pokemon.isWild()` (dikonfirmasi dari source resmi
 * Cobblemon: `Pokemon.isWild() = storeCoordinates.get() == null`, yaitu belum tercatat
 * dimiliki pemain manapun) -- TIDAK PERNAH menghapus Pokemon milik pemain (mis. yang sedang
 * mengikuti/follow di luar Poke Ball), meski pemain itu kebetulan berdiri di dalam region.
 */
class WildPokemonCleaner(
    private val configManager: ConfigManager,
    private val regionManager: RegionManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/WildPokemonCleaner")

    fun removeAllWildPokemon(server: MinecraftServer) {
        if (!configManager.current.wildCleanup.enabled) return

        var removed = 0
        for (region in regionManager.allRegions()) {
            val world = server.getWorld(region.worldKey) ?: continue
            val wildEntities = world.getEntitiesByClass(PokemonEntity::class.java, region.toBox()) { entity ->
                entity.pokemon.isWild()
            }
            for (entity in wildEntities) {
                entity.discard()
                removed++
            }
        }

        if (removed > 0) {
            logger.info("{} Pokemon liar dihapus dari area Safari setelah giliran selesai.", removed)
        }
    }
}
