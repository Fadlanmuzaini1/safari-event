package com.github.fadlanmuzaini1.safarievent.item

import com.cobblemon.mod.common.CobblemonItems
import com.github.fadlanmuzaini1.safarievent.config.ConfigManager
import com.github.fadlanmuzaini1.safarievent.event.TurnManager
import com.github.fadlanmuzaini1.safarievent.session.Group
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

/**
 * Memberi Safari Ball secara BERKALA ke semua anggota grup yang sedang giliran -- default
 * 20 buah tiap 20 detik (BUKAN 1 buah tiap 1 detik, sesuai permintaan eksplisit).
 *
 * Pemberian PERTAMA terjadi tepat saat giliran di-start (dipanggil langsung dari
 * `EventManager.startGroup()` lewat `grantImmediate()`), supaya anggota grup tidak menunggu
 * `intervalTicks` pertama tanpa Safari Ball sama sekali. Sesudah itu, `tick()` mengulang
 * pemberian setiap `intervalTicks` selama giliran masih berjalan.
 *
 * `CobblemonItems.SAFARI_BALL` diverifikasi langsung dari source resmi Cobblemon
 * (`CobblemonItems.kt`) sebagai konstanta publik -- bukan identifier string yang ditebak.
 */
class SafariBallSupplier(
    private val configManager: ConfigManager,
    private val turnManager: TurnManager
) {
    private val logger = LoggerFactory.getLogger("SafariEvent/SafariBallSupplier")

    private var tickCounter = 0

    /** Dipanggil EventManager.startGroup() SEKALI tepat saat giliran dimulai. */
    fun grantImmediate(server: MinecraftServer, group: Group) {
        if (!configManager.current.safariBallSupply.enabled) return
        giveToGroup(server, group)
    }

    /**
     * Dipanggil dari EventManager.tick() (EventManager sudah memegang dependency ke class
     * ini untuk keperluan grantImmediate(), jadi tick internal juga didelegasikan lewat sana
     * -- tidak perlu pendaftaran ServerTickEvents terpisah).
     */
    fun tick(server: MinecraftServer) {
        val config = configManager.current.safariBallSupply
        if (!config.enabled) return

        val group = turnManager.currentGroup()
        if (group == null) {
            tickCounter = 0
            return
        }

        tickCounter++
        val interval = config.intervalTicks.coerceAtLeast(1)
        if (tickCounter < interval) return
        tickCounter = 0

        giveToGroup(server, group)
    }

    private fun giveToGroup(server: MinecraftServer, group: Group) {
        val amount = configManager.current.safariBallSupply.amount.coerceAtLeast(1)
        var givenTo = 0
        for (uuid in group.members) {
            val player = server.playerManager.getPlayer(uuid) ?: continue // offline, dilewati -- tidak ada mekanisme "titip" seperti teleport
            player.giveItemStack(ItemStack(CobblemonItems.SAFARI_BALL, amount))
            givenTo++
        }
        logger.debug("Memberi {} Safari Ball ke {} pemain online ({}).", amount, givenTo, group.displayName)
    }
}
