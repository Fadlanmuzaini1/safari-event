package com.github.fadlanmuzaini1.safarievent.event.scoring

import org.slf4j.LoggerFactory

/**
 * Tempat semua EventScoringStrategy terdaftar dan dicari berdasarkan id.
 *
 * Ini SATU-SATUNYA tempat yang perlu disentuh saat menambah mode event baru: tambahkan
 * pemanggilan register(...) di init block. Tidak perlu menyentuh EventManager,
 * CommandManager, HudManager, atau CatchTracker sama sekali untuk mode baru.
 */
object EventScoringStrategyRegistry {

    private val logger = LoggerFactory.getLogger("SafariEvent/EventScoringStrategyRegistry")

    // LinkedHashMap supaya urutan pendaftaran konsisten saat ditampilkan (mis. di /safari help
    // masa depan), bukan urutan hash yang tidak terduga.
    private val strategies = linkedMapOf<String, EventScoringStrategy>()

    init {
        register(MostCatchStrategy())
        // Mode event masa depan didaftarkan di sini, tanpa mengubah class lain, contoh:
        // register(MostShinyStrategy())
        // register(MostLegendaryStrategy())
        // register(MostWaterTypeStrategy())
    }

    fun register(strategy: EventScoringStrategy) {
        if (strategies.containsKey(strategy.id)) {
            logger.warn(
                "EventScoringStrategy dengan id '{}' sudah terdaftar, entri lama ditimpa.",
                strategy.id
            )
        }
        strategies[strategy.id] = strategy
    }

    fun get(id: String): EventScoringStrategy? = strategies[id]

    /** Dipakai saat /safari start dijalankan tanpa argumen mode -- default ke Most Catch. */
    fun default(): EventScoringStrategy = strategies.getValue(MostCatchStrategy().id)

    fun allIds(): Set<String> = strategies.keys
}
