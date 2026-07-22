package com.github.fadlanmuzaini1.safarievent.region

import com.github.fadlanmuzaini1.safarievent.event.TurnManager
import com.github.fadlanmuzaini1.safarievent.session.Group
import net.minecraft.registry.RegistryKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Teleport pemain MASUK ke titik acak di area Safari tepat saat gilirannya di-start, dan
 * MENGEMBALIKAN mereka ke lokasi semula tepat saat giliran itu selesai (baik selesai wajar
 * lewat timer maupun dihentikan paksa admin -- keduanya sama-sama memicu titik callback yang
 * sama, lihat EventManager.handleGroupTurnFinished()).
 *
 * MENANGANI PEMAIN YANG OFFLINE di momen krusial (lihat onPlayerJoin()):
 *  - Kalau pemain baru online SELAGI gilirannya sendiri masih aktif dan dia belum pernah
 *    di-teleport masuk -> langsung di-teleport masuk saat login.
 *  - Kalau pemain baru online SETELAH gilirannya sudah selesai (sempat offline tepat saat
 *    seharusnya dikembalikan) -> langsung dikembalikan ke lokasi semula saat login, supaya
 *    tidak "terjebak" permanen di area Safari.
 */
class SafariTeleporter(
    private val regionManager: RegionManager,
    private val turnManager: TurnManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/SafariTeleporter")

    private data class ReturnLocation(
        val groupIndex: Int,
        val worldKey: RegistryKey<World>,
        val pos: Vec3d,
        val yaw: Float,
        val pitch: Float
    )

    // Hanya berisi entri untuk pemain yang SEDANG di dalam area Safari (sudah di-teleport
    // masuk, belum dikembalikan). Dipakai baik untuk teleportGroupOut() maupun onPlayerJoin().
    private val pendingReturns = mutableMapOf<UUID, ReturnLocation>()

    /** Dipanggil EventManager tepat setelah grup dikunci (lockAndSnapshot), sebelum broadcast. */
    fun teleportGroupIn(server: MinecraftServer, group: Group) {
        for (uuid in group.members) {
            val player = server.playerManager.getPlayer(uuid) ?: continue // offline -> ditangani onPlayerJoin()
            teleportPlayerIn(player, group.index)
        }
    }

    /** Dipanggil EventManager tepat sebelum broadcast leaderboard sementara/hasil akhir giliran. */
    fun teleportGroupOut(server: MinecraftServer, group: Group) {
        for (uuid in group.members) {
            val player = server.playerManager.getPlayer(uuid) ?: continue // offline -> ditangani onPlayerJoin()
            restorePlayer(player)
        }
    }

    /** Didaftarkan sekali dari ServerPlayConnectionEvents.JOIN di SafariEventMod. */
    fun onPlayerJoin(player: ServerPlayerEntity) {
        val saved = pendingReturns[player.uuid]
        if (saved != null) {
            val currentGroup = turnManager.currentGroup()
            val stillOwnActiveTurn = currentGroup != null && currentGroup.index == saved.groupIndex
            if (!stillOwnActiveTurn) {
                // Gilirannya sudah selesai selagi dia offline -- kembalikan sekarang supaya
                // tidak terjebak permanen di area Safari.
                restorePlayer(player)
            }
            // else: masih giliran dia sendiri yang aktif -- posisi terakhir yang tersimpan di
            // playerdata vanilla memang sudah benar (di dalam area Safari), tidak perlu apa-apa.
            return
        }

        val currentGroup = turnManager.currentGroup() ?: return
        if (currentGroup.contains(player.uuid)) {
            teleportPlayerIn(player, currentGroup.index)
        }
    }

    private fun teleportPlayerIn(player: ServerPlayerEntity, groupIndex: Int) {
        // Jangan timpa lokasi asal yang sudah tersimpan -- kalau method ini terpanggil dua
        // kali untuk pemain yang sama (mis. race kecil antara teleportGroupIn dan join event),
        // lokasi asal HARUS tetap yang pertama kali tercatat.
        if (pendingReturns.containsKey(player.uuid)) return

        val region = regionManager.randomRegion()
        if (region == null) {
            logger.warn("Tidak ada region Safari valid, {} tidak bisa di-teleport masuk.", player.gameProfile.name)
            return
        }
        val ground = RegionPositionFinder.randomGroundPosition(player.server, region)
        if (ground == null) {
            logger.warn("World region '{}' belum dimuat, {} tidak bisa di-teleport masuk.", region.id, player.gameProfile.name)
            return
        }

        pendingReturns[player.uuid] = ReturnLocation(
            groupIndex = groupIndex,
            worldKey = player.serverWorld.registryKey,
            pos = player.pos,
            yaw = player.yaw,
            pitch = player.pitch
        )

        player.teleport(ground.world, ground.x, ground.y, ground.z, emptySet(), player.yaw, player.pitch)
    }

    private fun restorePlayer(player: ServerPlayerEntity) {
        val saved = pendingReturns.remove(player.uuid) ?: return
        val world = player.server.getWorld(saved.worldKey)
        if (world == null) {
            logger.warn(
                "Dunia asal ({}) untuk {} sudah tidak ada/tidak dimuat, tidak bisa dikembalikan ke lokasi semula.",
                saved.worldKey.value,
                player.gameProfile.name
            )
            return
        }
        player.teleport(world, saved.pos.x, saved.pos.y, saved.pos.z, emptySet(), saved.yaw, saved.pitch)
    }
}
