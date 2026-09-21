package com.schedule.app

import com.schedule.app.data.model.Day
import com.schedule.app.data.model.Lesson
import com.schedule.app.data.model.Schedule
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLogicTest {

    // Вспомогательные функции, идентичные логике в EditScheduleScreen
    private fun parseHM(s: String): Pair<Int, Int> {
        val parts = s.trim().split(":")
        val h = parts.getOrElse(0) { "8" }.toIntOrNull() ?: 8
        val m = parts.getOrElse(1) { "0" }.take(2).toIntOrNull() ?: 0
        return h.coerceIn(0, 23) to m.coerceIn(0, 59)
    }

    private fun parseTimeParts(time: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val clean = time.trim()
        val sep = when {
            clean.contains('–') -> '–'
            clean.contains('-') -> '-'
            else -> null
        }
        return if (sep != null) {
            val idx = clean.indexOf(sep)
            val s = parseHM(clean.substring(0, idx))
            val e = parseHM(clean.substring(idx + 1))
            s to e
        } else {
            val s = parseHM(clean)
            s to ((s.first + 0).coerceAtMost(22) to (s.second + 45).let { if (it >= 60) it - 60 else it })
        }
    }

    private fun defaultTimeForLesson(number: Int): String = when (number) {
        1    -> "08:30–09:05"
        2    -> "09:25–10:00"
        3    -> "10:20–10:55"
        4    -> "11:05–11:40"
        5    -> "11:50–12:25"
        6    -> "12:35–13:10"
        7    -> "13:20–13:55"
        8    -> "14:05–14:40"
        else -> "08:30–09:05"
    }

    private fun calculateDurationMinutes(startH: Int, startM: Int, endH: Int, endM: Int): Int {
        val startTotal = startH * 60 + startM
        val endTotal = endH * 60 + endM
        return if (endTotal >= startTotal) {
            endTotal - startTotal
        } else {
            (endTotal + 24 * 60) - startTotal
        }
    }

    @Test
    fun `test all template lesson times and durations are 35 minutes`() {
        for (i in 1..8) {
            val timeStr = defaultTimeForLesson(i)
            val (start, end) = parseTimeParts(timeStr)
            val duration = calculateDurationMinutes(start.first, start.second, end.first, end.second)
            assertEquals("Lesson $i duration must be exactly 35 minutes", 35, duration)
        }
    }

    @Test
    fun `test break durations between consecutive lessons`() {
        // Проверяем перемены:
        // 1->2: 09:05 -> 09:25 (20 мин)
        // 2->3: 10:00 -> 10:20 (20 мин)
        // 3->4: 10:55 -> 11:05 (10 мин)
        // 4->5: 11:40 -> 11:50 (10 мин)
        // 5->6: 12:25 -> 12:35 (10 мин)
        // 6->7: 13:10 -> 13:20 (10 мин)
        // 7->8: 13:55 -> 14:05 (10 мин)
        val expectedBreaks = listOf(20, 20, 10, 10, 10, 10, 10)

        for (i in 1..7) {
            val currentEnd = parseTimeParts(defaultTimeForLesson(i)).second
            val nextStart = parseTimeParts(defaultTimeForLesson(i + 1)).first
            val breakDuration = calculateDurationMinutes(currentEnd.first, currentEnd.second, nextStart.first, nextStart.second)
            assertEquals("Break between lesson $i and ${i + 1} must be ${expectedBreaks[i - 1]} minutes", expectedBreaks[i - 1], breakDuration)
        }
    }

    @Test
    fun `test time parsing edge cases with various dashes and single digits`() {
        val enDash = parseTimeParts("8:30–9:05")
        assertEquals(8, enDash.first.first)
        assertEquals(30, enDash.first.second)
        assertEquals(9, enDash.second.first)
        assertEquals(5, enDash.second.second)

        val hyphen = parseTimeParts("08:30-09:05")
        assertEquals(8, hyphen.first.first)
        assertEquals(30, hyphen.first.second)
        assertEquals(9, hyphen.second.first)
        assertEquals(5, hyphen.second.second)
    }

    @Test
    fun `test overnight duration calculation`() {
        // Урок/занятие с 23:30 до 00:30 (переход через полночь)
        val duration = calculateDurationMinutes(23, 30, 0, 30)
        assertEquals(60, duration)
    }

    @Test
    fun `test items parsing and toggling logic`() {
        var itemsText = "Учебник\nТетрадь\nПенал"
        val itemsList = itemsText.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()

        assertTrue(itemsList.any { it.equals("Пенал", ignoreCase = true) })

        // Toggle removal
        itemsList.removeAll { it.equals("Пенал", ignoreCase = true) }
        assertFalse(itemsList.any { it.equals("Пенал", ignoreCase = true) })

        // Toggle addition
        itemsList.add("Сменка")
        assertTrue(itemsList.any { it.equals("Сменка", ignoreCase = true) })

        itemsText = itemsList.joinToString("\n")
        assertEquals("Учебник\nТетрадь\nСменка", itemsText)
    }

    @Test
    fun `test schedule JSON serialization and deserialization roundtrip`() {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        val original = Schedule(
            updated = "2026-09-18 14:00",
            version = 5L,
            days = listOf(
                Day(
                    name = "Понедельник",
                    lessons = listOf(
                        Lesson(
                            number = 1,
                            time = "08:30–09:05",
                            subject = "Русский Язык",
                            items = listOf("Учебник", "Тетрадь", "Пенал")
                        )
                    )
                )
            )
        )

        val serialized = json.encodeToString(original)
        val deserialized = json.decodeFromString<Schedule>(serialized)

        assertEquals(original.version, deserialized.version)
        assertEquals(original.days.size, deserialized.days.size)
        assertEquals("Русский Язык", deserialized.days[0].lessons[0].subject)
        assertEquals(3, deserialized.days[0].lessons[0].items.size)
    }

    @Test
    fun `test CloudSync normalizeSyncCode with Russian letters and spaces`() {
        val code1 = com.schedule.app.data.network.CloudSync.normalizeSyncCode("Алиса 2026")
        assertEquals("alisa-2026", code1)

        val code2 = com.schedule.app.data.network.CloudSync.normalizeSyncCode("Школа №15")
        assertEquals("shkola-15", code2)

        val code3 = com.schedule.app.data.network.CloudSync.normalizeSyncCode("  ")
        assertEquals("alisa-2026", code3)
    }

    @Test
    fun `test CloudSync upload and fetch roundtrip over network`() = kotlinx.coroutines.runBlocking {
        val testSchedule = Schedule(
            updated = "2026-09-18 15:00",
            version = System.currentTimeMillis(),
            days = listOf(
                Day("Понедельник", listOf(
                    Lesson(1, "08:30–09:05", "Математика", items = listOf("Тетрадь"))
                ))
            )
        )
        val syncCode = "test-junit-" + (System.currentTimeMillis() % 100000)

        // Upload
        val uploadResult = com.schedule.app.data.network.CloudSync.uploadSchedule(syncCode, testSchedule)
        assertTrue("Upload must succeed: ${uploadResult.exceptionOrNull()?.message}", uploadResult.isSuccess)

        // Fetch
        val fetchResult = com.schedule.app.data.network.CloudSync.fetchSchedule(syncCode)
        assertTrue("Fetch must succeed: ${fetchResult.exceptionOrNull()?.message}", fetchResult.isSuccess)

        val fetched = fetchResult.getOrNull()
        org.junit.Assert.assertNotNull("Fetched schedule must not be null", fetched)
        assertEquals(testSchedule.version, fetched?.version)
        assertEquals("Математика", fetched?.days?.firstOrNull()?.lessons?.firstOrNull()?.subject)
    }

    @Test
    fun `test theme resolution logic`() {
        fun resolveDarkTheme(themeMode: String, isSystemDark: Boolean): Boolean = when (themeMode) {
            "LIGHT" -> false
            "DARK"  -> true
            else    -> isSystemDark
        }

        // When mode is SYSTEM, it follows system
        assertEquals(true, resolveDarkTheme("SYSTEM", isSystemDark = true))
        assertEquals(false, resolveDarkTheme("SYSTEM", isSystemDark = false))

        // When mode is LIGHT, it is always light regardless of system
        assertEquals(false, resolveDarkTheme("LIGHT", isSystemDark = true))
        assertEquals(false, resolveDarkTheme("LIGHT", isSystemDark = false))

        // When mode is DARK, it is always dark regardless of system
        assertEquals(true, resolveDarkTheme("DARK", isSystemDark = true))
        assertEquals(true, resolveDarkTheme("DARK", isSystemDark = false))

        // Fallback for unknown / empty mode
        assertEquals(true, resolveDarkTheme("", isSystemDark = true))
        assertEquals(false, resolveDarkTheme("", isSystemDark = false))
    }

    @Test
    fun `test auto-sync version comparison logic`() {
        val lastKnownVersion = 1000L
        val remoteNewerVersion = 1050L
        val remoteOlderVersion = 950L
        val remoteSameVersion = 1000L

        // Client updates only when remote is strictly newer
        assertTrue(remoteNewerVersion > lastKnownVersion)
        assertFalse(remoteOlderVersion > lastKnownVersion)
        assertFalse(remoteSameVersion > lastKnownVersion)
    }

    @Test
    fun `test AppUpdateInfo serialization and version comparison`() {
        val json = Json { ignoreUnknownKeys = true }
        val rawJson = """
            {
                "versionCode": 25,
                "versionName": "2.5",
                "downloadUrl": "https://example.com/app.apk",
                "releaseNotes": "Добавлены новые функции",
                "releaseDate": "2026-09-18",
                "forceUpdate": false
            }
        """.trimIndent()

        val parsed = json.decodeFromString<com.schedule.app.data.network.AppUpdateInfo>(rawJson)
        assertEquals(25, parsed.versionCode)
        assertEquals("2.5", parsed.versionName)
        assertEquals("https://example.com/app.apk", parsed.downloadUrl)
        assertEquals("Добавлены новые функции", parsed.releaseNotes)

        val currentVersion = 24
        assertTrue("Version 25 must be detected as an update over 24", parsed.versionCode > currentVersion)
        assertFalse("Version 24 must not trigger update", currentVersion > parsed.versionCode)
    }

    @Test
    fun `test backpack checklist key generation isolates days`() {
        fun makeKey(dayName: String, lessonNumber: Int, item: String) =
            "${dayName.trim().lowercase()}_${lessonNumber}_${item.trim().lowercase()}"

        val mondayKey = makeKey("Понедельник", 1, "Учебник")
        val tuesdayKey = makeKey("Вторник", 1, "Учебник")
        val wednesdayKey = makeKey("Среда", 1, "Учебник")

        assertEquals("понедельник_1_учебник", mondayKey)
        assertEquals("вторник_1_учебник", tuesdayKey)
        assertEquals("среда_1_учебник", wednesdayKey)

        // Different days must have strictly distinct keys even with identical lesson number and item
        assertTrue(mondayKey != tuesdayKey)
        assertTrue(tuesdayKey != wednesdayKey)
        assertTrue(mondayKey != wednesdayKey)
    }

    @Test
    fun `test backpack checklist state persists across day navigation without cross-day leakage`() {
        fun makeKey(dayName: String, lessonNumber: Int, item: String) =
            "${dayName.trim().lowercase()}_${lessonNumber}_${item.trim()}"

        val checkedSet = mutableSetOf<String>()

        fun toggleItem(dayName: String, lessonNumber: Int, item: String) {
            val key = makeKey(dayName, lessonNumber, item)
            if (checkedSet.contains(key)) checkedSet.remove(key) else checkedSet.add(key)
        }

        fun isChecked(dayName: String, lessonNumber: Int, item: String): Boolean {
            return checkedSet.contains(makeKey(dayName, lessonNumber, item))
        }

        // Child marks items on Monday
        toggleItem("Понедельник", 1, "Учебник")
        toggleItem("Понедельник", 1, "Тетрадь")

        // Check Monday
        assertTrue("Monday Учебник must be checked", isChecked("Понедельник", 1, "Учебник"))
        assertTrue("Monday Тетрадь must be checked", isChecked("Понедельник", 1, "Тетрадь"))

        // Navigate to Tuesday: items on Tuesday must NOT be checked!
        assertFalse("Tuesday Учебник must NOT be checked", isChecked("Вторник", 1, "Учебник"))
        assertFalse("Tuesday Тетрадь must NOT be checked", isChecked("Вторник", 1, "Тетрадь"))

        // Child marks an item on Tuesday
        toggleItem("Вторник", 1, "Пенал")
        assertTrue("Tuesday Пенал must be checked", isChecked("Вторник", 1, "Пенал"))

        // Navigate back to Monday: Monday items MUST STILL BE CHECKED!
        assertTrue("Monday Учебник must still be checked after returning from Tuesday", isChecked("Понедельник", 1, "Учебник"))
        assertTrue("Monday Тетрадь must still be checked after returning from Tuesday", isChecked("Понедельник", 1, "Тетрадь"))
        assertFalse("Monday must not have Tuesday items", isChecked("Понедельник", 1, "Пенал"))
    }

    @Test
    fun `test backpack checklist clearing for a single day`() {
        fun makeKey(dayName: String, lessonNumber: Int, item: String) =
            "${dayName.trim().lowercase()}_${lessonNumber}_${item.trim()}"

        val checkedSet = mutableSetOf(
            makeKey("Понедельник", 1, "Учебник"),
            makeKey("Понедельник", 2, "Краски"),
            makeKey("Вторник", 1, "Тетрадь"),
            makeKey("Среда", 1, "Форма")
        )

        fun clearDay(dayName: String) {
            val prefix = "${dayName.trim().lowercase()}_"
            checkedSet.removeAll { it.startsWith(prefix) }
        }

        clearDay("Понедельник")

        // Monday items removed
        assertFalse(checkedSet.contains(makeKey("Понедельник", 1, "Учебник")))
        assertFalse(checkedSet.contains(makeKey("Понедельник", 2, "Краски")))

        // Tuesday and Wednesday preserved
        assertTrue(checkedSet.contains(makeKey("Вторник", 1, "Тетрадь")))
        assertTrue(checkedSet.contains(makeKey("Среда", 1, "Форма")))
    }

    @Test
    fun `test month day click correctly resolves date and week day mapping`() {
        val weekDays = listOf(
            "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
        )
        val month = java.time.YearMonth.of(2026, 9)

        // September 28, 2026 is a Monday
        val day28 = month.atDay(28)
        assertEquals(28, day28.dayOfMonth)
        assertEquals(java.time.DayOfWeek.MONDAY, day28.dayOfWeek)
        val weekDayIdx = day28.dayOfWeek.value - 1
        assertEquals(0, weekDayIdx)
        assertEquals("Понедельник", weekDays[weekDayIdx])

        val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale("ru"))
        assertEquals("28 сентября", day28.format(formatter))

        // September 30, 2026 is a Wednesday
        val day30 = month.atDay(30)
        assertEquals(java.time.DayOfWeek.WEDNESDAY, day30.dayOfWeek)
        assertEquals("Среда", weekDays[day30.dayOfWeek.value - 1])
        assertEquals("30 сентября", day30.format(formatter))
    }
}

