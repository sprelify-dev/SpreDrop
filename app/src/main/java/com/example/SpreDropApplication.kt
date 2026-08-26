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
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:947368133167:android:947368133167spredrop")
                    .setProjectId("spredrop")
                    .setApiKey("AIzaSyDummyKeyForLocalFirebaseInit947368133167")
                    .setStorageBucket("spredrop.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("SpreDropApp", "Firebase manually initialized with fallback options")
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
