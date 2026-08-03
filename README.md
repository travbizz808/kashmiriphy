# Odyssey Travels India CRM Android App

Android WebView app for:

`https://crm.odysseytravelsindia.com/login`

## Included

- Direct Odyssey CRM login launch
- No custom delayed splash screen
- Zoom and pinch zoom disabled
- Smooth hardware-accelerated WebView scrolling
- Login cookies and session retained
- File upload support
- CRM file download support
- Phone, mail, WhatsApp and intent links supported
- Android back button navigates WebView history
- Network error screen with Retry button
- Adaptive Android launcher icon

## Android configuration

- Package / Application ID: `com.odysseytravelsindia.crm`
- Minimum Android: API 24 (Android 7.0)
- Target Android: API 35
- Version: `1.0.3`

## Build APK on GitHub

1. Create a GitHub repository and upload this project to the repository root.
2. Open **Actions** > **Build Android APK**.
3. Run the workflow.
4. Download the artifact named **odyssey-travels-india-crm-apk**.
5. Extract it to get `app-debug.apk`.

The workflow also runs automatically after a push to `main`.

## Main app URL

The CRM URL is defined in:

`app/src/main/java/com/odysseytravelsindia/crm/MainActivity.java`


## Mobile Live API update

- Uses the normal mobile WebView user agent; desktop mode is not enabled.
- Flight and Hotel Live API taps use the website's final mobile implementation.
- Multiple WebView windows/custom popup WebViews remain disabled.
- On the first launch after this app update, old WebView CSS/JavaScript cache is cleared once. Login cookies are not removed.
