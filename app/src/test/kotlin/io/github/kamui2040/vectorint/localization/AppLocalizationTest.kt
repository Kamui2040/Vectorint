package io.github.kamui2040.vectorint.localization

import android.app.Application
import io.github.kamui2040.vectorint.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

class AppLocalizationParityTest {
    private val resourceRoot: Path =
        sequenceOf(
            Path.of("src/main/res"),
            Path.of("app/src/main/res"),
        ).firstOrNull { Files.isDirectory(it) }
            ?: error("Android resource directory not found")

    @Test
    fun `every localized catalogue matches the English fallback`() {
        val base = readCatalogue(resourceRoot.resolve("values/strings.xml"))
        val translatableBase = base.filterValues { it.translatable }
        val localizedCatalogues =
            Files.list(resourceRoot).use { paths ->
                paths
                    .filter { path ->
                        Files.isDirectory(path) &&
                            path.fileName.toString().startsWith("values-") &&
                            Files.isRegularFile(path.resolve("strings.xml"))
                    }.sorted()
                    .toList()
            }

        assertTrue(
            "German resources must be present",
            localizedCatalogues.any { it.fileName.toString() == "values-de" },
        )
        assertFalse("At least one localized catalogue is required", localizedCatalogues.isEmpty())
        assertFalse("The app name is a brand, not translatable text", base.getValue("app_name").translatable)

        localizedCatalogues.forEach { directory ->
            val localized = readCatalogue(directory.resolve("strings.xml"))
            assertEquals(
                "Localized keys differ in ${directory.fileName}",
                translatableBase.keys,
                localized.keys,
            )
            localized.forEach { (name, entry) ->
                assertTrue("$name is blank in ${directory.fileName}", entry.value.isNotBlank())
                assertEquals(
                    "Format placeholders differ for $name in ${directory.fileName}",
                    placeholders(translatableBase.getValue(name).value),
                    placeholders(entry.value),
                )
            }
        }
    }

    @Test
    fun `English is the declared unqualified resource locale`() {
        val properties = Properties()
        Files.newInputStream(resourceRoot.resolve("resources.properties")).use(properties::load)

        assertEquals("en", properties.getProperty("unqualifiedResLocale"))
    }

    @Test
    fun `German copy uses informal address`() {
        val german = readCatalogue(resourceRoot.resolve("values-de/strings.xml"))
        val formalAddress = Regex("\\b(?:Sie|Ihnen|Ihr|Ihre|Ihren|Ihrem|Ihres)\\b")
        val violations =
            german
                .filterValues { entry -> formalAddress.containsMatchIn(entry.value) }
                .keys

        assertTrue("Formal German address remains in: $violations", violations.isEmpty())
    }

    private fun readCatalogue(path: Path): Map<String, StringEntry> {
        val documentBuilderFactory =
            DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setAttribute(ACCESS_EXTERNAL_DTD, "")
                setAttribute(ACCESS_EXTERNAL_SCHEMA, "")
                isExpandEntityReferences = false
            }
        val document = Files.newInputStream(path).use(documentBuilderFactory.newDocumentBuilder()::parse)
        val nodes = document.documentElement.getElementsByTagName("string")
        val result = linkedMapOf<String, StringEntry>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            val name = element.getAttribute("name")
            val previous =
                result.put(
                    name,
                    StringEntry(
                        value = element.textContent.trim(),
                        translatable = element.getAttribute("translatable") != "false",
                    ),
                )
            assertEquals("Duplicate string resource $name in $path", null, previous)
        }
        return result
    }

    private fun placeholders(value: String): List<String> =
        FORMAT_PLACEHOLDER
            .findAll(value)
            .map { it.value }
            .sorted()
            .toList()

    private data class StringEntry(
        val value: String,
        val translatable: Boolean,
    )

    private companion object {
        const val ACCESS_EXTERNAL_DTD = "http://javax.xml.XMLConstants/property/accessExternalDTD"
        const val ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema"
        val FORMAT_PLACEHOLDER = Regex("%(?:\\d+\\$)?[a-zA-Z]")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "de")
class GermanLocalizationRuntimeTest {
    private val application: Application
        get() = RuntimeEnvironment.getApplication()

    @Test
    fun `Android resolves German UI resources and format arguments`() {
        assertEquals("Start", application.getString(R.string.navigation_home))
        assertEquals("Verlauf", application.getString(R.string.navigation_history))
        assertEquals("Einstellungen", application.getString(R.string.navigation_settings))
        assertEquals("Jetzt verfügbar", application.getString(R.string.home_available_now))
        assertEquals("Währung: EUR", application.getString(R.string.activity_currency, "EUR"))
        assertEquals(
            "Alle 3 Monate · erstmals 30.09.2026",
            application.getString(
                R.string.recurring_schedule_summary,
                application.resources.getQuantityString(R.plurals.recurring_months, 3, 3),
                "30.09.2026",
            ),
        )
        assertEquals(
            "Monatlich · erstmals 30.09.2026",
            application.getString(
                R.string.recurring_schedule_summary,
                application.resources.getQuantityString(R.plurals.recurring_months, 1, 1),
                "30.09.2026",
            ),
        )
    }
}
