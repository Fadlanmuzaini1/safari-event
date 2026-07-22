package com.github.fadlanmuzaini1.safarievent.capture

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent
import com.github.fadlanmuzaini1.safarievent.event.EventManager
import com.github.fadlanmuzaini1.safarievent.region.RegionManager
import org.slf4j.LoggerFactory

/**
 * Subscriber TUNGGAL ke CobblemonEvents.POKEMON_CAPTURED, didaftarkan SEKALI seumur hidup
 * server (bukan tiap kali event start/stop) -- lihat SafariEventMod.
 *
 * KENAPA event ini yang dipakai -- sudah DIVERIFIKASI LANGSUNG dari source Cobblemon 1.7.x
 * (di-clone dan dibaca, bukan tebakan):
 *
 * 1. `CobblemonEvents.POKEMON_CAPTURED` di-post() dari SATU TEMPAT SAJA di seluruh codebase
 *    Cobblemon: `EmptyPokeBallEntity.shakeBall()`, persis di baris tempat proses shake-check
 *    pokéball dinyatakan sukses. Kode itu melakukan `this.owner as? ServerPlayer ?: return`
 *    -- artinya event ini HANYA fire kalau pemilik bola adalah pemain sungguhan yang
 *    melempar bola. Command (`/pokegive` dsb), trade, breeding, dan hadiah NPC semua TIDAK
 *    pernah melewati jalur EmptyPokeBallEntity ini sama sekali, jadi tidak mungkin memicu
 *    event ini secara keliru.
 * 2. Sebelum event di-post(), Cobblemon sendiri sudah mengecek `pokemon.pokemon.isWild()`
 *    (bukan Pokemon yang sudah dimiliki siapapun) dan memanggil `party.add(pokemon.pokemon)`.
 * 3. StatHandler dan AdvancementHandler bawaan Cobblemon (pelacak achievement/statistik
 *    resmi mereka sendiri) SAMA-SAMA subscribe ke event yang persis sama ini untuk
 *    keperluan "berapa Pokemon yang sudah ditangkap pemain" -- mengkonfirmasi ini memang
 *    event semantik "tangkapan sah", bukan sekadar "Pokemon masuk penyimpanan".
 *
 * LAPIS PENGAMAN TAMBAHAN: `party.add()` di Cobblemon mengembalikan false kalau party penuh
 * TANPA otomatis melimpah ke PC (dikonfirmasi dari source PokemonStore.add()), jadi secara
 * teori ada celah sempit di mana event ini fire tapi Pokemon gagal masuk penyimpanan manapun.
 * Untuk menutup celah itu, CatchTracker memverifikasi ulang dengan `pokemon.belongsTo(player)`
 * -- method resmi Cobblemon (Pokemon.kt) yang mengecek Pokemon ini benar tercatat di
 * penyimpanan (party ATAU PC) milik player tsb -- persis sesuai requirement "harus masuk ke
 * Party atau PC".
 *
 * Validasi region/giliran-grup/strategy TIDAK dilakukan di sini -- semua itu didelegasikan
 * ke EventManager.registerCatch(), supaya CatchTracker hanya bertanggung jawab atas SATU hal:
 * "apakah tangkapan ini sah dari sisi Cobblemon, dan terjadi di dalam region Safari".
 */
class CatchTracker(
    private val regionManager: RegionManager,
    private val eventManager: EventManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/CatchTracker")

    /** Dipanggil sekali dari SafariEventMod.onInitialize(). */
    fun register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event -> handleCapture(event) }
        logger.info("CatchTracker terdaftar ke CobblemonEvents.POKEMON_CAPTURED.")
    }

    private fun handleCapture(event: PokemonCapturedEvent) {
        // Guard termurah dulu: kalau event Safari memang tidak sedang berjalan, tidak perlu
        // repot cek region/penyimpanan sama sekali (EventManager.registerCatch() akan
        // menolak juga, tapi mengecek di sini menghindari pekerjaan yang sudah pasti sia-sia).
        if (!eventManager.isRunning) return

        val player = event.player
        val pokemon = event.pokemon

        // Lapis pengaman tambahan di atas jaminan Cobblemon sendiri -- lihat catatan di atas
        // soal party.add() yang bisa gagal diam-diam saat party penuh.
        if (!pokemon.belongsTo(player)) {
            logger.warn(
                "POKEMON_CAPTURED fire untuk {} tapi Pokemon tidak ditemukan di party/PC-nya " +
                    "(kemungkinan party dan PC sama-sama penuh). Tangkapan ini TIDAK dihitung.",
                player.gameProfile.name
            )
            return
        }

        if (!regionManager.isPlayerInsideSafari(player)) return

        val result = eventManager.registerCatch(player.uuid, player.gameProfile.name, pokemon)

        if (result is EventManager.CatchRegistrationResult.Counted) {
            logger.debug(
                "{} menangkap {} di area Safari. Total skor sekarang: {}.",
                player.gameProfile.name,
                pokemon.species.name,
                result.newTotal
            )
        }
    }
}
