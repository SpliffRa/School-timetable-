package com.schedule.app.data.excel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Xml
import androidx.core.content.FileProvider
import com.schedule.app.data.model.Day
import com.schedule.app.data.model.Lesson
import com.schedule.app.data.model.Schedule
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Утилита для экспорта и импорта школьного расписания в формате Microsoft Excel (.xlsx).
 * Работает автономно через стандартный формат OpenXML без тяжёлых внешних библиотек.
 */
object ScheduleExcelManager {

    private const val TAG = "ScheduleExcelManager"

    private val WEEK_DAYS = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // ЭКСПОРТ В EXCEL (.XLSX)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Создаёт файл .xlsx из текущего расписания и возвращает ссылку на него.
     */
    fun exportToExcelFile(context: Context, schedule: Schedule): File {
        val fileName = "Raspisanie_urokov.xlsx"
        val outputFile = File(context.cacheDir, fileName)
        if (outputFile.exists()) {
            outputFile.delete()
        }

        FileOutputStream(outputFile).use { fos ->
            ZipOutputStream(fos).use { zip ->
                // 1. [Content_Types].xml
                zip.putNextEntry(ZipEntry("[Content_Types].xml"))
                zip.write(buildContentTypes().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 2. _rels/.rels
                zip.putNextEntry(ZipEntry("_rels/.rels"))
                zip.write(buildRootRels().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 3. xl/_rels/workbook.xml.rels
                zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                zip.write(buildWorkbookRels().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 4. xl/workbook.xml
                zip.putNextEntry(ZipEntry("xl/workbook.xml"))
                zip.write(buildWorkbookXml().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 5. xl/styles.xml (Стили: заголовки с синим фоном и границы)
                zip.putNextEntry(ZipEntry("xl/styles.xml"))
                zip.write(buildStylesXml().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 6. xl/worksheets/sheet1.xml
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zip.write(buildWorksheetXml(schedule).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }

        return outputFile
    }

    /**
     * Открывает стандартный диалог Android «Поделиться» для созданного файла Excel.
     */
    fun shareExcelFile(context: Context, excelFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            excelFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Школьное расписание уроков")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Экспорт расписания в Excel")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ИМПОРТ ИЗ EXCEL (.XLSX ИЛИ .CSV)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Читает расписание из выбранного пользователем файла (.xlsx или .csv).
     */
    fun importFromExcelOrCsv(context: Context, uri: Uri): Result<Schedule> {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return Result.failure(Exception("Не удалось открыть выбранный файл"))

            val bytes = inputStream.use { it.readBytes() }

            // Проверяем ZIP-сигнатуру (PK.. для .xlsx)
            val isZip = bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

            if (isZip) {
                parseXlsx(bytes)
            } else {
                parseCsv(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing schedule: ${e.message}", e)
            Result.failure(Exception("Ошибка чтения таблицы: ${e.localizedMessage ?: "неверный формат"}"))
        }
    }

    private fun parseXlsx(bytes: ByteArray): Result<Schedule> {
        var sheetXml: String? = null
        var sharedStringsXml: String? = null

        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name == "xl/worksheets/sheet1.xml" || entry.name.endsWith("sheet1.xml") -> {
                        sheetXml = zis.reader(Charsets.UTF_8).readText()
                    }
                    entry.name == "xl/sharedStrings.xml" -> {
                        sharedStringsXml = zis.reader(Charsets.UTF_8).readText()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (sheetXml == null) {
            return Result.failure(Exception("В файле Excel не найден лист с расписанием (sheet1.xml)"))
        }

        val sharedStrings = if (sharedStringsXml != null) parseSharedStrings(sharedStringsXml!!) else emptyList()
        val parsedRows = parseSheetData(sheetXml!!, sharedStrings)

        if (parsedRows.isEmpty()) {
            return Result.failure(Exception("Таблица Excel пуста или не содержит строк с уроками"))
        }

        return convertRowsToSchedule(parsedRows)
    }

    private fun parseSharedStrings(xmlStr: String): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmlStr))

        var eventType = parser.eventType
        var currentString = StringBuilder()
        var insideSi = false
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> {
                            insideSi = true
                            currentString = StringBuilder()
                        }
                        "t" -> insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideSi && insideT) {
                        currentString.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> insideT = false
                        "si" -> {
                            insideSi = false
                            result.add(currentString.toString())
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return result
    }

    private fun parseSheetData(xmlStr: String, sharedStrings: List<String>): List<Map<Int, String>> {
        val rows = mutableListOf<Map<Int, String>>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmlStr))

        var eventType = parser.eventType
        var currentRow = mutableMapOf<Int, String>()
        var currentCellCol = 0
        var currentCellType = ""
        var insideValue = false
        var cellText = StringBuilder()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRow = mutableMapOf()
                        }
                        "c" -> {
                            val rAttr = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentCellCol = columnRefToIndex(rAttr)
                            cellText = StringBuilder()
                        }
                        "v", "t" -> {
                            insideValue = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideValue) {
                        cellText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v", "t" -> {
                            insideValue = false
                        }
                        "c" -> {
                            val raw = cellText.toString()
                            val value = if (currentCellType == "s") {
                                val idx = raw.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                            } else {
                                raw
                            }
                            if (currentCellCol >= 0) {
                                currentRow[currentCellCol] = value.trim()
                            }
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                rows.add(currentRow)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    private fun columnRefToIndex(ref: String): Int {
        val colLetters = ref.takeWhile { it.isLetter() }.uppercase()
        if (colLetters.isEmpty()) return -1
        var idx = 0
        for (ch in colLetters) {
            idx = idx * 26 + (ch - 'A' + 1)
        }
        return idx - 1 // 0-based
    }

    private fun parseCsv(bytes: ByteArray): Result<Schedule> {
        val text = String(bytes, Charsets.UTF_8)
        val lines = text.lines().filter { it.isNotBlank() }
        val rows = mutableListOf<Map<Int, String>>()

        for (line in lines) {
            val delimiter = if (line.contains(';')) ';' else ','
            val cols = line.split(delimiter).map { it.trim('"', ' ', '\t') }
            val rowMap = mutableMapOf<Int, String>()
            cols.forEachIndexed { i, s -> rowMap[i] = s }
            rows.add(rowMap)
        }

        return convertRowsToSchedule(rows)
    }

    private fun convertRowsToSchedule(rows: List<Map<Int, String>>): Result<Schedule> {
        // Проверяем, является ли первая строка шапкой
        val startIndex = if (rows.isNotEmpty() && isHeaderRow(rows[0])) 1 else 0

        val daysMap = linkedMapOf<String, MutableList<Lesson>>()
        WEEK_DAYS.forEach { daysMap[it] = mutableListOf() }

        var currentDay = "Понедельник"

        for (i in startIndex until rows.size) {
            val row = rows[i]
            val dayCol = row[0]?.trim() ?: ""
            val numCol = row[1]?.trim() ?: ""
            val timeCol = row[2]?.trim() ?: ""
            val subjectCol = row[3]?.trim() ?: ""
            val roomCol = row[4]?.trim() ?: ""
            val itemsCol = row[5]?.trim() ?: ""

            // Определение дня недели
            if (dayCol.isNotBlank()) {
                val matchedDay = matchDayName(dayCol)
                if (matchedDay != null) {
                    currentDay = matchedDay
                }
            }

            // Если предмет пустой, пропускаем строку
            if (subjectCol.isBlank()) continue

            val lessonNum = numCol.toIntOrNull() ?: (daysMap[currentDay]?.size?.plus(1) ?: 1)
            val itemsList = if (itemsCol.isNotBlank()) {
                itemsCol.split(",", ";", "\n").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            val lesson = Lesson(
                number = lessonNum,
                time = timeCol.ifBlank { defaultTimeForLesson(lessonNum) },
                subject = subjectCol,
                room = roomCol,
                items = itemsList
            )

            daysMap[currentDay]?.add(lesson)
        }

        // Сортируем уроки каждого дня по номеру
        val dayList = WEEK_DAYS.map { dayName ->
            val sortedLessons = daysMap[dayName]?.sortedBy { it.number } ?: emptyList()
            Day(name = dayName, lessons = sortedLessons)
        }

        val totalLessons = dayList.sumOf { it.lessons.size }
        if (totalLessons == 0) {
            return Result.failure(Exception("В таблице не найдено ни одного урока. Проверьте правильность колонок."))
        }

        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        val schedule = Schedule(
            updated = now,
            version = System.currentTimeMillis(),
            days = dayList
        )

        return Result.success(schedule)
    }

    private fun isHeaderRow(row: Map<Int, String>): Boolean {
        val first = row[0]?.lowercase() ?: ""
        val fourth = row[3]?.lowercase() ?: ""
        return first.contains("день") || first.contains("day") || fourth.contains("предмет") || fourth.contains("урок")
    }

    private fun matchDayName(raw: String): String? {
        val s = raw.trim().lowercase()
        return when {
            s.startsWith("пон") || s.startsWith("пн") -> "Понедельник"
            s.startsWith("вто") || s.startsWith("вт") -> "Вторник"
            s.startsWith("сре") || s.startsWith("ср") -> "Среда"
            s.startsWith("чет") || s.startsWith("чт") -> "Четверг"
            s.startsWith("пят") || s.startsWith("пт") -> "Пятница"
            s.startsWith("суб") || s.startsWith("сб") -> "Суббота"
            s.startsWith("вос") || s.startsWith("вс") -> "Воскресенье"
            else -> null
        }
    }

    private fun defaultTimeForLesson(num: Int): String {
        return when (num) {
            1 -> "08:30 - 09:15"
            2 -> "09:25 - 10:10"
            3 -> "10:30 - 11:15"
            4 -> "11:35 - 12:20"
            5 -> "12:35 - 13:20"
            6 -> "13:30 - 14:15"
            7 -> "14:25 - 15:10"
            else -> ""
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ГЕНЕРАТОРЫ XML ДЛЯ OPENXML (.XLSX)
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildContentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun buildRootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun buildWorkbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun buildWorkbookXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Расписание" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    private fun buildStylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF2A52BE"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFD3D3D3"/></left>
      <right style="thin"><color rgb="FFD3D3D3"/></right>
      <top style="thin"><color rgb="FFD3D3D3"/></top>
      <bottom style="thin"><color rgb="FFD3D3D3"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1">
      <alignment vertical="center"/>
    </xf>
  </cellXfs>
</styleSheet>"""

    private fun buildWorksheetXml(schedule: Schedule): String {
        val colLetters = listOf("A", "B", "C", "D", "E", "F")
        val headers = listOf("День недели", "№", "Время", "Предмет", "Кабинет", "Что взять с собой (в рюкзак)")

        val sb = java.lang.StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="16" customWidth="1"/>
    <col min="2" max="2" width="6" customWidth="1"/>
    <col min="3" max="3" width="16" customWidth="1"/>
    <col min="4" max="4" width="26" customWidth="1"/>
    <col min="5" max="5" width="14" customWidth="1"/>
    <col min="6" max="6" width="45" customWidth="1"/>
  </cols>
  <sheetData>
""")

        // Строка заголовков (стиль 1)
        sb.append("    <row r=\"1\" ht=\"28\" customHeight=\"1\">\n")
        headers.forEachIndexed { i, h ->
            val ref = "${colLetters[i]}1"
            sb.append("      <c r=\"$ref\" s=\"1\" t=\"inlineStr\"><is><t>${xmlEscape(h)}</t></is></c>\n")
        }
        sb.append("    </row>\n")

        var rowIndex = 2
        for (day in schedule.days) {
            for (lesson in day.lessons) {
                sb.append("    <row r=\"$rowIndex\" ht=\"22\" customHeight=\"1\">\n")
                val vals = listOf(
                    day.name,
                    lesson.number.toString(),
                    lesson.time,
                    lesson.subject,
                    lesson.room,
                    lesson.items.joinToString(", ")
                )
                vals.forEachIndexed { colIdx, v ->
                    val ref = "${colLetters[colIdx]}$rowIndex"
                    sb.append("      <c r=\"$ref\" s=\"2\" t=\"inlineStr\"><is><t>${xmlEscape(v)}</t></is></c>\n")
                }
                sb.append("    </row>\n")
                rowIndex++
            }
        }

        sb.append("""  </sheetData>
</worksheet>""")
        return sb.toString()
    }

    private fun xmlEscape(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
