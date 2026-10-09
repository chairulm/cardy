# Cardy — Business Card → Contact (Android)

Scan a business card, review the details, save to Contacts or WhatsApp the person. On-device OCR (Google ML Kit), works offline.

## Download
**Latest APK:** https://github.com/chairulm/cardy/releases/latest → `Cardy.apk`
Install: open the file on your phone, allow "Install unknown apps". Android 7.0+.

## Features
- **In-app camera with card frame** — fit the card in the frame; image is cropped to the card. Flash toggle and vertical-card mode.
- Gallery import, or share an image into Cardy from any app
- Extracts name, title, company, mobile, office phone, fax, email, website, address — all editable
- **Save to Contacts** — pre-filled system contact screen (no contacts permission needed)
- **WhatsApp** — message the person before or after saving, with an editable greeting template (`{name}`, `{fullname}`, `{company}`)
- **Archive** — every scanned card (image + details) is kept in-app; search, reopen, re-save, delete
- Share as vCard
- Settings: default country code (62 Indonesia) for numbers starting with 0

## Build
- Every push to `main` builds the APK on GitHub Actions and publishes a Release.
- Local: open in Android Studio, or `./gradlew assembleDebug`.
- Builds are signed with a fixed debug key (`app/debug.keystore`) so new versions install over old ones. Not for Play Store.

## Code map
| File | Purpose |
|---|---|
| `CardParser.kt` | Heuristic field extraction (ID/MY/AU phone formats, ID/EN keywords) |
| `CameraScreen.kt` | CameraX preview, guide frame overlay, crop-to-frame |
| `Archive.kt` | Archive storage (JSON index + images in app storage) |
| `WhatsApp.kt` | Number normalisation, wa.me deep link, settings |
| `CardViewModel.kt` | OCR → parser → state, archive sync |
| `ContactSaver.kt` | Contacts insert intent + vCard |
| `MainActivity.kt` | Compose UI: review, archive, dialogs |
