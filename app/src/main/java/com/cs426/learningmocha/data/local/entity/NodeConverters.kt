package com.cs426.learningmocha.data.local.entity

import androidx.room.TypeConverter

class NodeConverters {
    @TypeConverter
    fun nodeTypeToString(value: NodeType): String = value.name

    @TypeConverter
    fun stringToNodeType(value: String): NodeType = NodeType.valueOf(value)

    @TypeConverter
    fun statusToString(value: LearningStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): LearningStatus = LearningStatus.valueOf(value)

    @TypeConverter
    fun resourceTypeToString(value: ResourceType): String = value.name

    @TypeConverter
    fun stringToResourceType(value: String): ResourceType = ResourceType.valueOf(value)
}
