package com.boy0000

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jglrxavpok.hephaistos.mca.RegionFile
import org.jglrxavpok.hephaistos.nbt.NBT
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTReader
import java.io.File
import java.io.RandomAccessFile

var threadCount: Int = 2
val terminal = Terminal()
suspend fun main(args: Array<String>) = runBlocking {
    if (args.size < 2) {
        terminal.println(
            """
            ${
                TextColors.brightRed(
                    "Usage: java -jar MinecraftWorldScanner.jar " + TextColors.yellow("--world <path>") + TextColors.brightYellow(
                        " --threadcount <x>"
                    )
                )
            }
            ${TextColors.yellow("--world")} The path to your world-folder, example: D:\Servers\MyServer\world
            ${TextColors.brightYellow("--threadcount")} The amount of threads the process should use. Defaults to 2 unless specified
        """.trimIndent()
        )
        return@runBlocking
    }
    val args = " " + args.joinToString(" ")
    threadCount =
        args.substringAfter(" --threadCount ", args.substringAfter(" -t ")).substringBefore(" ").toIntOrNull() ?: 2

    val worldArg = args.substringAfter(" --world ", args.substringAfter(" -w ")).substringBefore(" ")
    val worldFolder = File(worldArg)
    if (!worldFolder.isDirectory || worldFolder.list()?.none { it == "level.dat" } != false) {
        terminal.println(TextColors.brightRed("$worldArg is not a valid world, stopping..."))
        return@runBlocking
    }
    val (scanBlocks, scanBlockEntities) = ("--blocks" in args) to ("--blockEntities" in args)
    val (scanPlayers, scanEntities) = ("--players" in args) to ("--entities" in args)

    terminal.println(TextColors.yellow("Starting scanning of ${worldFolder.name}-world..."))

    val dispatcher = newFixedThreadPoolContext(threadCount, "regionFilePool")
    launch(dispatcher) {
        val regionFiles = Helpers.getRegionFilesInWorldFolder(worldFolder)
        val entityRegionFiles = Helpers.getEntityRegionFilesInWorldFolder(worldFolder)

        val semaphore = Semaphore(threadCount)
        if (scanBlocks || scanBlockEntities) regionFiles.map { regionFile ->
            async {
                semaphore.withPermit { processRegionFile(regionFile, scanBlocks, scanBlockEntities) }
            }
        }.awaitAll()

        if (scanPlayers) {
            async {
                semaphore.withPermit {
                    processPlayerData(worldFolder)
                }
            }
        }
    }
}

suspend fun processRegionFile(regionFile: File, scanBlocks: Boolean, scanBlockEntities: Boolean) {
    terminal.println(TextColors.yellow("Starting scanning of ${regionFile.name}..."))
    val (x, z) = regionFile.nameWithoutExtension.split(".").let { it[1].toInt() to it[2].toInt() }
    val region = RegionFile(RandomAccessFile(regionFile, "rw"), x, z)
    val chunkRange = (0..31).flatMap { chunkX -> (0..31).map { chunkZ -> chunkX + x * 32 to chunkZ + z * 32 } }

    val semaphore = Semaphore(threadCount)
    coroutineScope {
        chunkRange.map { (chunkX, chunkZ) ->
            async {
                semaphore.withPermit {
                    if (scanBlocks) processBlocksInChunk(region, chunkX, chunkZ)
                    if (scanBlockEntities) processBlockEntitiesInChunk(region, chunkX, chunkZ)
                }
            }
        }.awaitAll() // Wait for all async operations to complete
    }

    terminal.println(TextColors.green("Finished scanning ${regionFile.name}!"))
}

val BLOCK_BLACKLIST = setOf("minecraft:beacon")

// only flag if more than x in chunk
val BLOCK_PARTIAL_BLACKLIST = mapOf("minecraft:netherite_block" to 10)
fun processBlocksInChunk(regionFile: RegionFile, chunkX: Int, chunkZ: Int) {
    val chunkData = runCatching { regionFile.getChunkData(chunkX, chunkZ) }
        .onFailure { terminal.println(TextColors.yellow(it.message!!)) }
        .getOrNull() ?: return
    val sections = chunkData.getList<NBTCompound>("sections") ?: return

//    val newChunkData = chunkData.kmodify {
//        val newSections = sections.map { section ->
//            val blockstates = section.getCompound("block_states")?.kmodify blockstates@{
//                val palette = getList<NBTCompound>("palette")?.map { palette ->
//                    when (palette.getString("Name")) {
//                        "minecraft:chest" -> processNoteBlockCompound(palette, regionFile, chunkX, chunkZ)
//                        "minecraft:tripwire" -> processTripwireCompound(palette, regionFile, chunkX, chunkZ)
//                        //"minecraft:waxed_copper_" -> processWaxedCopperCompound(palette, modifiedRegion, chunkX, chunkZ)
//                        else -> palette
//                    }
//                } ?: return@blockstates
//            } ?: return@kmodify
//            section.kmodify { set("block_states", blockstates) }
//        }
//    }
}

fun processBlockEntitiesInChunk(regionFile: RegionFile, chunkX: Int, chunkZ: Int) {

}

val BLOCK_ENTITY_BLACKLIST = setOf("")
fun processEntitiesInChunk(regionFile: RegionFile, chunkX: Int, chunkZ: Int) {

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
    "minecraft:structure_void", "minecraft:spawner"
).map { it.toRegex() }

val ITEM_GRAYLIST = mapOf(
    "minecraft:enchanted_golden_apple" to 200,
    "minecraft:wither_rose" to 100,
    "minecraft:netherite_pickaxe" to 10
).map { it.key.toRegex() to it.value }.plus(ITEM_BLACKLIST.map { it to 1 }).toMap()

suspend fun processPlayerData(worldFolder: File) {
    val playerDataInWorld = Helpers.getPlayerDataFilesInWorld(worldFolder)
    terminal.println(TextColors.yellow("Starting scanning of PlayerData for ${worldFolder.name}..."))

    fun mergeItems(items: List<NBTCompound>): List<NBTCompound> {
        return items.groupBy { it.getString("id") }
            .mapNotNull { (_, itemsWithSameId) ->
                val mergedCount = itemsWithSameId.sumOf { it.getAsInt("Count") ?: return@sumOf 0 }
                val slots = itemsWithSameId.map { it.getAsByte("Slot") ?: 0 }
                itemsWithSameId.firstOrNull()?.toMutableCompound()?.set("Count", NBT.Byte(mergedCount))?.set("Slot", NBT.ByteArray(*slots.toByteArray()))?.toCompound()
            }
    }

    val offendingPlayers = mutableMapOf<String, List<NBTCompound>>()
    playerDataInWorld.forEach { playerdata ->
        if (playerdata.nameWithoutExtension in PLAYER_WHITELIST) return@forEach
        //terminal.println(TextColors.brightYellow("Scanning ${playerdata.nameWithoutExtension}..."))
        runCatching { NBTReader(playerdata) }.getOrNull()?.use {
            val data = it.read() as NBTCompound
            val inventory = data.getList<NBTCompound>("Inventory") ?: emptyList()
            val enderchest = data.getList<NBTCompound>("EnderItems") ?: emptyList()

            fun NBTCompound.isShulker() = getString("id")?.takeIf { it.matches(".*shulker.*".toRegex()) } != null
            fun NBTCompound.handleItemCompound() {
                val itemId = getString("id") ?: return
                itemId.takeIf { ITEM_BLACKLIST.any { r -> r.matches(itemId) } }
                    ?: ITEM_GRAYLIST.entries.find { it.key.matches(itemId) && it.value <= (getAsInt("Count") ?: 0) } ?: return
                offendingPlayers.compute(playerdata.nameWithoutExtension) { uuid: String, items: List<NBTCompound>? ->
                    (items?.toMutableList() ?: mutableListOf()).apply {
                        add(this@handleItemCompound)
                    }
                }
            }

            fun flatmapShulkerItems(items: List<NBTCompound>): List<NBTCompound> {
                return items.map { item ->
                    if (item.isShulker())
                        item.getCompound("tag")?.getCompound("BlockEntityTag")?.getList("Items") ?: listOf(item)
                    else listOf(item)
                }.flatten()
            }

            mergeItems(flatmapShulkerItems(inventory.plus(enderchest))).forEach items@{ item ->
                item.handleItemCompound()
            }
        } ?: terminal.println(TextColors.red("Failed scanning ${playerdata.nameWithoutExtension} :("))
    }



    offendingPlayers.forEach {
        terminal.println(TextColors.red("https://namemc.com/search?q=${it.key}: ") + "\n"
                + TextColors.yellow(it.value.joinToString("\n") { it.toSNBT() }))
    }

    terminal.println(TextColors.yellow("Scanned ${playerDataInWorld.size} player-files and found ${offendingPlayers.size} players with a total of ${offendingPlayers.values.flatten().size} illegal items"))

    terminal.println(TextColors.green("Finished scanning PlayerData for ${worldFolder.name}!"))
}
