package com.github.monun.invcaptive.plugin

import io.github.monun.invfx.InvFX
import io.github.monun.invfx.frame.InvFrame
import io.github.monun.invfx.frame.InvList
import io.github.monun.invfx.openFrame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import javax.naming.Name
import kotlin.math.ceil
import kotlin.math.min

private val excluded = setOf(
    Material.STRUCTURE_VOID,
    Material.WATER,
    Material.LAVA,
    Material.DIAMOND_ORE,
    Material.DEEPSLATE_COAL_ORE,
    Material.PLAYER_HEAD,
    Material.PLAYER_WALL_HEAD,
    Material.DRAGON_EGG
)

val blocks = Material.entries.filter {
    it.isBlock && !it.isAir && it.hardness >= 0 && !it.isEmpty && !excluded.contains(it)
}

val frames = mutableMapOf<Player, ProgressFrame>()

data class ProgressFrame(
    val frame: InvFrame, val list: InvList<Material>
)

fun updateFrames() {
    frames.values.forEach {
        it.list.refresh()
    }
}

fun showProgressGUI(player: Player) {
    var onlyPinned = false

    val frame = InvFX.frame(6, Component.text("남은 블럭")) {
        val items = list(0, 0, 8, 4, true, {
            if (onlyPinned) InvCaptive.pinnedBlocks.toList() - InvCaptive.triedBlocks
            else (InvCaptive.pinnedBlocks.toList() + (blocks - InvCaptive.pinnedBlocks)) - InvCaptive.triedBlocks
        }) {
            transform { material ->
                ItemStack(if (material.isItem) material else Material.BARRIER).apply {
                    editMeta {
                        it.displayName(Component.translatable(material.translationKey()))
                        it.lore(
                            mutableListOf(
                                Component.text(material.name).color(NamedTextColor.GRAY),
                                if (InvCaptive.pinnedBlocks.contains(material))
                                    Component.text("클릭해 고정 해제하기").color(NamedTextColor.RED)
                                else Component.text("클릭해 고정하기").color(NamedTextColor.YELLOW)
                            )
                        )
                    }
                }
            }

            onClickItem { _, _, (material), _ ->
                InvCaptive.apply {
                    if (pinnedBlocks.contains(material))
                        pinnedBlocks.remove(material) else
                        pinnedBlocks.add(material)
                    updateFrames()
                }
            }
        }

        slot(0, 5) {
            item = ItemStack(Material.ARROW).apply {
                editMeta {
                    it.displayName(Component.text("이전 페이지"))
                }
            }

            onClick {
                items.page -= 1
            }
        }

        slot(4, 5) {
            fun update() {
                item = (if (onlyPinned)
                    ItemStack(Material.GOLD_BLOCK).apply {
                        editMeta {
                            it.lore(listOf(Component.text("고정된 아이템만 보기").color(NamedTextColor.YELLOW)))
                        }
                    }
                else
                    ItemStack(Material.STONE).apply {
                        editMeta {
                            it.lore(listOf(Component.text("모든 아이템 보기").color(NamedTextColor.GRAY)))
                        }
                    }).apply {
                    editMeta {
                        it.displayName(Component.text("필터"))
                    }
                }
            }

            update()

            onClick {
                onlyPinned = !onlyPinned
                update()
                updateFrames()
            }
        }

        slot(8, 5) {
            item = ItemStack(Material.ARROW).apply {
                editMeta {
                    it.displayName(Component.text("다음 페이지"))
                }
            }

            onClick {
                items.page += 1
            }
        }

        frames[player] = ProgressFrame(this, items)
    }

    player.openFrame(frame)
}
