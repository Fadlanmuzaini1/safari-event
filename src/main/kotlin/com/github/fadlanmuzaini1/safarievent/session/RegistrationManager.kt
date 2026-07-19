package com.github.fadlanmuzaini1.safarievent.session

import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Mengelola pendaftaran pemain untuk sesi event BERIKUTNYA.
 *
 * Hanya tahu soal "siapa sudah daftar, dalam urutan apa, dan apakah pendaftaran sedang
 * dibuka" -- tidak tahu apa-apa soal grup, giliran, atau region. GroupManager yang nanti
 * mengonsumsi hasil `closeAndSnapshot()` untuk membentuk grup.
 *
 * Dipakai sebagai class biasa (bukan object) supaya gampang di-instantiate ulang jika
 * suatu saat dibutuhkan lebih dari satu instance (mis. testing), diinject dari SafariEventMod.
 */
class RegistrationManager {

    private val logger = LoggerFactory.getLogger("SafariEvent/RegistrationManager")

    // LinkedHashMap menjaga urutan pendaftaran (first-come-first-grouped) -- penting untuk
    // GroupManager supaya pembagian grup deterministik & adil, bukan urutan hash acak.
    private val registrants = linkedMapOf<UUID, RegisteredPlayer>()

    /** True saat event belum berjalan (IDLE). False otomatis begitu /safari start dieksekusi. */
    var isOpen: Boolean = true
        private set

    sealed class RegisterResult {
        data object Success : RegisterResult()
        data object AlreadyRegistered : RegisterResult()
        data object RegistrationClosed : RegisterResult()
    }

    sealed class UnregisterResult {
        data object Success : UnregisterResult()
        data object NotRegistered : UnregisterResult()
        data object RegistrationClosed : UnregisterResult()
    }

    fun register(uuid: UUID, username: String): RegisterResult {
        if (!isOpen) return RegisterResult.RegistrationClosed
        if (registrants.containsKey(uuid)) return RegisterResult.AlreadyRegistered

        registrants[uuid] = RegisteredPlayer(
            uuid = uuid,
            username = username,
            registeredAtMillis = System.currentTimeMillis()
        )
        return RegisterResult.Success
    }

    fun unregister(uuid: UUID): UnregisterResult {
        if (!isOpen) return UnregisterResult.RegistrationClosed
        return if (registrants.remove(uuid) != null) {
            UnregisterResult.Success
        } else {
            UnregisterResult.NotRegistered
        }
    }

    fun isRegistered(uuid: UUID): Boolean = registrants.containsKey(uuid)

    fun count(): Int = registrants.size

    /** Salinan read-only, urutan pendaftaran terjaga. Untuk keperluan tampilan (mis. command info). */
    fun list(): List<RegisteredPlayer> = registrants.values.toList()

    /**
     * Dipanggil EventManager TEPAT SEBELUM membentuk grup (di dalam /safari start).
     * Menutup pendaftaran (supaya tidak ada pemain baru masuk di tengah event berjalan)
     * dan mengembalikan snapshot terurut untuk dikonsumsi GroupManager.
     */
    fun closeAndSnapshot(): List<RegisteredPlayer> {
        isOpen = false
        val snapshot = registrants.values.toList()
        logger.info("Pendaftaran ditutup. {} pemain akan dibagi ke dalam grup.", snapshot.size)
        return snapshot
    }

    /**
     * Dipanggil EventManager saat event selesai ATAU dibatalkan admin: bersihkan daftar
     * lama dan buka lagi untuk sesi berikutnya.
     */
    fun resetAndOpen() {
        registrants.clear()
        isOpen = true
        logger.info("Pendaftaran event dibuka kembali untuk sesi berikutnya.")
    }
}
