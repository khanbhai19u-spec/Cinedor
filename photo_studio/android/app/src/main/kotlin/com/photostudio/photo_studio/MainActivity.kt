package com.photostudio.photo_studio

import android.content.Context
import android.net.wifi.WifiManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "photo_studio/net")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    // Android drops multicast packets unless a lock is held;
                    // needed to discover Wi-Fi printers via mDNS.
                    "acquireMulticast" -> {
                        try {
                            if (multicastLock == null) {
                                val wifi = applicationContext
                                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
                                multicastLock = wifi.createMulticastLock("photo_studio_mdns")
                                    .apply { setReferenceCounted(false) }
                            }
                            multicastLock?.acquire()
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("multicast", e.message, null)
                        }
                    }
                    "releaseMulticast" -> {
                        multicastLock?.let { if (it.isHeld) it.release() }
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun onDestroy() {
        multicastLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
