package com.boy0000

import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.widgets.progress.*
import org.jglrxavpok.hephaistos.mca.RegionFile
import org.jglrxavpok.hephaistos.nbt.NBT
import org.jglrxavpok.hephaistos.nbt.NBTByte
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import org.jglrxavpok.hephaistos.nbt.NBTType
import java.io.File

object BlockScanHelpers {

    data class BlockResult(val scanBlocks: Boolean, val persistentLeaves: Boolean, val blackListed: MutableMap<String, MutableList<Block>> = mutableMapOf(), val failedToRead: MutableList<File> = mutableListOf())

    fun blockScanProgress(worldFolder: File, regionFiles: List<File>) = progressBarContextLayout {
        text { terminal.theme.warning("Scanning region ${terminal.theme.success(context)} in ${terminal.theme.danger(worldFolder.nameWithoutExtension)}") }
        percentage()
        progressBar(completeChar = "-", pendingChar = "-", separatorChar = "-")
        completed(style = terminal.theme.success, suffix = " Regions")
        timeRemaining(style = TextColors.magenta)
    }.animateInCoroutine(terminal, visible = true, start = true, context = "Starting...", total = regionFiles.size.toLong(), completed = 0)

    data class Block(val type: String, val location: Location) {
        val isAir = type == "minecraft:air"
        val isBlackListed = BLOCK_BLACKLIST.any { it.matches(type) }
        val isLeaf = type.endsWith("leaves")
    }
    data class Location(val x: Int, val y: Int, val z: Int)

    val BLOCK_BLACKLIST = setOf(
        "minecraft:beacon",
        "minecraft:command_block.*",
        "minecraft:.*command_block",
        "minecraft:conduit",
        "minecraft:end_portal_frame",
        "minecraft:dragon_egg",
        "minecraft:dragon_head",
        "minecraft:vault",
        "minecraft:ominous_vault",
        "minecraft:spawner",
        "minecraft:trial_spawner",
        "minecraft:structure_block",
        "minecraft:jigsaw",
        "minecraft:heavy_core",
        "minecraft:wither_skeleton_skull",
        "minecraft:copper_grate"
    ).map { it.toRegex() }

    fun processBlocksInChunk(blockResult: BlockResult, region: File, regionFile: RegionFile, chunkX: Int, chunkZ: Int) {
        if (blockResult.persistentLeaves) {
            val chunkData = runCatching { regionFile.getChunkData(chunkX, chunkZ) }
                .onFailure { println(it.message!!) }
                .getOrNull() ?: return

            val sections = chunkData.getList<NBTCompound>("sections") ?: return
            val newChunkData = chunkData.kmodify {
                val newSections = sections.map { section ->
                    section.getCompound("block_states")?.kmodify blockstates@{
                        getList<NBTCompound>("palette")?.map { palette ->
                            if (palette.getString("Name")?.endsWith("leaves") == true) {
                                palette.kmodify palette@{
                                    getCompound("Properties")?.kmodify {
                                        set("persistent", NBT.String("true"))
                                    }?.also { properties ->
                                        this@palette["Properties"] = properties
                                    }
                                }
                            } else palette
                        }?.also { palette ->
                            set("palette", NBT.List(NBTType.TAG_Compound, palette))
                        }

                    }?.let { blockstates ->
                        section.kmodify { set("block_states", blockstates) }
                    } ?: section
                }

                set("sections", NBT.List(NBTType.TAG_Compound, newSections))
            }

            regionFile.writeColumnData(newChunkData, chunkX, chunkZ)
        }

    }

    fun processBlockEntitiesInChunk(regionFile: RegionFile, chunkX: Int, chunkZ: Int) {
        val chunkData = runCatching { regionFile.getChunkData(chunkX, chunkZ) }
            .onFailure { println(it.message!!) }
            .getOrNull()?.toMutableCompound() ?: return

        val blockEntities = chunkData.getList<NBTCompound>("block_entities")?.takeIf { it.isNotEmpty() } ?: return

        chunkData["block_entities"] = NBT.List(NBTType.TAG_Compound, blockEntities.map {
            it.kmodify {
                getList<NBTCompound>("Items")?.also { items ->
                    val newItems = items.map { itemCompound ->
                        NBT.Compound(PlayerScanHelpers.handleDisplayName(itemCompound.toMutableCompound()).asMapView())
                    }

                    set("Items", NBT.List(NBTType.TAG_Compound, newItems))
                }
            }
        })

        regionFile.writeColumnData(chunkData.toCompound(), chunkX, chunkZ)
    }

    private fun findBlocks(dataLongArray: LongArray, palette: List<String>): List<Block> {
        val maxPaletteIndex = palette.size - 1
        val bitLength = maxOf(getBitLength(maxPaletteIndex), 4)
        val indices = extractIndices(dataLongArray, bitLength)

        val blocks = mutableListOf<Block>()
        for (i in indices.indices) {
            val blockType = palette[indices[i]]
            val x = i % 16
            val y = i / (16 * 16)
            val z = (i / 16) % 16
            blocks += Block(blockType, Location(x,y,z))
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
}