package com.github.fadlanmuzaini1.safarievent.command

import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.event.EventManager
import com.github.fadlanmuzaini1.safarievent.event.scoring.EventScoringStrategyRegistry
import com.github.fadlanmuzaini1.safarievent.leaderboard.LeaderboardManager
import com.github.fadlanmuzaini1.safarievent.region.RegionManager
import com.github.fadlanmuzaini1.safarievent.session.RegistrationManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Registrasi seluruh subcommand /safari. Class ini SENGAJA tidak berisi logic domain apapun --
 * setiap handler cuma parsing argumen lalu mendelegasikan ke manager yang relevan
 * (RegistrationManager, EventManager, LeaderboardManager, ConfigManager, RegionManager),
 * lalu menerjemahkan hasilnya (sealed class result) jadi pesan untuk pengirim command.
 *
 * CATATAN: /safari register, /safari unregister, dan /safari reset TIDAK ada di daftar
 * command awal -- ditambahkan belakangan sebagai konsekuensi wajar dari fitur pendaftaran,
 * giliran grup manual per-grup, dan kebutuhan mengakhiri sesi secara eksplisit.
 */
class CommandManager(
    private val configManager: ConfigManager,
    private val regionManager: RegionManager,
    private val registrationManager: RegistrationManager,
    private val eventManager: EventManager,
    private val leaderboardManager: LeaderboardManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/CommandManager")

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            registerCommands(dispatcher)
        }
    }

    private fun registerCommands(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("safari")
                .then(literal("register").executes(::executeRegister))
                .then(literal("unregister").executes(::executeUnregister))
                .then(
                    literal("start")
                        .requires { it.hasPermissionLevel(2) }
                        .then(
                            // Contoh: /safari start group1 60 [mode]. Label grup fleksibel --
                            // "group1", "grup1", "1" semua diterima (lihat
                            // RegistrationManager.resolveGroupLabel).
                            argument("group", StringArgumentType.word())
                                .then(
                                    argument("duration", StringArgumentType.word())
                                        .executes { ctx -> executeStart(ctx, null) }
                                        .then(
                                            argument("mode", StringArgumentType.word())
                                                .executes { ctx ->
                                                    executeStart(ctx, StringArgumentType.getString(ctx, "mode"))
                                                }
                                        )
                                )
                        )
                )
                .then(
                    literal("stop")
                        .requires { it.hasPermissionLevel(2) }
                        .executes(::executeStop)
                )
                .then(
                    literal("reset")
                        .requires { it.hasPermissionLevel(2) }
                        .executes(::executeReset)
                )
                .then(
                    literal("reload")
                        .requires { it.hasPermissionLevel(2) }
                        .executes(::executeReload)
                )
                .then(literal("leaderboard").executes(::executeLeaderboard))
                .then(
                    literal("stats")
                        .executes(::executeStatsSelf)
                        .then(
                            argument("player", StringArgumentType.word())
                                .executes { ctx ->
                                    executeStatsOther(ctx, StringArgumentType.getString(ctx, "player"))
                                }
                        )
                )
        )
        logger.info("Command /safari terdaftar.")
    }

    // ---------------------------------------------------------------------
    // register / unregister
    // ---------------------------------------------------------------------

    private fun executeRegister(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player
        if (player == null) {
            ctx.source.sendError(Text.literal("Hanya pemain yang bisa mendaftar."))
            return 0
        }

        return when (val result = registrationManager.register(player.uuid, player.gameProfile.name)) {
            is RegistrationManager.RegisterResult.Success -> {
                ctx.source.sendFeedback(
                    { Text.literal("Kamu terdaftar di ${result.groupDisplayName}.") },
                    false
                )
                1
            }
            RegistrationManager.RegisterResult.AlreadyRegistered -> {
                val index = registrationManager.groupOf(player.uuid)
                val groupText = index?.let { " (${registrationManager.displayName(it)})" } ?: ""
                ctx.source.sendFeedback({ Text.literal("Kamu sudah terdaftar$groupText.") }, false)
                0
            }
        }
    }

    private fun executeUnregister(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player
        if (player == null) {
            ctx.source.sendError(Text.literal("Hanya pemain yang bisa membatalkan pendaftaran."))
            return 0
        }

        return when (registrationManager.unregister(player.uuid)) {
            RegistrationManager.UnregisterResult.Success -> {
                ctx.source.sendFeedback({ Text.literal("Pendaftaranmu dibatalkan.") }, false)
                1
            }
            RegistrationManager.UnregisterResult.NotRegistered -> {
                ctx.source.sendFeedback({ Text.literal("Kamu belum terdaftar.") }, false)
                0
            }
            RegistrationManager.UnregisterResult.GroupAlreadyLocked -> {
                ctx.source.sendError(
                    Text.literal("Grupmu sudah pernah di-start admin, tidak bisa membatalkan pendaftaran sekarang.")
                )
                0
            }
        }
    }

    // ---------------------------------------------------------------------
    // start / stop / reset / reload
    // ---------------------------------------------------------------------

    private fun executeStart(ctx: CommandContext<ServerCommandSource>, modeArg: String?): Int {
        val groupLabel = StringArgumentType.getString(ctx, "group")
        val durationRaw = StringArgumentType.getString(ctx, "duration")
        val seconds = parseDuration(durationRaw)
        if (seconds == null || seconds <= 0) {
            ctx.source.sendError(
                Text.literal("Durasi tidak valid: '$durationRaw'. Contoh yang benar: 30m, 1800, atau 2h.")
            )
            return 0
        }

        val server = ctx.source.server
        return when (val result = eventManager.startGroup(server, groupLabel, seconds, modeArg)) {
            is EventManager.StartGroupResult.Success -> {
                ctx.source.sendFeedback(
                    {
                        Text.literal(
                            "${result.groupDisplayName} dimulai: ${result.memberNames.joinToString(", ")}."
                        )
                    },
                    true
                )
                1
            }
            EventManager.StartGroupResult.AnotherGroupRunning -> {
                ctx.source.sendError(Text.literal("Masih ada giliran grup lain yang sedang berjalan. Tunggu selesai atau /safari stop dulu."))
                0
            }
            EventManager.StartGroupResult.GroupNotFound -> {
                ctx.source.sendError(
                    Text.literal("Grup '$groupLabel' tidak ditemukan. Belum ada pendaftar untuk grup itu.")
                )
                0
            }
            EventManager.StartGroupResult.GroupAlreadyStarted -> {
                ctx.source.sendError(Text.literal("Grup '$groupLabel' sudah pernah di-start sebelumnya."))
                0
            }
            EventManager.StartGroupResult.GroupEmpty -> {
                ctx.source.sendError(Text.literal("Grup '$groupLabel' kosong, tidak ada yang bisa di-start."))
                0
            }
            is EventManager.StartGroupResult.UnknownStrategy -> {
                ctx.source.sendError(
                    Text.literal(
                        "Mode '${result.id}' tidak dikenal. Mode tersedia: " +
                            EventScoringStrategyRegistry.allIds().joinToString(", ")
                    )
                )
                0
            }
        }
    }

    private fun executeStop(ctx: CommandContext<ServerCommandSource>): Int {
        return when (eventManager.stop(ctx.source.server)) {
            EventManager.StopResult.Success -> {
                ctx.source.sendFeedback({ Text.literal("Giliran yang sedang berjalan dihentikan paksa oleh admin.") }, true)
                1
            }
            EventManager.StopResult.NotRunning -> {
                ctx.source.sendError(Text.literal("Tidak ada giliran grup yang sedang berjalan."))
                0
            }
        }
    }

    private fun executeReset(ctx: CommandContext<ServerCommandSource>): Int {
        return when (eventManager.resetSession(ctx.source.server)) {
            EventManager.ResetResult.Success -> {
                ctx.source.sendFeedback({ Text.literal("Sesi Safari Event direset. Pendaftaran dibuka untuk sesi baru.") }, true)
                1
            }
            EventManager.ResetResult.TurnStillActive -> {
                ctx.source.sendError(Text.literal("Masih ada giliran grup yang berjalan. /safari stop dulu sebelum reset."))
                0
            }
        }
    }

    private fun executeReload(ctx: CommandContext<ServerCommandSource>): Int {
        configManager.reload()
        regionManager.reload()
        ctx.source.sendFeedback({ Text.literal("Config Safari Event berhasil dimuat ulang.") }, true)
        return 1
    }

    // ---------------------------------------------------------------------
    // leaderboard / stats
    // ---------------------------------------------------------------------

    private fun executeLeaderboard(ctx: CommandContext<ServerCommandSource>): Int {
        val top = leaderboardManager.getTop(10)
        if (top.isEmpty()) {
            ctx.source.sendFeedback({ Text.literal("Leaderboard masih kosong.") }, false)
            return 1
        }

        val message = buildString {
            append("===== Safari Leaderboard =====")
            top.forEachIndexed { index, entry ->
                append("\n${index + 1}. ${entry.username} - ${entry.score}")
            }
        }
        ctx.source.sendFeedback({ Text.literal(message) }, false)
        return 1
    }

    private fun executeStatsSelf(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player
        if (player == null) {
            ctx.source.sendError(Text.literal("Sertakan nama pemain: /safari stats <player>."))
            return 0
        }
        return sendStats(ctx, player.uuid, player.gameProfile.name)
    }

    private fun executeStatsOther(ctx: CommandContext<ServerCommandSource>, username: String): Int {
        val entry = leaderboardManager.getAllSortedDescending()
            .firstOrNull { it.username.equals(username, ignoreCase = true) }

        if (entry == null) {
            ctx.source.sendFeedback({ Text.literal("Tidak ada data tangkapan untuk pemain '$username'.") }, false)
            return 0
        }
        return sendStats(ctx, entry.uuid, entry.username)
    }

    private fun sendStats(ctx: CommandContext<ServerCommandSource>, uuid: UUID, username: String): Int {
        val score = leaderboardManager.getStats(uuid)?.score ?: 0
        ctx.source.sendFeedback({ Text.literal("$username: $score tangkapan.") }, false)
        return 1
    }

    // ---------------------------------------------------------------------
    // util
    // ---------------------------------------------------------------------

    /**
     * Parse durasi fleksibel:
     * - Angka polos dianggap DETIK, mis. "1800".
     * - Angka + akhiran 's'/'m'/'h' (detik/menit/jam), mis. "30m", "90s", "2h".
     * Null kalau format tidak dikenali sama sekali.
     */
    private fun parseDuration(input: String): Long? {
        if (input.isEmpty()) return null

        if (input.all { it.isDigit() }) {
            return input.toLongOrNull()
        }

        val suffix = input.last().lowercaseChar()
        val numberPart = input.dropLast(1)
        val amount = numberPart.toLongOrNull() ?: return null

        return when (suffix) {
            's' -> amount
            'm' -> amount * 60
            'h' -> amount * 3600
            else -> null
        }
    }
}
