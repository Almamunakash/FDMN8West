package com.informationplant.fdmn

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporter {
    val headers = listOf("SL", "Name", "Sex", "FCN", "Date of birth", "Relation", "Main Block", "Sub-Block", "Majhi", "Remarks")

    private fun values(i: Int, p: Person) = listOf(
        (i + 1).toString(), p.name, p.sex, p.fcn, p.dobText, p.relation, p.mainBlock, p.subBlock, p.majhi, p.remarks,
    )

    fun fileName(ext: String): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm", Locale.US))
        return "FDMN_8W_$stamp.$ext"
    }

    fun exportDir(ctx: Context): File = File(ctx.cacheDir, "exports").apply {
        mkdirs()
        listFiles()?.forEach { if (it.lastModified() < System.currentTimeMillis() - 86_400_000L) it.delete() }
    }

    // ---------------- Excel (.xlsx) ----------------

    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when {
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '"' -> sb.append("&quot;")
                c.code < 0x20 && c != '\t' && c != '\n' && c != '\r' -> {}
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }
        return sb.toString()
    }

    fun writeXlsx(file: File, rows: List<Person>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Beneficiaries" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFCDEBE1"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")

            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val w = zip.bufferedWriter(Charsets.UTF_8)
            w.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="1" width="7" customWidth="1"/><col min="2" max="2" width="28" customWidth="1"/><col min="3" max="3" width="6" customWidth="1"/><col min="4" max="4" width="11" customWidth="1"/><col min="5" max="5" width="14" customWidth="1"/><col min="6" max="6" width="10" customWidth="1"/><col min="7" max="7" width="11" customWidth="1"/><col min="8" max="8" width="11" customWidth="1"/><col min="9" max="9" width="22" customWidth="1"/><col min="10" max="10" width="30" customWidth="1"/></cols><sheetData>""")
            w.write("<row r=\"1\">")
            headers.forEachIndexed { c, h -> w.write("<c r=\"${col(c)}1\" t=\"inlineStr\" s=\"1\"><is><t>${esc(h)}</t></is></c>") }
            w.write("</row>")
            rows.forEachIndexed { i, p ->
                val r = i + 2
                w.write("<row r=\"$r\">")
                values(i, p).forEachIndexed { c, v ->
                    val ref = "${col(c)}$r"
                    if (c == 0) {
                        w.write("<c r=\"$ref\" s=\"2\"><v>${i + 1}</v></c>")
                    } else {
                        w.write("<c r=\"$ref\" t=\"inlineStr\" s=\"2\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>")
                    }
                }
                w.write("</row>")
            }
            w.write("</sheetData></worksheet>")
            w.flush()
            zip.closeEntry()
        }
    }

    // ---------------- PDF ----------------

    fun writePdf(file: File, rows: List<Person>, subtitle: String) {
        val pageW = 842 // A4 landscape, in points
        val pageH = 595
        val margin = 28f
        val widths = floatArrayOf(34f, 150f, 30f, 60f, 70f, 52f, 46f, 52f, 120f, 0f)
        widths[9] = (pageW - margin * 2) - widths.sum()
        val rowH = 16f
        val titleH = 46f
        val footerH = 20f
        val firstRows = ((pageH - margin * 2 - titleH - footerH - rowH) / rowH).toInt()
        val otherRows = ((pageH - margin * 2 - footerH - rowH) / rowH).toInt()
        val totalPages = if (rows.size <= firstRows) 1 else 1 + Math.ceil((rows.size - firstRows) / otherRows.toDouble()).toInt()

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = Color.rgb(23, 32, 29) }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(11, 93, 75) }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.rgb(71, 85, 79) }
        val fillHead = Paint().apply { color = Color.rgb(205, 235, 225) }
        val fillAlt = Paint().apply { color = Color.rgb(244, 248, 247) }
        val line = Paint().apply { color = Color.rgb(201, 214, 209); strokeWidth = 0.6f }

        fun fit(s: String, w: Float, p: Paint): String {
            if (p.measureText(s) <= w) return s
            var end = s.length
            while (end > 0 && p.measureText(s.substring(0, end) + "…") > w) end--
            return s.substring(0, end) + "…"
        }

        val doc = PdfDocument()
        try {
            var index = 0
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH))
            for (pageNo in 1..totalPages) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
                val c = page.canvas
                var y = margin
                if (pageNo == 1) {
                    c.drawText("8 West FDMN Database — Beneficiary list", margin, y + 14f, title)
                    c.drawText("$subtitle   ·   ${fmt(rows.size)} records   ·   Exported $stamp", margin, y + 32f, small)
                    y += titleH
                }
                // header row
                c.drawRect(margin, y, pageW - margin, y + rowH, fillHead)
                var x = margin
                headers.forEachIndexed { i, h ->
                    c.drawText(fit(h, widths[i] - 6f, bold), x + 3f, y + 11.5f, bold)
                    x += widths[i]
                }
                y += rowH
                val count = if (pageNo == 1) firstRows else otherRows
                var n = 0
                while (n < count && index < rows.size) {
                    if (index % 2 == 1) c.drawRect(margin, y, pageW - margin, y + rowH, fillAlt)
                    x = margin
                    values(index, rows[index]).forEachIndexed { i, v ->
                        c.drawText(fit(v, widths[i] - 6f, text), x + 3f, y + 11.5f, text)
                        x += widths[i]
                    }
                    y += rowH
                    c.drawLine(margin, y, pageW - margin, y, line)
                    index++; n++
                }
                c.drawText("Page $pageNo of $totalPages", pageW - margin - 70f, pageH - margin + 4f, small)
                c.drawText("Confidential — for official use only", margin, pageH - margin + 4f, small)
                doc.finishPage(page)
            }
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    // ---------------- share / save ----------------

    fun uriFor(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)

    fun share(ctx: Context, file: File, mime: String) {
        val uri = uriFor(ctx, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share ${file.name}"))
    }

    fun open(ctx: Context, file: File, mime: String) {
        val uri = uriFor(ctx, file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(view, "Open with"))
    }

    fun copyTo(ctx: Context, file: File, dest: Uri) {
        ctx.contentResolver.openOutputStream(dest)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val PDF = "application/pdf"
}
