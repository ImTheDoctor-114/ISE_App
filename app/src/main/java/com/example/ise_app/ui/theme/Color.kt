package com.example.ise_app.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6A8CAF),  // Soft blue (Headers, Buttons)
    secondary = Color(0xFFFACD60), // Warm yellow (Highlights)
    tertiary = Color(0xFF3D5A80),  // Deep Blue-Grey (Accents)

    background = Color(0xFFFDFDFD), // Off-White for a softer UI
    surface = Color(0xFFF5F7FA),    // Light Grayish Background
    error = Color(0xFFD32F2F),      // Red for Errors

    onPrimary = Color.White,       // Text on Primary
    onSecondary = Color.Black,     // Text on Secondary
    onTertiary = Color.White,      // Text on Tertiary
    onBackground = Color.Black,    // Text on Background
    onSurface = Color.Black,       // Text on Surface
    onError = Color.White          // Text on Error
)
