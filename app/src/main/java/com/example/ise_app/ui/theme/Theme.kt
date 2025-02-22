package com.example.ise_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ISEAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // Detect system theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes, // ✅ Use AppShapes instead of 'Shapes'
        content = content
    )
}
