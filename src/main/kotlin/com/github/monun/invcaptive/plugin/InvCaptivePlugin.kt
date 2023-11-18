package com.github.monun.invcaptive.plugin

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Firework
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.*
import org.bukkit.event.world.WorldSaveEvent
import org.bukkit.plugin.java.JavaPlugin
import xyz.icetang.lib.kommand.kommand
import java.io.File
import java.util.*
import kotlin.random.Random.Default.nextLong

/**
 * @author Noonmaru
 */
class InvCaptivePlugin : JavaPlugin(), Listener {

    private lateinit var slotsByType: EnumMap<Material, Int>

    override fun onEnable() {
        val seed = loadSeed()

        server.pluginManager.registerEvents(this, this)
        loadInventory()

        val list = Material.entries.filter { it.isBlock && !it.isAir }.shuffled(Random(seed))
        val count = 9 * 4 + 5

        val map = EnumMap<Material, Int>(Material::class.java)

        for (i in 0 until count) {
            map[list[i]] = i
        }

        println(map)

        this.slotsByType = map

        kommand {
            register("invcaptive") {
                requires { sender.hasPermission("invcaptive.command") }
                executes { InvCaptive.captive() }
            }
        }

        for (player in Bukkit.getOnlinePlayers()) {
            InvCaptive.patch(player)
        }
    }

    override fun onDisable() {
        save()
    }

    private fun loadSeed(): Long {
        val folder = dataFolder.also { it.mkdirs() }
        val file = File(folder, "config.yml")
        val config = YamlConfiguration()

        if (file.exists())
            config.load(file)

        if (config.contains("seed"))
            return config.getLong("seed")

        val seed = nextLong()
        config.set("seed", seed)
        config.save(file)

        return seed
    }

    private fun loadInventory() {
        val file = File(dataFolder, "inventory.yml").also { if (!it.exists()) return }
        val yaml = YamlConfiguration.loadConfiguration(file)
        InvCaptive.load(yaml)
    }

    private fun save() {
        val yaml = InvCaptive.save()
        val dataFolder = dataFolder.also { it.mkdirs() }
        val file = File(dataFolder, "inventory.yml")

        yaml.save(file)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        InvCaptive.patch(event.player)
    }

    @Suppress("UNUSED_PARAMETER")
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        InvCaptive.save()
    }

    @Suppress("UNUSED_PARAMETER")
    @EventHandler
    fun onWorldSave(event: WorldSaveEvent) {
        save()
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        event.currentItem?.let {
            if (it.type == Material.BARRIER) {
                event.isCancelled = true
                return
            }
        }

        if (event.action == InventoryAction.HOTBAR_SWAP) {
            val item = event.whoClicked.inventory.getItem(event.hotbarButton)
            if (item != null && item.type == Material.BARRIER) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onDropItem(event: PlayerDropItemEvent) {
        if (event.itemDrop.itemStack.type == Material.BARRIER) {
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        slotsByType[event.block.type]?.let {
            println(it)
            if (InvCaptive.release(it)) {
                for (player in Bukkit.getOnlinePlayers()) {
                    player.world.spawn(player.location, Firework::class.java)
                }

                Bukkit.broadcast(
                    Component.text(event.player.name)
                        .hoverEvent(event.player.asHoverEvent())
                        .color(NamedTextColor.RED)
                        .append(Component.text("님이 "))
                        .append(Component.translatable(event.block.translationKey()).color(NamedTextColor.GOLD))
                        .append(Component.text("블록을 파괴하여 인벤토리 잠금이 한칸 해제되었습니다!"))
                )

//                Bukkit.broadcastMessage(
//                    "${ChatColor.RED}${event.player.name}${ChatColor.RESET}님이 ${ChatColor.GOLD}${
//                        event.block.translationKey.removePrefix("block.minecraft.")
//                    } ${ChatColor.RESET}블록을 파괴하여 인벤토리 잠금이 한칸 해제되었습니다!"
//                )
            }
        }
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.item?.type == Material.BARRIER) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onItemSpawn(event: ItemSpawnEvent) {
        val item = event.entity.itemStack

        if (item.type == Material.BARRIER) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        if (event.offHandItem?.type == Material.BARRIER || event.mainHandItem?.type == Material.BARRIER) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        event.keepInventory = true

        val drops = event.drops
        drops.clear()

        val inventory = event.entity.inventory
        for (i in 0 until inventory.count()) {
            inventory.getItem(i)?.let { itemStack ->
                if (itemStack.type != Material.BARRIER) {
                    event.drops += itemStack
                    inventory.setItem(i, null)
                }
            }
        }
    }
}