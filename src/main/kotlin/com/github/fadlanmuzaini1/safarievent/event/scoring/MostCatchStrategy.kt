package com.github.fadlanmuzaini1.safarievent.event.scoring

import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Mode event utama sesuai requirement: setiap tangkapan valid bernilai 1 poin,
 * tanpa syarat tambahan (semua spesies, shiny atau tidak, semua tipe dihitung sama).
 */
class MostCatchStrategy : EventScoringStrategy {

    override val id: String = "most_catch"
    override val displayName: String = "Most Pokémon Caught"

    override fun isEligible(pokemon: Pokemon): Boolean {
        // Sengaja selalu true: validasi "apakah tangkapan ini sah untuk dihitung sama sekali"
        // sudah selesai dilakukan sebelum sampai ke strategy manapun (lihat CatchTracker).
        // Mode ini tidak menambah syarat lain di atas itu.
        return true
    }

    override fun scoreIncrement(pokemon: Pokemon): Int = 1
}
