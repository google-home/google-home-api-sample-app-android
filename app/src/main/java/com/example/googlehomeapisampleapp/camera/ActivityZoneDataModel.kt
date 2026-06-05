/* Copyright 2026 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.example.googlehomeapisampleapp.camera

import com.google.home.google.ZoneManagementTrait
import com.google.home.matter.serialization.OptionalValue

/**
 * UI data model representing a single activity zone on a camera device.
 *
 * @param zoneId The unique ID assigned by the platform upon creation. Null for new zones.
 * @param zoneName The user-defined label for the zone (e.g., "Backyard").
 * @param vertices The list of 2D Cartesian vertices defining the zone polygon.
 * @param color The display color of the zone.
 * @param maxX The maximum X coordinate bound from [ZoneManagementTrait.twoDCartesianMax].
 * @param maxY The maximum Y coordinate bound from [ZoneManagementTrait.twoDCartesianMax].
 * @param modifiable Whether this zone can be edited/deleted by the user.
 */
data class ActivityZone(
    val zoneId: Int? = null,
    val zoneName: String = "",
    val vertices: List<ActivityZoneVertex> = emptyList(),
    val color: ActivityZoneColor = ActivityZoneColor.Salmon,
    val maxX: Int = 0,
    val maxY: Int = 0,
    val modifiable: Boolean = false,
) {
    companion object {
        /**
         * Converts a [ZoneManagementTrait.ZoneInformationStruct] from the SDK
         * into an [ActivityZone] UI model.
         */
        fun fromZoneInformationStruct(
            struct: ZoneManagementTrait.ZoneInformationStruct,
            maxVertex: ZoneManagementTrait.TwoDCartesianVertexStruct?,
        ): ActivityZone = ActivityZone(
            zoneId = struct.zoneId.toInt(),
            zoneName = struct.twoDCartesianZone.getOrNull()?.name ?: "",
            vertices = struct.twoDCartesianZone.getOrNull()?.vertices?.map {
                ActivityZoneVertex(it.x.toInt(), it.y.toInt())
            } ?: emptyList(),
            color = ActivityZoneColor.fromHexString(
                struct.twoDCartesianZone.getOrNull()?.color?.getOrNull() ?: ""
            ),
            maxX = maxVertex?.x?.toInt() ?: 0,
            maxY = maxVertex?.y?.toInt() ?: 0,
            modifiable = struct.zoneSource == ZoneManagementTrait.ZoneSourceEnum.User,
        )
    }
}

/**
 * Converts an [ActivityZone] to the SDK's [ZoneManagementTrait.TwoDCartesianZoneStruct]
 * for use in create/update commands.
 */
fun ActivityZone.toTwoDCartesianZoneStruct(): ZoneManagementTrait.TwoDCartesianZoneStruct =
    ZoneManagementTrait.TwoDCartesianZoneStruct(
        name = zoneName,
        vertices = vertices.map { vertex ->
            ZoneManagementTrait.TwoDCartesianVertexStruct(
                x = vertex.x.toUShort(),
                y = vertex.y.toUShort(),
            )
        },
        use = listOf(
            ZoneManagementTrait.ZoneUseEnum.Motion,
            ZoneManagementTrait.ZoneUseEnum.Person,
        ),
        color = OptionalValue.present(color.hexString),
    )

/**
 * A single 2D Cartesian point in the zone polygon.
 * Coordinates are scaled integers relative to [ActivityZone.maxX] and [ActivityZone.maxY].
 */
data class ActivityZoneVertex(val x: Int, val y: Int)

/**
 * Available display colors for activity zones.
 * Hex strings match the values expected by the ZoneManagement trait.
 */
enum class ActivityZoneColor(val displayName: String, val hexString: String) {
    Salmon("Pink", "#F439A0"),
    Teal("Cyan", "#24C1E0"),
    Purple("Purple", "#A142F4"),
    Orange("Yellow", "#FBBC04"),
    Grey("Grey", "#9AA0A6");

    companion object {
        /** Returns the [ActivityZoneColor] matching the given hex string, or [Salmon] as default. */
        fun fromHexString(hex: String): ActivityZoneColor =
            entries.firstOrNull { it.hexString.equals(hex, ignoreCase = true) } ?: Salmon
    }
}

/** Cycle of colors used when auto-assigning colors to new zones. */
val ActivityZoneColorCycle = listOf(
    ActivityZoneColor.Salmon,
    ActivityZoneColor.Teal,
    ActivityZoneColor.Purple,
    ActivityZoneColor.Orange,
)

/** Status of an async zone create/update/delete operation. */
sealed interface ZoneUpdateStatus {
    data object Idle : ZoneUpdateStatus
    data object InProgress : ZoneUpdateStatus
    data object Success : ZoneUpdateStatus
    data class Failure(val message: String) : ZoneUpdateStatus
}