# ⚡ Quick Setup Guide

## 🎯 3 Steps to Get Your App Running

### Step 1: Configure the App (2 minutes)

1. Open `app/src/main/java/com/hunttv/streamtv/utils/AppConfig.kt`

2. Replace these 3 values:

```kotlin
const val API_BASE_URL = "https://your-stream-api.vercel.app/"  // Your Vercel URL
const val API_KEY = "your-secret-key-12345"                     // Any random secure key
const val ONESIGNAL_APP_ID = "your-onesignal-app-id"           // From onesignal.com
```

### Step 2: Build APK (1 minute)

1. Open project in Android Studio
2. Go to: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
3. Wait for build to finish
4. APK will be in: `app/build/outputs/apk/release/`

### Step 3: Setup Backend (Next)

After building the Android app, proceed with Vercel backend setup to host your streams JSON.

---

## 🔧 Where to Find Config Values

### API_BASE_URL
- Your Vercel deployment URL (we'll set this up next)
- Example: `https://hunt-tv-api.vercel.app/`
- Must end with `/`

### API_KEY
- Generate any random secure string (32+ characters)
- Example: `sk_live_abc123xyz789_secure_key`
- Use same key in both app and Vercel backend
- Keep it secret!

### ONESIGNAL_APP_ID
1. Go to https://onesignal.com/ → Sign Up
2. Create New App → Select Android
3. Settings → Keys & IDs → Copy "OneSignal App ID"
4. Format: `12345678-1234-1234-1234-123456789012`

---

## ✅ Done!

Your Android app is now configured and ready to build!

Next: Set up the Vercel backend to serve your streams.
