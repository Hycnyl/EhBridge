package com.hycnyl.ehbridge

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class BridgeApp : Application() {
    val repository by lazy { BridgeRepository(this) }
    override fun onCreate() {
        super.onCreate()
        // Remove only this app's obsolete private stream-provider spools after process death.
        cacheDir.listFiles()?.filter { it.name.startsWith("ehbridge-") && it.extension == "zip" }?.forEach { it.delete() }
    }
}

fun Context.withBridgeLocale(): Context {
    val language = getSharedPreferences("settings", Context.MODE_PRIVATE).getString("language", "").orEmpty()
    if (language.isEmpty()) return this
    val config = Configuration(resources.configuration)
    config.setLocale(Locale.forLanguageTag(language))
    return createConfigurationContext(config)
}
