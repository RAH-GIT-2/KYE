package com.kye.app.work

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.kye.app.data.TxnType
import com.kye.app.domain.Dates
import com.kye.app.domain.Money
import com.kye.app.domain.MonthlyReport
import com.kye.app.domain.ReportLine
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Draws a [MonthlyReport] as an A4 PDF with Android's built-in PdfDocument (no extra library). */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 40f
    private const val BOTTOM = H - 50f

    private val ACCENT = Color.rgb(0x6C, 0x5C, 0xE7)
    private val GREEN = Color.rgb(0x00, 0x94, 0x73)
    private val RED = Color.rgb(0xD6, 0x30, 0x31)
    private val GREY = Color.rgb(0x6B, 0x6B, 0x76)
    private val LINE = Color.rgb(0xE4, 0xE4, 0xEA)
    private val TRACK = Color.rgb(0xEF, 0xEE, 0xF8)

    private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun write(report: MonthlyReport, out: OutputStream) {
        val doc = PdfDocument()
        try {
            Writer(doc, report.month.atDay(1).format(MONTH)).apply {
                header(report)
                summary(report)
                if (report.categories.isNotEmpty()) bars("Where the money went", report.categories, report.expense, ACCENT)
                if (report.budgets.isNotEmpty()) budgets(report)
                if (report.savings.isNotEmpty()) bars("Savings", report.savings, report.saving, GREEN)
                if (report.payments.isNotEmpty()) payments(report)
                if (report.topExpenses.isNotEmpty()) topExpenses(report)
                entries(report)
                finish()
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private class Writer(val doc: PdfDocument, val monthLabel: String) {
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = M

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK }
        val bold = Paint(text).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            newPage()
        }

        fun newPage() {
            if (pageNo > 0) doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            y = M
            val foot = Paint(text).apply { textSize = 8f; color = GREY }
            c.drawText("KYE · Know Your Expense · $monthLabel", M, H - 24f, foot)
            val p = "Page $pageNo"
            c.drawText(p, W - M - foot.measureText(p), H - 24f, foot)
        }

        fun finish() = doc.finishPage(page)

        fun need(h: Float) {
            if (y + h > BOTTOM) newPage()
        }

        fun paint(base: Paint, size: Float, color: Int = Color.BLACK) = Paint(base).apply { textSize = size; this.color = color }

        fun fit(s: String, p: Paint, width: Float): String {
            if (p.measureText(s) <= width) return s
            val n = p.breakText(s, true, width - p.measureText("…"), null)
            return s.take(n.coerceAtLeast(0)) + "…"
        }

        fun right(s: String, x: Float, yy: Float, p: Paint) = c.drawText(s, x - p.measureText(s), yy, p)

        fun section(title: String) {
            need(48f)
            y += 18f
            c.drawText(title, M, y, paint(bold, 13f, ACCENT))
            y += 8f
            fill.color = LINE
            c.drawRect(M, y, W - M, y + 1f, fill)
            y += 16f
        }

        fun header(r: MonthlyReport) {
            c.drawText("Monthly report", M, y + 10f, paint(text, 10f, GREY))
            y += 36f
            c.drawText(monthLabel, M, y, paint(bold, 24f))
            val made = "Made ${Dates.short(LocalDate.now())}"
            right(made, W - M, y, paint(text, 9f, GREY))
            y += 14f
            if (r.isEmpty) {
                y += 16f
                c.drawText("No entries this month.", M, y, paint(text, 11f, GREY))
            }
        }

        fun summary(r: MonthlyReport) {
            y += 12f
            val boxW = (W - 2 * M - 3 * 10f) / 4
            val items = listOf(
                Triple("Income", r.income, GREEN),
                Triple("Spent", r.expense, RED),
                Triple("Saved", r.saving, ACCENT),
                Triple("Left over", r.leftOver, if (r.leftOver < 0) RED else Color.BLACK)
            )
            items.forEachIndexed { i, (label, value, color) ->
                val x = M + i * (boxW + 10f)
                fill.color = TRACK
                c.drawRoundRect(RectF(x, y, x + boxW, y + 54f), 8f, 8f, fill)
                c.drawText(label, x + 10f, y + 18f, paint(text, 9f, GREY))
                c.drawText(fit(Money.format(value), paint(bold, 14f), boxW - 16f), x + 10f, y + 40f, paint(bold, 14f, color))
            }
            y += 54f + 18f
            val lines = ArrayList<String>()
            if (r.days > 0 && r.expense > 0) lines += "Daily average spend: ${Money.format(r.dailyAverage)} over ${r.days} days"
            r.changePercent?.let { pct ->
                val prev = r.month.minusMonths(1).atDay(1).format(MONTH)
                lines += when {
                    pct > 0 -> "Spent $pct% more than $prev (${Money.format(r.previousExpense)})"
                    pct < 0 -> "Spent ${abs(pct)}% less than $prev (${Money.format(r.previousExpense)})"
                    else -> "Spent the same as $prev"
                }
            }
            lines.forEach {
                c.drawText(it, M, y, paint(text, 10f, GREY))
                y += 15f
            }
        }

        fun bars(title: String, lines: List<ReportLine>, total: Long, color: Int) {
            section(title)
            val nameW = 150f
            val amountX = W - M
            val barX = M + nameW + 10f
            val barW = amountX - 120f - barX
            val max = lines.maxOf { it.amount }.coerceAtLeast(1)
            lines.forEach { l ->
                need(20f)
                c.drawText(fit(l.name, text, nameW), M, y, text)
                fill.color = TRACK
                c.drawRoundRect(RectF(barX, y - 8f, barX + barW, y + 1f), 4f, 4f, fill)
                fill.color = color
                c.drawRoundRect(RectF(barX, y - 8f, barX + barW * (l.amount.toFloat() / max), y + 1f), 4f, 4f, fill)
                val pct = if (total > 0) (l.amount * 100 / total) else 0
                right("$pct%", amountX - 78f, y, paint(text, 9f, GREY))
                right(Money.format(l.amount), amountX, y, bold)
                y += 20f
            }
        }

        fun budgets(r: MonthlyReport) {
            section("Budgets")
            r.budgets.forEach { b ->
                need(20f)
                val pct = if (b.limit > 0) (b.spent * 100 / b.limit).toInt() else 0
                c.drawText(fit(b.name, text, 200f), M, y, text)
                right("${Money.format(b.spent)} of ${Money.format(b.limit)}", W - M - 60f, y, text)
                right("$pct%", W - M, y, paint(bold, 10f, if (pct >= 100) RED else if (pct >= 80) Color.rgb(0xE1, 0x70, 0x55) else GREEN))
                y += 20f
            }
        }

        fun payments(r: MonthlyReport) {
            section("Paid by")
            r.payments.forEach { (mode, amount) ->
                need(18f)
                c.drawText(mode.label, M, y, text)
                right(Money.format(amount), W - M, y, text)
                y += 18f
            }
        }

        fun topExpenses(r: MonthlyReport) {
            section("Biggest expenses")
            r.topExpenses.forEach { row ->
                need(18f)
                c.drawText(Dates.toLocalDate(row.txn.dateTime).format(DAY), M, y, paint(text, 10f, GREY))
                val what = listOf(row.title, row.txn.note).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                c.drawText(fit(what, text, 360f), M + 50f, y, text)
                right(Money.format(row.txn.amountPaise, showPaise = true), W - M, y, bold)
                y += 18f
            }
        }

        fun entries(r: MonthlyReport) {
            if (r.entries.isEmpty()) return
            section("All entries (${r.entries.size})")
            val small = paint(text, 9f)
            val grey = paint(text, 9f, GREY)
            r.entries.forEach { row ->
                need(16f)
                val t = row.txn
                c.drawText(Dates.toLocalDate(t.dateTime).format(DAY), M, y, grey)
                c.drawText(fit(row.catName ?: "—", small, 110f), M + 46f, y, small)
                val what = listOfNotNull(row.specificSub, t.note.takeIf { it.isNotBlank() }).distinct().joinToString(" · ")
                c.drawText(fit(what, grey, 200f), M + 162f, y, grey)
                c.drawText(fit(t.paymentMode.label, grey, 62f), M + 370f, y, grey)
                val (sign, color) = when (t.type) {
                    TxnType.EXPENSE -> "−" to Color.BLACK
                    TxnType.INCOME -> "+" to GREEN
                    TxnType.SAVING -> "" to ACCENT
                    TxnType.TRANSFER -> "" to GREY
                }
                right(sign + Money.format(t.amountPaise, showPaise = true), W - M, y, paint(small, 9f, color))
                y += 16f
            }
            need(30f)
            y += 8f
            c.drawText("Purple = saving, green = income, grey = transfer.", M, y, grey)
            y += 14f
        }
    }
}
