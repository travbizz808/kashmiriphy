# Kashmir Travelify CRM Android App

Simple Android WebView frame app for:

`https://crm.kashmirtravelify.com/newcrm/`

## Included

- No custom splash activity or delayed splash screen
- Android 12+ mandatory system launch screen reduced to a transparent/instant appearance
- Zoom and pinch zoom disabled
- Smooth hardware-accelerated WebView scrolling
- Login cookies and session retained
- File upload support
- CRM file download support
- Phone, mail, WhatsApp and intent links supported
- Android back button navigates WebView history
- Network error screen with Retry button
- Adaptive Android launcher icon

## Build APK on GitHub

1. Create a new GitHub repository.
2. Upload all files from this project to the repository root.
3. Open **Actions** > **Build Android APK**.
4. Click **Run workflow**.
5. After completion, open the workflow run and download the artifact named **kashmir-travelify-crm-apk**.
6. Extract it to get `app-debug.apk`.

The workflow also runs automatically after a push to `main` or `master`.

## Change URL

Edit:

`app/src/main/java/com/kashmirtravelify/crm/MainActivity.java`

Change the `HOME_URL` value.

## Change app name

Edit `android:label` in:

`app/src/main/AndroidManifest.xml`
