package com.github.fadlanmuzaini1.safarievent.event

/**
 * Countdown generik berbasis tick server (BUKAN Thread.sleep atau java.util.Timer),
 * supaya semua perubahan state tetap terjadi di server thread utama -- aman dipakai
 * bersama API Minecraft/Cobblemon yang tidak thread-safe.
 *
 * Class ini SENGAJA tidak tahu apa-apa soal Fabric ServerTickEvents, event Safari, atau
 * grup -- dia hanya "hitung mundur dari N detik, panggil callback saat 0". Yang mendorong
 * `tick()` untuk dipanggil tiap tick server adalah pemiliknya (TurnManager), didaftarkan
 * satu kali secara global lewat ServerTickEvents di SafariEventMod.
 *
 * Didesain sebagai class biasa (bukan object/singleton) supaya bisa di-instantiate ulang
 * kapan pun durasi baru dibutuhkan (satu instance dipakai bergantian oleh TurnManager
 * untuk tiap giliran grup), tanpa state bocor antar pemakaian.
 */
class TimerManager {

    private var remainingTicksInternal: Long = 0
    private var onFinish: (() -> Unit)? = null

    val isRunning: Boolean
        get() = onFinish != null

    val remainingTicks: Long
        get() = remainingTicksInternal

    val remainingSeconds: Long
        get() = remainingTicksInternal / 20L

    /**
     * Mulai countdown baru dari `durationSeconds`. Jika instance ini sebelumnya sedang
     * menghitung mundur sesuatu yang lain, hitungan lama otomatis digantikan (TANPA memanggil
     * callback lama) -- pemanggil (TurnManager) yang menjamin start() tidak dipanggil dua kali
     * untuk keperluan berbeda secara tidak sengaja.
     */
    fun start(durationSeconds: Long, onFinish: () -> Unit) {
        require(durationSeconds > 0) { "durationSeconds harus > 0, didapat: $durationSeconds" }
        remainingTicksInternal = durationSeconds * 20L
        this.onFinish = onFinish
    }

    /**
     * Dipanggil setiap tick server (lewat ServerTickEvents.END_SERVER_TICK).
     * No-op jika sedang tidak menghitung mundur apapun.
     */
    fun tick() {
        if (!isRunning) return
        remainingTicksInternal -= 1
        if (remainingTicksInternal <= 0) {
            finishNow()
        }
    }

    /** Hentikan countdown TANPA memanggil callback -- dipakai saat digantikan proses lain. */
    fun cancel() {
        onFinish = null
        remainingTicksInternal = 0
    }

    /**
     * Hentikan countdown SEKARANG dan panggil callback-nya segera, seolah waktu sudah habis.
     * PENTING: ini memanggil callback yang SAMA seperti saat waktu habis normal -- jadi kalau
     * callback tersebut berarti "lanjut ke giliran berikutnya" (seperti di TurnManager),
     * forceFinish() akan ikut lanjut ke giliran berikutnya, BUKAN menghentikan seluruh
     * rangkaian giliran. Berguna untuk fitur skip-giliran di masa depan. Untuk menghentikan
     * seluruh rangkaian giliran (mis. /safari stop), pemanggil harus pakai cancel() lalu
     * menjalankan sendiri logic finalisasi totalnya (lihat TurnManager.stopNow()).
     */
    fun forceFinish() {
        if (!isRunning) return
        finishNow()
    }

    private fun finishNow() {
        val callback = onFinish
        onFinish = null
        remainingTicksInternal = 0
        callback?.invoke()
    }
}
