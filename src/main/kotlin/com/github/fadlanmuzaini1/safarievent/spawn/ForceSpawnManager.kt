package com.github.fadlanmuzaini1.safarievent.spawn

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.region.RegionManager
import com.github.fadlanmuzaini1.safarievent.region.RegionPositionFinder
import net.minecraft.entity.SpawnReason
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.random.Random

/**
 * Spawn paksa SATU Pokemon liar "common" (bukan legendary/mythical/ultra beast/paradox) di
 * titik ACAK di dalam salah satu region Safari yang terdaftar -- admin tidak perlu berada
 * di lokasi tersebut.
 *
 * SELURUH cara spawn di bawah ini diverifikasi langsung dari source resmi Cobblemon 1.7.x
 * (command bawaan `/pokespawn`, file `SpawnPokemon.kt`), bukan tebakan:
 *   1. `PokemonProperties.parse("species=\"...\" level=...").createEntity(world)` -- pola
 *      identik dengan yang dipakai command resmi Cobblemon untuk membuat PokemonEntity.
 *   2. `entity.refreshPositionAndAngles(...)` + `entity.initialize(world, difficulty,
 *      SpawnReason.COMMAND, null, null)` + `world.spawnEntity(entity)` -- persis urutan
 *      pemanggilan yang dipakai `SpawnPokemon.execute()` di Cobblemon (diterjemahkan dari
 *      Mojang mappings ke Yarn: `finalizeSpawn` -> `initialize`, `addFreshEntity` ->
 *      `spawnEntity`, `getCurrentDifficultyAt` -> `getLocalDifficulty`).
 *
 * Pokemon yang muncul dari sini adalah Pokemon LIAR SUNGGUHAN (bukan hasil trik lain) --
 * begitu ditangkap dengan Poke Ball, akan lolos verifikasi CatchTracker seperti tangkapan
 * biasa, karena jalurnya (CobblemonEvents.POKEMON_CAPTURED via EmptyPokeBallEntity) sama
 * persis dengan Pokemon yang muncul dari spawning alami.
 */
class ForceSpawnManager(
    private val configManager: ConfigManager,
    private val regionManager: RegionManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/ForceSpawnManager")

    sealed class ForceSpawnResult {
        data class Success(
            val speciesName: String,
            val x: Int,
            val y: Int,
            val z: Int,
            // uuid + worldKey dibutuhkan PassiveSpawnScheduler untuk melacak apakah Pokemon
            // hasil spawn ini masih hidup (belum ditangkap/despawn), demi membatasi jumlah
            // wild Pokemon yang menumpuk selama spawning otomatis berjalan lama.
            val uuid: UUID,
            val worldKey: RegistryKey<World>
        ) : ForceSpawnResult()
        data object NoRegionConfigured : ForceSpawnResult()
        data object WorldNotLoaded : ForceSpawnResult()
        data object SpawnFailed : ForceSpawnResult()
    }

    /** Spawn SATU Pokemon. Dipanggil berulang oleh CommandManager untuk /safari forcespawn <jumlah>, dan oleh PassiveSpawnScheduler tiap interval. */
    fun forceSpawnOne(server: MinecraftServer): ForceSpawnResult {
        val region = regionManager.randomRegion() ?: return ForceSpawnResult.NoRegionConfigured
        val ground = RegionPositionFinder.randomGroundPosition(server, region) ?: return ForceSpawnResult.WorldNotLoaded
        val world = ground.world

        val species = pickCommonSpecies()
        val level = configManager.current.forceSpawn.level

        return try {
            val properties = PokemonProperties.parse("species=\"${species.resourceIdentifier}\" level=$level")
            val entity = properties.createEntity(world)

            entity.refreshPositionAndAngles(
                ground.x,
                ground.y,
                ground.z,
                Random.nextFloat() * 360f,
                0f
            )

            val blockPos = BlockPos.ofFloored(ground.x, ground.y, ground.z)
            entity.initialize(world, world.getLocalDifficulty(blockPos), SpawnReason.COMMAND, null, null)

            if (world.spawnEntity(entity)) {
                logger.info(
                    "Force-spawn berhasil: {} level {} di ({}, {}, {}) region '{}'.",
                    species.name,
                    level,
                    blockPos.x,
                    blockPos.y,
                    blockPos.z,
                    region.id
                )
                ForceSpawnResult.Success(species.name, blockPos.x, blockPos.y, blockPos.z, entity.uuid, region.worldKey)
            } else {
                logger.warn("world.spawnEntity() mengembalikan false untuk {} di ({}, {}, {}).", species.name, blockPos.x, blockPos.y, blockPos.z)
                ForceSpawnResult.SpawnFailed
            }
        } catch (e: Exception) {
            logger.error("Gagal force-spawn Pokemon di region '{}'.", region.id, e)
            ForceSpawnResult.SpawnFailed
        }
    }

    /**
     * Ambil satu Species acak yang TIDAK punya label di `forceSpawn.excludedLabels` (default:
     * legendary/mythical/ultra_beast/paradox). Dicoba ulang sampai `maxSpeciesRerollAttempts`
     * kali -- kalau tetap tidak dapat (misalnya admin menulis excludedLabels yang terlalu
     * luas sehingga hampir semua spesies kena filter), pakai hasil percobaan terakhir apa
     * adanya dan catat warning -- bagaimanapun juga TIDAK membuat command menggantung/infinite loop.
     */
    private fun pickCommonSpecies(): Species {
        val config = configManager.current.forceSpawn
        val excluded = config.excludedLabels.map { it.lowercase() }.toSet()
        val maxAttempts = config.maxSpeciesRerollAttempts.coerceAtLeast(1)

        var candidate = PokemonSpecies.random()
        repeat(maxAttempts) {
            if (candidate.labels.none { it.lowercase() in excluded }) {
                return candidate
            }
            candidate = PokemonSpecies.random()
        }

        logger.warn(
            "Tidak menemukan spesies yang lolos filter excludedLabels setelah {} percobaan. " +
                "Memakai '{}' apa adanya -- pertimbangkan mempersempit excludedLabels di config.",
            maxAttempts,
            candidate.name
        )
        return candidate
    }
}
