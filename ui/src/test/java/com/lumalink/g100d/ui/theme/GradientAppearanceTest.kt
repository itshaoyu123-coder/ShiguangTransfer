package com.lumalink.g100d.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class GradientAppearanceTest {
    @Test fun parsesOpaqueRgbAndRejectsInvalidOrAlphaInput() {
        assertEquals(0xFFB4D3D1L, parseGradientHex(" #b4d3d1 "))
        assertEquals(0xFF000000L, parseGradientHex("000000"))
        for (text in listOf("", "#123", "#12345678", "GGGGGG", "-00000", "##123456")) assertNull(parseGradientHex(text))
    }
    @Test fun storedRgbRoundTripsWithoutAlphaOrLocaleAmbiguity() {
        for (color in listOf(0xFF000000L, 0xFFFFFFFFL, 0xFFB4D3D1L)) {
            assertEquals(color, parseGradientHex(gradientHex(color)))
        }
    }
    @Test fun presetsAndDarkCustomColoursKeepTextReadable() {
        val darkText = Color(0xFF405657).luminance()
        val candidates = GradientPresets + GradientPalette("custom", "", 0xFF000000L, 0xFFFF0000L, 0xFF0000FFL)
        for (palette in candidates) for (color in palette.colors()) {
            val contrast = (color.luminance() + .05f) / (darkText + .05f)
            assertTrue("${palette.id} contrast=$contrast", contrast >= 4.5f)
        }
    }
}
