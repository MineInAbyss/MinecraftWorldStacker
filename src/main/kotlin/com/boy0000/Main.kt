package com.boy0000

import com.github.ajalt.mordant.animation.progress.update
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jglrxavpok.hephaistos.mca.RegionFile
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
    val scanBlocks = "--blocks" in args
    val (scanPlayers, scanEntities) = ("--players" in args) to ("--entities" in args)

    val dispatcher = newFixedThreadPoolContext(threadCount, "regionFilePool")
    if (scanPlayers) async(dispatcher) { this@runBlocking.processPlayerData(worldFolder) }
    if (scanBlocks) async(dispatcher) { this@runBlocking.processRegionFiles(worldFolder) }
}

fun CoroutineScope.processRegionFiles(worldFolder: File) {
    val regionFiles = Helpers.getRegionFilesInWorldFolder(worldFolder)
    val blockScanProgress = BlockScanHelpers.blockScanProgress(worldFolder, regionFiles)

    launch { blockScanProgress.execute() }
    regionFiles.forEachIndexed { index, regionFile ->
        blockScanProgress.update {
            context = regionFile.name
            completed = index.toLong()
        }
        val (x, z) = regionFile.nameWithoutExtension.split(".").let { it[1].toInt() to it[2].toInt() }
        val region = runCatching { RegionFile(RandomAccessFile(regionFile, "r"), x, z) }.onFailure {
            terminal.println(TextColors.red("Failed to scan ${regionFile.name}"))
        }.getOrNull() ?: return@forEachIndexed

        val chunkRange = (0..31).flatMap { chunkX -> (0..31).map { chunkZ -> chunkX + x * 32 to chunkZ + z * 32 } }

        chunkRange.map { (chunkX, chunkZ) ->
            BlockScanHelpers.processBlocksInChunk(region, chunkX, chunkZ)
            BlockScanHelpers.processBlockEntitiesInChunk(region, chunkX, chunkZ)
        }
    }

    blockScanProgress.update(blockScanProgress.total!!)
}

fun CoroutineScope.processPlayerData(worldFolder: File) {
    val playerDataInWorld = Helpers.getPlayerDataFilesInWorld(worldFolder)
    val playerScanProgress = PlayerScanHelpers.playerScanProgress(worldFolder)

    launch { playerScanProgress.execute() }

    val offendingPlayers = mutableMapOf<String, List<NBTCompound>>()
    playerDataInWorld.forEachIndexed { index, playerdata ->
        playerScanProgress.update {
            context = playerdata.name
            completed = index.toLong()
        }
        if (playerdata.nameWithoutExtension in PlayerScanHelpers.PLAYER_WHITELIST) return@forEachIndexed
        runCatching { NBTReader(playerdata) }.getOrNull()?.use {
            val data = it.read() as NBTCompound
            val inventory = data.getList<NBTCompound>("Inventory") ?: emptyList()
            val enderchest = data.getList<NBTCompound>("EnderItems") ?: emptyList()

            PlayerScanHelpers.mergeItems(PlayerScanHelpers.flatmapShulkerItems(inventory.plus(enderchest))).forEach items@{ item ->
                PlayerScanHelpers.handleItemCompound(item, offendingPlayers, playerdata)
            }
        } ?: terminal.println(TextColors.red("Failed scanning ${playerdata.nameWithoutExtension} :("))
    }

    offendingPlayers.forEach {
        terminal.println(TextColors.red("https://namemc.com/search?q=${it.key}: ") + "\n"
                + TextColors.yellow(it.value.joinToString("\n") { it.toSNBT() })
        )
    }

    terminal.println(TextColors.yellow("Scanned ${playerDataInWorld.size} player-files and found ${offendingPlayers.size} players with a total of ${offendingPlayers.values.flatten().size} illegal items"))

    terminal.println(TextColors.green("Finished scanning PlayerData for ${worldFolder.name}!"))
    playerScanProgress.update(playerScanProgress.total!!)
}
