package com.github.fadlanmuzaini1.safarievent.event.scoring

import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Kontrak inti sistem extensibility event.
 *
 * Setiap "mode event" (Most Catch, Most Shiny, Most Legendary, Most Water Type, dst) adalah
 * satu implementasi dari interface ini. EventManager HANYA memanggil method-method di sini
 * untuk memutuskan apakah sebuah tangkapan valid dihitung dan berapa poin yang ditambahkan --
 * EventManager tidak pernah tahu detail "apa itu shiny" atau "apa itu tipe Water", dan tidak
 * perlu diubah sama sekali saat mode baru ditambahkan.
 *
 * Menambah mode event baru = membuat class baru yang mengimplementasikan interface ini
 * dan mendaftarkannya ke EventScoringStrategyRegistry. Tidak ada perubahan lain yang
 * diperlukan di EventManager, CatchTracker, HudManager, atau CommandManager.
 */
interface EventScoringStrategy {

    /** ID unik dipakai di config & command (mis. "most_catch", "most_shiny"). Selalu lowercase snake_case. */
    val id: String

    /** Nama yang ditampilkan ke player di broadcast/HUD, mis. "Most Pokémon Caught". */
    val displayName: String

    /**
     * Dipanggil oleh CatchTracker untuk SETIAP tangkapan yang sudah lolos validasi dasar
     * (event sedang berjalan, player di dalam region Safari, sumber capture asli dari
     * CobblemonEvents.POKEMON_CAPTURED -- bukan command/trade/breeding/hadiah NPC).
     *
     * Strategy memutuskan apakah tangkapan spesifik ini relevan untuk mode-nya.
     * Contoh: implementasi Most Shiny mengembalikan false untuk Pokemon yang bukan shiny.
     */
    fun isEligible(pokemon: Pokemon): Boolean

    /**
     * Berapa poin yang ditambahkan ke leaderboard untuk satu tangkapan yang isEligible == true.
     * Dipisah dari isEligible supaya strategy bisa memberi bobot berbeda per tangkapan
     * (mis. mode gabungan yang menghargai legendary 3 poin) tanpa mengubah kontrak inti.
     */
    fun scoreIncrement(pokemon: Pokemon): Int
}
