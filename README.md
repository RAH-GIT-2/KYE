# KYE · Know Your Expense

An India-first Android expense tracker with voice entry in English, Tamil and Tanglish. Built with Kotlin and Jetpack Compose. Everything stays on your phone.

## Get the APK

### Option A: let GitHub build it (no Android Studio needed)
1. Create a **private** GitHub repo and push this folder to its `main` branch.
2. Open the repo's **Actions** tab → **Build APK**. It runs automatically on every push, or tap **Run workflow**.
3. When it finishes (about 5–8 minutes), open the run and download **KYE-apk** from *Artifacts*.
4. Unzip it, copy `app-release.apk` to your phone, open it, and allow "Install unknown apps" when asked.

### Option B: Android Studio
1. Install Android Studio (Ladybug or newer) and open this folder.
2. Wait for Gradle sync, then **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. The APK is in `app/build/outputs/apk/`.

Command line: `./gradlew assembleRelease` (needs JDK 17 and the Android SDK).

The release APK is signed with the debug key so it installs directly. Use your own keystore before any Play Store release.

## What's in v1
- **Voice entry**: tap the mic and say "Swiggy la 450 biryani", "auto 120 nethu", "EB bill rendu aayiram" or "பால் 60 ரூபாய்". Several items in one sentence work ("petrol 500 and parking 40"). Every entry shows a confirmation card first. When you correct a category, KYE learns that word. You can also type the sentence instead of speaking it.
- **Manual entry** with Expense / Saving / Transfer types, categories, subcategories, payment mode, date, note, and "Make this recurring".
- **18 India-first categories** with subcategories: Swiggy/Zomato, Blinkit/Zepto, Aavin, Jio/Airtel (self/family), Netflix/JioHotstar/Sun NXT, EB/LPG, rent/maid, Amazon/Flipkart/Meesho, moi, chit/SIP and more.
- **Recurring**: every N days, weeks, months or years. *Auto-add* is for fixed amounts. *Remind me* is for changing bills like EB and sends a notification with Paid/Skip buttons. Missed entries catch up after the phone has been off.
- **Insights**: category donut with subcategory breakdown, daily/monthly trend, this vs previous period, top merchants, payment-mode split, fixed vs variable, budgets, and upcoming bills. Periods: this month, last month, 3 months, this financial year (April–March).
- **Budgets**: an overall budget and per-category budgets, with alerts at 80% and 100%.
- **CSV export**, light/dark theme, Indian number formatting (₹1,25,000).

## Not yet built (next steps from the spec)
Category manager (rename/add/hide), JSON backup and restore, app lock, home-screen widget, reading bank/UPI notifications, receipt photos, event budgets.

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
