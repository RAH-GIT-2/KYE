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

/** Stored by name. IMPORT entries share their import time in createdAt, which is how Undo finds them. */
enum class Source { MANUAL, VOICE, RECURRING, IMPORT }

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

@Entity(tableName = "txn", indices = [Index("dateTime"), Index("categoryId"), Index("eventId"), Index("goalId"), Index("schemeId")])
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
    val updatedAt: Long = System.currentTimeMillis(),
    /** Event this entry belongs to (Deepavali, a trip…). Still counts in normal spending too. */
    val eventId: Long? = null,
    /** Savings goal this Saving (or withdrawal Transfer) entry belongs to. */
    val goalId: Long? = null,
    /** Gold scheme this Saving entry is an instalment of. */
    val schemeId: Long? = null
)

/** A receipt / bill photo, stored privately in the app's files (receipts/<fileName>). */
@Entity(
    tableName = "attachment",
    foreignKeys = [ForeignKey(entity = Txn::class, parentColumns = ["id"], childColumns = ["txnId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("txnId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Warranty on a purchase. [stage]: 0 = nothing sent, 1 = "ends in 30 days" sent, 2 = "ends today" sent.
 * [untilDate] is LocalDate.toEpochDay().
 */
@Entity(
    tableName = "warranty",
    foreignKeys = [ForeignKey(entity = Txn::class, parentColumns = ["id"], childColumns = ["txnId"], onDelete = ForeignKey.CASCADE)]
)
data class Warranty(
    @PrimaryKey val txnId: Long,
    val untilDate: Long,
    val stage: Int = 0
)

/** A savings goal: "Goa trip ₹50,000 by March". Money in = Saving entries tagged to it. */
@Entity(tableName = "goal")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val targetPaise: Long,
    val targetDate: Long? = null,
    val isClosed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** A goal with money saved towards it (savings minus withdrawals). */
data class GoalRow(
    @Embedded val goal: Goal,
    val savedPaise: Long,
    val withdrawnPaise: Long
) {
    val progress: Long get() = (savedPaise - withdrawnPaise).coerceAtLeast(0)
}

/**
 * A jeweller's monthly gold savings scheme: pay [monthlyPaise] for [months] months, the jeweller
 * adds [bonusPaise], and the total is redeemed as jewellery. Instalments are Saving entries tagged
 * with the scheme. [startDate] is the first instalment's due date (LocalDate.toEpochDay()); later
 * ones fall due on the same day of each following month.
 */
@Entity(tableName = "gold_scheme")
data class GoldScheme(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val monthlyPaise: Long,
    val months: Int,
    val startDate: Long,
    val bonusPaise: Long = 0,
    val remind: Boolean = true,
    /** Due date (epoch day) last reminded about, so each instalment is reminded once. */
    val lastRemindedFor: Long? = null,
    val note: String = "",
    /** Redeemed or stopped: no longer reminded, kept as a record. */
    val isClosed: Boolean = false,
    val closedOn: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** A scheme with what's been paid into it so far, and how many entries. */
data class GoldSchemeRow(
    @Embedded val scheme: GoldScheme,
    val paidPaise: Long,
    val payments: Int
)

/** An entry with a warranty, for the Warranties list. */
data class WarrantyRow(
    @Embedded val txn: Txn,
    val untilDate: Long,
    val catName: String?,
    val catEmoji: String?,
    val subName: String?,
    val attachments: Int
) {
    val title: String get() = txn.note.ifBlank { subName ?: catName ?: "Purchase" }
}

/**
 * An occasion with its own budget: a festival, wedding, trip, school reopening. Entries are
 * tagged to it on top of their category. Dates are LocalDate.toEpochDay() values, both optional.
 */
@Entity(tableName = "event")
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val budgetPaise: Long? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    /** Marked done: no longer offered when adding entries, kept as a record. */
    val isClosed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** An event with what's been spent on it so far. */
data class EventRow(
    @Embedded val event: Event,
    val spentPaise: Long,
    val entries: Int
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

enum class LoanDirection { LENT, BORROWED }

/**
 * Money lent to or borrowed from someone. Kept apart from spending: lending isn't an expense.
 * Dates are LocalDate.toEpochDay() values.
 */
@Entity(tableName = "loan", indices = [Index("person")])
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val person: String,
    val direction: LoanDirection,
    val amountPaise: Long,
    val date: Long,
    val dueDate: Long? = null,
    val note: String = "",
    /** Fully repaid, or marked settled by the user. */
    val isClosed: Boolean = false,
    val lastRemindedOn: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** A part or full repayment of a [Loan]. */
@Entity(
    tableName = "loan_payment",
    foreignKeys = [
        ForeignKey(entity = Loan::class, parentColumns = ["id"], childColumns = ["loanId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("loanId")]
)
data class LoanPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amountPaise: Long,
    val date: Long,
    val note: String = ""
)

/** A loan with how much has been repaid so far. */
data class LoanRow(
    @Embedded val loan: Loan,
    val paidPaise: Long
) {
    val remaining: Long get() = (loan.amountPaise - paidPaise).coerceAtLeast(0)
}

/** A transaction joined with its category and subcategory for display. */
data class TxnRow(
    @Embedded val txn: Txn,
    val catName: String?,
    val catEmoji: String?,
    val catColor: Long?,
    val subName: String?,
    val subIsDefault: Boolean?,
    /** Receipt photos attached (📎). */
    val attachments: Int = 0,
    /** Warranty end (epoch day), if one was set (🛡️). */
    val warrantyUntil: Long? = null
) {
    /** Subcategory name, unless it's the catch-all "Others". */
    val specificSub: String?
        get() = subName?.takeIf { subIsDefault != true }

    val title: String
        get() = specificSub ?: catName ?: "Uncategorised"
}
