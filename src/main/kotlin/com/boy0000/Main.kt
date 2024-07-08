package com.boy0000

import com.github.ajalt.mordant.animation.progress.update
import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.*
import org.jglrxavpok.hephaistos.mca.RegionFile
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTReader
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max

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
    val playerResult = if (scanPlayers) async(dispatcher) { this@runBlocking.processPlayerData(worldFolder) }.await() else PlayerScanHelpers.PlayerResult()
    val regionData = if (scanBlocks) async(dispatcher) { this@runBlocking.processRegionFiles(worldFolder) }.await() else BlockScanHelpers.BlockResult()

    terminal.println(Helpers.resultTable(playerResult, regionData))
}

fun CoroutineScope.processRegionFiles(worldFolder: File): BlockScanHelpers.BlockResult {
    val blockResult = BlockScanHelpers.BlockResult()
    val regionFiles = Helpers.getRegionFilesInWorldFolder(worldFolder)
    val blockScanProgress = BlockScanHelpers.blockScanProgress(worldFolder, regionFiles)

    launch { blockScanProgress.execute() }
    regionFiles.forEachIndexed { index, regionFile ->
        blockScanProgress.update {
            context = regionFile.name
            completed = index.toLong()
        }
        val (x, z) = regionFile.nameWithoutExtension.split(".").let { it[1].toInt() to it[2].toInt() }
        val region = runCatching { RegionFile(RandomAccessFile(regionFile, "r"), x, z) }
            .onFailure { blockResult.failedToRead += regionFile }.getOrNull() ?: return@forEachIndexed

        val chunkRange = (0..31).flatMap { chunkX -> (0..31).map { chunkZ -> chunkX + x * 32 to chunkZ + z * 32 } }

        chunkRange.map { (chunkX, chunkZ) ->
            BlockScanHelpers.processBlocksInChunk(blockResult, region, chunkX, chunkZ)
            BlockScanHelpers.processBlockEntitiesInChunk(region, chunkX, chunkZ)
        }
    }

    blockScanProgress.update(blockScanProgress.total!!)

    return blockResult
}

fun CoroutineScope.processPlayerData(worldFolder: File): PlayerScanHelpers.PlayerResult {
    val playerResult = PlayerScanHelpers.PlayerResult()
    val playerDataInWorld = Helpers.getPlayerDataFilesInWorld(worldFolder)
    val playerScanProgress = PlayerScanHelpers.playerScanProgress(worldFolder)

    launch { playerScanProgress.execute() }

    playerDataInWorld.forEachIndexed { index, playerdata ->
        val uuid = playerdata.nameWithoutExtension
        playerScanProgress.update {
            context = playerdata.name
            completed = index.toLong()
        }
        if (uuid in PlayerScanHelpers.PLAYER_WHITELIST) return@forEachIndexed run { playerResult.ignored += uuid }

        runCatching { NBTReader(playerdata) }.onFailure { playerResult.failedToRead += uuid }.getOrNull()?.use {
            val data = it.read() as NBTCompound
            val inventory = data.getList<NBTCompound>("Inventory") ?: emptyList()
            val enderchest = data.getList<NBTCompound>("EnderItems") ?: emptyList()

            PlayerScanHelpers.mergeItems(PlayerScanHelpers.flatmapShulkerItems(inventory.plus(enderchest))).forEach items@{ item ->
                PlayerScanHelpers.handleItemCompound(item, playerResult.blackListed, playerdata)
            }
        }
    }

    playerScanProgress.update(playerScanProgress.total!!)

    return playerResult
}
