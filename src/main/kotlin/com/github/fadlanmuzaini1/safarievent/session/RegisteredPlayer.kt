package com.github.fadlanmuzaini1.safarievent.session

import java.util.UUID

/**
 * Satu pemain yang sudah mendaftar untuk sesi event berikutnya.
 * `registeredAtMillis` disimpan untuk menjaga urutan pendaftaran (first-come-first-grouped)
 * saat GroupManager membagi pendaftar ke dalam grup.
 */
data class RegisteredPlayer(
    val uuid: UUID,
    val username: String,
    val registeredAtMillis: Long
)
