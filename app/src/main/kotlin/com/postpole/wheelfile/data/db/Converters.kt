package com.postpole.wheelfile.data.db

import androidx.room.TypeConverter
import org.json.JSONArray

/** Lists are stored as JSON arrays in a TEXT column. */
class Converters {
    @TypeConverter
    fun stringsToJson(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToStrings(value: String): List<String> =
        JSONArray(value).let { arr -> List(arr.length()) { arr.getString(it) } }

    @TypeConverter
    fun intsToJson(value: List<Int>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToInts(value: String): List<Int> =
        JSONArray(value).let { arr -> List(arr.length()) { arr.getInt(it) } }
}
