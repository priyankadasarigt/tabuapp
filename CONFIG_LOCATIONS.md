# 📍 Configuration Locations Reference

## ⚙️ Where to Change Settings

### 1️⃣ API URL, API Key, OneSignal ID

**File:** `app/src/main/java/com/hunttv/streamtv/utils/AppConfig.kt`

**Line numbers:**
- Line 22: `API_BASE_URL` - Your Vercel backend URL
- Line 25: `API_KEY` - Your secret authentication key
- Line 28: `ONESIGNAL_APP_ID` - Your OneSignal App ID

**What to replace:**
```kotlin
// BEFORE (default):
const val API_BASE_URL = "https://your-stream-api.vercel.app/"
const val API_KEY = "sample-api-key-replace-this-12345"
const val ONESIGNAL_APP_ID = "your-onesignal-app-id-here"

// AFTER (your values):
const val API_BASE_URL = "https://hunt-tv-streams.vercel.app/"
const val API_KEY = "sk_live_abc123xyz789"
const val ONESIGNAL_APP_ID = "12345678-1234-1234-1234-123456789012"
```

---

### 2️⃣ App Name

**File:** `app/src/main/res/values/strings.xml`

**Line 3:**
```xml
<string name="app_name">HuntTV</string>
```

Change "HuntTV" to your desired app name.

---

### 3️⃣ App Package Name (Advanced)

**File:** `app/build.gradle`

**Line 9:**
```gradle
applicationId "com.hunttv.streamtv"
```

**⚠️ Warning:** Changing this requires refactoring all package imports. Only change if you know what you're doing!

---

### 4️⃣ App Colors

**File:** `app/src/main/res/values/colors.xml`

**Lines 11-14:**
```xml
<color name="stream_button_bg">#00BCD4</color>
<color name="gradient_start">#FF6B6B</color>
<color name="gradient_center">#9B59B6</color>
<color name="gradient_end">#4A90E2</color>
```

---

### 5️⃣ Gradient Background

**Files:**
- `app/src/main/res/drawable/gradient_background.xml` - Main background
- `app/src/main/res/drawable/header_gradient.xml` - Header background

---

## 🔍 Quick Search in Android Studio

**Finding config files:**

1. Press `Ctrl+Shift+F` (Windows/Linux) or `Cmd+Shift+F` (Mac)
2. Search for: `AppConfig.kt`
3. Double-click to open

**Finding text to replace:**

1. Press `Ctrl+Shift+F`
2. Search for: `your-stream-api.vercel.app`
3. Click "Replace in Path"
4. Enter your actual URL

---

## ✅ Quick Verification

After making changes:

1. **Check AppConfig.kt:**
   - [ ] API_BASE_URL has your Vercel URL
   - [ ] API_KEY is set to your secret key
   - [ ] ONESIGNAL_APP_ID has your OneSignal ID

2. **Build the app:**
   - Build → Build APK
   - Check for errors in Build output

3. **Test:**
   - Install APK on device
   - Open app
   - Check if streams load
   - Check if notifications work

---

## 🆘 Common Mistakes

❌ **Forgot to add `/` at end of API_BASE_URL**
```kotlin
// WRONG:
const val API_BASE_URL = "https://mysite.vercel.app"

// CORRECT:
const val API_BASE_URL = "https://mysite.vercel.app/"
```

❌ **Used wrong OneSignal ID format**
```kotlin
// WRONG:
const val ONESIGNAL_APP_ID = "MyApp"

// CORRECT:
const val ONESIGNAL_APP_ID = "12345678-1234-1234-1234-123456789012"
```

❌ **API key doesn't match backend**
- Make sure the same API_KEY is used in both Android app and Vercel backend!

---

**That's it! Just change these 3 values in AppConfig.kt and you're ready to build! 🚀**
