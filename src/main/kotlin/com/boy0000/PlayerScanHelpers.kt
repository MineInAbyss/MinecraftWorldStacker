package com.boy0000

import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.widgets.progress.*
import org.jglrxavpok.hephaistos.nbt.CompoundEntry
import org.jglrxavpok.hephaistos.nbt.NBT
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTType
import org.jglrxavpok.hephaistos.nbt.mutable.MutableNBTCompound
import java.io.File

object PlayerScanHelpers {

    data class PlayerResult(
        val blackListed: MutableMap<String, List<NBTCompound>> = mutableMapOf(),
        val failedToRead: MutableList<String> = mutableListOf(),
        val ignored: MutableList<String> = mutableListOf()
    )

    fun playerScanProgress(worldFolder: File) = progressBarContextLayout {
        text {
            terminal.theme.warning(
                "Scanning player ${terminal.theme.success(context)} in ${
                    terminal.theme.danger(
                        worldFolder.nameWithoutExtension
                    )
                }"
            )
        }
        percentage()
        progressBar(completeChar = "-", pendingChar = "-", separatorChar = "-")
        completed(style = terminal.theme.success, suffix = " Players")
        timeRemaining(style = TextColors.magenta)
    }.animateInCoroutine(
        terminal,
        visible = true,
        start = true,
        context = "Starting...",
        total = Helpers.getPlayerDataFilesInWorld(worldFolder).size.toLong(),
        completed = 0
    )

    fun handleDisplayName(itemCompound: MutableNBTCompound): MutableNBTCompound {
        val tagCompound = itemCompound.getCompound("tag")?.toMutableCompound() ?: return itemCompound
        if (isShulker(itemCompound)) {
            val blockEntityTag = tagCompound.getCompound("BlockEntityTag")?.toMutableCompound()
            blockEntityTag?.getList<NBTCompound>("Items")?.map {
                handleDisplayName(it.toMutableCompound()).toCompound()
            }?.also {
                tagCompound["BlockEntityTag"] = NBT.List(NBTType.TAG_Compound, it)
            }
        }

        // Only migrate geary items
        if (tagCompound.getCompound("PublicBukkitValues")?.get("geary:components") == null) return itemCompound

        val displayName = tagCompound.getCompound("display")?.getString("Name") ?: return itemCompound
        tagCompound.remove("display")

        // Set tag-tag without displayname property
        itemCompound["tag"] = NBT.Compound(tagCompound.asMapView())
        // Set new itemname tag NOT
        //itemCompound["components"] = NBTCompound().withEntries(mapOf("minecraft:item_name" to NBT.String(displayName)).entries.first())

        return itemCompound
    }

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
            if (isShulker(item.toMutableCompound()))
                item.getCompound("tag")?.getCompound("BlockEntityTag")?.getList("Items") ?: listOf(item)
            else listOf(item)
        }.flatten()
    }

    fun isShulker(nbt: MutableNBTCompound) = nbt.getString("id")?.takeIf { it.matches(".*shulker.*".toRegex()) } != null
    fun handleItemCompound(
        nbt: NBTCompound,
        offendingPlayers: MutableMap<String, List<NBTCompound>>,
        playerdata: File
    ) {
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

    val PLAYER_WHITELIST = setOf(
        "c6307390-acda-48f8-8584-42087ad918f4",
        "9cc444cd-47cf-4660-96b7-17a7bfef302c",
        "8f3aa7d8-b258-4e5f-a55b-4733f8b86a51"
    )

    val ITEM_BLACKLIST = setOf(
        "minecraft:beacon",
        "minecraft:totem_of_undying",
        "minecraft:trident",
        "minecraft:reinforced_deepslate",
        "minecraft:wither_skeleton_skull",
        "minecraft:dragon_egg",
        "minecraft:dragon_head",
        "minecraft:nether_star",
        "minecraft:elytra",
        "minecraft:phantom_membrane",
        "minecraft:command_block.*",
        "minecraft:.*command_block",
        "minecraft:light",
        "minecraft:.*spawn_egg",
        "minecraft:debug_stick",
        "minecraft:jigsaw",
        "minecraft:structure_block",
        "minecraft:barrier",
        "minecraft:structure_void", "minecraft:spawner",
        "minecraft:tadpole_bucket"
    ).map { it.toRegex() }

    val ITEM_GRAYLIST = mapOf(
        "minecraft:enchanted_golden_apple" to 200,
        "minecraft:wither_rose" to 100,
        "minecraft:netherite_pickaxe" to 10
    ).map { it.key.toRegex() to it.value }.plus(ITEM_BLACKLIST.map { it to 1 }).toMap()
}
