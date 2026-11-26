package com.cfstudio.hydrogenos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperAndReleaseTest {
    @Test
    fun wallpaperCatalogHasTwelveDistinctNamesAndThreeColorStops() {
        assertEquals(12, wallpaperNames.size)
        assertEquals(12, wallpaperNames.toSet().size)
        for (index in wallpaperNames.indices) {
            assertEquals(3, wallpaperColors(index).size)
        }
    }

    @Test
    fun updateComparisonUsesNumericVersionCodes() {
        assertTrue(isNewerVersionCode(2, 1))
        assertFalse(isNewerVersionCode(1, 1))
        assertFalse(isNewerVersionCode(1, 2))
    }
}
