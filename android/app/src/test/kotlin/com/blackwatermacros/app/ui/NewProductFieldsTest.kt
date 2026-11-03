package com.blackwatermacros.app.ui

import com.blackwatermacros.app.core.LabelBasis
import com.blackwatermacros.app.core.LabelReading
import com.blackwatermacros.app.core.OffKnownProduct
import com.blackwatermacros.app.ui.foods.NewProductFields
import com.blackwatermacros.app.ui.foods.ProductDraft
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * «Idioma del envase» in «Nuevo producto» (web `add-food-new-product.test.tsx`):
 * Open Food Facts keeps names and label photos per language, so a new product
 * goes under the package's language, not the app's.
 */
class NewProductFieldsTest {

    private val reading = LabelReading("Leche entera", "pt", "PIL", LabelBasis.SERVING, "200 ml", 124.0, 6.4, 9.6, 6.6)

    @Test
    fun `a new product starts in the app's language and takes the one the AI read`() {
        val fields = NewProductFields.of(ProductDraft(code = "7771234000123", existing = false), appLang = "fr")
        assertThat(fields.lang).isEqualTo("fr")
        assertThat(fields.withReading(reading).lang).isEqualTo("pt")
    }

    @Test
    fun `a product Open Food Facts has keeps its language, whatever the label photo says`() {
        val draft = ProductDraft(code = "8410000000017", existing = true, known = OffKnownProduct("Galletas", null, "es"), lang = "es")
        val fields = NewProductFields.of(draft, appLang = "fr")
        assertThat(fields.lang).isEqualTo("es")
        assertThat(fields.withReading(reading, keepLang = true).lang).isEqualTo("es")
    }
}
