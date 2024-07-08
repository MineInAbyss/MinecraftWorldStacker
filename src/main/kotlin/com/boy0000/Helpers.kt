package com.boy0000

import com.github.ajalt.mordant.rendering.*
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.Table
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.widgets.Text
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import java.io.File

object Helpers {
    fun getRegionFilesInWorldFolder(worldFolder: File): List<File> {
        if (!worldFolder.isDirectory || worldFolder.list()
                ?.none { it == "level.dat" } != false
        ) error("Not a world folder")
        return worldFolder.resolve("region").listFiles()?.filter { it.extension == "mca" } ?: emptyList()
    }

    fun getEntityRegionFilesInWorldFolder(worldFolder: File): List<File> {
        if (!worldFolder.isDirectory || worldFolder.list()
                ?.none { it == "level.dat" } != false
        ) error("Not a world folder")
        return worldFolder.resolve("entities").listFiles()?.filter { it.extension == "mca" } ?: emptyList()
    }

    fun getPlayerDataFilesInWorld(worldFolder: File): List<File> {
        if (!worldFolder.isDirectory || worldFolder.list()
                ?.none { it == "level.dat" } != false
        ) error("Not a world folder")
        return worldFolder.resolve("playerdata").listFiles()?.filter { it.extension == "dat" } ?: emptyList()
    }

    fun playerResultTable(worldFolder: File, playerResult: PlayerScanHelpers.PlayerResult): Table {
        return table {
            borderType = BorderType.SQUARE_DOUBLE_SECTION_SEPARATOR
            borderStyle = TextColors.rgb("#4b25b9")
            align = TextAlign.CENTER
            tableBorders = Borders.NONE
            header {
                //
                row {
                    style = TextColors.brightRed + TextStyles.bold
                    cellBorders = Borders.NONE
                    cells("", "","PlayerData") {
                        align = TextAlign.CENTER
                    }
                }
                rowStyles(TextStyle(color = TextColors.red), TextStyle(color = TextColors.yellow), TextStyle(color = TextColors.green))
                row {
                    cell("") {
                        cellBorders = Borders.NONE
                    }
                    cell("Blacklist") {
                        align = TextAlign.CENTER
                        cellBorders = Borders.BOTTOM
                        style = TextStyle(color = TextColors.red)
                    }
                    cell("Failed") {
                        align = TextAlign.CENTER
                        cellBorders = Borders.BOTTOM
                        style = TextStyle(color = TextColors.yellow)
                    }
                    cell("Ignored") {
                        align = TextAlign.CENTER
                        cellBorders = Borders.BOTTOM
                        style = TextStyle(color = TextColors.green)
                    }
                    cell("") {
                        cellBorders = Borders.NONE
                    }
                }
            }
            body {
                column(0) {
                    cellBorders = Borders.RIGHT
                }
                column(1) {
                    cellBorders = Borders.ALL
                    style = TextColors.brightRed
                }
                column(2) {
                    cellBorders = Borders.ALL
                    style = TextColors.brightYellow
                }
                column(3) {
                    cellBorders = Borders.ALL
                    style = TextColors.brightGreen
                }
                column(4) {
                    cellBorders = Borders.LEFT
                }
                cellBorders = Borders.ALL
                overflowWrap = OverflowWrap.BREAK_WORD
                whitespace = Whitespace.PRE_WRAP

                mutableListOf<Triple<Map.Entry<String, List<NBTCompound>>?, String?, String?>>().apply {
                    (0 until maxOf(playerResult.blackListed.size, playerResult.failedToRead.size, playerResult.ignored.size)).forEach {
                        add(Triple(playerResult.blackListed.toMap().entries.elementAtOrNull(it), playerResult.failedToRead.elementAtOrNull(it), playerResult.ignored.elementAtOrNull(it)))
                    }
                }.forEach {
                    fun String.toNameMC(): String = "https://namemc.com/search?q=$this"
                    row("", "${it.first?.key?.toNameMC()}\n${it.first?.value?.joinToString("\n") { "${it.getString("id")} ${it.getAsInt("Count") }" }}", it.second?.toNameMC(), it.third?.toNameMC()) {
                        whitespace = Whitespace.PRE_LINE
                    }
                }
                row {
                    cellBorders = Borders.ALL
                }
            }
            captionBottom(Text("Based on ${getPlayerDataFilesInWorld(worldFolder).size} player-files", align = TextAlign.CENTER))
        }
    }

    fun regionResultTable(worldFolder: File, regionResult: BlockScanHelpers.BlockResult): Table {
        return table {
            borderType = BorderType.SQUARE_DOUBLE_SECTION_SEPARATOR
            borderStyle = TextColors.rgb("#4b25b9")
            align = TextAlign.CENTER
            tableBorders = Borders.NONE
            header {
                //
                row {
                    style = TextColors.brightRed + TextStyles.bold
                    cellBorders = Borders.NONE
                    cells("", "","RegionData") {
                        align = TextAlign.CENTER
                    }
                }
                rowStyles(TextStyle(color = TextColors.red), TextStyle(color = TextColors.yellow), TextStyle(color = TextColors.green))
                row {
                    cell("") {
                        cellBorders = Borders.NONE
                    }
                    cell("Blacklist") {
                        align = TextAlign.CENTER
                        cellBorders = Borders.BOTTOM
                        style = TextStyle(color = TextColors.red)
                    }
                    cell("Failed") {
                        align = TextAlign.CENTER
                        cellBorders = Borders.BOTTOM
                        style = TextStyle(color = TextColors.yellow)
                    }
                    cell("") {
                        cellBorders = Borders.NONE
                    }
                }
            }
            body {
                column(0) {
                    cellBorders = Borders.RIGHT
                }
                column(1) {
                    cellBorders = Borders.ALL
                    style = TextColors.brightRed
                }
                column(2) {
                    cellBorders = Borders.ALL
                    style = TextColors.brightYellow
                }
                column(4) {
                    cellBorders = Borders.LEFT
                }
                cellBorders = Borders.ALL
                overflowWrap = OverflowWrap.BREAK_WORD
                whitespace = Whitespace.PRE_WRAP

                mutableListOf<Pair<Map.Entry<String, List<BlockScanHelpers.Block>>?, File?>>().apply {
                    (0 until maxOf(regionResult.blackListed.size, regionResult.failedToRead.size)).forEach {
                        add(regionResult.blackListed.entries.elementAtOrNull(it) to regionResult.failedToRead.elementAtOrNull(it))
                    }
                }.forEach {
                    row("", "${it.first?.key}\n${it.first?.value?.joinToString("\n")}", it.second.toString())
                }
                row {
                    cellBorders = Borders.ALL
                }
            }
            captionBottom(Text("Based on ${getRegionFilesInWorldFolder(worldFolder).size} region-files", align = TextAlign.CENTER))
        }
    }
}