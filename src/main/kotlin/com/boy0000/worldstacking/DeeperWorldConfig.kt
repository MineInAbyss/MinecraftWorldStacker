package com.boy0000.worldstacking

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.File

object DeeperWorldConfig {

    private val offset = 16384

    val config = File("D:\\WORLDSTACKING\\deeperworld.yml")

    val sections: Sections = Yaml.default.decodeFromString(config.readText())

    fun WorldStacker.Block.adjustLocation(): WorldStacker.Block {
        val yOffset = sections.sections.firstOrNull { it.region.contains(this.location) }?.refTop?.x?.floorDiv(offset)?.times() ?:  return this
        this.copy(location = this.location)
    }

    @Serializable
    data class Sections(val sections: List<Section>)

    @Serializable
    data class Section(
        val name: String,
        val region: Region,
        val world: String,
        val refTop: Coordinates,
        val refBottom: Coordinates
    )

    @Serializable
    data class Region(
        val start: Coordinates,
        val end: Coordinates
    ) {

        fun contains(location: WorldStacker.Location) = contains(location.x, location.y, location.z)

        fun contains(x: Int, y: Int, z: Int): Boolean {
            return x in start.x..end.x &&
                    y in end.y..start.y && // Y is inverted because Minecraft's Y-axis goes from bottom to top
                    z in start.z..end.z
        }
    }

    @Serializable(with = CoordinatesSerializer::class)
    data class Coordinates(
        val x: Int,
        val y: Int,
        val z: Int
    )

    object CoordinatesSerializer : KSerializer<Coordinates> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Coordinates", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Coordinates) {
            encoder.encodeString("${value.x},${value.y},${value.z}")
        }

        override fun deserialize(decoder: Decoder): Coordinates {
            val string = decoder.decodeString()
            val (x, y, z) = string.split(",").map { it.toInt() }
            return Coordinates(x, y, z)
        }
    }
}