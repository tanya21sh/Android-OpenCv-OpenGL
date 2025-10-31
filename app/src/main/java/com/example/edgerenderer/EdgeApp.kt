package com.example.edgerenderer

import android.app.Application
import com.example.edgerenderer.nativebridge.NativeBridge

class EdgeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NativeBridge.init()
    }
}
