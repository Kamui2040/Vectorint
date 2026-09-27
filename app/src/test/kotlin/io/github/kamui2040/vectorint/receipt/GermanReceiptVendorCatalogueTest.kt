package io.github.kamui2040.vectorint.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class GermanReceiptVendorCatalogueTest {
    private val vendors = GermanReceiptVendorCatalogue.vendors

    @Test
    fun `covers a broad first set of German retailers`() {
        assertTrue(vendors.size >= 100)
        assertEquals(ReceiptVendorCategory.entries.toSet(), vendors.map { it.category }.toSet())
    }

    @Test
    fun `uses stable unique identifiers`() {
        assertEquals(vendors.size, vendors.map { it.id }.toSet().size)
        assertTrue(vendors.all { it.id.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) })
    }

    @Test
    fun `each vendor has a nonblank name and aliases`() {
        assertTrue(vendors.all { it.displayName.isNotBlank() })
        assertTrue(vendors.all { vendor -> vendor.receiptAliases.all(String::isNotBlank) })
        assertTrue(vendors.all { it.displayName in it.receiptAliases })
    }

    @Test
    fun `receipt aliases do not collide after matching normalization`() {
        val aliases =
            vendors.flatMap { vendor ->
                vendor.receiptAliases.map { alias -> normalized(alias) to vendor.id }
            }
        val collisions =
            aliases
                .groupBy({ it.first }, { it.second })
                .filterValues { ids -> ids.distinct().size > 1 }

        assertTrue("Ambiguous aliases: $collisions", collisions.isEmpty())
    }

    @Test
    fun `keeps ambiguous discount names qualified`() {
        val aliasesById = vendors.associate { it.id to it.receiptAliases.map(::normalized).toSet() }

        assertTrue("aldi" !in aliasesById.getValue("aldi-nord"))
        assertTrue("aldi" !in aliasesById.getValue("aldi-sued"))
        assertTrue("netto" !in aliasesById.getValue("netto-marken-discount"))
        assertTrue("netto" !in aliasesById.getValue("netto-scottie"))
    }

    private fun normalized(value: String): String =
        Normalizer
            .normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
