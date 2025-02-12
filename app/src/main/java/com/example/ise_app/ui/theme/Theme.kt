package com.example.ise_app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ISEAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme, // Use the custom color scheme from Color.kt
        typography = Typography, // Use the improved typography
        content = content
    )
}
