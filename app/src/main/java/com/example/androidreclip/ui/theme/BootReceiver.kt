package com.example.androidreclip.ui.theme

import com.example.androidreclip.AudioService // <-- ADD THIS IMPORT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, AudioService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}