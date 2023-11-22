package com.github.monun.invcaptive.plugin

import com.google.common.collect.ImmutableList
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.NonNullList
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.craftbukkit.v1_20_R2.entity.CraftPlayer
import org.bukkit.craftbukkit.v1_20_R2.inventory.CraftItemStack
import org.bukkit.entity.Player
import kotlin.math.min

object InvCaptive {
    val triedBlocks = mutableSetOf<Material>()
    val pinnedBlocks = mutableSetOf<Material>()

    private val items: NonNullList<ItemStack>
    private val armor: NonNullList<ItemStack>
    private val offhand: NonNullList<ItemStack>

    private val contents: List<NonNullList<ItemStack>>

    init {
        val inv = net.minecraft.world.entity.player.Inventory(null)

        items = inv.items
        armor = inv.armor
        offhand = inv.offhand
        contents = ImmutableList.of(items, armor, offhand)
    }

    private const val ITEMS = "items"
    private const val ARMOR = "armor"
    private const val OFFHAND = "offhand"

    fun loadProgressFile(yaml: YamlConfiguration) {
        triedBlocks.clear()
        triedBlocks += yaml.getStringList("items").map { Material.valueOf(it) }
        pinnedBlocks.clear()
        pinnedBlocks += yaml.getStringList("pinnedItems").map { Material.valueOf(it) }
    }

    fun load(yaml: YamlConfiguration) {
        yaml.loadItemStackList(ITEMS, items)
        yaml.loadItemStackList(ARMOR, armor)
        yaml.loadItemStackList(OFFHAND, offhand)
    }

    @Suppress("UNCHECKED_CAST")
    private fun ConfigurationSection.loadItemStackList(name: String, list: NonNullList<ItemStack>) {
        val map = getMapList(name)
        val items = map.map { CraftItemStack.asNMSCopy(CraftItemStack.deserialize(it as Map<String, Any>)) }

        for (i in 0 until min(list.count(), items.count())) {
            list[i] = items[i]
        }
    }

    fun save(): YamlConfiguration {
        val yaml = YamlConfiguration()

        yaml.setItemStackList(ITEMS, items)
        yaml.setItemStackList(ARMOR, armor)
        yaml.setItemStackList(OFFHAND, offhand)

        return yaml
    }

    private fun ConfigurationSection.setItemStackList(name: String, list: NonNullList<ItemStack>) {
        set(name, list.map { CraftItemStack.asCraftMirror(it).serialize() })
    }

    fun patch(player: Player) {
        val nmsPlayer = (player as CraftPlayer).handle

        val playerInv = nmsPlayer.inventory

        playerInv.setField("i", items)
        playerInv.setField("j", armor)
        playerInv.setField("k", offhand)
        playerInv.setField("o", contents)
    }

    private fun Any.setField(name: String, value: Any) {
        val field = javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }

        field.set(this, value)
    }

    fun captive() {
        val item = ItemStack(Blocks.BARRIER)
        items.replaceAll { item.copy() }
        items[0] = ItemStack(Blocks.AIR)
        armor.replaceAll { item.copy() }
        offhand.replaceAll { item.copy() }

        for (player in Bukkit.getOnlinePlayers()) {
            player.updateInventory()
        }
    }

    private val releaseSlotItem = CraftItemStack.asNMSCopy(org.bukkit.inventory.ItemStack(Material.GOLDEN_APPLE).apply {
        itemMeta = itemMeta!!.apply {
            displayName(Component.text("새로운 인벤토리").color(NamedTextColor.GOLD))
        }
    })

    fun release(slot: Int): Boolean {
        return when {
            slot < 36 -> {
                items.replaceBarrier(slot, releaseSlotItem)
            }

            slot < 40 -> {
                armor.replaceBarrier(slot - 36, releaseSlotItem)
            }

            else -> {
                offhand.replaceBarrier(slot - 40, releaseSlotItem)
            }
        }
    }

    private fun NonNullList<ItemStack>.replaceBarrier(index: Int, item: ItemStack): Boolean {
        val current = this[index]

        if (current.`is`(Items.BARRIER)) {
            this[index] = item.copy()
            return true
        }
        return false
    }
}