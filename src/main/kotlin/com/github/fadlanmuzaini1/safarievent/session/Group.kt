package com.github.fadlanmuzaini1.safarievent.session

import java.util.UUID

/**
 * Satu grup dalam sebuah sesi event, hasil pembekuan (lock) keanggotaan oleh
 * RegistrationManager.lockAndSnapshot() tepat saat admin men-start giliran grup ini.
 * Immutable setelah dibentuk -- anggota grup TIDAK berubah selama giliran berjalan,
 * walau ada anggota yang disconnect di tengah jalan (TurnManager yang menangani
 * kasus player offline saat gilirannya, bukan dengan mengubah isi grup ini).
 */
data class Group(
    /** 0-based, sekaligus menentukan urutan giliran main (index 0 main duluan). */
    val index: Int,
    val members: Set<UUID>,
    /** Disimpan terpisah dari lookup player online, supaya broadcast/HUD tetap bisa
     *  menampilkan nama walau pemainnya sedang offline saat giliran grup lain berjalan. */
    val memberNames: Map<UUID, String>
) {
    val displayName: String get() = "Grup ${index + 1}"
    val size: Int get() = members.size

    fun contains(uuid: UUID): Boolean = uuid in members
}
