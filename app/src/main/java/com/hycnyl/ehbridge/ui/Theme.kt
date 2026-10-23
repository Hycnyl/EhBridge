package com.hycnyl.ehbridge.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.hycnyl.ehbridge.ThemeMode

@Composable
fun BridgeTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) darkColorScheme(primary = Color(0xFFA0D1B9), secondary = Color(0xFFB7CCBF), background = Color(0xFF101512), surface = Color(0xFF101512), primaryContainer = Color(0xFF254D3C), onPrimaryContainer = Color(0xFFC0EED5))
    else lightColorScheme(primary = Color(0xFF356653), secondary = Color(0xFF51675B), background = Color(0xFFF7F9F5), surface = Color(0xFFF7F9F5), primaryContainer = Color(0xFFD7EDDF), onPrimaryContainer = Color(0xFF163E2C))
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
