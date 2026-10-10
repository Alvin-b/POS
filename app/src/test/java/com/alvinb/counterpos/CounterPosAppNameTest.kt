package com.alvinb.counterpos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CounterPosAppNameTest {
    @Test
    fun appIsNamedCounterPosAndHasCatalogSeed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Counter POS", context.getString(R.string.app_name))
        assertTrue(context.assets.list("")?.contains("counterpos_catalog.json") == true)
    }
}
