package io.github.kamui2040.vectorint.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.data.ColorPalette
import io.github.kamui2040.vectorint.data.ThemeMode

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
            shapes = VectorintShapes,
            content = content,
        )
    }
}
