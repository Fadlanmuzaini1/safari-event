package com.github.fadlanmuzaini1.safarievent

import com.github.fadlanmuzaini1.safarievent.capture.CatchTracker
import com.github.fadlanmuzaini1.safarievent.command.CommandManager
import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.event.EventManager
import com.github.fadlanmuzaini1.safarievent.event.TimerManager
import com.github.fadlanmuzaini1.safarievent.event.TurnManager
import com.github.fadlanmuzaini1.safarievent.event.scoring.EventScoringStrategyRegistry
import com.github.fadlanmuzaini1.safarievent.hud.HudManager
import com.github.fadlanmuzaini1.safarievent.item.SafariBallSupplier
import com.github.fadlanmuzaini1.safarievent.leaderboard.LeaderboardManager
import com.github.fadlanmuzaini1.safarievent.region.RegionManager
import com.github.fadlanmuzaini1.safarievent.region.SafariAccessGuard
import com.github.fadlanmuzaini1.safarievent.region.SafariTeleporter
import com.github.fadlanmuzaini1.safarievent.session.RegistrationManager
import com.github.fadlanmuzaini1.safarievent.spawn.ForceSpawnManager
import com.github.fadlanmuzaini1.safarievent.spawn.PassiveSpawnScheduler
import com.github.fadlanmuzaini1.safarievent.spawn.WildPokemonCleaner
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

/**
 * Entry point mod. Bootstrap seluruh manager sesuai urutan dependensinya, lalu daftarkan
 * mereka ke lifecycle Fabric (tick, subscriber Cobblemon, koneksi pemain, command). Ini
 * adalah titik penyatuan SEMUA komponen -- setelah ini, seluruh alur fitur
 * (registrasi+grouping langsung -> giliran per-grup manual -> teleport masuk/keluar ->
 * Safari Ball berkala -> capture -> pembersihan Pokemon liar -> leaderboard -> HUD per-grup
 * -> command) sudah tersambung.
 *
 * CATATAN: GroupManager (session/GroupManager.kt) sudah DIHAPUS -- fungsinya sepenuhnya
 * pindah ke RegistrationManager, yang sekarang langsung menempatkan pemain ke grup saat
 * mereka /safari register (bukan lagi dibentuk sesudah pendaftaran ditutup).
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
    lateinit var turnManager: TurnManager
        private set
    lateinit var safariAccessGuard: SafariAccessGuard
        private set
    lateinit var safariTeleporter: SafariTeleporter
        private set
    lateinit var safariBallSupplier: SafariBallSupplier
        private set
    lateinit var forceSpawnManager: ForceSpawnManager
        private set
    lateinit var wildPokemonCleaner: WildPokemonCleaner
        private set
    lateinit var leaderboardManager: LeaderboardManager
        private set
    lateinit var eventManager: EventManager
        private set
    lateinit var catchTracker: CatchTracker
        private set
    lateinit var hudManager: HudManager
        private set
    lateinit var passiveSpawnScheduler: PassiveSpawnScheduler
        private set
    lateinit var commandManager: CommandManager
        private set

    override fun onInitialize() {
        logger.info("Menginisialisasi Safari Event Framework...")

        configManager = ConfigManager(FabricLoader.getInstance().configDir)
        configManager.load()

        regionManager = RegionManager(configManager)
        regionManager.reload()

        registrationManager = RegistrationManager(configManager)
        turnManager = TurnManager(TimerManager())
        safariAccessGuard = SafariAccessGuard(configManager, regionManager, turnManager)
        safariTeleporter = SafariTeleporter(regionManager, turnManager)
        safariBallSupplier = SafariBallSupplier(configManager, turnManager)
        forceSpawnManager = ForceSpawnManager(configManager, regionManager)
        wildPokemonCleaner = WildPokemonCleaner(configManager, regionManager)
        leaderboardManager = LeaderboardManager()

        eventManager = EventManager(
            configManager = configManager,
            registrationManager = registrationManager,
            turnManager = turnManager,
            safariAccessGuard = safariAccessGuard,
            safariTeleporter = safariTeleporter,
            safariBallSupplier = safariBallSupplier,
            wildPokemonCleaner = wildPokemonCleaner,
            leaderboardManager = leaderboardManager
        )

        catchTracker = CatchTracker(regionManager, eventManager)
        catchTracker.register()

        // HudManager sengaja TIDAK menjadi dependency EventManager (menghindari dependensi
        // melingkar) -- di-tick sejajar lewat listener yang sama.
        hudManager = HudManager(configManager, eventManager, turnManager, leaderboardManager)

        passiveSpawnScheduler = PassiveSpawnScheduler(configManager, eventManager, forceSpawnManager)

        commandManager = CommandManager(
            configManager = configManager,
            regionManager = regionManager,
            registrationManager = registrationManager,
            eventManager = eventManager,
            leaderboardManager = leaderboardManager,
            forceSpawnManager = forceSpawnManager
        )
        commandManager.register()

        // SATU-SATUNYA pendaftaran ServerTickEvents untuk seluruh mod. Setiap manager di sini
        // aman dipanggil terus-menerus (early-return sendiri saat tidak ada giliran berjalan),
        // jadi tidak perlu didaftar/dibatalkan berulang kali saat giliran start/stop.
        // SafariBallSupplier TIDAK didaftarkan terpisah di sini -- tick-nya didelegasikan
        // lewat EventManager.tick() karena EventManager sudah memegang dependency ke situ
        // (untuk grantImmediate() saat startGroup()).
        ServerTickEvents.END_SERVER_TICK.register { server ->
            eventManager.tick(server)
            hudManager.tick(server)
            passiveSpawnScheduler.tick(server)
        }

        // Menangani pemain yang sempat OFFLINE tepat di momen krusial teleport (lihat
        // dokumentasi SafariTeleporter.onPlayerJoin() untuk kedua kasusnya).
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            safariTeleporter.onPlayerJoin(handler.player)
        }

        logger.info(
            "Safari Event Framework siap. Region terdaftar: {}, strategy tersedia: {}",
            configManager.current.regions.size,
            EventScoringStrategyRegistry.allIds()
        )
    }
}
