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
    val keywords: List<KeywordRule>
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
                    createdAt = it.optLong("createdAt"), updatedAt = it.optLong("updatedAt")
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
            if (categories.isEmpty()) throw UserError("This backup has no categories")
            return BackupData(root.optInt("seedVersion", 1), categories, subcategories, txns, rules, budgets, keywords)
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
