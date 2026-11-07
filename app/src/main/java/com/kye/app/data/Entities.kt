package com.kye.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TxnType { EXPENSE, SAVING, TRANSFER, INCOME }

enum class PaymentMode(val label: String) {
    UPI("UPI"),
    CASH("Cash"),
    DEBIT_CARD("Debit card"),
    CREDIT_CARD("Credit card"),
    NET_BANKING("Net banking"),
    WALLET("Wallet")
}

enum class Source { MANUAL, VOICE, RECURRING }

enum class CategoryKind { EXPENSE, SAVING, INCOME }

enum class RepeatUnit(val singular: String, val plural: String) {
    DAY("day", "days"),
    WEEK("week", "weeks"),
    MONTH("month", "months"),
    YEAR("year", "years")
}

enum class RecurringMode { AUTO_ADD, REMIND_ONLY }

@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Long,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false
)

@Entity(
    tableName = "subcategory",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("categoryId")]
)
data class Subcategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val isTransfer: Boolean = false,
    val isArchived: Boolean = false,
    /** The catch-all "Others" subcategory every category has. It can't be renamed, hidden or deleted. */
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false
)

@Entity(tableName = "txn", indices = [Index("dateTime"), Index("categoryId")])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: TxnType,
    val categoryId: Long?,
    val subcategoryId: Long?,
    val dateTime: Long,
    val paymentMode: PaymentMode,
    val note: String = "",
    val source: Source = Source.MANUAL,
    val recurringRuleId: Long? = null,
    val rawVoiceText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Dates in a rule are LocalDate.toEpochDay() values. */
@Entity(tableName = "recurring_rule")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountPaise: Long,
    val type: TxnType = TxnType.EXPENSE,
    val categoryId: Long?,
    val subcategoryId: Long?,
    val paymentMode: PaymentMode = PaymentMode.UPI,
    val unit: RepeatUnit,
    val interval: Int = 1,
    val anchorDate: Long,
    val occurrenceIndex: Int = 0,
    val nextDueDate: Long,
    val endDate: Long? = null,
    val mode: RecurringMode = RecurringMode.AUTO_ADD,
    val remindDaysBefore: Int = 0,
    val isActive: Boolean = true,
    val lastRemindedFor: Long? = null
)

/** categoryId = 0 is the overall monthly budget. */
@Entity(tableName = "budget")
data class Budget(
    @PrimaryKey val categoryId: Long,
    val monthlyLimitPaise: Long
)

@Entity(tableName = "keyword_rule", indices = [Index("phrase")])
data class KeywordRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phrase: String,
    val categoryId: Long,
    val subcategoryId: Long?,
    val weight: Int = 1,
    val isUserLearned: Boolean = false
)

enum class PendingStatus { NEW, ADDED, DISMISSED }

/** A payment read from a UPI / bank notification, waiting for the user to confirm it. */
@Entity(tableName = "pending_txn", indices = [Index(value = ["fingerprint"], unique = true)])
data class PendingTxn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val isCredit: Boolean,
    val merchant: String,
    val sourceApp: String,
    val text: String,
    val postedAt: Long,
    /** Same payment seen twice (app + bank SMS, or an updated notification) is stored once. */
    val fingerprint: String,
    val status: PendingStatus = PendingStatus.NEW
)

/** A transaction joined with its category and subcategory for display. */
data class TxnRow(
    @Embedded val txn: Txn,
    val catName: String?,
    val catEmoji: String?,
    val catColor: Long?,
    val subName: String?,
    val subIsDefault: Boolean?
) {
    /** Subcategory name, unless it's the catch-all "Others". */
    val specificSub: String?
        get() = subName?.takeIf { subIsDefault != true }

    val title: String
        get() = specificSub ?: catName ?: "Uncategorised"
}
