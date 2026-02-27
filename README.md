# 📺 HuntTV - Android Streaming App

A professional Android streaming application with ExoPlayer, supporting M3U8/MPD streams, DRM (ClearKey), custom headers, and push notifications via OneSignal.

---

## 🎯 Features

✅ **Stream Playback**
- Supports M3U8 (HLS) and MPD (DASH) formats
- ExoPlayer with adaptive bitrate streaming
- ClearKey DRM support
- Custom HTTP headers (Cookie, Origin, Referer, User-Agent)

✅ **UI/UX**
- Beautiful gradient background design
- Pull-to-refresh stream list
- Fullscreen video player
- Loading states and error handling

✅ **Backend Integration**
- Fetch streams from your JSON API
- API key authentication
- Hide/show streams remotely
- No app update needed to change streams

✅ **Push Notifications**
- OneSignal integration
- Match start alerts
- Custom notifications
- Automatic permission prompt

---

## 🔧 Configuration (IMPORTANT!)

Before building the app, you MUST replace the sample configuration values:

### Location: `app/src/main/java/com/hunttv/streamtv/utils/AppConfig.kt`

```kotlin
object AppConfig {
    // ✏️ REPLACE THIS: Your Vercel/backend URL
    const val API_BASE_URL = "https://your-stream-api.vercel.app/"
    
    // ✏️ REPLACE THIS: Your secret API key
    const val API_KEY = "your-secret-api-key-12345"
    
    // ✏️ REPLACE THIS: Your OneSignal App ID
    const val ONESIGNAL_APP_ID = "your-onesignal-app-id-here"
}
```

### How to get these values:

**1. API_BASE_URL** (Vercel URL)
- After deploying your backend to Vercel (explained in next section)
- Get URL like: `https://your-app.vercel.app/`
- Make sure it ends with `/`

**2. API_KEY**
- Generate a strong random key (32+ characters)
- Example: `sk_live_abc123xyz789def456ghi`
- This same key must be set in your Vercel backend

**3. ONESIGNAL_APP_ID**
- Go to https://onesignal.com/
- Create free account
- Create new app
- Go to Settings → Keys & IDs
- Copy "OneSignal App ID"
- Example: `12345678-1234-1234-1234-123456789012`

---

## 📱 JSON API Format

Your backend should return this JSON structure:

```json
{
  "powered_by": "Powered By @HuntTV",
  "streams": [
    {
      "id": 1,
      "title": "[English] - Afghanistan Vs UAE - SS1 HD (WEB)",
      "stream_url": "https://server.com/stream.mpd?token=...|Cookie=...&Origin=https://jiotv.com/&Referer=https://jiotv.com/&User-Agent=plaYtv/7.1.3&drmScheme=clearkey&drmLicense=KEY:VALUE",
      "is_hidden": false
    },
    {
      "id": 2,
      "title": "[Hindi] - Match 2 - HD",
      "stream_url": "https://server.com/stream2.m3u8",
      "is_hidden": true
    }
  ]
}
```

### Stream URL Format:

**Simple streams (no auth):**
```
https://server.com/stream.m3u8
```

**With cookies and headers:**
```
https://server.com/stream.mpd|Cookie=value&Origin=https://site.com&Referer=https://site.com&User-Agent=CustomAgent
```

**With DRM (ClearKey):**
```
https://server.com/stream.mpd|Cookie=value&drmScheme=clearkey&drmLicense=KEY:VALUE
```

---

## 🏗️ Building the App

### Prerequisites:
- Android Studio (latest version)
- JDK 11 or higher
- Android SDK (API 24+)

### Steps:

1. **Open project in Android Studio**
   ```bash
   File → Open → Select StreamTV folder
   ```

2. **Update configuration** (CRITICAL!)
   - Open `AppConfig.kt`
   - Replace `API_BASE_URL`, `API_KEY`, `ONESIGNAL_APP_ID`

3. **Sync Gradle**
   ```
   File → Sync Project with Gradle Files
   ```

4. **Build APK**
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```

5. **Find APK**
   ```
   app/build/outputs/apk/release/app-release.apk
   ```

---

## 📦 Project Structure

```
StreamTV/
├── app/
│   ├── src/main/
│   │   ├── java/com/hunttv/streamtv/
│   │   │   ├── activities/
│   │   │   │   ├── MainActivity.kt          # Stream list
│   │   │   │   └── PlayerActivity.kt        # Video player
│   │   │   ├── adapters/
│   │   │   │   └── StreamAdapter.kt         # RecyclerView adapter
│   │   │   ├── models/
│   │   │   │   └── StreamModels.kt          # Data classes
│   │   │   ├── network/
│   │   │   │   ├── StreamAPI.kt             # API interface
│   │   │   │   └── RetrofitClient.kt        # HTTP client
│   │   │   └── utils/
│   │   │       ├── AppConfig.kt             # ⚙️ CONFIG FILE
│   │   │       └── StreamParser.kt          # URL parser
│   │   ├── res/
│   │   │   ├── layout/                      # UI layouts
│   │   │   ├── drawable/                    # Gradients
│   │   │   └── values/                      # Strings, colors, themes
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 🔐 Security Features

### API Key Authentication
- Every request includes `X-API-Key` header
- Backend verifies key before sending streams
- Prevents unauthorized API access

### User-Agent Verification
- App sends custom User-Agent
- Backend can verify requests come from your app
- Blocks web browsers and other apps

### Hidden Streams
- Set `is_hidden: true` in JSON
- Stream won't appear in app
- Control visibility without app update

---

## 🔔 OneSignal Setup

1. **Create OneSignal Account**
   - Go to https://onesignal.com/
   - Sign up for free

2. **Create App**
   - Click "New App/Website"
   - Select "Android"
   - Follow setup wizard

3. **Get App ID**
   - Settings → Keys & IDs
   - Copy "OneSignal App ID"
   - Paste in `AppConfig.kt`

4. **Send Notifications**
   - Dashboard → Messages → New Push
   - Write message
   - Send to all users or segments

---

## 🚀 Next Steps: Vercel Backend

After building the Android app, you need to set up the backend API on Vercel.

The backend will:
- Host your `streams.json` file
- Verify API key from app
- Check User-Agent to prevent abuse
- Allow you to update streams anytime

**See VERCEL_SETUP.md for complete backend setup instructions.**

---

## 📝 Testing Checklist

Before release:

- [ ] Updated `API_BASE_URL` in AppConfig.kt
- [ ] Updated `API_KEY` in AppConfig.kt
- [ ] Updated `ONESIGNAL_APP_ID` in AppConfig.kt
- [ ] Tested stream playback with M3U8
- [ ] Tested stream playback with MPD
- [ ] Tested DRM streams (if using)
- [ ] Tested pull-to-refresh
- [ ] Tested hide/show streams
- [ ] Tested push notifications
- [ ] Tested on different Android versions
- [ ] Built release APK and tested
- [ ] Backend API is deployed and working

---

## 🐛 Troubleshooting

### Streams not loading?
- Check `API_BASE_URL` is correct
- Check `API_KEY` matches backend
- Check internet connection
- Check backend is deployed and accessible

### Video won't play?
- Check stream URL format
- Test URL in VLC or another player first
- Check DRM configuration if using ClearKey
- Check logs in Android Studio Logcat

### Notifications not working?
- Check `ONESIGNAL_APP_ID` is correct
- Grant notification permission on device
- Check OneSignal dashboard for errors
- Check device has Play Services

### Build errors?
- Clean project: Build → Clean Project
- Invalidate caches: File → Invalidate Caches / Restart
- Check Gradle sync succeeded
- Update Android Studio to latest version

---

## 📄 License

This is a sample project. Modify and use as needed.

---

## 🤝 Support

For issues:
1. Check configuration in `AppConfig.kt`
2. Check backend is running
3. Check Android Studio Logcat for errors
4. Verify stream URLs work in other players

---

**Made with ❤️ for HuntTV**
