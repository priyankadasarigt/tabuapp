# 📱 How to Build Android APK for FREE

## 🎯 Complete Guide - No Cost!

You have **3 FREE methods** to build your Android APK:

---

## Method 1: Android Studio (Recommended) ⭐

**Best for:** Full control, debugging, making changes

### Step 1: Download Android Studio (FREE)

1. Go to https://developer.android.com/studio
2. Download for your OS (Windows/Mac/Linux)
3. Install (takes ~10-15 minutes)

### Step 2: Extract Your Project

1. Extract `StreamTV_Android_App.zip`
2. You'll get `StreamTV` folder

### Step 3: Open Project

1. Open Android Studio
2. Click "Open"
3. Select the `StreamTV` folder
4. Click "OK"
5. Wait for Gradle sync (first time takes 5-10 minutes)

### Step 4: Configure App

Edit `app/src/main/java/com/hunttv/streamtv/utils/AppConfig.kt`:

```kotlin
const val API_BASE_URL = "https://your-vercel-url.vercel.app/"
const val API_KEY = "your-secret-api-key"
const val ONESIGNAL_APP_ID = "your-onesignal-app-id"
```

### Step 5: Build APK

**For Debug (Testing):**
1. Build → Build Bundle(s) / APK(s) → Build APK(s)
2. Wait 2-3 minutes
3. APK location: `app/build/outputs/apk/debug/app-debug.apk`

**For Release (Distribution):**
1. Build → Generate Signed Bundle / APK
2. Select "APK"
3. Click "Next"
4. Create new keystore (explained below)

### Step 6: Create Keystore (First time only)

**For release APK:**

1. Click "Create new..."
2. Fill in:
   - **Key store path:** Choose location (e.g., `hunttv-keystore.jks`)
   - **Password:** Choose strong password (remember this!)
   - **Alias:** `hunttv-key`
   - **Password:** Same or different password
   - **Validity:** 25 (years)
   - **First and Last Name:** Your name
   - **Organization:** HuntTV (or your name)
   - Rest: Optional
3. Click "OK"
4. Click "Next"
5. Select "release"
6. Click "Finish"

**IMPORTANT:** Save the keystore file and passwords! You need them for future updates!

### Step 7: Get Your APK

APK will be in:
```
app/build/outputs/apk/release/app-release.apk
```

Done! 🎉

**APK size:** ~15-20 MB

---

## Method 2: GitHub Actions (100% Online) 🌐

**Best for:** No local setup, cloud building, automatic builds

### Step 1: Create GitHub Account (FREE)

1. Go to https://github.com/
2. Sign up (free forever)
3. Verify email

### Step 2: Create Repository

1. Click "New repository"
2. Name: `hunttv-android`
3. Public or Private (both work)
4. Click "Create repository"

### Step 3: Upload Code

1. Extract `StreamTV_Android_App.zip`
2. Upload all files to your GitHub repo:
   - Use GitHub web interface "Upload files"
   - Or use Git command line

### Step 4: Create GitHub Actions Workflow

Create file: `.github/workflows/build-apk.yml`

```yaml
name: Build Android APK

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Build Debug APK
      run: ./gradlew assembleDebug
    
    - name: Upload APK
      uses: actions/upload-artifact@v3
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk
```

### Step 5: Trigger Build

1. Commit and push changes
2. Go to "Actions" tab in GitHub
3. Click on latest workflow run
4. Wait for build (~5 minutes)
5. Download APK from "Artifacts" section

**Benefits:**
- No local setup needed
- Works on any device (even phone/tablet)
- Build from anywhere
- Free unlimited builds

---

## Method 3: Termux (On Android Phone!) 📱

**Best for:** Building APK directly on your Android phone!

### Step 1: Install Termux (FREE)

1. Download from F-Droid: https://f-droid.org/packages/com.termux/
   - **Don't use Play Store version** (outdated)
2. Install F-Droid app first, then install Termux from F-Droid

### Step 2: Setup Environment

Open Termux and run:

```bash
# Update packages
pkg update && pkg upgrade -y

# Install required tools
pkg install -y git openjdk-17 wget

# Download Android SDK Command Line Tools
mkdir -p ~/android-sdk/cmdline-tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
unzip commandlinetools-linux-9477386_latest.zip
mv cmdline-tools latest

# Set environment variables
echo 'export ANDROID_HOME=$HOME/android-sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin' >> ~/.bashrc
source ~/.bashrc

# Accept licenses
yes | sdkmanager --licenses

# Install build tools and platform
sdkmanager "build-tools;33.0.0" "platforms;android-33"
```

### Step 3: Get Your Code

```bash
cd ~
# Upload your StreamTV folder to phone storage
# Or clone from GitHub if you uploaded there
git clone https://github.com/your-username/hunttv-android.git
cd hunttv-android
```

### Step 4: Build APK

```bash
chmod +x gradlew
./gradlew assembleDebug
```

### Step 5: Get APK

```bash
# Copy APK to phone storage
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/
```

APK will be in your Downloads folder!

**Amazing:** Build APKs directly on your phone! 🤯

---

## Comparison

| Method | Setup Time | Build Time | Ease | Best For |
|--------|-----------|------------|------|----------|
| **Android Studio** | 30 min | 2-3 min | ⭐⭐⭐ | Development |
| **GitHub Actions** | 10 min | 5-7 min | ⭐⭐⭐⭐⭐ | No setup |
| **Termux** | 20 min | 3-5 min | ⭐⭐⭐⭐ | Phone only |

---

## ⚡ Quick Start (Absolute Beginner)

**Never coded before? Use GitHub Actions!**

1. Create GitHub account (5 min)
2. Upload your code (5 min)
3. Add workflow file (2 min)
4. Wait for build (5 min)
5. Download APK (1 min)

**Total:** 18 minutes, **zero cost!**

---

## 🔐 Signing APK (For Play Store)

**If you want to publish:**

### Generate Keystore

```bash
# Using Android Studio (easiest)
Build → Generate Signed Bundle/APK → Create new keystore

# Or using command line
keytool -genkey -v -keystore hunttv.keystore -alias hunttv-key -keyalg RSA -keysize 2048 -validity 10000
```

### Sign APK

Android Studio does this automatically when generating signed APK.

**CRITICAL:** 
- **Save keystore file safely!**
- **Save passwords!**
- **You need them for updates!**
- **Lost keystore = can't update app!**

---

## 📊 APK Size Optimization

**Want smaller APK?**

Add to `app/build.gradle`:

```gradle
android {
    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

**Before:** ~20 MB  
**After:** ~8-10 MB

---

## 🐛 Troubleshooting

### "Gradle sync failed"

**Solution:**
```
File → Invalidate Caches / Restart → Invalidate and Restart
```

### "SDK not found"

**Solution:**
```
File → Settings → Appearance & Behavior → System Settings → Android SDK
→ Install Android 13 (API 33)
```

### "Build failed: Out of memory"

**Solution:**
Edit `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m
```

### "APK not installing"

**Solutions:**
- Enable "Install unknown apps" for your file manager
- Uninstall old version first
- Check if APK is for correct architecture (ARM/x86)

---

## 💰 Cost Breakdown

| Item | Cost |
|------|------|
| Android Studio | **FREE** ✅ |
| GitHub Account | **FREE** ✅ |
| GitHub Actions | **FREE** (2000 min/month) ✅ |
| Vercel Hosting | **FREE** ✅ |
| OneSignal | **FREE** ✅ |
| Google Play Dev Account | **$25 one-time** (optional) |

**Total to start:** **$0** 🎉

---

## 🎓 Learning Resources

**New to Android?**

- Official Docs: https://developer.android.com/
- Kotlin Tutorial: https://kotlinlang.org/docs/tutorials/
- YouTube: "Android App Development for Beginners"

**Need help?**
- Stack Overflow: https://stackoverflow.com/
- r/androiddev: https://reddit.com/r/androiddev
- Android Discord communities

---

## ✅ Final Checklist

Before building:

- [ ] Extracted Android project
- [ ] Updated `AppConfig.kt` with:
  - [ ] Vercel URL
  - [ ] API Key
  - [ ] OneSignal App ID
- [ ] Tested backend is working
- [ ] Have Android Studio installed OR
- [ ] Have GitHub account ready OR
- [ ] Have Termux installed
- [ ] Know your app name

After building:

- [ ] APK builds successfully
- [ ] APK size reasonable (~15-20 MB)
- [ ] Installed APK on test device
- [ ] Streams load correctly
- [ ] Notifications work
- [ ] App doesn't crash

---

## 🚀 Next Steps After Building

1. **Test thoroughly** on multiple devices
2. **Share with beta testers**
3. **Fix any bugs**
4. **Optimize performance**
5. **Add analytics** (Firebase, optional)
6. **Publish to Play Store** (optional, $25 fee)

---

## 🎯 Quick Build Commands

**Android Studio:**
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

**Command Line:**
```bash
./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK (needs signing)
```

**GitHub Actions:**
```
Push to main branch → Wait → Download from Artifacts
```

---

**You're all set! Choose your method and build your first APK! 🎉**
