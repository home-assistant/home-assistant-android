package io.homeassistant.companion.android.database.widget

import androidx.room.TypeConverter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.homeassistant.companion.android.widgets.multi.MultiWidget.Orientation
import io.homeassistant.companion.android.widgets.multi.elements.MultiWidgetElementEntity

class MultiWidgetEntityConverters {
    @TypeConverter
    fun listToJson(elements: List<MultiWidgetElementEntity>?): String =
        jacksonObjectMapper().writeValueAsString(elements)

    @TypeConverter
    fun jsonToList(json: String): List<MultiWidgetElementEntity> =
        jacksonObjectMapper().readValue(json)

    @TypeConverter
    fun orientationToBoolean(orientation: Orientation): Boolean =
        orientation != Orientation.HORIZONTAL

    @TypeConverter
    fun booleanToOrientation(boolOrient: Boolean): Orientation =
        if (boolOrient) Orientation.VERTICAL else Orientation.HORIZONTAL
}
