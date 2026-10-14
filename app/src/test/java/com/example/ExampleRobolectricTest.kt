package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.telephony.UssdManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app_name string resource from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("USSD QuickDial", appName)
    }

    @Test
    fun `test ussd simulation response for target code`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = UssdManager(context)
        val targetCode = "*31124*1#"
        val result = manager.simulateUssd(targetCode)

        assertEquals(targetCode, result.code)
        assertTrue(result.isSuccess)
        assertNotNull(result.responseMessage)
        assertTrue(result.responseMessage.contains("*31124*1#"))
        assertTrue(result.responseMessage.contains("Active"))
    }
}
