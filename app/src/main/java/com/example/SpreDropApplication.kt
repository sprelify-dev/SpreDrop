package com.example

import android.app.Application
import android.util.Log
import com.example.spredrop.service.TransferNotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class SpreDropApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        initChannels()
    }

    private fun initFirebase() {
        try {
            val prefs = getSharedPreferences("spredrop_auth_prefs", MODE_PRIVATE)
            val apiKey = prefs.getString("custom_firebase_api_key", "AIzaSyBehu2ei4kWs3L89UJhwGlsq0wvmi-_lkg") ?: "AIzaSyBehu2ei4kWs3L89UJhwGlsq0wvmi-_lkg"
            val projectId = prefs.getString("custom_firebase_project_id", "spredrop") ?: "spredrop"
            val appId = prefs.getString("custom_firebase_app_id", "1:947368133167:android:7966e7ee9812d68f8fbcda") ?: "1:947368133167:android:7966e7ee9812d68f8fbcda"

            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(appId)
                    .setProjectId(projectId)
                    .setApiKey(apiKey)
                    .setStorageBucket("$projectId.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("SpreDropApp", "Firebase initialized for project: $projectId")
            } else {
                Log.i("SpreDropApp", "Firebase auto-initialized by google-services")
            }
        } catch (e: Exception) {
            Log.w("SpreDropApp", "Firebase initialization caught warning: ${e.message}")
        }
    }

    private fun initChannels() {
        try {
            TransferNotificationHelper.initNotificationChannels(this)
        } catch (e: Exception) {
            Log.w("SpreDropApp", "Notification channel creation warning: ${e.message}")
        }
    }
}
