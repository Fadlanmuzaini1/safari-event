package com.github.fadlanmuzaini1.safarievent.session

import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Mengelola pendaftaran pemain SEKALIGUS penempatan mereka ke grup -- kedua hal ini SENGAJA
 * digabung di satu class karena keduanya satu tindakan atomik: begitu pemain /safari register,
 * mereka LANGSUNG masuk grup tertentu (grup 1 diisi dulu sampai penuh, baru grup 2 dibuka, dst).
 * Ini menggantikan desain lama (GroupManager.formGroups() dipanggil sekali sesudah pendaftaran
 * ditutup) -- GroupManager.kt sudah dihapus karena fungsinya sepenuhnya pindah ke sini.
 *
 * Pendaftaran TIDAK PERNAH otomatis tertutup (tidak ada lagi konsep "ditutup saat event start"),
 * karena admin sekarang men-start GRUP SATU PER SATU secara manual sementara grup lain tetap
 * bisa terisi/terbentuk. Satu grup baru "dibekukan" (tidak bisa lagi kemasukan anggota baru)
 * TEPAT SAAT admin men-start giliran grup tersebut lewat lockAndSnapshot().
 */
class RegistrationManager(private val configManager: ConfigManager) {

    private val logger = LoggerFactory.getLogger("SafariEvent/RegistrationManager")

    /** Representasi mutable satu grup SELAMA fase pendaftaran (sebelum dibekukan). */
    private class LiveGroup(val index: Int) {
        // LinkedHashMap menjaga urutan pendaftaran di dalam grup (first-come-first-served).
        val members = linkedMapOf<UUID, String>()
        var locked = false
    }

    private val groups = mutableListOf<LiveGroup>()
    private val playerGroupIndex = mutableMapOf<UUID, Int>()

    sealed class RegisterResult {
        data class Success(val groupIndex: Int, val groupDisplayName: String) : RegisterResult()
        data object AlreadyRegistered : RegisterResult()
    }

    sealed class UnregisterResult {
        data object Success : UnregisterResult()
        data object NotRegistered : UnregisterResult()
        /** Grup pemain ini sudah pernah di-start admin -- tidak masuk akal keluar di tengah giliran. */
        data object GroupAlreadyLocked : UnregisterResult()
    }

    /**
     * Daftarkan pemain: masuk ke grup TERAKHIR yang belum penuh & belum dibekukan, atau bikin
     * grup baru kalau grup terakhir sudah penuh/sudah dibekukan (giliran sudah pernah di-start).
     */
    fun register(uuid: UUID, username: String): RegisterResult {
        if (playerGroupIndex.containsKey(uuid)) return RegisterResult.AlreadyRegistered

        val maxGroupSize = configManager.current.session.maxGroupSize.coerceAtLeast(1)
        val target = groups.lastOrNull()?.takeIf { !it.locked && it.members.size < maxGroupSize }
            ?: LiveGroup(groups.size).also { groups.add(it) }

        target.members[uuid] = username
        playerGroupIndex[uuid] = target.index

        logger.info("{} mendaftar dan masuk ke {}.", username, displayName(target.index))
        return RegisterResult.Success(target.index, displayName(target.index))
    }

    fun unregister(uuid: UUID): UnregisterResult {
        val index = playerGroupIndex[uuid] ?: return UnregisterResult.NotRegistered
        val group = groups.getOrNull(index) ?: return UnregisterResult.NotRegistered
        if (group.locked) return UnregisterResult.GroupAlreadyLocked

        group.members.remove(uuid)
        playerGroupIndex.remove(uuid)
        return UnregisterResult.Success
    }

    /** Grup tempat pemain ini terdaftar sekarang, atau null kalau belum daftar sama sekali. */
    fun groupOf(uuid: UUID): Int? = playerGroupIndex[uuid]

    fun displayName(index: Int): String = "Grup ${index + 1}"

    /**
     * Terjemahkan label bebas dari command ("group1", "grup1", "Group 1", "1") menjadi index
     * 0-based. Hanya mengambil digit dari string, jadi format apapun yang mengandung angka
     * grup akan berhasil di-parse. Null kalau tidak ada digit atau grup dengan index itu
     * belum pernah terbentuk (belum ada pendaftar sama sekali di grup tsb).
     */
    fun resolveGroupLabel(label: String): Int? {
        val digits = label.filter { it.isDigit() }
        val number = digits.toIntOrNull() ?: return null
        val index = number - 1
        return if (index in groups.indices) index else null
    }

    fun isGroupLocked(index: Int): Boolean = groups.getOrNull(index)?.locked ?: false

    fun groupSize(index: Int): Int = groups.getOrNull(index)?.members?.size ?: 0

    fun totalGroups(): Int = groups.size

    /**
     * Dipanggil EventManager tepat saat admin /safari start <grup> <durasi>. Membekukan
     * keanggotaan grup ini SEKARANG JUGA (pemain baru tidak bisa lagi masuk grup ini walau
     * belum penuh) dan mengembalikan snapshot immutable Group untuk dipakai TurnManager.
     * Null kalau grup tidak ditemukan ATAU grup itu kosong (tidak ada gunanya men-start
     * giliran tanpa pemain).
     */
    fun lockAndSnapshot(index: Int): Group? {
        val group = groups.getOrNull(index) ?: return null
        if (group.members.isEmpty()) return null

        group.locked = true
        return Group(
            index = group.index,
            members = group.members.keys.toSet(),
            memberNames = group.members.toMap()
        )
    }

    /**
     * Reset TOTAL untuk memulai sesi baru dari nol: hapus semua grup & pendaftar. Dipanggil
     * dari /safari reset (lihat EventManager.resetSession()) -- BUKAN dipanggil otomatis lagi
     * saat giliran grup selesai, karena sekarang tidak ada momen tunggal "event dimulai/selesai"
     * yang otomatis terdeteksi (admin men-start grup satu per satu secara manual).
     */
    fun resetAll() {
        groups.clear()
        playerGroupIndex.clear()
        logger.info("Seluruh pendaftaran & grup direset untuk sesi baru.")
    }
}
