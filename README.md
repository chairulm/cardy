# Cardy — Business Card → Contact (Android)

Kotlin + Jetpack Compose. On-device OCR with Google ML Kit (bundled model, works offline).

## Features
- Capture card with camera, pick from gallery, or share an image into the app
- Extracts: name, job title, company, mobile, office phone, fax, email, website, address
- All fields editable before saving
- **Save to Contacts** — opens the system "New contact" screen pre-filled (no contacts permission needed; you pick Google/phone account)
- **vCard** — share as .vcf via WhatsApp/email
- Raw OCR text view for troubleshooting

## Build
**Android Studio:** File → Open → `Cardy` folder → Run (needs Android SDK 35, JDK 17).

**CLI:** `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

**GitHub (no local SDK):** push this folder to a GitHub repo → Actions tab → "Build APK" run → download artifact `Cardy-debug-apk`.

Install on phone: enable "Install unknown apps" for your file manager, open the APK.

## Code map
| File | Purpose |
|---|---|
| `CardParser.kt` | Heuristic field extraction (pure Kotlin; ID/MY/AU phone formats, ID/EN keywords) |
| `CardViewModel.kt` | ML Kit OCR → parser → UI state |
| `ContactSaver.kt` | Contacts insert intent + vCard export |
| `MainActivity.kt` | Compose UI |

## Tuning
Add keywords to `TITLE_WORDS`, `COMPANY_WORDS`, `ADDRESS_WORDS` in `CardParser.kt` to improve detection for your region. Name is chosen by largest text height + match against email local-part.

Specs: minSdk 24 (Android 7.0), targetSdk 35. Latin script only (add `text-recognition-chinese` etc. for CJK cards).
