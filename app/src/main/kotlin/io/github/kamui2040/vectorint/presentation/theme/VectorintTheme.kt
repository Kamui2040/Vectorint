package io.github.kamui2040.vectorint.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.ThemeMode

private val DefaultTypography = Typography()

private val VectorintTypography =
    Typography(
        headlineMedium = DefaultTypography.headlineMedium.copy(lineHeight = 40.sp),
        titleLarge = DefaultTypography.titleLarge.copy(lineHeight = 30.sp),
        titleMedium = DefaultTypography.titleMedium.copy(lineHeight = 26.sp),
        bodyLarge = DefaultTypography.bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = DefaultTypography.bodyMedium.copy(lineHeight = 22.sp),
        bodySmall = DefaultTypography.bodySmall.copy(lineHeight = 20.sp),
        labelLarge = DefaultTypography.labelLarge.copy(lineHeight = 20.sp),
        labelMedium = DefaultTypography.labelMedium.copy(lineHeight = 18.sp),
    )

private val VectorintShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(10.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

@Composable
internal fun VectorintTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    colorPalette: ColorPalette = ColorPalette.ORBIT,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    CompositionLocalProvider(LocalFlowColors provides flowColors(darkTheme)) {
        MaterialTheme(
            colorScheme = colorPalette.colorScheme(darkTheme),
            typography = VectorintTypography,
            shapes = VectorintShapes,
            content = content,
        )
    }
}
