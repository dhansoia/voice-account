# Voice Hisaab — Android income & expense book

Speak an entry in Hindi, English or Hinglish → it is booked with amount, income/expense, category, payment mode and date. Export Excel reports any time.

- Android 10 or newer · works offline · data stays on the phone · no ads, no login, no permissions
- Voice uses the phone's speech service inside the app (Hindi + English by default; falls back to English, then to Google's voice pop-up, then to typing)

## 1. Get the APK

**Option A — GitHub (no software needed, ~5 min)**
1. Sign in at github.com → **New repository** → name `VoiceHisaab` → Private → Create.
2. **Add file › Upload files** → drag in *everything inside* this folder (including the hidden `.github` folder; on Windows enable "show hidden files") → Commit.
   If the Actions tab shows nothing, the hidden folder was skipped: **Add file › Create new file**, name it `.github/workflows/build-apk.yml`, paste the contents of that file from this folder, Commit.
3. Open the **Actions** tab → **Build APK** run → wait for the green tick (~4 min).
4. Download **VoiceHisaab-apk** at the bottom of the run page, unzip → `VoiceHisaab.apk`.

**Option B — Android Studio**
Open this folder → wait for Gradle sync → **Build › Build App Bundle(s)/APK(s) › Build APK(s)** → `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Install on the phone
Copy the APK to the phone (WhatsApp to self / USB / Drive) → tap it → allow **Install unknown apps** for that app when asked → Install.

## 3. Use
| Say | Booked as |
|---|---|
| Petrol 500 UPI se | Expense ₹500 · Fuel · UPI |
| Doodh 60 rupaye cash | Expense ₹60 · Groceries · Cash |
| Bijli ka bill 3450 bhara | Expense ₹3,450 · Bills & Utilities |
| Kal dawai pe 1200 kharch kiye | Expense ₹1,200 · Medical · yesterday |
| Do hazar paanch sau ka ration | Expense ₹2,500 · Groceries |
| Salary 85000 mili bank mein | Income ₹85,000 · Salary · Bank |
| किराया 15000 मिला | Income ₹15,000 · Rent Received |

- **Income words:** mila / mile / aaya / received / credited. **Expense words:** kharch / diya / bhara / paid / liya. No word → expense.
- **Date:** kal, parson, "5 tarikh", "3 November". Otherwise today.
- **Mode:** cash, UPI / PhonePe / GPay / Paytm, card, bank / NEFT / cheque. Otherwise the default in Settings.
- A confirmation screen shows before saving (switch off in **Settings** for one-tap saving).
- Tap an entry to edit · long-press to delete · ‹ › to change month.
- Long-press the app icon → **Speak entry** opens straight to the mic.

## 4. Excel reports
**Excel Report** (top right) → This month / Last month / This or last **financial year (Apr–Mar)** / Calendar year / All / Custom dates → **Export to Excel** → Open or Share.

Saved in **Downloads › VoiceHisaab**. Sheets: Summary · Transactions (with running balance) · Monthly · Daily · Expense by Month. Totals are live formulas.

## 5. Backup
Data is in the app's private storage and included in Android's Google backup (Settings › Google › Backup). Export "All entries" to Excel periodically as an extra copy. Uninstalling the app deletes the data.

## Project notes
Plain Java, no third-party libraries. Key files in `app/src/main/java/com/ndsg/voicehisaab/`:
`VoiceParser.java` (speech → entry), `Categories.java` (categories & spoken keywords — add your own words here), `XlsxWriter.java` + `ReportBuilder.java` (Excel), `Db.java` (SQLite), `MainActivity.java`, `EntryDialog.java`, `ReportActivity.java`.
