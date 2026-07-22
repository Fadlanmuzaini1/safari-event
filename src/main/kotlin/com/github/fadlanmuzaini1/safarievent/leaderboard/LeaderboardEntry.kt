package com.github.fadlanmuzaini1.safarievent.leaderboard

import java.util.UUID

/** Snapshot read-only satu entri leaderboard, dipakai untuk tampilan (command, broadcast). */
data class LeaderboardEntry(
    val uuid: UUID,
    val username: String,
    val score: Int
)
