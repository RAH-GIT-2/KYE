package com.kye.app.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val seedVersion: Int,
    val categories: List<Category>,
    val subcategories: List<Subcategory>,
    val txns: List<Txn>,
    val rules: List<RecurringRule>,
    val budgets: List<Budget>,
    val keywords: List<KeywordRule>,
    val loans: List<Loan> = emptyList(),
    val loanPayments: List<LoanPayment> = emptyList(),
    val events: List<Event> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val warranties: List<Warranty> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val schemes: List<GoldScheme> = emptyList()
)

/**
 * A complete, human-readable copy of the database. IDs are kept so every link between
 * entries, categories, rules and keywords survives a restore exactly.
 * Bump [FORMAT] only for changes older app versions can't read.
 */
object BackupJson {
    private const val APP = "KYE"
    private const val FORMAT = 1

    fun encode(d: BackupData): String {
        val root = JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("exportedAt", System.currentTimeMillis())
            .put("seedVersion", d.seedVersion)
            .put("categories", JSONArray(d.categories.map {
                JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji).put("color", it.color)
                    .put("kind", it.kind.name).put("sortOrder", it.sortOrder).put("isArchived", it.isArchived)
            }))
            .put("subcategories", JSONArray(d.subcategories.map {
                JSONObject().put("id", it.id).put("categoryId", it.categoryId).put("name", it.name)
                    .put("isTransfer", it.isTransfer).put("isArchived", it.isArchived).put("isDefault", it.isDefault)
            }))
            .put("transactions", JSONArray(d.txns.map {
                JSONObject().put("id", it.id).put("amountPaise", it.amountPaise).put("type", it.type.name)
                    .putOpt("categoryId", it.categoryId).putOpt("subcategoryId", it.subcategoryId)
                    .put("dateTime", it.dateTime).put("paymentMode", it.paymentMode.name).put("note", it.note)
                    .put("source", it.source.name).putOpt("recurringRuleId", it.recurringRuleId)
                    .putOpt("rawVoiceText", it.rawVoiceText).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt)
                    .putOpt("eventId", it.eventId).putOpt("goalId", it.goalId).putOpt("schemeId", it.schemeId)
            }))
            .put("recurringRules", JSONArray(d.rules.map {
                JSONObject().put("id", it.id).put("name", it.name).put("amountPaise", it.amountPaise).put("type", it.type.name)
                    .putOpt("categoryId", it.categoryId).putOpt("subcategoryId", it.subcategoryId)
                    .put("paymentMode", it.paymentMode.name).put("unit", it.unit.name).put("interval", it.interval)
                    .put("anchorDate", it.anchorDate).put("occurrenceIndex", it.occurrenceIndex)
                    .put("nextDueDate", it.nextDueDate).putOpt("endDate", it.endDate).put("mode", it.mode.name)
                    .put("remindDaysBefore", it.remindDaysBefore).put("isActive", it.isActive)
                    .putOpt("lastRemindedFor", it.lastRemindedFor)
            }))
            .put("budgets", JSONArray(d.budgets.map {
                JSONObject().put("categoryId", it.categoryId).put("monthlyLimitPaise", it.monthlyLimitPaise)
            }))
            .put("keywords", JSONArray(d.keywords.map {
                JSONObject().put("id", it.id).put("phrase", it.phrase).put("categoryId", it.categoryId)
                    .putOpt("subcategoryId", it.subcategoryId).put("weight", it.weight).put("isUserLearned", it.isUserLearned)
            }))
            .put("loans", JSONArray(d.loans.map {
                JSONObject().put("id", it.id).put("person", it.person).put("direction", it.direction.name)
                    .put("amountPaise", it.amountPaise).put("date", it.date).putOpt("dueDate", it.dueDate)
                    .put("note", it.note).put("isClosed", it.isClosed).putOpt("lastRemindedOn", it.lastRemindedOn)
                    .put("createdAt", it.createdAt)
            }))
            .put("loanPayments", JSONArray(d.loanPayments.map {
                JSONObject().put("id", it.id).put("loanId", it.loanId).put("amountPaise", it.amountPaise)
                    .put("date", it.date).put("note", it.note)
            }))
            .put("events", JSONArray(d.events.map {
                JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji)
                    .putOpt("budgetPaise", it.budgetPaise).putOpt("startDate", it.startDate).putOpt("endDate", it.endDate)
                    .put("isClosed", it.isClosed).put("createdAt", it.createdAt)
            }))
            .put("attachments", JSONArray(d.attachments.map {
                JSONObject().put("id", it.id).put("txnId", it.txnId).put("fileName", it.fileName).put("createdAt", it.createdAt)
            }))
            .put("warranties", JSONArray(d.warranties.map {
                JSONObject().put("txnId", it.txnId).put("untilDate", it.untilDate).put("stage", it.stage)
            }))
            .put("goals", JSONArray(d.goals.map {
                JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji).put("targetPaise", it.targetPaise)
                    .putOpt("targetDate", it.targetDate).put("isClosed", it.isClosed).put("createdAt", it.createdAt)
            }))
            .put("goldSchemes", JSONArray(d.schemes.map {
                JSONObject().put("id", it.id).put("name", it.name).put("monthlyPaise", it.monthlyPaise)
                    .put("months", it.months).put("startDate", it.startDate).put("bonusPaise", it.bonusPaise)
                    .put("remind", it.remind).putOpt("lastRemindedFor", it.lastRemindedFor).put("note", it.note)
                    .put("isClosed", it.isClosed).putOpt("closedOn", it.closedOn).put("createdAt", it.createdAt)
            }))
        return root.toString(1)
    }

    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw UserError("This isn't a KYE backup file")
        }
        if (root.optString("app") != APP) throw UserError("This isn't a KYE backup file")
        val format = root.optInt("format", 0)
        if (format > FORMAT) throw UserError("This backup is from a newer version of KYE. Update the app first.")

        try {
            val categories = root.getJSONArray("categories").objects().map {
                Category(
                    id = it.getLong("id"), name = it.getString("name"), emoji = it.getString("emoji"),
                    color = it.getLong("color"), kind = enumOr(it.optString("kind"), CategoryKind.EXPENSE),
                    sortOrder = it.optInt("sortOrder"), isArchived = it.optBoolean("isArchived")
                )
            }
            val catIds = categories.map { it.id }.toSet()
            val subcategories = root.getJSONArray("subcategories").objects().map {
                Subcategory(
                    id = it.getLong("id"), categoryId = it.getLong("categoryId"), name = it.getString("name"),
                    isTransfer = it.optBoolean("isTransfer"), isArchived = it.optBoolean("isArchived"),
                    isDefault = it.optBoolean("isDefault")
                )
            }.filter { it.categoryId in catIds }
            val txns = root.getJSONArray("transactions").objects().map {
                Txn(
                    id = it.getLong("id"), amountPaise = it.getLong("amountPaise"),
                    type = enumOr(it.optString("type"), TxnType.EXPENSE),
                    categoryId = it.longOrNull("categoryId"), subcategoryId = it.longOrNull("subcategoryId"),
                    dateTime = it.getLong("dateTime"), paymentMode = enumOr(it.optString("paymentMode"), PaymentMode.UPI),
                    note = it.optString("note"), source = enumOr(it.optString("source"), Source.MANUAL),
                    recurringRuleId = it.longOrNull("recurringRuleId"), rawVoiceText = it.stringOrNull("rawVoiceText"),
                    createdAt = it.optLong("createdAt"), updatedAt = it.optLong("updatedAt"),
                    eventId = it.longOrNull("eventId"),
                    goalId = it.longOrNull("goalId"),
                    schemeId = it.longOrNull("schemeId")
                )
            }
            val rules = root.getJSONArray("recurringRules").objects().map {
                RecurringRule(
                    id = it.getLong("id"), name = it.getString("name"), amountPaise = it.getLong("amountPaise"),
                    type = enumOr(it.optString("type"), TxnType.EXPENSE),
                    categoryId = it.longOrNull("categoryId"), subcategoryId = it.longOrNull("subcategoryId"),
                    paymentMode = enumOr(it.optString("paymentMode"), PaymentMode.UPI),
                    unit = enumOr(it.optString("unit"), RepeatUnit.MONTH), interval = it.optInt("interval", 1),
                    anchorDate = it.getLong("anchorDate"), occurrenceIndex = it.optInt("occurrenceIndex"),
                    nextDueDate = it.getLong("nextDueDate"), endDate = it.longOrNull("endDate"),
                    mode = enumOr(it.optString("mode"), RecurringMode.AUTO_ADD),
                    remindDaysBefore = it.optInt("remindDaysBefore"), isActive = it.optBoolean("isActive", true),
                    lastRemindedFor = it.longOrNull("lastRemindedFor")
                )
            }
            val budgets = root.getJSONArray("budgets").objects().map {
                Budget(it.getLong("categoryId"), it.getLong("monthlyLimitPaise"))
            }
            val keywords = root.getJSONArray("keywords").objects().map {
                KeywordRule(
                    id = it.getLong("id"), phrase = it.getString("phrase"), categoryId = it.getLong("categoryId"),
                    subcategoryId = it.longOrNull("subcategoryId"), weight = it.optInt("weight", 1),
                    isUserLearned = it.optBoolean("isUserLearned")
                )
            }.filter { it.categoryId in catIds }
            // Loans arrived in v1.9; older backups simply have none.
            val loans = (root.optJSONArray("loans") ?: JSONArray()).objects().map {
                Loan(
                    id = it.getLong("id"), person = it.getString("person"),
                    direction = enumOr(it.optString("direction"), LoanDirection.LENT),
                    amountPaise = it.getLong("amountPaise"), date = it.getLong("date"), dueDate = it.longOrNull("dueDate"),
                    note = it.optString("note"), isClosed = it.optBoolean("isClosed"),
                    lastRemindedOn = it.longOrNull("lastRemindedOn"), createdAt = it.optLong("createdAt")
                )
            }
            val loanIds = loans.map { it.id }.toSet()
            val loanPayments = (root.optJSONArray("loanPayments") ?: JSONArray()).objects().map {
                LoanPayment(
                    id = it.getLong("id"), loanId = it.getLong("loanId"), amountPaise = it.getLong("amountPaise"),
                    date = it.getLong("date"), note = it.optString("note")
                )
            }.filter { it.loanId in loanIds }
            // Events arrived in v2.2.
            val events = (root.optJSONArray("events") ?: JSONArray()).objects().map {
                Event(
                    id = it.getLong("id"), name = it.getString("name"), emoji = it.optString("emoji", "🎉"),
                    budgetPaise = it.longOrNull("budgetPaise"), startDate = it.longOrNull("startDate"),
                    endDate = it.longOrNull("endDate"), isClosed = it.optBoolean("isClosed"), createdAt = it.optLong("createdAt")
                )
            }
            val eventIds = events.map { it.id }.toSet()
            // Photos, warranties and goals arrived in v2.3.
            val goals = (root.optJSONArray("goals") ?: JSONArray()).objects().map {
                Goal(
                    id = it.getLong("id"), name = it.getString("name"), emoji = it.optString("emoji", "🎯"),
                    targetPaise = it.getLong("targetPaise"), targetDate = it.longOrNull("targetDate"),
                    isClosed = it.optBoolean("isClosed"), createdAt = it.optLong("createdAt")
                )
            }
            val goalIds = goals.map { it.id }.toSet()
            // Gold schemes arrived in v2.4.
            val schemes = (root.optJSONArray("goldSchemes") ?: JSONArray()).objects().map {
                GoldScheme(
                    id = it.getLong("id"), name = it.getString("name"), monthlyPaise = it.getLong("monthlyPaise"),
                    months = it.getInt("months"), startDate = it.getLong("startDate"), bonusPaise = it.optLong("bonusPaise"),
                    remind = it.optBoolean("remind", true), lastRemindedFor = it.longOrNull("lastRemindedFor"),
                    note = it.optString("note"), isClosed = it.optBoolean("isClosed"), closedOn = it.longOrNull("closedOn"),
                    createdAt = it.optLong("createdAt")
                )
            }
            val schemeIds = schemes.map { it.id }.toSet()
            val txnsChecked = txns.map {
                it.copy(
                    eventId = it.eventId?.takeIf { id -> id in eventIds },
                    goalId = it.goalId?.takeIf { id -> id in goalIds },
                    schemeId = it.schemeId?.takeIf { id -> id in schemeIds }
                )
            }
            val txnIds = txnsChecked.map { it.id }.toSet()
            val attachments = (root.optJSONArray("attachments") ?: JSONArray()).objects().map {
                Attachment(id = it.getLong("id"), txnId = it.getLong("txnId"), fileName = it.getString("fileName"), createdAt = it.optLong("createdAt"))
            }.filter { it.txnId in txnIds && it.fileName.matches(Regex("[A-Za-z0-9._-]+")) }
            val warranties = (root.optJSONArray("warranties") ?: JSONArray()).objects().map {
                Warranty(txnId = it.getLong("txnId"), untilDate = it.getLong("untilDate"), stage = it.optInt("stage"))
            }.filter { it.txnId in txnIds }
            if (categories.isEmpty()) throw UserError("This backup has no categories")
            return BackupData(
                root.optInt("seedVersion", 1), categories, subcategories, txnsChecked, rules, budgets, keywords,
                loans, loanPayments, events, attachments, warranties, goals, schemes
            )
        } catch (e: UserError) {
            throw e
        } catch (e: Exception) {
            throw UserError("This backup file is damaged and can't be restored")
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
