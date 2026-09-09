package io.github.kamui2040.vectorint.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kamui2040.vectorint.R

internal enum class MainView(
    @StringRes val labelResource: Int,
) {
    HOME(R.string.navigation_home),
    HISTORY(R.string.navigation_history),
    OVERVIEW(R.string.navigation_overview),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainViewScaffold(
    selectedView: MainView,
    onViewSelected: (MainView) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            VectorintTopAppBar(
                onOpenAbout = onOpenAbout,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = {
            MainNavigationBar(
                selectedView = selectedView,
                onViewSelected = onViewSelected,
            )
        },
    ) { contentPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VectorintTopAppBar(
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val aboutLabel = stringResource(R.string.about_open)
    val settingsLabel = stringResource(R.string.settings_open)
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onOpenAbout,
                modifier = Modifier.semantics { contentDescription = aboutLabel },
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = RavenBackground,
                ) {
                    Image(
                        painter = painterResource(R.drawable.vectorint_raven),
                        contentDescription = null,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
        },
        actions = {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.semantics { contentDescription = settingsLabel },
            ) {
                SettingsControlsIcon()
            }
        },
    )
}

@Composable
internal fun MainNavigationBar(
    selectedView: MainView,
    onViewSelected: (MainView) -> Unit,
) {
    NavigationBar {
        MainView.entries.forEach { view ->
            NavigationBarItem(
                selected = selectedView == view,
                onClick = { onViewSelected(view) },
                icon = { MainViewIcon(view) },
                label = { Text(stringResource(view.labelResource)) },
            )
        }
    }
}

@Composable
private fun MainViewIcon(view: MainView) {
    val color = LocalContentColor.current
    Canvas(
        modifier =
            Modifier
                .size(24.dp)
                .clearAndSetSemantics {},
    ) {
        val strokeWidth = 2.dp.toPx()
        val lineStyle =
            Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        when (view) {
            MainView.HOME -> {
                val path =
                    Path().apply {
                        moveTo(size.width * 0.18f, size.height * 0.46f)
                        lineTo(size.width * 0.5f, size.height * 0.18f)
                        lineTo(size.width * 0.82f, size.height * 0.46f)
                        moveTo(size.width * 0.26f, size.height * 0.39f)
                        lineTo(size.width * 0.26f, size.height * 0.82f)
                        lineTo(size.width * 0.74f, size.height * 0.82f)
                        lineTo(size.width * 0.74f, size.height * 0.39f)
                    }
                drawPath(path = path, color = color, style = lineStyle)
            }

            MainView.HISTORY -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = color,
                    radius = size.minDimension * 0.34f,
                    center = center,
                    style = lineStyle,
                )
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(center.x, size.height * 0.3f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = center,
                    end = Offset(size.width * 0.66f, size.height * 0.59f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            MainView.OVERVIEW -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                val diameter = size.minDimension * 0.68f
                drawCircle(
                    color = color,
                    radius = diameter / 2f,
                    center = center,
                    style = lineStyle,
                )
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 105f,
                    useCenter = true,
                    topLeft = Offset(center.x - diameter / 2f, center.y - diameter / 2f),
                    size = Size(diameter, diameter),
                )
            }
        }
    }
}

@Composable
private fun SettingsControlsIcon() {
    val color = LocalContentColor.current
    Canvas(
        modifier =
            Modifier
                .size(24.dp)
                .clearAndSetSemantics {},
    ) {
        val strokeWidth = 2.dp.toPx()
        val knobPositions = listOf(0.35f, 0.68f, 0.43f)
        val rowPositions = listOf(0.25f, 0.5f, 0.75f)
        rowPositions.zip(knobPositions).forEach { (row, knob) ->
            val y = size.height * row
            drawLine(
                color = color,
                start = Offset(size.width * 0.16f, y),
                end = Offset(size.width * 0.84f, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = color,
                radius = size.minDimension * 0.09f,
                center = Offset(size.width * knob, y),
            )
        }
    }
}

private val RavenBackground = Color(0xFF08051F)
