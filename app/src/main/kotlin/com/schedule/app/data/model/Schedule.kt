package com.schedule.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Schedule(
    val updated: String,
    /** Unix-время последнего изменения в мс. Используется для обнаружения обновлений при синхронизации. */
    val version: Long = 0L,
    val days: List<Day>
)

@Serializable
data class Day(
    val name: String,
    val lessons: List<Lesson>
)

@Serializable
data class Lesson(
    val number: Int,
    val time: String,
    val subject: String,
    val room: String = "",          // сохранено для совместимости, в UI не отображается
    val items: List<String>
)
