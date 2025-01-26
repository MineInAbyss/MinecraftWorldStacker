package com.boy0000.worldstacking

import com.boy0000.Helpers
import kotlinx.coroutines.*
import org.jglrxavpok.hephaistos.collections.ImmutableLongArray
import org.jglrxavpok.hephaistos.mca.RegionFile
import org.jglrxavpok.hephaistos.nbt.NBT
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTType
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile

object WorldStacker {
    val input = File("D:\\WORLDSTACKING\\input")
    val output = File("D:\\WORLDSTACKING\\output")
    const val worldHeight = 512
    const val targetWorldHeight = worldHeight * 2
    const val newStartHeight = targetWorldHeight - 1
    const val sectionOverlap = 32

    data class Result(val failedToRead: MutableList<File> = mutableListOf())

    data class BlockState(val compound: NBTCompound, val location: Location)

    data class Block(val type: String, val location: Location)
    data class Location(val x: Int, val y: Int, val z: Int)
}

fun main() = runBlocking {
    val regionFiles = Helpers.getRegionFilesInWorldFolder(WorldStacker.input)
    val (palette, indices) = stackChunksIntoCylinder(regionFiles)

    val outputFile = WorldStacker.output.resolve("giant_cylinder_chunk.dat")
    writeCylinderChunk(outputFile, palette, indices)

    println("Cylinder chunk written to: ${outputFile.absolutePath}")
}

fun stackChunksIntoCylinder(regionFiles: List<File>): Pair<List<String>, ImmutableLongArray> {
    val allBlocks = mutableListOf<WorldStacker.Block>()
    var currentYOffset = WorldStacker.targetWorldHeight

    regionFiles.forEach { regionFile ->
        val (x, z) = regionFile.nameWithoutExtension.split(".").let { it[1].toInt() to it[2].toInt() }
        val region = RegionFile(RandomAccessFile(regionFile, "r"), x, z)

        // Only process chunk at (1, 1) in each region
        if (shouldProcessChunk(1, 1)) {
            val blocks = mapBlocksInChunkToCylinder(region, 1, 1, currentYOffset)
            allBlocks += blocks
        }
        currentYOffset += 256 // Increment Y-offset for next region
    }

    adjustBlockLocations(allBlocks)

    val palette = rebuildPalette(allBlocks)
    val indices = rebuildIndices(allBlocks, palette, 0)

    return palette to indices
}

fun adjustBlockLocations(blocks: MutableList<WorldStacker.Block>) {
    val blocksCopy = blocks.toMutableList()

    blocksCopy.forEach { block ->

    }

    blocks.clear()
    blocks.addAll(blocksCopy)
}

private fun shouldProcessChunk(chunkX: Int, chunkZ: Int): Boolean {
    return chunkX == 1 && chunkZ == 1 // Only process chunks at (1, 1)
}

fun mapBlocksInChunkToCylinder(regionFile: RegionFile, chunkX: Int, chunkZ: Int, yOffset: Int): List<WorldStacker.Block> {
    // Check if the chunk exists in the region file
    if (!regionFile.hasChunk(chunkX, chunkZ)) {
        println("Chunk at ($chunkX, $chunkZ) does not exist in region file. Skipping.")
        return emptyList()
    }

    val chunkData = runCatching { regionFile.getChunkData(chunkX, chunkZ) }
        .onFailure { println("Failed to read chunk at ($chunkX, $chunkZ): ${it.message}") }
        .getOrNull() ?: return emptyList()

    val sections = chunkData.getList<NBTCompound>("sections") ?: return emptyList()
    val allBlocks = mutableListOf<WorldStacker.Block>()

    sections.forEach { section ->
        val blockStates = section.getCompound("block_states") ?: return@forEach
        val palette = blockStates.getList<NBTCompound>("palette")?.map { it.getString("Name") ?: "minecraft:air" } ?: return@forEach
        val data = blockStates.getLongArray("data")?.copyArray() ?: return@forEach

        allBlocks += findBlocks(data, palette)
    }

    return allBlocks
}

private fun rebuildPalette(blocks: List<WorldStacker.Block>): List<String> {
    return blocks.map { it.type }.distinct()
}

private fun rebuildIndices(blocks: List<WorldStacker.Block>, palette: List<String>, sectionYOffset: Int): ImmutableLongArray {
    val maxPaletteIndex = palette.size - 1
    val bitLength = maxOf(getBitLength(maxPaletteIndex), 4)

    val indices = IntArray(16 * 16 * 16) { 0 }
    for (block in blocks) {
        val newY = block.location.y + sectionYOffset
        if (newY in 0 until 256) { // Ensure within valid section height
            val index = block.location.x + (newY % 16) * 16 * 16 + block.location.z * 16
            indices[index] = palette.indexOf(block.type)
        }
    }

    return buildDataLongArray(indices, bitLength)
}

private fun buildDataLongArray(indices: IntArray, bitLength: Int): ImmutableLongArray {
    val dataLongArray = mutableListOf<Long>()
    var currentLong = 0L
    var currentLongBits = 0

    indices.forEach { index ->
        currentLong = currentLong or (index.toLong() shl currentLongBits)
        currentLongBits += bitLength

        if (currentLongBits >= 64) {
            dataLongArray.add(currentLong and ((1L shl 64) - 1))
            currentLong = currentLong shr 64
            currentLongBits -= 64
        }
    }

    if (currentLongBits > 0) {
        dataLongArray.add(currentLong)
    }

    return ImmutableLongArray(*dataLongArray.toLongArray())
}

fun writeCylinderChunk(outputFile: File, palette: List<String>, indices: ImmutableLongArray) {
    val blockStates = NBTCompound().kmodify {
        set("palette", NBT.List(NBTType.TAG_Compound, palette.map { NBTCompound().kmodify { set("Name", NBT.String(it)) } }))
        set("data", NBT.LongArray(indices))
    }

    val sections = mutableListOf<NBTCompound>()
    sections.add(NBTCompound().kmodify {
        set("block_states", blockStates)
    })

    val chunkData = NBTCompound().kmodify {
        set("sections", NBT.List(NBTType.TAG_Compound, sections))
    }

    DataOutputStream(outputFile.outputStream()).use { out ->
        chunkData.writeContents(out)
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
        currentLongBits += 64

        while (currentLongBits >= bitLength) {
            indices.add((currentLong and ((1L shl bitLength) - 1)).toInt())
            currentLong = currentLong shr bitLength
            currentLongBits -= bitLength
        }
    }

    return indices
}