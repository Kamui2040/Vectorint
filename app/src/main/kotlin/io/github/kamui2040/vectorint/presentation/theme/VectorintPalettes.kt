package io.github.kamui2040.vectorint.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.kamui2040.vectorint.data.ColorPalette

internal val BudgetIncome = Color(0xFF2CCB78)
internal val BudgetExpense = Color(0xFFEF5B5B)

@Immutable
internal data class FlowColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
)

internal val LocalFlowColors = staticCompositionLocalOf { flowColors(darkTheme = false) }

internal val MaterialTheme.flowColors: FlowColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFlowColors.current

internal fun flowColors(darkTheme: Boolean): FlowColors =
    if (darkTheme) {
        FlowColors(
            income = BudgetIncome,
            onIncome = Color(0xFF00391B),
            incomeContainer = Color(0xFF123D29),
            onIncomeContainer = Color(0xFF8FF0BA),
            expense = BudgetExpense,
            onExpense = Color(0xFF4A0006),
            expenseContainer = Color(0xFF4A1C1F),
            onExpenseContainer = Color(0xFFFFB3B3),
        )
    } else {
        FlowColors(
            income = BudgetIncome,
            onIncome = Color(0xFF00210D),
            incomeContainer = Color(0xFFD5F8E4),
            onIncomeContainer = Color(0xFF07542E),
            expense = BudgetExpense,
            onExpense = Color(0xFF350003),
            expenseContainer = Color(0xFFFFDAD9),
            onExpenseContainer = Color(0xFF761C24),
        )
    }

internal fun ColorPalette.colorScheme(darkTheme: Boolean): ColorScheme =
    when (this) {
        ColorPalette.ORBIT -> if (darkTheme) OrbitDarkColors else OrbitLightColors
        ColorPalette.NOVA -> if (darkTheme) NovaDarkColors else NovaLightColors
        ColorPalette.NEBULA -> if (darkTheme) NebulaDarkColors else NebulaLightColors
    }

private val OrbitLightColors =
    lightColorScheme(
        primary = Color(0xFF19C8FF),
        onPrimary = Color(0xFF003546),
        primaryContainer = Color(0xFFD9F3FF),
        onPrimaryContainer = Color(0xFF002635),
        secondary = Color(0xFF246BFD),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDDE6FF),
        onSecondaryContainer = Color(0xFF102A61),
        tertiary = Color(0xFF8B5CF6),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEDE6FF),
        onTertiaryContainer = Color(0xFF2D146C),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onError = Color.White,
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF7FAFC),
        onBackground = Color(0xFF12202E),
        surface = Color.White,
        onSurface = Color(0xFF12202E),
        surfaceVariant = Color(0xFFEAF2F7),
        onSurfaceVariant = Color(0xFF607182),
        outline = Color(0xFFBFD7E4),
        inverseOnSurface = Color(0xFFF4F8FB),
        inverseSurface = Color(0xFF22313F),
        inversePrimary = Color(0xFF74D8FF),
        surfaceTint = Color(0xFF19C8FF),
        outlineVariant = Color(0xFFDDEAF0),
        scrim = Color.Black,
    ).withSurfaceContainers(
        lowest = Color.White,
        low = Color(0xFFF3F8FB),
        container = Color(0xFFEDF4F7),
        high = Color(0xFFE7F0F4),
        highest = Color(0xFFDFEAF0),
    )

private val OrbitDarkColors =
    darkColorScheme(
        primary = Color(0xFF19C8FF),
        onPrimary = Color(0xFF002B39),
        primaryContainer = Color(0xFF07374A),
        onPrimaryContainer = Color(0xFFBCEBFF),
        secondary = Color(0xFF7EA5FF),
        onSecondary = Color(0xFF002B6D),
        secondaryContainer = Color(0xFF17345F),
        onSecondaryContainer = Color(0xFFD9E2FF),
        tertiary = Color(0xFFB9A0FF),
        onTertiary = Color(0xFF32166F),
        tertiaryContainer = Color(0xFF39275E),
        onTertiaryContainer = Color(0xFFE9DDFF),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0A1118),
        onBackground = Color(0xFFEDF7FF),
        surface = Color(0xFF111A24),
        onSurface = Color(0xFFEDF7FF),
        surfaceVariant = Color(0xFF182530),
        onSurfaceVariant = Color(0xFF8DA0B1),
        outline = Color(0xFF1E3A4A),
        inverseOnSurface = Color(0xFF12202E),
        inverseSurface = Color(0xFFEDF7FF),
        inversePrimary = Color(0xFF006684),
        surfaceTint = Color(0xFF19C8FF),
        outlineVariant = Color(0xFF182E3B),
        scrim = Color.Black,
    ).withSurfaceContainers(
        lowest = Color(0xFF070C11),
        low = Color(0xFF0D161F),
        container = Color(0xFF111A24),
        high = Color(0xFF16212C),
        highest = Color(0xFF1B2834),
    )

private val NovaLightColors =
    lightColorScheme(
        primary = Color(0xFF19D3C5),
        onPrimary = Color(0xFF003734),
        primaryContainer = Color(0xFFD4F8F4),
        onPrimaryContainer = Color(0xFF002725),
        secondary = Color(0xFF0891B2),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD5F3FA),
        onSecondaryContainer = Color(0xFF003642),
        tertiary = Color(0xFF39E6FF),
        onTertiary = Color(0xFF00363D),
        tertiaryContainer = Color(0xFFD3F7FC),
        onTertiaryContainer = Color(0xFF00363D),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onError = Color.White,
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF6FBFB),
        onBackground = Color(0xFF102526),
        surface = Color.White,
        onSurface = Color(0xFF102526),
        surfaceVariant = Color(0xFFE8F4F4),
        onSurfaceVariant = Color(0xFF5D7475),
        outline = Color(0xFFB9DADA),
        inverseOnSurface = Color(0xFFF1FAFA),
        inverseSurface = Color(0xFF203536),
        inversePrimary = Color(0xFF75E6DC),
        surfaceTint = Color(0xFF19D3C5),
        outlineVariant = Color(0xFFD6E9E9),
        scrim = Color.Black,
    ).withSurfaceContainers(
        lowest = Color.White,
        low = Color(0xFFF1F9F9),
        container = Color(0xFFEBF5F5),
        high = Color(0xFFE4F0F0),
        highest = Color(0xFFDDEAEA),
    )

private val NovaDarkColors =
    darkColorScheme(
        primary = Color(0xFF19D3C5),
        onPrimary = Color(0xFF00332F),
        primaryContainer = Color(0xFF0A3B3A),
        onPrimaryContainer = Color(0xFFB7F3EE),
        secondary = Color(0xFF53C9E2),
        onSecondary = Color(0xFF003641),
        secondaryContainer = Color(0xFF123A43),
        onSecondaryContainer = Color(0xFFC4F3FC),
        tertiary = Color(0xFF39E6FF),
        onTertiary = Color(0xFF00363D),
        tertiaryContainer = Color(0xFF10404A),
        onTertiaryContainer = Color(0xFFC8F7FF),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF081417),
        onBackground = Color(0xFFE8FFFF),
        surface = Color(0xFF0E2024),
        onSurface = Color(0xFFE8FFFF),
        surfaceVariant = Color(0xFF152B2F),
        onSurfaceVariant = Color(0xFF87A5A6),
        outline = Color(0xFF173A3E),
        inverseOnSurface = Color(0xFF102526),
        inverseSurface = Color(0xFFE8FFFF),
        inversePrimary = Color(0xFF006A63),
        surfaceTint = Color(0xFF19D3C5),
        outlineVariant = Color(0xFF153035),
        scrim = Color.Black,
    ).withSurfaceContainers(
        lowest = Color(0xFF050D0F),
        low = Color(0xFF0A191C),
        container = Color(0xFF0E2024),
        high = Color(0xFF13272B),
        highest = Color(0xFF182E32),
    )

private val NebulaLightColors =
    lightColorScheme(
        primary = Color(0xFF8B5CF6),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE6FF),
        onPrimaryContainer = Color(0xFF2D146C),
        secondary = Color(0xFF6D28D9),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE9DDFF),
        onSecondaryContainer = Color(0xFF2B0053),
        tertiary = Color(0xFF22D3EE),
        onTertiary = Color(0xFF00363D),
        tertiaryContainer = Color(0xFFD2F6FB),
        onTertiaryContainer = Color(0xFF00363D),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onError = Color.White,
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFFAF8FF),
        onBackground = Color(0xFF20182B),
        surface = Color.White,
        onSurface = Color(0xFF20182B),
        surfaceVariant = Color(0xFFF1ECF8),
        onSurfaceVariant = Color(0xFF776B84),
        outline = Color(0xFFD8CFF0),
        inverseOnSurface = Color(0xFFF8F3FE),
        inverseSurface = Color(0xFF342B3E),
        inversePrimary = Color(0xFFBEA6FF),
        surfaceTint = Color(0xFF8B5CF6),
        outlineVariant = Color(0xFFE8E0F2),
        scrim = Color.Black,
    ).withSurfaceContainers(
        lowest = Color.White,
        low = Color(0xFFF8F5FC),
        container = Color(0xFFF2EDF7),
        high = Color(0xFFECE6F1),
        highest = Color(0xFFE5DEEB),
    )

private val NebulaDarkColors =
    darkColorScheme(
        primary = Color(0xFFB79CFF),
        onPrimary = Color(0xFF32156F),
        primaryContainer = Color(0xFF3C2763),
        onPrimaryContainer = Color(0xFFE9DDFF),
        secondary = Color(0xFFB48CFF),
        onSecondary = Color(0xFF381368),
        secondaryContainer = Color(0xFF412769),
        onSecondaryContainer = Color(0xFFEADFFF),
        tertiary = Color(0xFF22D3EE),
        onTertiary = Color(0xFF00363D),
        tertiaryContainer = Color(0xFF10404A),
        onTertiaryContainer = Color(0xFFC8F7FF),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        onError = Color(0xFF690005),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF120D1B),
        onBackground = Color(0xFFF7F1FF),
        surface = Color(0xFF1D142A),
        onSurface = Color(0xFFF7F1FF),
        surfaceVariant = Color(0xFF281D36),
        onSurfaceVariant = Color(0xFFAA9AB8),
        outline = Color(0xFF392B4A),
        inverseOnSurface = Color(0xFF20182B),
        inverseSurface = Color(0xFFF7F1FF),
        inversePrimary = Color(0xFF7141DE),
        outlineVariant = Color(0xFF30223F),
        scrim = Color.Black,
    ).withSurfaceContainers(
        lowest = Color(0xFF0C0912),
        low = Color(0xFF171020),
        container = Color(0xFF1D142A),
        high = Color(0xFF241A31),
        highest = Color(0xFF2B2038),
    )

private fun ColorScheme.withSurfaceContainers(
    lowest: Color,
    low: Color,
    container: Color,
    high: Color,
    highest: Color,
): ColorScheme =
    copy(
        surfaceContainerLowest = lowest,
        surfaceContainerLow = low,
        surfaceContainer = container,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
    )
