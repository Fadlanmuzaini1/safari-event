package com.github.fadlanmuzaini1.safarievent.leaderboard

import java.util.UUID

/**
 * Leaderboard in-memory untuk SATU sesi event. Tidak ada persistensi ke disk sama sekali,
 * sesuai requirement -- hilang begitu server restart, dan direset ulang tiap kali
 * /safari start dijalankan (BUKAN saat event selesai -- lihat EventManager).
 *
 * Murni "siapa, berapa skornya" -- tidak tahu apa-apa soal grup atau giliran. Skor tetap
 * satu leaderboard gabungan lintas grup (grup hanya menentukan KAPAN boleh masuk area,
 * bukan leaderboard terpisah per grup).
 */
class LeaderboardManager {

    private data class MutableEntry(var username: String, var score: Int)

    private val entries = mutableMapOf<UUID, MutableEntry>()

    /**
     * Tambah skor untuk seorang pemain. Entry baru dibuat otomatis kalau ini kontribusi
     * pertamanya. Username selalu di-refresh (jaga-jaga pemain ganti nama di tengah event).
     * @return skor TOTAL pemain tersebut setelah ditambah.
     */
    fun addScore(uuid: UUID, username: String, amount: Int): Int {
        val entry = entries.getOrPut(uuid) { MutableEntry(username, 0) }
        entry.username = username
        entry.score += amount
        return entry.score
    }

    fun getStats(uuid: UUID): LeaderboardEntry? {
        val entry = entries[uuid] ?: return null
        return LeaderboardEntry(uuid, entry.username, entry.score)
    }

    /** Top-N terurut skor tertinggi -> terendah. */
    fun getTop(n: Int): List<LeaderboardEntry> {
        return entries.entries
            .map { (uuid, e) -> LeaderboardEntry(uuid, e.username, e.score) }
            .sortedByDescending { it.score }
            .take(n)
    }

    fun getAllSortedDescending(): List<LeaderboardEntry> = getTop(entries.size)

    fun reset() {
        entries.clear()
    }

    fun isEmpty(): Boolean = entries.isEmpty()
}
