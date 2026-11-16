package com.kye.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.kye.app.data.PaymentMode
import com.kye.app.data.Txn
import com.kye.app.data.TxnType
import com.kye.app.domain.Dates
import kotlinx.coroutines.flow.first
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

    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Opens KYE. Tests that need entries add them first, like opening the app with data in it. */
    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() {
        scenario?.close()
    }

    /**
     * Waits for [condition], letting the main thread run what the database and background work
     * hand back to the screen (on Robolectric that only happens when the test lets it).
     */
    private fun waitFor(condition: () -> Boolean) = try {
        rule.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            condition()
        }
    } catch (e: Throwable) {
        // Show what was on screen, so a failure in the build log says why.
        println(runCatching { rule.onRoot().printToString() }.getOrElse { "screen: $it" })
        throw e
    }

    /** Opens History from the bottom bar and waits until Home has gone. */
    private fun openHistory() {
        tab("History").performClick()
        waitFor { rule.onAllNodesWithText("Know Your Expense").fetchSemanticsNodes().isEmpty() }
        waitForText("Search notes, merchants, amounts")
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        waitFor { rule.onAllNodesWithText(text, substring).fetchSemanticsNodes().isNotEmpty() }

    /** A bottom tab: the selectable item labelled with its name (not the History page title). */
    private fun tab(name: String): SemanticsNodeInteraction =
        rule.onNode(hasText(name) and androidx.compose.ui.test.isSelectable())

    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** Home shows the mic with a small + when the phone has speech recognition, otherwise just +. */
    private fun openAdd() {
        waitFor {
            rule.onAllNodes(hasContentDescription("Add entry") or hasContentDescription("Add manually"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodes(hasContentDescription("Add entry") or hasContentDescription("Add manually")).onLast().performClick()
        waitForText("Quick type")
    }

    private fun addEntries(vararg notes: String) = runBlocking {
        val repo = ApplicationProvider.getApplicationContext<KyeApp>().container.repo
        notes.forEachIndexed { i, note ->
            repo.saveTxn(
                Txn(
                    amountPaise = (100L + i) * 100, type = TxnType.EXPENSE, categoryId = null, subcategoryId = null,
                    dateTime = Dates.atMillis(LocalDate.now(), LocalTime.NOON), paymentMode = PaymentMode.UPI, note = note
                )
            )
        }
        println("SCREEN entries in the database: ${repo.transactions.first().size}")
    }

    @Test fun eachTabOpens() {
        launch()
        waitForText("Know Your Expense")
        listOf("History", "Insights", "Recurring", "Home").forEach { name ->
            tab(name).performClick()
            rule.waitForIdle()
            tab(name).assertIsSelected()
        }
        openHistory()
    }

    @Test fun addedEntryShowsOnHomeAndInHistory() {
        launch()
        waitForText("Know Your Expense")
        openAdd()
        rule.onNodeWithTag("amount").performTextInput("437")
        rule.onNodeWithText("Note (optional)").performScrollTo().performTextInput("test samosa")
        rule.onNodeWithText("Save").performClick()

        // Back on Home (the form is gone), under Recent.
        waitFor { rule.onAllNodesWithText("Quick type").fetchSemanticsNodes().isEmpty() }
        waitForText("test samosa")
        waitForText("437", substring = true)

        openHistory()
        waitForText("test samosa")
    }

    @Test fun quickTypeFillsTheAmount() {
        launch()
        waitForText("Know Your Expense")
        openAdd()
        rule.onNodeWithText("Quick type").performTextInput("auto 85")
        rule.onNodeWithContentDescription("Fill the form").performClick()
        waitFor {
            rule.onAllNodes(androidx.compose.ui.test.hasTestTag("amount") and hasText("85", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Save").performClick()
        waitFor { rule.onAllNodesWithText("Quick type").fetchSemanticsNodes().isEmpty() }
        waitForText("Know Your Expense")
        waitForText("85", substring = true)
    }

    @Test fun historySearchFindsOnlyMatches() {
        addEntries("Swiggy dinner", "Petrol")
        launch()
        var vmSize = -1
        runCatching { waitFor { scenario!!.onActivity { vmSize = androidx.lifecycle.ViewModelProvider(it)[com.kye.app.ui.KyeViewModel::class.java].txns.value.size }; vmSize > 0 } }
        println("SCREEN entries the screen sees: $vmSize; in the database now: ${runBlocking { ApplicationProvider.getApplicationContext<KyeApp>().container.repo.transactions.first().size }}")
        // Home lists them under Recent.
        waitForText("Swiggy dinner")
        openHistory()
        waitForText("Swiggy dinner")
        waitForText("Petrol")

        rule.onNodeWithText("Search notes, merchants, amounts").performTextInput("swig")
        waitFor { rule.onAllNodesWithText("Petrol").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Swiggy dinner").assertExists()
    }

    @Test fun historyDeletesSelectedEntries() {
        addEntries("Swiggy dinner", "Petrol")
        launch()
        // Home lists them under Recent.
        waitForText("Swiggy dinner")
        openHistory()
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
        launch()
        waitForText("Know Your Expense")
        rule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Settings")
        listOf(
            "Categories", "Budgets", "Savings goals", "Gold schemes", "Credit cards",
            "Subscriptions", "Events", "Lent & borrowed", "Warranties"
        ).forEach { page ->
            rule.onNodeWithText(page).performScrollTo().performClick()
            // The page opened: the Settings title is gone.
            waitFor { rule.onAllNodesWithText("Settings").fetchSemanticsNodes().isEmpty() }
            back()
            waitForText("Settings")
        }
    }
}
