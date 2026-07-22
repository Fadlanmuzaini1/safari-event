package com.github.fadlanmuzaini1.safarievent.region

import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.Heightmap

/**
 * Titik dipakai ulang oleh ForceSpawnManager (spawn Pokemon) DAN SafariTeleporter (teleport
 * pemain) -- keduanya butuh persis logic yang sama: "titik X/Z acak dalam region, cari
 * permukaan tanah di kolom itu, clamp ke rentang Y region". Diekstrak ke sini supaya tidak
 * ada duplikasi antara dua fitur tersebut.
 */
object RegionPositionFinder {

    data class GroundPosition(val world: ServerWorld, val x: Double, val y: Double, val z: Double)

    /**
     * Null kalau world region ini belum/tidak dimuat di server (mis. dimensi custom yang
     * belum ter-generate) -- pemanggil wajib menangani null ini, bukan asumsi selalu berhasil.
     */
    fun randomGroundPosition(server: MinecraftServer, region: SafariRegion): GroundPosition? {
        val world = server.getWorld(region.worldKey) ?: return null

        val (x, z) = region.randomXZ()
        // Cari permukaan tanah di kolom (x, z) supaya tidak muncul melayang di udara atau
        // terjebak di dalam blok, lalu clamp ke rentang Y region -- kalau permukaan tanah
        // ternyata di luar rentang Y yang admin definisikan (mis. gua/gunung), lebih baik
        // tetap di dalam batas region daripada keluar dari area yang dimaksud admin.
        val groundY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)
        val y = groundY.coerceIn(region.minY, region.maxY)

        return GroundPosition(world, x + 0.5, y.toDouble(), z + 0.5)
    }
}
