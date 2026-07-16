package com.github.fadlanmuzaini1.safarievent.event

import com.cobblemon.mod.common.pokemon.Pokemon
import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.event.scoring.EventScoringStrategy
import com.github.fadlanmuzaini1.safarievent.event.scoring.EventScoringStrategyRegistry
import com.github.fadlanmuzaini1.safarievent.leaderboard.LeaderboardManager
import com.github.fadlanmuzaini1.safarievent.region.SafariAccessGuard
import com.github.fadlanmuzaini1.safarievent.session.Group
import com.github.fadlanmuzaini1.safarievent.session.GroupManager
import com.github.fadlanmuzaini1.safarievent.session.RegistrationManager
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Orkestrator utama: satu-satunya class yang menyatukan RegistrationManager, GroupManager,
 * TurnManager, SafariAccessGuard, LeaderboardManager, dan EventScoringStrategy menjadi satu
 * alur lifecycle event.
 *
 * CatchTracker, HudManager, dan CommandManager (belum dibuat) semua berinteraksi lewat
 * EventManager -- tidak satupun dari mereka menyentuh TurnManager/RegistrationManager langsung.
 */
class EventManager(
    private val configManager: ConfigManager,
    private val registrationManager: RegistrationManager,
    private val groupManager: GroupManager,
    private val turnManager: TurnManager,
    private val safariAccessGuard: SafariAccessGuard,
    private val leaderboardManager: LeaderboardManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/EventManager")

    private var activeStrategy: EventScoringStrategy? = null

    // Referensi server disimpan supaya transisi giliran OTOMATIS (dipicu timer habis, bukan
    // dari eksekusi command) tetap bisa broadcast ke semua pemain. Diperbarui tiap tick lewat
    // tick(), dan juga langsung diisi ulang saat start()/stop() dipanggil dari command.
    private var cachedServer: MinecraftServer? = null

    /**
     * SENGAJA tidak disimpan sebagai flag boolean terpisah -- disamakan dengan
     * TurnManager.isActive supaya tidak ada dua sumber kebenaran soal "apakah event
     * sedang berjalan" yang bisa saling tidak sinkron.
     */
    val isRunning: Boolean
        get() = turnManager.isActive

    sealed class StartResult {
        data class Success(val groupCount: Int, val playerCount: Int) : StartResult()
        data object AlreadyRunning : StartResult()
        data object NoRegistrants : StartResult()
        data class UnknownStrategy(val id: String) : StartResult()
    }

    sealed class StopResult {
        data object Success : StopResult()
        data object NotRunning : StopResult()
    }

    sealed class CatchRegistrationResult {
        data class Counted(val newTotal: Int) : CatchRegistrationResult()
        data object EventNotRunning : CatchRegistrationResult()
        data object NotPlayerTurn : CatchRegistrationResult()
        data object NotEligibleForStrategy : CatchRegistrationResult()
    }

    /** Dipanggil dari ServerTickEvents.END_SERVER_TICK -- SATU-SATUNYA listener tick di seluruh mod. */
    fun tick(server: MinecraftServer) {
        cachedServer = server
        turnManager.tick()
        safariAccessGuard.tick(server)
    }

    /**
     * Dipanggil CommandManager dari handler /safari start <duration> [strategyId].
     * Menutup pendaftaran, membentuk grup, mereset leaderboard, lalu memulai giliran pertama.
     */
    fun start(server: MinecraftServer, durationSecondsPerTurn: Long, strategyId: String? = null): StartResult {
        cachedServer = server

        if (isRunning) return StartResult.AlreadyRunning

        val strategy = if (strategyId != null) {
            EventScoringStrategyRegistry.get(strategyId) ?: return StartResult.UnknownStrategy(strategyId)
        } else {
            EventScoringStrategyRegistry.default()
        }

        val snapshot = registrationManager.closeAndSnapshot()
        if (snapshot.isEmpty()) {
            // Tidak ada yang perlu dijalankan -- buka lagi pendaftaran, jangan biarkan
            // pendaftaran diam-diam tertutup permanen gara-gara start yang gagal.
            registrationManager.resetAndOpen()
            return StartResult.NoRegistrants
        }

        val groups = groupManager.formGroups(snapshot, configManager.current.session.maxGroupSize)

        activeStrategy = strategy
        leaderboardManager.reset()
        safariAccessGuard.resetWarnings()

        broadcastToAll(
            server,
            Text.literal(
                "===== Safari Event Dimulai! =====\n" +
                    "Mode: ${strategy.displayName}\n" +
                    "${snapshot.size} pemain dibagi menjadi ${groups.size} grup, " +
                    "masing-masing $durationSecondsPerTurn detik giliran."
            )
        )

        turnManager.begin(
            groups = groups,
            durationSecondsPerTurn = durationSecondsPerTurn,
            onTurnAdvanced = { previous, next -> handleTurnAdvanced(previous, next) },
            onAllTurnsFinished = { handleAllTurnsFinished() }
        )

        logger.info(
            "Event dimulai. Strategy: {}, {} grup, {} detik/giliran.",
            strategy.id,
            groups.size,
            durationSecondsPerTurn
        )

        return StartResult.Success(groups.size, snapshot.size)
    }

    /**
     * Dipanggil CommandManager dari /safari stop. Memaksa seluruh rangkaian giliran berhenti
     * SEKARANG -- lewat TurnManager.stopNow(), yang secara SINKRON memicu handleAllTurnsFinished()
     * di bawah. Tidak ada logic broadcast/reset yang diduplikasi di sini.
     */
    fun stop(server: MinecraftServer): StopResult {
        cachedServer = server
        if (!isRunning) return StopResult.NotRunning
        turnManager.stopNow()
        return StopResult.Success
    }

    /**
     * Dipanggil CatchTracker untuk setiap event CobblemonEvents.POKEMON_CAPTURED yang sudah
     * lolos cek region (lihat CatchTracker). Method ini yang memvalidasi sisanya: apakah event
     * berjalan, apakah ini gilirannya, dan apakah strategy aktif menganggap tangkapan ini relevan.
     */
    fun registerCatch(uuid: UUID, username: String, pokemon: Pokemon): CatchRegistrationResult {
        if (!isRunning) return CatchRegistrationResult.EventNotRunning
        if (!turnManager.isPlayerAllowedNow(uuid)) return CatchRegistrationResult.NotPlayerTurn

        val strategy = activeStrategy ?: return CatchRegistrationResult.EventNotRunning
        if (!strategy.isEligible(pokemon)) return CatchRegistrationResult.NotEligibleForStrategy

        val newTotal = leaderboardManager.addScore(uuid, username, strategy.scoreIncrement(pokemon))
        return CatchRegistrationResult.Counted(newTotal)
    }

    private fun handleTurnAdvanced(previous: Group?, next: Group) {
        val server = cachedServer ?: run {
            logger.warn("Giliran berpindah tapi tidak ada referensi server untuk broadcast.")
            return
        }

        // Setiap kali SATU giliran grup selesai (previous != null), broadcast leaderboard
        // sementara -- ini pengganti rencana awal "broadcast tiap N detik": broadcast di sini
        // dipicu oleh SELESAINYA GILIRAN, bukan timer terpisah, supaya pesan selalu relevan
        // (tepat setelah ada aktivitas) dan tidak berpotensi spam di tengah giliran yang sama.
        if (previous != null) {
            broadcastToAll(server, Text.literal(buildLeaderboardSnapshotMessage(previous)))
        }

        val message = if (previous == null) {
            "Giliran pertama dimulai: ${next.displayName} (${next.size} pemain). Bersiap!"
        } else {
            "Giliran berikutnya: ${next.displayName} (${next.size} pemain) dimulai!"
        }
        broadcastToAll(server, Text.literal(message))
    }

    private fun buildLeaderboardSnapshotMessage(finishedGroup: Group): String {
        val topN = configManager.current.leaderboard.topNPerBroadcast.coerceAtLeast(1)
        val top = leaderboardManager.getTop(topN)
        return buildString {
            append("===== Leaderboard Sementara (setelah ${finishedGroup.displayName}) =====")
            if (top.isEmpty()) {
                append("\nBelum ada tangkapan tercatat.")
            } else {
                top.forEachIndexed { index, entry ->
                    append("\n${index + 1}. ${entry.username} - ${entry.score}")
                }
            }
        }
    }

    private fun handleAllTurnsFinished() {
        val topN = configManager.current.leaderboard.topNPerBroadcast.coerceAtLeast(1)
        val top = leaderboardManager.getTop(topN)
        val message = buildString {
            append("===== Safari Event Finished =====")
            if (top.isEmpty()) {
                append("\nTidak ada tangkapan yang tercatat.")
            } else {
                top.forEachIndexed { index, entry ->
                    append("\n${index + 1}. ${entry.username} - ${entry.score}")
                }
            }
        }

        val server = cachedServer
        if (server != null) {
            broadcastToAll(server, Text.literal(message))
        } else {
            logger.warn("Event selesai tapi tidak ada referensi server untuk broadcast hasil akhir.")
        }

        activeStrategy = null
        registrationManager.resetAndOpen()
        logger.info(
            "Event selesai. Leaderboard TIDAK direset di sini -- tetap bisa dilihat lewat " +
                "/safari leaderboard sampai /safari start berikutnya dijalankan."
        )
    }

    private fun broadcastToAll(server: MinecraftServer, message: Text) {
        for (player in server.playerManager.playerList) {
            player.sendMessage(message, false)
        }
    }
}
