package com.kye.app.domain

import com.kye.app.data.PaymentMode
import com.kye.app.data.TxnType
import com.kye.app.data.UserError
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** One valid line of an import file, before categories are resolved to IDs. */
data class ImportRow(
    val line: Int,
    val date: LocalDate,
    /** Null when the Type column was blank: decided later from the category. */
    val type: TxnType?,
    val amountPaise: Long,
    val category: String,
    val subcategory: String,
    val payment: PaymentMode,
    val note: String
)

data class ImportProblem(val line: Int, val message: String)

data class ImportFile(val rows: List<ImportRow>, val problems: List<ImportProblem>)

/**
 * KYE's CSV format, shared by the blank template, the export and the importer:
 *   Date,Type,Amount,Category,Subcategory,Payment,Note
 * Columns are found by name, in any order, and unknown columns are ignored, so older
 * exports (with Time / Source / "Amount (INR)") import too. Pure Kotlin, unit-tested on the JVM.
 */
object CsvImport {
    val HEADER = listOf("Date", "Type", "Amount", "Category", "Subcategory", "Payment", "Note")

    /** Excel needs the byte-order mark to open UTF-8 (Tamil notes) correctly. */
    const val BOM = "﻿"

    val TEMPLATE: String = BOM + HEADER.joinToString(",") + "\r\n"

    private val DATE_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    fun formatDate(d: LocalDate): String = d.format(DATE_OUT)

    fun typeLabel(t: TxnType): String = t.name.lowercase().replaceFirstChar { it.uppercase() }

    // Day first, the Indian way. Two-digit years are 20xx.
    private val DATE_IN: List<DateTimeFormatter> = listOf(
        "d-M-uuuu", "d/M/uuuu", "d.M.uuuu", "uuuu-M-d", "uuuu/M/d",
        "d-MMM-uuuu", "d MMM uuuu", "d-MMM-uu", "d MMM uu", "d/MMM/uuuu",
        "d-M-uu", "d/M/uu", "d.M.uu"
    ).map {
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT)
    }

    fun read(text: String): ImportFile {
        // Line numbers match the spreadsheet row numbers, blank rows included.
        val table = parseCsv(text.removePrefix(BOM))
        fun blank(row: List<String>) = row.all { it.isBlank() }
        val headerAt = table.indexOfFirst { !blank(it) }
        if (headerAt < 0) throw UserError("The file is empty")

        val header = table[headerAt].map { it.trim().lowercase(Locale.ROOT) }
        fun col(vararg names: String): Int = header.indexOfFirst { h -> names.any { h == it } }
        val cDate = col("date")
        val cType = col("type")
        val cAmount = col("amount", "amount (inr)", "amount (₹)")
        val cCat = col("category")
        val cSub = col("subcategory", "sub category", "sub-category")
        val cPay = col("payment", "payment mode", "paid with")
        val cNote = col("note", "notes", "description")
        if (cDate < 0 || cAmount < 0) {
            throw UserError("The first row must be the header: ${HEADER.joinToString(",")}. Start from the blank template.")
        }

        val rows = ArrayList<ImportRow>()
        val problems = ArrayList<ImportProblem>()
        table.forEachIndexed { i, cells ->
            if (i <= headerAt || blank(cells)) return@forEachIndexed
            val line = i + 1
            fun cell(c: Int) = if (c in cells.indices) cells[c].trim() else ""

            val date = parseDate(cell(cDate))
            if (date == null) {
                problems += ImportProblem(line, if (cell(cDate).isEmpty()) "Date is missing" else "Date \"${cell(cDate)}\" not understood. Use 07-11-2026.")
                return@forEachIndexed
            }
            val amount = parseAmount(cell(cAmount))
            if (amount == null || amount == 0L) {
                problems += ImportProblem(line, if (cell(cAmount).isEmpty()) "Amount is missing" else "Amount \"${cell(cAmount)}\" isn't a number")
                return@forEachIndexed
            }
            val typeText = cell(cType)
            val type = if (typeText.isEmpty()) null else parseType(typeText)
            if (typeText.isNotEmpty() && type == null) {
                problems += ImportProblem(line, "Type \"$typeText\" should be Expense, Income, Saving or Transfer")
                return@forEachIndexed
            }
            val payText = cell(cPay)
            val payment = if (payText.isEmpty()) PaymentMode.UPI else parsePayment(payText)
            if (payment == null) {
                problems += ImportProblem(line, "Payment \"$payText\" should be UPI, Cash, Debit card, Credit card, Net banking or Wallet")
                return@forEachIndexed
            }
            rows += ImportRow(
                line = line,
                date = date,
                type = type,
                amountPaise = kotlin.math.abs(amount),
                category = cell(cCat),
                subcategory = cell(cSub),
                payment = payment,
                note = cell(cNote)
            )
        }
        return ImportFile(rows, problems)
    }

    fun parseDate(s: String): LocalDate? {
        val t = s.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        for (f in DATE_IN) {
            try {
                return LocalDate.parse(t, f)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    /** "1,250.50", "₹450", "Rs. 90", "-300" → paise. */
    fun parseAmount(s: String): Long? {
        val t = s.trim()
            .replace(Regex("^(₹|rs\\.?|inr)\\s*", RegexOption.IGNORE_CASE), "")
            .replace(",", "")
            .replace(" ", "")
        if (t.isEmpty()) return null
        return runCatching { BigDecimal(t).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact() }.getOrNull()
    }

    fun parseType(s: String): TxnType? = when (s.trim().lowercase(Locale.ROOT)) {
        "expense", "expenses", "spent", "debit", "dr" -> TxnType.EXPENSE
        "income", "credit", "cr", "received" -> TxnType.INCOME
        "saving", "savings", "investment" -> TxnType.SAVING
        "transfer" -> TxnType.TRANSFER
        else -> null
    }

    fun parsePayment(s: String): PaymentMode? {
        val t = s.trim().lowercase(Locale.ROOT)
        PaymentMode.entries.firstOrNull { it.label.lowercase(Locale.ROOT) == t || it.name.lowercase(Locale.ROOT) == t }?.let { return it }
        return when (t) {
            "gpay", "google pay", "phonepe", "paytm", "bhim" -> PaymentMode.UPI
            "debit", "debitcard", "atm card" -> PaymentMode.DEBIT_CARD
            "credit", "creditcard", "card", "cc" -> PaymentMode.CREDIT_CARD
            "netbanking", "neft", "imps", "rtgs", "bank transfer" -> PaymentMode.NET_BANKING
            else -> null
        }
    }

    /** Standard CSV: quoted fields may hold commas, quotes ("") and line breaks. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"'); i++
                    } else quoted = false
                } else field.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                ',' -> { row += field.toString(); field.clear() }
                '\r' -> Unit
                '\n' -> { row += field.toString(); field.clear(); rows += row; row = ArrayList() }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            rows += row
        }
        return rows
    }

    fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
