package com.kye.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
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
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Taps through the real app the way a person would: tabs, adding an entry, History search and
 * delete, and every page under Settings. Entries added by one test can still be there in the
 * next (the test machine keeps them), so each test uses notes of its own and never counts on
 * an empty list.
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

    /**
     * Opens KYE and adds entries through the + form, the way a person does. (Entries written
     * straight into the database before the app opens don't reach the screen under Robolectric.)
     */
    private fun launchWithEntries(vararg notes: String) {
        launch()
        waitForText("Know Your Expense")
        notes.forEachIndexed { i, note ->
            openAdd()
            rule.onNodeWithTag("amount").performTextInput("${101 + i}")
            rule.onNodeWithText("Note (optional)").performScrollTo().performTextInput(note)
            rule.onNodeWithText("Save").performClick()
            waitFor { rule.onAllNodesWithText("Quick type").fetchSemanticsNodes().isEmpty() }
            waitForText(note)
        }
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
        launchWithEntries("Search swiggy", "Search petrol")
        openHistory()
        waitForText("Search swiggy")
        waitForText("Search petrol")

        rule.onNodeWithText("Search notes, merchants, amounts").performTextInput("swig")
        waitFor { rule.onAllNodesWithText("Search petrol").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Search swiggy").assertExists()
    }

    @Test fun historyDeletesSelectedEntries() {
        launchWithEntries("Delete lunch", "Delete auto")
        openHistory()
        waitForText("Delete auto")

        rule.onNodeWithText("Delete lunch").performTouchInput { longClick() }
        waitForText("1 selected")
        rule.onNodeWithText("Delete auto").performClick()
        waitForText("2 selected")
        rule.onNodeWithText("Delete").performClick()
        waitForText("Delete 2 entries?")
        rule.onAllNodesWithText("Delete").onLast().performClick()

        waitFor {
            rule.onAllNodesWithText("Delete lunch").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithText("Delete auto").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun historySwipeDeletesWithUndoAndAddsAgain() {
        // Notes only this test uses: entries from other tests can still be around.
        launchWithEntries("Swipe chai", "Swipe fuel")
        openHistory()
        waitForText("Swipe fuel")

        // Left: gone, with Undo; Undo brings it back.
        rule.onNodeWithText("Swipe fuel").performTouchInput { swipeLeft() }
        waitFor { rule.onAllNodesWithText("Swipe fuel").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Undo").performClick()
        waitForText("Swipe fuel")

        // Right: the same entry is added again.
        rule.onNodeWithText("Swipe chai").performTouchInput { swipeRight() }
        waitFor { rule.onAllNodesWithText("Swipe chai").fetchSemanticsNodes().size == 2 }
    }

    @Test fun calendarShowsTodaysEntries() {
        launchWithEntries("Calendar snack")
        tab("Insights").performClick()
        waitForText("Spending calendar")
        val today = java.time.LocalDate.now()
        rule.onNode(hasContentDescription("${com.kye.app.domain.Dates.label(today)}:", substring = true))
            .performScrollTo().performClick()
        waitForText("Calendar snack")
    }

    @Test fun storyOpensFromTheCalendarAndCloses() {
        launchWithEntries("Story tea")
        tab("Insights").performClick()
        waitForText("▶ Watch this month's story")
        rule.onNodeWithText("▶ Watch this month's story").performScrollTo().performClick()
        waitForText("KYE story")
        waitForText("Share")
        rule.onNodeWithContentDescription("Close").performClick()
        waitForText("Spending calendar")
    }

    @Test fun tappedEntryOpensItsFormAndGoesBack() {
        launchWithEntries("Open me chai")
        rule.onNodeWithText("Open me chai").performClick()
        waitForText("Edit entry")
        back()
        waitFor { rule.onAllNodesWithText("Edit entry").fetchSemanticsNodes().isEmpty() }
        waitForText("Know Your Expense")
    }

    @Test fun coloursAndPureBlackSwitch() {
        launch()
        waitForText("Know Your Expense")
        rule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Settings")
        listOf("Ocean", "Wallpaper", "Gold", "Violet").forEach { name ->
            rule.onNodeWithText(name).performScrollTo().performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("Dark").performScrollTo().performClick()
        rule.onNodeWithText("Pure black in dark mode").performScrollTo().performClick()
        rule.waitForIdle()
        back()
        waitForText("Know Your Expense")
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
