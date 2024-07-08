package com.boy0000

import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.widgets.progress.*
import org.jglrxavpok.hephaistos.nbt.NBT
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import java.io.File

object PlayerScanHelpers {

    fun playerScanProgress(worldFolder: File) = progressBarContextLayout {
        text { terminal.theme.warning("Scanning player ${terminal.theme.success(context)} in ${terminal.theme.danger(worldFolder.nameWithoutExtension)}") }
        percentage()
        progressBar(completeChar = "-", pendingChar = "-", separatorChar = "-")
        completed(style = terminal.theme.success, suffix = " Players")
        timeRemaining(style = TextColors.magenta)
    }.animateInCoroutine(terminal, context = "Starting...", total = Helpers.getPlayerDataFilesInWorld(worldFolder).size.toLong(), completed = 0)

    fun mergeItems(items: List<NBTCompound>): List<NBTCompound> {
        return items.groupBy { it.getString("id") }
            .mapNotNull { (_, itemsWithSameId) ->
                val mergedCount = itemsWithSameId.sumOf { it.getAsInt("Count") ?: return@sumOf 0 }
                val slots = itemsWithSameId.map { it.getAsByte("Slot") ?: 0 }
                itemsWithSameId.firstOrNull()?.toMutableCompound()?.set("Count", NBT.Byte(mergedCount))
                    ?.set("Slot", NBT.ByteArray(*slots.toByteArray()))?.toCompound()
            }
    }

    fun flatmapShulkerItems(items: List<NBTCompound>): List<NBTCompound> {
        return items.map { item ->
            if (isShulker(item))
                item.getCompound("tag")?.getCompound("BlockEntityTag")?.getList("Items") ?: listOf(item)
            else listOf(item)
        }.flatten()
    }

    fun isShulker(nbt: NBTCompound) = nbt.getString("id")?.takeIf { it.matches(".*shulker.*".toRegex()) } != null
    fun handleItemCompound(nbt: NBTCompound, offendingPlayers: MutableMap<String, List<NBTCompound>>, playerdata: File) {
        val itemId = nbt.getString("id") ?: return
        itemId.takeIf { ITEM_BLACKLIST.any { r -> r.matches(itemId) } }
            ?: ITEM_GRAYLIST.entries.find { it.key.matches(itemId) && it.value <= (nbt.getAsInt("Count") ?: 0) }
            ?: return
        offendingPlayers.compute(playerdata.nameWithoutExtension) { uuid: String, items: List<NBTCompound>? ->
            (items?.toMutableList() ?: mutableListOf()).apply {
                add(nbt)
            }
        }
    }
}