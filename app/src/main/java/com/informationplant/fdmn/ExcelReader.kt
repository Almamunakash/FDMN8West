package com.informationplant.fdmn

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ImportRow(
    val name: String, val sex: String, val fcn: String, val dob: String, val relation: String,
    val mainBlock: String, val subBlock: String, val majhi: String, val remarks: String,
) {
    fun toJson(): JSONObject = jo(
        "name" to name, "sex" to sex, "fcn" to fcn, "dob" to dob, "relation" to relation,
        "main_block" to mainBlock, "sub_block" to subBlock, "majhi" to majhi, "remarks" to remarks,
    )
}

data class ParseResult(
    val rows: List<ImportRow>,
    val sheetsUsed: List<String>,
    val sheetsSkipped: List<String>,
    val columnsFound: List<String>,
    val columnsMissing: List<String>,
    val emptyRowsSkipped: Int,
    val badDates: Int,
)

/** Reads .xlsx (and .csv) files without any extra library. */
object ExcelReader {

    private class Cell(val text: String, val numeric: Boolean)

    private val FIELDS = listOf("name", "sex", "fcn", "dob", "relation", "main", "sub", "majhi", "remarks")
    private val LABELS = mapOf(
        "name" to "Name", "sex" to "Sex", "fcn" to "FCN", "dob" to "Date of birth", "relation" to "Relation",
        "main" to "Main Block", "sub" to "Sub-Block", "majhi" to "Majhi", "remarks" to "Remarks",
    )

    fun read(ctx: Context, uri: Uri): ParseResult {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not open the file.")
        if (bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte()) {
            throw IllegalStateException("This is an old Excel file (.xls). Open it in Excel and use \"Save As\" → Excel Workbook (.xlsx), then import again.")
        }
        val sheets: List<Pair<String, List<List<Cell>>>> =
            if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) readXlsx(bytes)
            else listOf("CSV" to readCsv(bytes))
        return build(sheets)
    }

    // ---------------- xlsx ----------------

    private fun readXlsx(bytes: ByteArray): List<Pair<String, List<List<Cell>>>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val n = e.name.removePrefix("/")
                if (!e.isDirectory && (n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || n == "xl/sharedStrings.xml" || n.startsWith("xl/worksheets/"))) {
                    files[n] = zip.readBytes()
                }
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { parseShared(it) } ?: emptyList()
        val rels = files["xl/_rels/workbook.xml.rels"]?.let { parseRels(it) } ?: emptyMap()
        val order = files["xl/workbook.xml"]?.let { parseWorkbook(it) } ?: emptyList()
        val result = ArrayList<Pair<String, List<List<Cell>>>>()
        val used = HashSet<String>()
        for ((name, rid) in order) {
            val target = rels[rid] ?: continue
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            val data = files[path] ?: continue
            used += path
            result += name to parseSheet(data, shared)
        }
        if (result.isEmpty()) {
            files.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") && it !in used }.sorted().forEach { k ->
                result += k.substringAfterLast('/').removeSuffix(".xml") to parseSheet(files[k]!!, shared)
            }
        }
        if (result.isEmpty()) throw IllegalStateException("No sheets were found in this Excel file.")
        return result
    }

    private fun parser(data: ByteArray): XmlPullParser {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(ByteArrayInputStream(data) as InputStream, "UTF-8")
        return p
    }

    private fun XmlPullParser.local(): String = name.substringAfter(':')

    private fun XmlPullParser.attr(n: String): String? {
        for (i in 0 until attributeCount) {
            val a = getAttributeName(i)
            if (a == n || a.substringAfter(':') == n) return getAttributeValue(i)
        }
        return null
    }

    private fun parseShared(data: ByteArray): List<String> {
        val out = ArrayList<String>()
        val p = parser(data)
        val sb = StringBuilder()
        var inSi = false
        var inT = false
        var inRph = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.local()) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> inT = true
                    "rPh" -> inRph = true
                }
                XmlPullParser.TEXT -> if (inSi && inT && !inRph) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.local()) {
                    "si" -> { inSi = false; out.add(sb.toString()) }
                    "t" -> inT = false
                    "rPh" -> inRph = false
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun parseRels(data: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        val p = parser(data)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.local() == "Relationship") {
                val id = p.attr("Id")
                val t = p.attr("Target")
                if (id != null && t != null) out[id] = t
            }
            ev = p.next()
        }
        return out
    }

    private fun parseWorkbook(data: ByteArray): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        val p = parser(data)
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && p.local() == "sheet") {
                val name = p.attr("name") ?: "Sheet"
                val rid = p.attr("id") // r:id
                if (rid != null) out += name to rid
            }
            ev = p.next()
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1)
            else if (c in 'a'..'z') n = n * 26 + (c - 'a' + 1)
            else break
        }
        return n - 1
    }

    private fun parseSheet(data: ByteArray, shared: List<String>): List<List<Cell>> {
        val rows = ArrayList<List<Cell>>()
        val p = parser(data)
        var cells = HashMap<Int, Cell>()
        var nextCol = 0
        var col = 0
        var type: String? = null
        val value = StringBuilder()
        var inV = false
        var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.local()) {
                    "row" -> { cells = HashMap(); nextCol = 0 }
                    "c" -> {
                        val r = p.attr("r")
                        col = if (r != null) colIndex(r) else nextCol
                        if (col < 0) col = nextCol
                        nextCol = col + 1
                        type = p.attr("t")
                        value.setLength(0)
                    }
                    "v" -> inV = true
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inV || inT) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.local()) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val raw = value.toString()
                        val cell = when (type) {
                            "s" -> Cell(raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: "", false)
                            "inlineStr", "str" -> Cell(raw, false)
                            "b" -> Cell(if (raw.trim() == "1") "TRUE" else "FALSE", false)
                            "e" -> Cell("", false)
                            else -> Cell(raw.trim(), raw.isNotBlank())
                        }
                        if (col in 0..200) cells[col] = cell
                    }
                    "row" -> {
                        if (cells.isNotEmpty()) {
                            val max = cells.keys.max()
                            rows.add((0..max).map { cells[it] ?: Cell("", false) })
                        }
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }

    // ---------------- csv ----------------

    private fun readCsv(bytes: ByteArray): List<List<Cell>> {
        var text = String(bytes, Charsets.UTF_8).removePrefix("﻿")
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delim = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = ArrayList<List<Cell>>()
        var row = ArrayList<Cell>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        text += "\n"
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { sb.append('"'); i++ } else quoted = false
                } else sb.append(c)
            } else when (c) {
                '"' -> quoted = true
                delim -> { row.add(Cell(sb.toString(), false)); sb.setLength(0) }
                '\r' -> {}
                '\n' -> {
                    row.add(Cell(sb.toString(), false)); sb.setLength(0)
                    if (row.any { it.text.isNotBlank() }) rows.add(row)
                    row = ArrayList()
                }
                else -> sb.append(c)
            }
            i++
        }
        return rows
    }

    // ---------------- mapping ----------------

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun fieldFor(header: String): String? {
        val h = norm(header)
        if (h.isEmpty()) return null
        return when {
            h.contains("majh") || h.contains("majee") || h.contains("mahjee") -> "majhi"
            h.contains("fcn") || h == "familycountingnumber" || h == "familycount" -> "fcn"
            h.contains("birth") || h == "dob" -> "dob"
            h.contains("relation") -> "relation"
            h.startsWith("main") -> "main"
            h.startsWith("sub") -> "sub"
            h.contains("remark") || h == "note" || h == "notes" || h == "comment" || h == "comments" -> "remarks"
            h == "sex" || h == "gender" -> "sex"
            h == "name" || h.endsWith("name") || h.startsWith("name") -> "name"
            else -> null
        }
    }

    private fun findHeader(rows: List<List<Cell>>): Pair<Int, Map<String, Int>>? {
        for (r in 0 until minOf(rows.size, 20)) {
            val map = HashMap<String, Int>()
            rows[r].forEachIndexed { i, c ->
                val f = fieldFor(c.text)
                if (f != null && f !in map) map[f] = i
            }
            if ("name" in map && map.size >= 3) return r to map
        }
        return null
    }

    private fun numText(c: Cell): String {
        val t = c.text.trim()
        if (!c.numeric) return t
        val d = t.toDoubleOrNull() ?: return t
        return if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else t
    }

    private fun build(sheets: List<Pair<String, List<List<Cell>>>>): ParseResult {
        val out = ArrayList<ImportRow>()
        val used = ArrayList<String>()
        val skippedSheets = ArrayList<String>()
        val found = LinkedHashSet<String>()
        var empty = 0
        var badDates = 0
        for ((sheetName, rows) in sheets) {
            val h = findHeader(rows)
            if (h == null) { if (rows.isNotEmpty()) skippedSheets += sheetName; continue }
            val (hr, map) = h
            used += sheetName
            found += map.keys
            fun get(row: List<Cell>, f: String): Cell? = map[f]?.let { row.getOrNull(it) }
            for (r in hr + 1 until rows.size) {
                val row = rows[r]
                val name = get(row, "name")?.text?.trim().orEmpty()
                val fcn = get(row, "fcn")?.let { numText(it) }.orEmpty()
                if (name.isEmpty() && fcn.isEmpty()) { empty++; continue }
                if (norm(name) == "name") continue // repeated header
                if (name.isEmpty()) { empty++; continue }
                var remarks = get(row, "remarks")?.let { numText(it) }.orEmpty()
                var dob = ""
                val dc = get(row, "dob")
                if (dc != null && dc.text.isNotBlank()) {
                    val d = dc.text.trim().toDoubleOrNull()
                    dob = (if (dc.numeric && d != null) DateUtil.fromExcelSerial(d) else DateUtil.parseFlexible(dc.text)) ?: ""
                    if (dob.isEmpty()) {
                        badDates++
                        remarks = listOf(remarks, "DOB: ${dc.text.trim()}").filter { it.isNotBlank() }.joinToString("; ")
                    }
                }
                val sexRaw = get(row, "sex")?.text?.trim().orEmpty().uppercase()
                val sex = when (sexRaw) { "FEMALE" -> "F"; "MALE" -> "M"; else -> sexRaw }
                out += ImportRow(
                    name = name,
                    sex = sex,
                    fcn = fcn,
                    dob = dob,
                    relation = get(row, "relation")?.text?.trim().orEmpty().uppercase(),
                    mainBlock = get(row, "main")?.let { numText(it) }.orEmpty(),
                    subBlock = get(row, "sub")?.let { numText(it) }.orEmpty(),
                    majhi = get(row, "majhi")?.text?.trim().orEmpty(),
                    remarks = remarks,
                )
            }
        }
        if (used.isEmpty()) {
            throw IllegalStateException("Could not find the heading row. The sheet needs a row with column names like SL, Name, Sex, FCN, Date of birth, Relation, Main Block, Sub-Block, Majhi, Remarks.")
        }
        return ParseResult(
            rows = out,
            sheetsUsed = used,
            sheetsSkipped = skippedSheets,
            columnsFound = FIELDS.filter { it in found }.map { LABELS[it] ?: it },
            columnsMissing = FIELDS.filter { it !in found }.map { LABELS[it] ?: it },
            emptyRowsSkipped = empty,
            badDates = badDates,
        )
    }
}
