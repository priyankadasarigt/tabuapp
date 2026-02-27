package com.hunttv.streamtv.utils

/**
 * ========================================
 * 🔧 CONFIGURATION - REPLACE THESE VALUES
 * ========================================
 * 
 * Before building the app, replace these sample values with your actual credentials:
 * 
 * 1. API_BASE_URL: Your Vercel/backend URL where streams.json is hosted
 *    Example: "https://your-app.vercel.app/"
 * 
 * 2. API_KEY: Secret key that your app sends to authenticate with backend
 *    Make sure this matches the key you set in Vercel
 *    Example: "your-secret-api-key-12345"
 * 
 * 3. ONESIGNAL_APP_ID: Your OneSignal App ID for push notifications
 *    Get this from: https://app.onesignal.com/ → Your App → Settings → Keys & IDs
 *    Example: "12345678-1234-1234-1234-123456789012"
 */

object AppConfig {
    
    // ✏️ REPLACE THIS: Your backend API URL (must end with /)
    const val API_BASE_URL = "https://tabu-phi.vercel.app/"
    
    // ✏️ REPLACE THIS: Your secret API key for authentication
    const val API_KEY = "samlovestabu143"
    
    // ✏️ REPLACE THIS: Your OneSignal App ID
    const val ONESIGNAL_APP_ID = "your-onesignal-app-id-here"
    
    // API endpoints
    const val STREAMS_ENDPOINT = "api/streams.json"
    
    // App info
    const val APP_NAME = "HuntTV"
    const val APP_VERSION = "1.0.0"
    const val USER_AGENT = "HuntTV/1.0.0 (Android)"
}
