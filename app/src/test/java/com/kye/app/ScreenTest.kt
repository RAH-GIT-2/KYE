package com.kye.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.kye.app.data.PaymentMode
import com.kye.app.data.Txn
import com.kye.app.data.TxnType
import com.kye.app.domain.Dates
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

/**
 * Taps through the real app the way a person would: tabs, adding an entry, History search and
 * delete, and every page under Settings. Each test starts with a fresh, empty KYE.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestKyeApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, substring: Boolean = false) =
        rule.waitUntil(10_000) { rule.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }

    private fun tab(name: String): SemanticsNodeInteraction = rule.onNodeWithContentDescription(name)

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** Home shows the mic with a small + when the phone has speech recognition, otherwise just +. */
    private fun openAdd() {
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasContentDescription("Add entry") or hasContentDescription("Add manually"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasContentDescription("Add entry") or hasContentDescription("Add manually")).onLast().performClick()
        waitForText("Quick type")
    }

    private fun addEntries(vararg notes: String) = runBlocking {
        val repo = (rule.activity.application as KyeApp).container.repo
        notes.forEachIndexed { i, note ->
            repo.saveTxn(
                Txn(
                    amountPaise = (100L + i) * 100, type = TxnType.EXPENSE, categoryId = null, subcategoryId = null,
                    dateTime = Dates.atMillis(LocalDate.now(), LocalTime.NOON), paymentMode = PaymentMode.UPI, note = note
                )
            )
        }
    }

    @Test fun eachTabOpens() {
        waitForText("Know Your Expense")
        listOf("History", "Insights", "Recurring", "Home").forEach { name ->
            tab(name).performClick()
            rule.waitForIdle()
            tab(name).assertIsSelected()
        }
        tab("History").performClick()
        waitForText("Search notes, merchants, amounts")
    }

    @Test fun addedEntryShowsOnHomeAndInHistory() {
        waitForText("Know Your Expense")
        openAdd()
        rule.onNodeWithTag("amount").performTextInput("437")
        rule.onNodeWithText("Note (optional)").performScrollTo().performTextInput("test samosa")
        rule.onNodeWithText("Save").performClick()

        // Back on Home, under Recent.
        waitForText("test samosa")
        waitForText("437", substring = true)

        tab("History").performClick()
        waitForText("Search notes, merchants, amounts")
        waitForText("test samosa")
    }

    @Test fun quickTypeFillsTheAmount() {
        waitForText("Know Your Expense")
        openAdd()
        rule.onNodeWithText("Quick type").performTextInput("auto 85")
        rule.onNodeWithContentDescription("Fill the form").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("amount") and hasText("85", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Save").performClick()
        waitForText("Know Your Expense")
        waitForText("85", substring = true)
    }

    @Test fun historySearchFindsOnlyMatches() {
        addEntries("Swiggy dinner", "Petrol")
        tab("History").performClick()
        waitForText("Swiggy dinner")
        waitForText("Petrol")

        rule.onNodeWithText("Search notes, merchants, amounts").performTextInput("swig")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Petrol").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Swiggy dinner").assertExists()
    }

    @Test fun historyDeletesSelectedEntries() {
        addEntries("Swiggy dinner", "Petrol")
        tab("History").performClick()
        waitForText("Swiggy dinner")

        rule.onNodeWithText("Swiggy dinner").performTouchInput { longClick() }
        waitForText("1 selected")
        rule.onNodeWithText("Select all").performClick()
        waitForText("2 selected")
        rule.onNodeWithText("Delete").performClick()
        waitForText("Delete 2 entries?")
        rule.onAllNodesWithText("Delete").onLast().performClick()

        waitForText("No entries yet")
    }

    @Test fun everySettingsPageOpens() {
        waitForText("Know Your Expense")
        rule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Settings")
        listOf(
            "Categories", "Budgets", "Savings goals", "Gold schemes", "Credit cards",
            "Subscriptions", "Events", "Lent & borrowed", "Warranties"
        ).forEach { page ->
            rule.onNodeWithText(page).performScrollTo().performClick()
            // The page opened: the Settings title is gone.
            rule.waitUntil(10_000) { rule.onAllNodesWithText("Settings").fetchSemanticsNodes().isEmpty() }
            back()
            waitForText("Settings")
        }
    }
}
