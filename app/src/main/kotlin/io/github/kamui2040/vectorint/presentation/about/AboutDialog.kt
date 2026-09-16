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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val scrollState = rememberScrollState()

    LaunchedEffect(page) {
        scrollState.scrollTo(0)
    }

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
                    .padding(horizontal = 12.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth(0.9f)
                        .widthIn(max = 440.dp)
                        .heightIn(max = 680.dp)
                        .semantics { this.paneTitle = paneTitle },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Box {
                    Column(
                        modifier =
                            Modifier
                                .verticalScroll(scrollState)
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (page) {
                            AboutPage.ROOT -> {
                                AboutHeader()
                                Text(
                                    text = stringResource(R.string.about_purpose),
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                AboutMenuCard(
                                    title = stringResource(R.string.about_support_kofi),
                                    external = true,
                                    onClick = { uriHandler.openUri(KOFI_URL) },
                                )
                                Text(
                                    text = stringResource(R.string.about_privacy),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = 2.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            AboutPage.CHANGELOG ->
                                AboutChangelog(onBack = { page = AboutPage.ROOT })

                            AboutPage.LICENSE ->
                                AboutLicense(onBack = { page = AboutPage.ROOT })

                            AboutPage.SOURCES ->
                                AboutSources(
                                    onBack = { page = AboutPage.ROOT },
                                    onOpenRepository = { uriHandler.openUri(REPOSITORY_URL) },
                                    onOpenAppWebsite = { uriHandler.openUri(APP_WEBSITE_URL) },
                                    onOpenMainWebsite = { uriHandler.openUri(MAIN_WEBSITE_URL) },
                                )
                        }
                    }
                    AboutCloseButton(
                        onDismiss = onDismiss,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutHeader() {
    val enlargedText = LocalDensity.current.fontScale >= 1.2f
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Surface(
            modifier = Modifier.size(if (enlargedText) 56.dp else 64.dp),
            shape = RoundedCornerShape(20.dp),
            color = BrandMarkBackground,
        ) {
            Image(
                painter = painterResource(R.drawable.k2040_logo),
                contentDescription = null,
                modifier = Modifier.padding(4.dp),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.semantics { heading() },
            style = if (enlargedText) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AboutCloseButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeLabel = stringResource(R.string.about_close)
    TextButton(
        onClick = onDismiss,
        modifier =
            modifier
                .size(48.dp)
                .semantics { contentDescription = closeLabel },
    ) {
        Text(
            text = "×",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun AboutMenuCard(
    title: String,
    onClick: () -> Unit,
    external: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (external) "↗" else "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AboutChangelog(onBack: () -> Unit) {
    AboutSectionHeader(
        title = stringResource(R.string.about_changelog),
        onBack = onBack,
    )
    Text(
        text = stringResource(R.string.about_changelog_version, BuildConfig.VERSION_NAME),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = stringResource(R.string.about_changelog_current_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AboutLicense(onBack: () -> Unit) {
    AboutSectionHeader(
        title = stringResource(R.string.about_license),
        onBack = onBack,
    )
    AboutInfoCard(
        title = stringResource(R.string.about_license_source_name),
        value = stringResource(R.string.about_license_source_value),
    )
    AboutInfoCard(
        title = stringResource(R.string.about_license_raven_name),
        value = stringResource(R.string.about_license_raven_value),
        supporting = stringResource(R.string.about_license_raven_note),
    )
    AboutInfoCard(
        title = stringResource(R.string.about_license_material_icons_name),
        value = stringResource(R.string.about_license_material_icons_value),
    )
    AboutInfoCard(
        title = stringResource(R.string.about_license_desugar_name),
        value = stringResource(R.string.about_license_desugar_value),
    )
    Text(
        text = stringResource(R.string.about_license_logo_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.about_license_inventory_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AboutSources(
    onBack: () -> Unit,
    onOpenRepository: () -> Unit,
    onOpenAppWebsite: () -> Unit,
    onOpenMainWebsite: () -> Unit,
) {
    AboutSectionHeader(
        title = stringResource(R.string.about_sources),
        onBack = onBack,
    )
    Text(
        text = stringResource(R.string.about_sources_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AboutLinkCard(
        title = stringResource(R.string.about_source_repository),
        supporting = stringResource(R.string.about_source_repository_detail),
        onClick = onOpenRepository,
    )
    AboutLinkCard(
        title = stringResource(R.string.about_source_app_website),
        supporting = stringResource(R.string.about_source_app_website_detail),
        onClick = onOpenAppWebsite,
    )
    AboutLinkCard(
        title = stringResource(R.string.about_source_main_website),
        supporting = stringResource(R.string.about_source_main_website_detail),
        onClick = onOpenMainWebsite,
    )
}

@Composable
private fun AboutSectionHeader(
    title: String,
    onBack: () -> Unit,
) {
    TextButton(
        onClick = onBack,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Text("‹ ${stringResource(R.string.entry_back)}")
    }
    Text(
        text = title,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(end = 48.dp)
                .semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun AboutInfoCard(
    title: String,
    value: String,
    supporting: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AboutLinkCard(
    title: String,
    supporting: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "↗",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val BrandMarkBackground = Color(0xFF08051F)
private const val REPOSITORY_URL = "https://github.com/Kamui2040/Vectorint"
private const val APP_WEBSITE_URL = "https://kamui2040.github.io/K2040-Android-Releases/apps/vectorint/"
private const val MAIN_WEBSITE_URL = "https://kamui2040.github.io/"
private const val KOFI_URL = "https://ko-fi.com/k2040"
