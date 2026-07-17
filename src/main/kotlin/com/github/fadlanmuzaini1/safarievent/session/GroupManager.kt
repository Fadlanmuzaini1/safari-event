package com.github.fadlanmuzaini1.safarievent.session

import org.slf4j.LoggerFactory

/**
 * Membagi snapshot pendaftar (dari RegistrationManager.closeAndSnapshot()) menjadi
 * beberapa Group berukuran maksimal tetap (maxGroupSize dari SessionConfig).
 *
 * Pembagian murni berurutan sesuai urutan pendaftaran (first-come-first-grouped),
 * SENGAJA tidak diacak -- supaya perilakunya predictable dan gampang dijelaskan ke
 * player ("kamu di grup sekian karena daftar duluan").
 */
class GroupManager {

    private val logger = LoggerFactory.getLogger("SafariEvent/GroupManager")

    /**
     * @param registrants snapshot pendaftar terurut dari RegistrationManager.closeAndSnapshot()
     * @param maxGroupSize ukuran maksimal 1 grup (SessionConfig.maxGroupSize)
     * @return list grup terurut sesuai urutan giliran main (index 0 duluan). Kosong jika
     *         tidak ada pendaftar sama sekali -- EventManager yang memutuskan apa artinya
     *         itu (mis. batalkan /safari start dan beri tahu admin).
     */
    fun formGroups(registrants: List<RegisteredPlayer>, maxGroupSize: Int): List<Group> {
        if (registrants.isEmpty()) {
            logger.warn("Tidak ada pendaftar sama sekali, tidak ada grup yang dibentuk.")
            return emptyList()
        }

        // Jaga-jaga config diisi 0/negatif secara tidak sengaja -- daripada chunked() error
        // atau menghasilkan perilaku aneh, fallback ke satu grup besar berisi semua pendaftar
        // dan catat warning yang jelas supaya admin tahu config-nya perlu diperbaiki.
        val safeGroupSize = if (maxGroupSize > 0) {
            maxGroupSize
        } else {
            logger.warn(
                "session.maxGroupSize di config bernilai {} (tidak valid, harus > 0). " +
                    "Semua {} pendaftar dimasukkan ke dalam satu grup sebagai fallback.",
                maxGroupSize,
                registrants.size
            )
            registrants.size
        }

        val groups = registrants
            .chunked(safeGroupSize)
            .mapIndexed { index, chunk ->
                Group(
                    index = index,
                    members = chunk.map { it.uuid }.toSet(),
                    memberNames = chunk.associate { it.uuid to it.username }
                )
            }

        logger.info(
            "{} pendaftar dibagi menjadi {} grup (maksimal {} pemain/grup).",
            registrants.size,
            groups.size,
            safeGroupSize
        )

        return groups
    }
}
