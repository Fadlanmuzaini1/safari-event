package com.github.fadlanmuzaini1.safarievent

import com.github.fadlanmuzaini1.safarievent.capture.CatchTracker
import com.github.fadlanmuzaini1.safarievent.command.CommandManager
import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.event.EventManager
import com.github.fadlanmuzaini1.safarievent.event.TimerManager
import com.github.fadlanmuzaini1.safarievent.event.TurnManager
import com.github.fadlanmuzaini1.safarievent.event.scoring.EventScoringStrategyRegistry
import com.github.fadlanmuzaini1.safarievent.hud.HudManager
import com.github.fadlanmuzaini1.safarievent.leaderboard.LeaderboardManager
import com.github.fadlanmuzaini1.safarievent.region.RegionManager
import com.github.fadlanmuzaini1.safarievent.region.SafariAccessGuard
import com.github.fadlanmuzaini1.safarievent.session.GroupManager
import com.github.fadlanmuzaini1.safarievent.session.RegistrationManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

/**
 * Entry point mod. Bootstrap seluruh manager sesuai urutan dependensinya, lalu daftarkan
 * mereka ke lifecycle Fabric (tick, subscriber Cobblemon, command). Ini adalah titik
 * penyatuan SEMUA komponen -- setelah ini, seluruh alur fitur (registrasi -> giliran grup
 * -> capture -> leaderboard -> HUD -> command) sudah lengkap tersambung.
 */
object SafariEventMod : ModInitializer {

    const val MOD_ID = "safari-event"
    val logger = LoggerFactory.getLogger(MOD_ID)

    lateinit var configManager: ConfigManager
        private set
    lateinit var regionManager: RegionManager
        private set
    lateinit var registrationManager: RegistrationManager
        private set
    lateinit var groupManager: GroupManager
        private set
    lateinit var turnManager: TurnManager
        private set
    lateinit var safariAccessGuard: SafariAccessGuard
        private set
    lateinit var leaderboardManager: LeaderboardManager
        private set
    lateinit var eventManager: EventManager
        private set
    lateinit var catchTracker: CatchTracker
        private set
    lateinit var hudManager: HudManager
        private set
    lateinit var commandManager: CommandManager
        private set

    override fun onInitialize() {
        logger.info("Menginisialisasi Safari Event Framework...")

        configManager = ConfigManager(FabricLoader.getInstance().configDir)
        configManager.load()

        regionManager = RegionManager(configManager)
        regionManager.reload()

        registrationManager = RegistrationManager()
        groupManager = GroupManager()
        turnManager = TurnManager(TimerManager())
        safariAccessGuard = SafariAccessGuard(configManager, regionManager, turnManager)
        leaderboardManager = LeaderboardManager()

        eventManager = EventManager(
            configManager = configManager,
            registrationManager = registrationManager,
            groupManager = groupManager,
            turnManager = turnManager,
            safariAccessGuard = safariAccessGuard,
            leaderboardManager = leaderboardManager
        )

        catchTracker = CatchTracker(regionManager, eventManager)
        catchTracker.register()

        // HudManager sengaja TIDAK menjadi dependency EventManager (menghindari dependensi
        // melingkar) -- di-tick sejajar lewat listener yang sama.
        hudManager = HudManager(configManager, eventManager, turnManager, leaderboardManager)

        commandManager = CommandManager(
            configManager = configManager,
            regionManager = regionManager,
            registrationManager = registrationManager,
            eventManager = eventManager,
            leaderboardManager = leaderboardManager
        )
        commandManager.register()

        // SATU-SATUNYA pendaftaran ServerTickEvents untuk seluruh mod. Setiap manager di sini
        // aman dipanggil terus-menerus (early-return sendiri saat tidak ada event berjalan),
        // jadi tidak perlu didaftar/dibatalkan berulang kali saat event start/stop.
        ServerTickEvents.END_SERVER_TICK.register { server ->
            eventManager.tick(server)
            hudManager.tick(server)
        }

        logger.info(
            "Safari Event Framework siap. Region terdaftar: {}, strategy tersedia: {}",
            configManager.current.regions.size,
            EventScoringStrategyRegistry.allIds()
        )
    }
}
