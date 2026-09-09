package io.github.kamui2040.vectorint.presentation.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.kamui2040.vectorint.BuildConfig
import io.github.kamui2040.vectorint.R

private enum class AboutPage {
    ROOT,
    CHANGELOG,
    LICENSE,
    SOURCES,
}

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(AboutPage.ROOT) }
    val uriHandler = LocalUriHandler.current
    val paneTitle = stringResource(R.string.about_title)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .heightIn(max = 760.dp)
                        .semantics { this.paneTitle = paneTitle },
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    AboutHeader(onDismiss = onDismiss)
                    if (page == AboutPage.ROOT) {
                        Text(
                            text = stringResource(R.string.about_purpose),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        AboutMenuCard(
                            title = stringResource(R.string.about_changelog),
                            onClick = { page = AboutPage.CHANGELOG },
                        )
                        AboutMenuCard(
                            title = stringResource(R.string.about_license),
                            onClick = { page = AboutPage.LICENSE },
                        )
                        AboutMenuCard(
                            title = stringResource(R.string.about_sources),
                            onClick = { page = AboutPage.SOURCES },
                        )
                        Text(
                            text = stringResource(R.string.about_privacy),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        TextButton(onClick = { page = AboutPage.ROOT }) {
                            Text(stringResource(R.string.entry_back))
                        }
                        when (page) {
                            AboutPage.CHANGELOG -> AboutChangelog()
                            AboutPage.LICENSE -> AboutLicense()
                            AboutPage.SOURCES ->
                                AboutSources(
                                    onOpenSource = { uriHandler.openUri(SOURCE_URL) },
                                )
                            AboutPage.ROOT -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutHeader(onDismiss: () -> Unit) {
    val closeLabel = stringResource(R.string.about_close)
    val enlargedText = LocalDensity.current.fontScale >= 1.2f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (enlargedText) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(if (enlargedText) 64.dp else 76.dp),
            shape = RoundedCornerShape(22.dp),
            color = BrandMarkBackground,
        ) {
            Image(
                painter = painterResource(R.drawable.k2040_logo),
                contentDescription = null,
                modifier = Modifier.padding(4.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.semantics { heading() },
                style = if (enlargedText) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .size(48.dp)
                    .semantics { contentDescription = closeLabel },
        ) {
            Text(
                text = "×",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun AboutMenuCard(
    title: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "›",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutChangelog() {
    AboutSection(
        title = stringResource(R.string.about_changelog),
        body = stringResource(R.string.about_changelog_body),
    )
}

@Composable
private fun AboutLicense() {
    AboutSection(
        title = stringResource(R.string.about_license),
        body = stringResource(R.string.about_license_body),
    )
}

@Composable
private fun AboutSources(onOpenSource: () -> Unit) {
    AboutSection(
        title = stringResource(R.string.about_sources),
        body = stringResource(R.string.about_sources_body),
    )
    Button(
        onClick = onOpenSource,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.about_open_source))
    }
}

@Composable
private fun AboutSection(
    title: String,
    body: String,
) {
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val BrandMarkBackground = Color(0xFF08051F)
private const val SOURCE_URL = "https://github.com/Kamui2040/Vectorint"
