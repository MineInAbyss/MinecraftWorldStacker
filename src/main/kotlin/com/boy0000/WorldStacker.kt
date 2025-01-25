package com.boy0000

import com.boy0000.BlockScanHelpers.BLOCK_BLACKLIST
import com.github.ajalt.mordant.rendering.TextColors
import kotlinx.coroutines.*
import org.jglrxavpok.hephaistos.mca.RegionFile
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import java.io.File
import java.io.RandomAccessFile


object WorldStacker {
    val input = File("D:\\WORLDSTACKING\\input")
    val output = File("D:\\WORLDSTACKING\\output")
    val worldHeight = 512
    val targetWorldHeight = worldHeight * 2
    val newStartHeight = targetWorldHeight - 1
    val sectionOverlap = 32

    data class Result(val failedToRead: MutableList<File> = mutableListOf())

    data class BlockState(val compound: NBTCompound, val location: Location)

    data class Block(val type: String, val location: Location)
    data class Location(val x: Int, val y: Int, val z: Int)
}

fun main() {

    stackWorld(WorldStacker.input, WorldStacker.output)
}

fun stackWorld(worldFolder: File, outputFolder: File) = runBlocking {
    if (!worldFolder.isDirectory) error("${worldFolder.path} is not a directory")
    if (!worldFolder.resolve("level.dat").exists()) error("${worldFolder.path} is not a world-directory")
    if (!worldFolder.resolve("region").let { it.isDirectory && !it.list().isNullOrEmpty() }) error("${worldFolder.path} contains no region-files")

    processRegionFiles(worldFolder).await()

    terminal.println(TextColors.green("Finished stacking world ${worldFolder.name} to new height ${WorldStacker.targetWorldHeight} starting from ${WorldStacker.newStartHeight} and put it to ${outputFolder.absolutePath}"))
}

fun CoroutineScope.processRegionFiles(worldFolder: File): Deferred<Unit> {
    val regionFiles = Helpers.getRegionFilesInWorldFolder(worldFolder)
    val regionScanProgress = BlockScanHelpers.blockScanProgress(worldFolder, regionFiles)

    launch { regionScanProgress.execute() }

    return async {
        val worldStackResult = WorldStacker.Result(mutableListOf())
        regionFiles.forEachIndexed { index, region ->
            regionScanProgress.update {
                context = region.name
                completed = index.toLong()
            }

            val (x, z) = region.nameWithoutExtension.split(".").let { it[1].toInt() to it[2].toInt() }
            val regionFile = runCatching { RegionFile(RandomAccessFile(region, "r"), x, z) }
                .onFailure { worldStackResult.failedToRead += region }.getOrNull() ?: return@forEachIndexed

            val chunkRange = (0..31).flatMap { chunkX -> (0..31).map { chunkZ -> chunkX + x * 32 to chunkZ + z * 32 } }

            chunkRange.map { (chunkX, chunkZ) ->
                async {
                    // Read a RegionFiles ChunkData for the specified chunk X and Z
                    // Scan and map all blocks according to their y-coordinate
                    // Calculate the in-world coordinate and reference DeeperWorld config
                    // Find the given section
                    mapBlocksInChunk(worldStackResult, region, regionFile, chunkX, chunkZ)
                }
            }.awaitAll()
        }
    }
}

fun mapBlocksInChunk(worldStackResult: WorldStacker.Result, region: File, regionFile: RegionFile, chunkX: Int, chunkZ: Int) {
    val chunkData = runCatching { regionFile.getChunkData(chunkX, chunkZ) }
        .onFailure { println(it.message!!) }
        .getOrNull() ?: return

    val sections = chunkData.getList<NBTCompound>("sections") ?: return

    sections.forEach { section ->
        val blockStates = section.getCompound("block_states") ?: return
        val palette = blockStates.getList<NBTCompound>("palette")?.takeIf { it.isNotEmpty() } ?: return
        val data = blockStates.getLongArray("data")?.copyArray() ?: return

        findBlocks(data, palette.map { it.getString("Name") ?: "minecraft:air" })
    }
}

private fun findBlocks(dataLongArray: LongArray, palette: List<String>): List<WorldStacker.Block> {
    val maxPaletteIndex = palette.size - 1
    val bitLength = maxOf(getBitLength(maxPaletteIndex), 4)
    val indices = extractIndices(dataLongArray, bitLength)

    val blocks = mutableListOf<WorldStacker.Block>()
    for (i in indices.indices) {
        val blockType = palette[indices[i]]
        val x = i % 16
        val y = i / (16 * 16)
        val z = (i / 16) % 16
        blocks += WorldStacker.Block(blockType, WorldStacker.Location(x, y, z))
    }

    return blocks
}

private fun getBitLength(value: Int): Int {
    return Integer.SIZE - Integer.numberOfLeadingZeros(value)
}

private fun extractIndices(dataLongArray: LongArray, bitLength: Int): List<Int> {
    val indices = mutableListOf<Int>()
    var currentLong = 0L
    var currentLongBits = 0

    for (longValue in dataLongArray) {
        currentLong = currentLong or (longValue shl currentLongBits)
        while (currentLongBits + 64 >= bitLength) {
            val index = (currentLong and ((1L shl bitLength) - 1)).toInt()
            indices.add(index)
            currentLong = currentLong shr bitLength
            currentLongBits -= bitLength
        }
        currentLongBits += 64
    }

    return indices
}