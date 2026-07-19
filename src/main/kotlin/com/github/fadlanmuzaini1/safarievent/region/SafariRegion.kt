package com.github.fadlanmuzaini1.safarievent.region

import com.github.fadlanmuzaini1.safarievent.config.SafariRegionData
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryKey
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * Representasi runtime dari satu region Safari, hasil "kompilasi" [SafariRegionData] (raw config)
 * menjadi bentuk yang siap dipakai untuk pengecekan posisi tiap kali dibutuhkan.
 *
 * Dipisah dari SafariRegionData supaya lapisan config tetap data murni yang gampang di-
 * serialize ke/dari JSON, sementara class ini boleh menyimpan tipe spesifik Minecraft
 * (RegistryKey<World>, Vec3d) yang tidak seharusnya bocor ke ConfigManager.
 */
class SafariRegion private constructor(
    val id: String,
    val worldKey: RegistryKey<World>,
    // Null jika admin belum mengisi queueX/Y/Z di config. SafariAccessGuard WAJIB menangani
    // null ini secara fail-safe (lewati enforcement + log warning), bukan menebak lokasi.
    val queuePoint: Vec3d?,
    private val minX: Int,
    private val maxX: Int,
    private val minY: Int,
    private val maxY: Int,
    private val minZ: Int,
    private val maxZ: Int
) {
    /**
     * Cek apakah sebuah koordinat berada di dalam AABB region ini.
     * Menerima Double karena posisi entity di Minecraft selalu berupa pecahan, bukan int.
     * Kecocokan dunia (world) sengaja TIDAK dicek di sini -- itu tanggung jawab pemanggil
     * (RegionManager), supaya class ini murni geometri dan gampang di-unit-test.
     */
    fun contains(x: Double, y: Double, z: Double): Boolean {
        return x >= minX && x <= maxX &&
            y >= minY && y <= maxY &&
            z >= minZ && z <= maxZ
    }

    companion object {
        /**
         * Membuat SafariRegion dari data config mentah.
         * Mengembalikan null (bukan melempar exception) jika world identifier tidak valid,
         * supaya satu entri region yang salah ketik tidak meng-crash seluruh mod saat startup --
         * RegionManager yang memutuskan bagaimana melaporkan/menangani kegagalan ini.
         */
        fun fromConfig(data: SafariRegionData): SafariRegion? {
            val identifier = Identifier.tryParse(data.world) ?: return null
            val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)

            // Titik antrian hanya valid kalau KETIGA koordinat diisi. Jika cuma sebagian
            // yang diisi (kemungkinan besar salah konfigurasi), perlakukan sebagai belum
            // diatur sama sekali (null) daripada menebak nilai yang hilang dengan 0.
            val queuePoint = if (data.queueX != null && data.queueY != null && data.queueZ != null) {
                Vec3d(data.queueX, data.queueY, data.queueZ)
            } else {
                null
            }

            // minOf/maxOf menormalkan kasus admin menulis minX > maxX (atau min/max lain)
            // secara tidak sengaja di JSON, supaya region tetap valid alih-alih diam-diam
            // menjadi region kosong (tidak pernah true untuk koordinat manapun).
            return SafariRegion(
                id = data.id,
                worldKey = worldKey,
                queuePoint = queuePoint,
                minX = minOf(data.minX, data.maxX),
                maxX = maxOf(data.minX, data.maxX),
                minY = minOf(data.minY, data.maxY),
                maxY = maxOf(data.minY, data.maxY),
                minZ = minOf(data.minZ, data.maxZ),
                maxZ = maxOf(data.minZ, data.maxZ)
            )
        }
    }
}
