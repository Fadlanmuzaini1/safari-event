package com.github.fadlanmuzaini1.safarievent.event

import com.github.fadlanmuzaini1.safarievent.session.Group
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Menjalankan SATU giliran grup pada satu waktu, dimulai secara MANUAL oleh admin lewat
 * /safari start <grup> <durasi> -- BUKAN auto-lanjut ke grup berikutnya seperti desain
 * sebelumnya. Begitu giliran selesai (waktu habis atau dipaksa admin), TurnManager kembali
 * idle dan MENUNGGU admin men-start grup berikutnya secara eksplisit.
 *
 * Tidak tahu apa-apa soal broadcast, HUD, atau leaderboard -- itu reaksi EventManager
 * terhadap callback `onFinish` di bawah.
 */
class TurnManager(private val timerManager: TimerManager) {

    private val logger = LoggerFactory.getLogger("SafariEvent/TurnManager")

    private var activeGroup: Group? = null
    private var durationSeconds: Long = 0
    private var onFinish: ((Group) -> Unit)? = null

    /** True selama masih ada SATU giliran grup yang berjalan. */
    val isActive: Boolean
        get() = activeGroup != null

    fun currentGroup(): Group? = activeGroup

    fun remainingSecondsInTurn(): Long = timerManager.remainingSeconds

    /** Durasi total (detik) giliran yang sedang berjalan. Dipakai HudManager untuk menghitung persentase progress BossBar. */
    fun turnDurationSeconds(): Long = durationSeconds

    /**
     * Mulai giliran untuk SATU grup. Dipanggil EventManager dari handler /safari start.
     * Melempar IllegalStateException kalau sudah ada giliran aktif -- EventManager WAJIB
     * mengecek `isActive` dulu sebelum memanggil ini (lihat EventManager.startGroup()).
     */
    fun startTurn(group: Group, durationSeconds: Long, onFinish: (Group) -> Unit) {
        check(!isActive) { "TurnManager sudah punya giliran aktif, tidak bisa mulai giliran baru." }
        require(durationSeconds > 0) { "durationSeconds harus > 0." }

        activeGroup = group
        this.durationSeconds = durationSeconds
        this.onFinish = onFinish

        logger.info("Giliran {} dimulai, {} pemain, {} detik.", group.displayName, group.size, durationSeconds)
        timerManager.start(durationSeconds) { finishNow() }
    }

    /** Dipanggil dari ServerTickEvents.END_SERVER_TICK (didelegasikan lewat EventManager.tick()). */
    fun tick() {
        timerManager.tick()
    }

    /** Paksa giliran yang sedang berjalan selesai SEKARANG (dipakai /safari stop). No-op kalau tidak ada giliran aktif. */
    fun stopNow() {
        if (!isActive) return
        timerManager.cancel()
        finishNow()
    }

    /** True hanya jika ada giliran aktif DAN uuid adalah anggota grup yang sedang giliran. */
    fun isPlayerAllowedNow(uuid: UUID): Boolean = activeGroup?.contains(uuid) == true

    private fun finishNow() {
        val group = activeGroup ?: return
        val callback = onFinish
        activeGroup = null
        onFinish = null
        durationSeconds = 0
        logger.info("Giliran {} selesai.", group.displayName)
        callback?.invoke(group)
    }
}
