package com.example.expensetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColors = lightColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    secondary = Color(0xFF03DAC6),
    error = Color(0xFFCF6679),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
    onSecondary = Color.Black,
    onError = Color.Black
)

@Composable
fun ExpenseTrackerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        content = content
    )
}
