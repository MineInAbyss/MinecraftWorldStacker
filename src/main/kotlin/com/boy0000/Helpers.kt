package com.boy0000

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
}