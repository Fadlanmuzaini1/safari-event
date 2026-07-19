package com.github.fadlanmuzaini1.safarievent.hud

import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.config.HudConfig
import com.github.fadlanmuzaini1.safarievent.event.EventManager
import com.github.fadlanmuzaini1.safarievent.event.TurnManager
import com.github.fadlanmuzaini1.safarievent.leaderboard.LeaderboardManager
import net.minecraft.entity.boss.BossBar
import net.minecraft.entity.boss.ServerBossBar
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Menampilkan status event ke semua pemain online lewat BossBar dan/atau ActionBar (sesuai
 * config), diperbarui BERKALA (config.hud.updateIntervalTicks) -- bukan tiap tick, dan sama
 * sekali tidak lewat chat, supaya tidak spam.
 *
 * CATATAN DESAIN: BossBar dibuat SATU INSTANCE PER PEMAIN (bukan satu bar dipakai bersama
 * semua orang). Alasannya: "Caught: XX" itu personal per pemain (skor masing-masing beda),
 * sementara satu ServerBossBar vanilla hanya bisa menampilkan SATU baris teks yang identik
 * untuk semua viewer-nya -- tidak bisa dipersonalisasi per penonton. Progress bar (0.0-1.0)
 * tetap merepresentasikan sisa waktu giliran saat ini, yang sama untuk semua orang.
 */
class HudManager(
    private val configManager: ConfigManager,
    private val eventManager: EventManager,
    private val turnManager: TurnManager,
    private val leaderboardManager: LeaderboardManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/HudManager")

    private val bossBars = mutableMapOf<UUID, ServerBossBar>()
    private var tickCounter = 0

    /** Dipanggil dari EventManager.tick() (didelegasikan lewat SafariEventMod, sama seperti TurnManager & SafariAccessGuard). */
    fun tick(server: MinecraftServer) {
        if (!eventManager.isRunning) {
            clearAllBossBars()
            tickCounter = 0
            return
        }

        tickCounter++
        val interval = configManager.current.hud.updateIntervalTicks.coerceAtLeast(1)
        if (tickCounter < interval) return
        tickCounter = 0

        updateAll(server)
    }

    private fun updateAll(server: MinecraftServer) {
        val hud = configManager.current.hud
        val group = turnManager.currentGroup()
        val remaining = turnManager.remainingSecondsInTurn()
        val total = turnManager.turnDurationSeconds()
        val percent = if (total > 0) (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
        val timeText = formatTime(remaining)

        for (player in server.playerManager.playerList) {
            val score = leaderboardManager.getStats(player.uuid)?.score ?: 0
            val line = buildString {
                append(hud.bossbar.title)
                if (group != null) append(" | ${group.displayName}")
                append(" | Caught: $score | Time: $timeText")
            }
            val text = Text.literal(line)

            if (hud.bossbar.enabled) {
                val bar = bossBars.getOrPut(player.uuid) { createBossBar(hud) }
                bar.setName(text)
                bar.setPercent(percent)
                // addPlayer aman dipanggil berulang -- no-op kalau player sudah jadi viewer.
                bar.addPlayer(player)
            }

            if (hud.actionbar.enabled) {
                player.sendMessage(text, true)
            }
        }

        // Buang bossbar milik pemain yang sudah disconnect, supaya map tidak menumpuk
        // entry basi selama event berjalan lama dengan banyak pemain keluar-masuk.
        val onlineUuids = server.playerManager.playerList.map { it.uuid }.toSet()
        val disconnected = bossBars.keys - onlineUuids
        for (uuid in disconnected) {
            bossBars.remove(uuid)?.clearPlayers()
        }
    }

    private fun createBossBar(hud: HudConfig): ServerBossBar {
        val color = runCatching { BossBar.Color.valueOf(hud.bossbar.color.uppercase()) }
            .getOrElse {
                logger.warn("Warna BossBar '{}' di config tidak dikenal, fallback ke WHITE.", hud.bossbar.color)
                BossBar.Color.WHITE
            }
        val style = runCatching { BossBar.Style.valueOf(hud.bossbar.style.uppercase()) }
            .getOrElse {
                logger.warn("Style BossBar '{}' di config tidak dikenal, fallback ke PROGRESS.", hud.bossbar.style)
                BossBar.Style.PROGRESS
            }
        return ServerBossBar(Text.literal(hud.bossbar.title), color, style)
    }

    /** Dipanggil saat event tidak/berhenti berjalan -- BossBar tidak boleh "nyangkut" di layar pemain setelah event selesai. */
    private fun clearAllBossBars() {
        if (bossBars.isEmpty()) return
        for (bar in bossBars.values) {
            bar.clearPlayers()
        }
        bossBars.clear()
    }

    private fun formatTime(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}
