package com.github.fadlanmuzaini1.safarievent.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Satu-satunya class yang menyentuh disk untuk config/safari-event.json.
 *
 * Tanggung jawabnya SEMPIT dan disengaja begitu: load, reload, dan (saat pertama kali)
 * menuliskan default. Tidak ada logic domain di sini (tidak tahu apa itu "event", "region
 * checking", dst) -- manager lain (RegionManager, EventManager, HudManager) yang membaca
 * `current` dan menerjemahkannya jadi perilaku.
 */
class ConfigManager(private val configDir: Path) {

    private val logger = LoggerFactory.getLogger("SafariEvent/ConfigManager")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile: Path = configDir.resolve("safari-event.json")

    /** Config yang sedang aktif. Selalu ada nilai valid (default jika load gagal). */
    var current: SafariEventConfig = SafariEventConfig()
        private set

    /**
     * Dipanggil sekali saat mod initialize.
     * - Jika file belum ada -> buat file baru berisi default, lalu pakai default itu.
     * - Jika file ada dan valid -> pakai isinya.
     * - Jika file ada tapi JSON rusak -> JANGAN ditimpa (supaya admin bisa perbaiki manual),
     *   fallback ke default untuk sesi berjalan, dan catat error yang jelas di log.
     */
    fun load(): SafariEventConfig {
        if (Files.notExists(configFile)) {
            return createDefaultAndUse("File config belum ada, membuat default di {}")
        }

        return try {
            val json = Files.readString(configFile)
            val parsed = gson.fromJson(json, SafariEventConfig::class.java)
            current = parsed ?: SafariEventConfig()
            logger.info(
                "Config berhasil dimuat dari {} ({} region terdaftar).",
                configFile,
                current.regions.size
            )
            current
        } catch (e: JsonSyntaxException) {
            logger.error(
                "safari-event.json berisi JSON tidak valid. Menggunakan nilai default untuk " +
                    "sesi ini TANPA menimpa file Anda. Perbaiki file lalu jalankan /safari reload.",
                e
            )
            current = SafariEventConfig()
            current
        } catch (e: IOException) {
            logger.error("Gagal membaca safari-event.json, menggunakan nilai default.", e)
            current = SafariEventConfig()
            current
        }
    }

    /**
     * Dipanggil dari /safari reload. Berbeda dari load(): jika file dihapus admin secara
     * sengaja, reload TIDAK diam-diam membuat ulang file tersebut -- itu keputusan admin,
     * bukan sesuatu yang harus "diperbaiki otomatis" oleh mod.
     */
    fun reload(): SafariEventConfig {
        if (Files.notExists(configFile)) {
            logger.warn(
                "safari-event.json tidak ditemukan saat reload. Konfigurasi tetap memakai " +
                    "nilai sebelumnya di memory (tidak dibuat ulang otomatis)."
            )
            return current
        }
        return load()
    }

    private fun createDefaultAndUse(logMessageTemplate: String): SafariEventConfig {
        return try {
            Files.createDirectories(configDir)
            val default = SafariEventConfig()
            Files.writeString(configFile, gson.toJson(default))
            current = default
            logger.info(logMessageTemplate, configFile)
            default
        } catch (e: IOException) {
            logger.error("Gagal membuat file config default di {}, memakai default in-memory saja.", configFile, e)
            current = SafariEventConfig()
            current
        }
    }
}
