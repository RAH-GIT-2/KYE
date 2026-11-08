# KYE · Know Your Expense

An India-first Android expense tracker with voice entry in English, Tamil and Tanglish. Built with Kotlin and Jetpack Compose. Everything stays on your phone.

## Get the APK

### Option A: let GitHub build it (no Android Studio needed)
Every push to `main` runs `.github/workflows/build.yml`: all unit tests, then a release APK signed with the project key, published as a GitHub Release named after the version (e.g. **KYE v1.7.1**).
1. One-time setup: add the repository secrets `KYE_KEYSTORE_BASE64`, `KYE_STORE_PASSWORD`, `KYE_KEY_ALIAS` and `KYE_KEY_PASSWORD` (Settings → Secrets and variables → Actions). Without them the build stops instead of producing an APK that can't install over the current one.
2. Push, or open **Actions → Build APK → Run workflow**.
3. On the phone, open the repo's **Releases** page, download `KYE-v<version>.apk` and install it over the current app.

### Option B: Android Studio
1. Install Android Studio (Ladybug or newer) and open this folder.
2. Wait for Gradle sync, then **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. The APK is in `app/build/outputs/apk/`.

Command line: `./gradlew assembleRelease` (needs JDK 17 and the Android SDK).

**Signing.** Release builds are signed with the key named in `keystore.properties` (project root, gitignored), for example:
```
storeFile=C:/Users/you/kye-keys/kye-release.jks
storePassword=…
keyAlias=kye
keyPassword=…
```
Without that file the release build falls back to the debug key, so it still builds anywhere (including Option A). Every update must be signed with the **same** key, or Android refuses to install it over the old version, and since data lives only on the phone, uninstalling loses it. Keep the `.jks` file and its password backed up somewhere safe.

**Database changes.** Bump the version in `KyeDatabase` and add a `Migration`; never wipe. Schemas are exported to `app/schemas/` (commit them). `DatabaseUpgradeTest` checks that a v1 database upgrades with its data intact.

## What's in v1
- **Voice entry**: tap the mic and say "Swiggy la 450 biryani", "auto 120 nethu", "EB bill rendu aayiram" or "பால் 60 ரூபாய்". Several items in one sentence work ("petrol 500 and parking 40"). Every entry shows a confirmation card first. When you correct a category, KYE learns that word. You can also type the sentence instead of speaking it.
- **Manual entry** with Expense / Saving / Transfer types, categories, subcategories, payment mode, date, note, and "Make this recurring".
- **18 India-first categories** with subcategories: Swiggy/Zomato, Blinkit/Zepto, Aavin, Jio/Airtel (self/family), Netflix/JioHotstar/Sun NXT, EB/LPG, rent/maid, Amazon/Flipkart/Meesho, moi, chit/SIP and more.
- **Recurring**: every N days, weeks, months or years. *Auto-add* is for fixed amounts. *Remind me* is for changing bills like EB and sends a notification with Paid/Skip buttons. Missed entries catch up after the phone has been off.
- **Insights**: category donut with subcategory breakdown, daily/monthly trend, this vs previous period, top merchants, payment-mode split, fixed vs variable, budgets, and upcoming bills. Periods: this month, last month, 3 months, this financial year (April–March).
- **Budgets**: an overall budget and per-category budgets, with alerts at 80% and 100%.
- **CSV export**, light/dark theme, Indian number formatting (₹1,25,000).

## New in v1.1 – v1.2
- **Category manager** (Settings → Categories, or "Add / edit" on the add screen): add expense, income or saving categories with your own icon and colour; add, rename, hide, merge or delete subcategories; reorder; manage the voice keywords for each. Hiding keeps old entries intact. A category that's in use can't be deleted outright, only hidden or merged (entries, subcategories, recurring bills, budget and keywords all move over). Every category keeps a catch-all "Others".
- **Income**: Salary, Business & Freelance, Interest & Returns, Refunds & Cashback, Gifts Received. Home shows income and what's left; Insights shows income, left over and % kept. Existing installs get these categories on upgrade.
- **CSV import** (Settings → Import from CSV): save the blank template (`Date,Type,Amount,Category,Subcategory,Payment,Note`), fill it in a spreadsheet, upload it. A preview lists rows with problems by row number, skips duplicates (same date, amount and note), and asks what to do with each category KYE doesn't have: create it, use an existing one, or leave it uncategorised. Import only adds, and **Undo this import** removes exactly what it added. CSV export uses the same columns, so you can export, edit and re-import.
- **Automatic backup** (Settings): pick a folder once and how often (Off / Daily / Weekly / Monthly). Settings shows the last backup's date and time and the next one. Keeps the newest 5 automatic backups in that folder; failures (e.g. folder deleted) show in Settings. "Back up now" saves one immediately.
- **Backup & restore** (Settings): one JSON file with everything. Restore replaces the phone's data after a confirmation; a wrong or damaged file is refused before anything changes.
- **No notification access**: v1.2 removed notification reading entirely. Upgrading drops the stored detected payments, and Android revokes the permission automatically.
- **Lighter buttons**: the mic is standard size and only on Home, with a small + above it. History and Recurring have a single + at the bottom right, and Insights has none.
- **Category picker**: picking a category folds the list into one line ("🏠 Home & Help · Change") with its subcategories right under it, on the add screen, the recurring bill screen and the voice card. Settings → Voice entry turns the mic off entirely, and it hides itself on phones without speech recognition.
- **Quick type** on the + screen: type `swiggy 450 biryani` in one line and the form fills itself, using the same parser as voice.

- **App lock** (Settings): fingerprint, face or phone PIN when opening KYE and after a minute in the background. Uses the phone's own screen lock, so there's no separate KYE PIN.
- **Home-screen widget**: today's and this month's spend with + and mic buttons. Amounts are hidden while app lock is on.
- **Quick-settings tile** "Speak expense": opens KYE straight to the mic from the pull-down panel.

## Not yet built
App lock, home-screen widget, receipt photos, event budgets, per-subcategory budgets.

## Project layout
```
app/src/main/java/com/kye/app/
  data/     Room entities, DAOs, seed categories, repository, settings
  domain/   Money formatting, dates, recurrence maths, analytics
  voice/    Speech recogniser wrapper + English/Tamil/Tanglish parser
  work/     Daily recurring job, reminder notifications
  ui/       ViewModel, theme, shared components, screens
app/src/test/  Parser unit tests (run: ./gradlew testDebugUnitTest)
```
