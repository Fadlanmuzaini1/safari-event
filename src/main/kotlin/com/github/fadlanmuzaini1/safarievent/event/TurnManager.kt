package com.github.fadlanmuzaini1.safarievent.event

import com.github.fadlanmuzaini1.safarievent.session.Group
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Menjalankan rangkaian giliran grup secara berurutan: Grup 1 main selama N detik,
 * lalu otomatis lanjut ke Grup 2, dst, sampai grup terakhir selesai.
 *
 * Tidak tahu apa-apa soal broadcast, HUD, atau leaderboard -- itu semua reaksi EventManager
 * terhadap callback `onTurnAdvanced`/`onAllTurnsFinished` di bawah. TurnManager murni
 * menjaga: grup mana yang sedang giliran, dan berapa sisa waktunya.
 */
class TurnManager(private val timerManager: TimerManager) {

    private val logger = LoggerFactory.getLogger("SafariEvent/TurnManager")

    private var groups: List<Group> = emptyList()
    private var currentIndex: Int = -1
    private var durationSecondsPerTurn: Long = 0

    private var onTurnAdvanced: ((previous: Group?, next: Group) -> Unit)? = null
    private var onAllTurnsFinished: (() -> Unit)? = null

    /** True selama masih ada giliran yang berjalan (dari begin() sampai grup terakhir selesai). */
    val isActive: Boolean
        get() = currentIndex in groups.indices

    fun currentGroup(): Group? = groups.getOrNull(currentIndex)

    fun remainingSecondsInTurn(): Long = timerManager.remainingSeconds

    /** Durasi total (detik) yang dipakai untuk giliran saat ini. Dipakai HudManager untuk menghitung persentase progress BossBar. */
    fun turnDurationSeconds(): Long = durationSecondsPerTurn

    fun totalGroups(): Int = groups.size

    /** 1-based, untuk ditampilkan ke player (mis. "Giliran 2 dari 5"). 0 jika tidak aktif. */
    fun currentTurnNumber(): Int = if (isActive) currentIndex + 1 else 0

    /**
     * Mulai rangkaian giliran. Dipanggil EventManager tepat setelah GroupManager membentuk
     * grup dari hasil RegistrationManager.closeAndSnapshot().
     *
     * @param onTurnAdvanced dipanggil setiap kali berpindah ke grup berikutnya, TERMASUK
     *        giliran pertama (dengan previous = null). EventManager pakai callback ini untuk
     *        broadcast "Giliran Grup X dimulai / Giliran Grup Y selesai, lanjut Grup Z".
     * @param onAllTurnsFinished dipanggil sekali setelah giliran grup TERAKHIR selesai.
     */
    fun begin(
        groups: List<Group>,
        durationSecondsPerTurn: Long,
        onTurnAdvanced: (previous: Group?, next: Group) -> Unit,
        onAllTurnsFinished: () -> Unit
    ) {
        require(groups.isNotEmpty()) { "TurnManager tidak bisa dimulai tanpa grup." }
        require(durationSecondsPerTurn > 0) { "durationSecondsPerTurn harus > 0." }

        this.groups = groups
        this.currentIndex = -1
        this.durationSecondsPerTurn = durationSecondsPerTurn
        this.onTurnAdvanced = onTurnAdvanced
        this.onAllTurnsFinished = onAllTurnsFinished

        advanceToNextTurn()
    }

    /** Dipanggil dari ServerTickEvents.END_SERVER_TICK (didaftarkan sekali secara global di SafariEventMod). */
    fun tick() {
        timerManager.tick()
    }

    /**
     * Hentikan SELURUH rangkaian giliran sekarang juga (dipakai /safari stop admin).
     * Tidak lanjut ke grup berikutnya walau masih tersisa -- langsung finalize seperti
     * grup terakhir baru saja selesai.
     */
    fun stopNow() {
        if (!isActive) return
        // cancel() dulu (bukan forceFinish()) karena callback timer saat ini berarti
        // "lanjut ke giliran berikutnya" -- kalau dipanggil, malah akan maju satu giliran,
        // bukan berhenti total. finishAllTurns() dipanggil manual sebagai gantinya.
        timerManager.cancel()
        logger.info("Rangkaian giliran dihentikan paksa oleh admin.")
        finishAllTurns()
    }

    /** True hanya jika sedang ada giliran aktif DAN uuid adalah anggota grup yang sedang giliran. */
    fun isPlayerAllowedNow(uuid: UUID): Boolean {
        val current = currentGroup() ?: return false
        return current.contains(uuid)
    }

    private fun advanceToNextTurn() {
        val previous = currentGroup()
        currentIndex += 1
        val next = groups.getOrNull(currentIndex)

        if (next == null) {
            finishAllTurns()
            return
        }

        logger.info(
            "Memulai giliran {} dari {} ({} pemain), durasi {} detik.",
            next.displayName,
            groups.size,
            next.size,
            durationSecondsPerTurn
        )
        onTurnAdvanced?.invoke(previous, next)
        timerManager.start(durationSecondsPerTurn) { advanceToNextTurn() }
    }

    private fun finishAllTurns() {
        val callback = onAllTurnsFinished
        groups = emptyList()
        currentIndex = -1
        onTurnAdvanced = null
        onAllTurnsFinished = null
        logger.info("Seluruh giliran grup selesai.")
        callback?.invoke()
    }
}
