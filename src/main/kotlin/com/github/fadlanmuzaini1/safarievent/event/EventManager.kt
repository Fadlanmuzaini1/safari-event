package com.github.fadlanmuzaini1.safarievent.event

import com.cobblemon.mod.common.pokemon.Pokemon
import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.event.scoring.EventScoringStrategy
import com.github.fadlanmuzaini1.safarievent.event.scoring.EventScoringStrategyRegistry
import com.github.fadlanmuzaini1.safarievent.leaderboard.LeaderboardManager
import com.github.fadlanmuzaini1.safarievent.region.SafariAccessGuard
import com.github.fadlanmuzaini1.safarievent.session.Group
import com.github.fadlanmuzaini1.safarievent.session.RegistrationManager
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Orkestrator utama: menyatukan RegistrationManager, TurnManager, SafariAccessGuard,
 * LeaderboardManager, dan EventScoringStrategy menjadi satu alur.
 *
 * PERUBAHAN ARSITEKTUR PENTING dari desain sebelumnya: dulu /safari start <durasi> menutup
 * pendaftaran dan menjalankan SEMUA grup secara otomatis berurutan dalam satu rangkaian. 
 * Sekarang admin men-start SATU GRUP SAJA per perintah (/safari start <grup> <durasi>),
 * grup lain tetap bisa terbentuk/terisi dari pendaftaran yang tetap terbuka, dan tidak ada
 * lagi auto-lanjut ke grup berikutnya -- admin yang memutuskan kapan grup berikutnya di-start.
 *
 * Konsekuensinya: tidak ada lagi satu momen tunggal "mulai/selesai event" yang terdeteksi
 * otomatis (dulu momen itu yang mereset leaderboard & pendaftaran). Method `resetSession()`
 * DITAMBAHKAN untuk mengisi celah itu -- dipanggil admin lewat /safari reset saat mereka
 * sendiri yang memutuskan sesi sudah selesai dan ingin memulai sesi baru dari nol.
 */
class EventManager(
    private val configManager: ConfigManager,
    private val registrationManager: RegistrationManager,
    private val turnManager: TurnManager,
    private val safariAccessGuard: SafariAccessGuard,
    private val leaderboardManager: LeaderboardManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/EventManager")

    // Strategy dipilih SEKALI untuk seluruh sesi (saat grup pertama di-start), lalu dipakai
    // konsisten untuk grup-grup berikutnya dalam sesi yang sama -- tidak bisa berganti mode
    // di tengah sesi tanpa /safari reset dulu.
    private var activeStrategy: EventScoringStrategy? = null

    // Referensi server disimpan supaya broadcast otomatis (dipicu timer habis, bukan dari
    // eksekusi command) tetap bisa menjangkau semua pemain.
    private var cachedServer: MinecraftServer? = null

    /** True selama ada SATU giliran grup yang sedang berjalan (bukan "seluruh event", karena konsep itu sudah tidak ada). */
    val isRunning: Boolean
        get() = turnManager.isActive

    sealed class StartGroupResult {
        data class Success(val groupDisplayName: String, val memberNames: List<String>) : StartGroupResult()
        data object AnotherGroupRunning : StartGroupResult()
        data object GroupNotFound : StartGroupResult()
        data object GroupAlreadyStarted : StartGroupResult()
        data object GroupEmpty : StartGroupResult()
        data class UnknownStrategy(val id: String) : StartGroupResult()
    }

    sealed class StopResult {
        data object Success : StopResult()
        data object NotRunning : StopResult()
    }

    sealed class ResetResult {
        data object Success : ResetResult()
        data object TurnStillActive : ResetResult()
    }

    sealed class CatchRegistrationResult {
        data class Counted(val newTotal: Int) : CatchRegistrationResult()
        data object EventNotRunning : CatchRegistrationResult()
        data object NotPlayerTurn : CatchRegistrationResult()
        data object NotEligibleForStrategy : CatchRegistrationResult()
    }

    /** Dipanggil dari ServerTickEvents.END_SERVER_TICK -- SATU-SATUNYA listener tick untuk EventManager. */
    fun tick(server: MinecraftServer) {
        cachedServer = server
        turnManager.tick()
        safariAccessGuard.tick(server)
    }

    /**
     * Dipanggil CommandManager dari /safari start <grup> <durasi> [mode]. Mengunci
     * keanggotaan grup yang diminta (via RegistrationManager.lockAndSnapshot), lalu
     * memulai giliran untuk grup itu SAJA -- grup lain tidak terpengaruh.
     */
    fun startGroup(
        server: MinecraftServer,
        groupLabel: String,
        durationSeconds: Long,
        strategyId: String? = null
    ): StartGroupResult {
        cachedServer = server

        if (turnManager.isActive) return StartGroupResult.AnotherGroupRunning

        val index = registrationManager.resolveGroupLabel(groupLabel)
            ?: return StartGroupResult.GroupNotFound

        if (registrationManager.isGroupLocked(index)) return StartGroupResult.GroupAlreadyStarted

        val strategy = resolveStrategyForStart(strategyId) ?: return StartGroupResult.UnknownStrategy(strategyId!!)

        val group = registrationManager.lockAndSnapshot(index) ?: return StartGroupResult.GroupEmpty

        activeStrategy = strategy
        safariAccessGuard.resetWarnings()

        val memberNames = group.memberNames.values.toList()
        broadcastToAll(
            server,
            Text.literal(
                "===== Giliran ${group.displayName} Dimulai! =====\n" +
                    "Pemain: ${memberNames.joinToString(", ")}\n" +
                    "Durasi: $durationSeconds detik"
            )
        )

        turnManager.startTurn(group, durationSeconds) { finishedGroup -> handleGroupTurnFinished(finishedGroup) }

        return StartGroupResult.Success(group.displayName, memberNames)
    }

    /**
     * Menentukan strategy yang dipakai: kalau sesi sudah punya activeStrategy (dari giliran
     * grup sebelumnya di sesi yang sama), tetap pakai itu -- mode TIDAK bisa berganti di
     * tengah sesi. Kalau mode berbeda diminta di tengah sesi, permintaan itu diabaikan
     * (dicatat sebagai warning), bukan error, supaya admin tidak perlu mengulang command
     * tanpa argumen mode hanya karena lupa itu sudah terkunci.
     */
    private fun resolveStrategyForStart(strategyId: String?): EventScoringStrategy? {
        val existing = activeStrategy
        if (existing != null) {
            if (strategyId != null && strategyId != existing.id) {
                logger.warn(
                    "Mode '{}' diminta tapi sesi sudah berjalan dengan mode '{}' sejak giliran " +
                        "grup sebelumnya; permintaan mode diabaikan (mode terkunci sampai /safari reset).",
                    strategyId,
                    existing.id
                )
            }
            return existing
        }

        return if (strategyId != null) {
            EventScoringStrategyRegistry.get(strategyId)
        } else {
            EventScoringStrategyRegistry.default()
        }
    }

    /** Dipanggil CommandManager dari /safari stop -- paksa giliran yang SEDANG AKTIF selesai sekarang. */
    fun stop(server: MinecraftServer): StopResult {
        cachedServer = server
        if (!isRunning) return StopResult.NotRunning
        turnManager.stopNow()
        return StopResult.Success
    }

    /**
     * Dipanggil CommandManager dari /safari reset. Mengakhiri SESI SEPENUHNYA: broadcast
     * hasil akhir (top-N), lalu hapus seluruh pendaftaran/grup & leaderboard supaya sesi
     * berikutnya mulai dari nol. Ditolak kalau masih ada giliran yang sedang berjalan --
     * admin harus /safari stop dulu (atau tunggu selesai) sebelum reset.
     */
    fun resetSession(server: MinecraftServer): ResetResult {
        if (turnManager.isActive) return ResetResult.TurnStillActive

        val topN = configManager.current.leaderboard.topNPerBroadcast.coerceAtLeast(1)
        val top = leaderboardManager.getTop(topN)
        val message = buildString {
            append("===== Safari Event Selesai =====")
            if (top.isEmpty()) {
                append("\nTidak ada tangkapan yang tercatat.")
            } else {
                top.forEachIndexed { index, entry -> append("\n${index + 1}. ${entry.username} - ${entry.score}") }
            }
        }
        broadcastToAll(server, Text.literal(message))

        leaderboardManager.reset()
        registrationManager.resetAll()
        activeStrategy = null

        logger.info("Sesi direset oleh admin. Pendaftaran & leaderboard dibersihkan untuk sesi baru.")
        return ResetResult.Success
    }

    /**
     * Dipanggil CatchTracker untuk setiap event CobblemonEvents.POKEMON_CAPTURED yang sudah
     * lolos cek region. Memvalidasi: apakah ada giliran berjalan, apakah ini gilirannya, dan
     * apakah strategy aktif menganggap tangkapan ini relevan.
     */
    fun registerCatch(uuid: UUID, username: String, pokemon: Pokemon): CatchRegistrationResult {
        if (!isRunning) return CatchRegistrationResult.EventNotRunning
        if (!turnManager.isPlayerAllowedNow(uuid)) return CatchRegistrationResult.NotPlayerTurn

        val strategy = activeStrategy ?: return CatchRegistrationResult.EventNotRunning
        if (!strategy.isEligible(pokemon)) return CatchRegistrationResult.NotEligibleForStrategy

        val newTotal = leaderboardManager.addScore(uuid, username, strategy.scoreIncrement(pokemon))
        return CatchRegistrationResult.Counted(newTotal)
    }

    private fun handleGroupTurnFinished(finishedGroup: Group) {
        val server = cachedServer ?: run {
            logger.warn("Giliran {} selesai tapi tidak ada referensi server untuk broadcast.", finishedGroup.displayName)
            return
        }
        broadcastToAll(server, Text.literal(buildLeaderboardSnapshotMessage(finishedGroup)))
    }

    private fun buildLeaderboardSnapshotMessage(finishedGroup: Group): String {
        val topN = configManager.current.leaderboard.topNPerBroadcast.coerceAtLeast(1)
        val top = leaderboardManager.getTop(topN)
        return buildString {
            append("===== Leaderboard Sementara (setelah ${finishedGroup.displayName}) =====")
            if (top.isEmpty()) {
                append("\nBelum ada tangkapan tercatat.")
            } else {
                top.forEachIndexed { index, entry -> append("\n${index + 1}. ${entry.username} - ${entry.score}") }
            }
        }
    }

    private fun broadcastToAll(server: MinecraftServer, message: Text) {
        for (player in server.playerManager.playerList) {
            player.sendMessage(message, false)
        }
    }
}
