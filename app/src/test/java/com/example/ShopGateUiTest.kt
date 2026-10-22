package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.InventoryViewModel
import com.example.ui.MainScreen
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The first thing an employee sees: connect the phone, sign in, then the app.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShopGateUiTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var shop: TestShopServer

    @Before
    fun setUp() {
        AppDatabase.resetForTests()
        shop = TestShopServer()
        shop.auth.createEmployee("Sam", "employee", "1234")
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTests()
        shop.close()
    }

    @Test
    fun `a new phone connects with a setup code, signs in with a PIN, then sees the app`() {
        val vm = InventoryViewModel(ApplicationProvider.getApplicationContext<Application>())
        compose.setContent { MainScreen(viewModel = vm) }

        compose.onNodeWithTag("tab_scanner").assertDoesNotExist()
        compose.onNodeWithTag("connect_address").performTextClearance()
        compose.onNodeWithTag("connect_address").performTextInput("http://example.org")
        compose.onNodeWithTag("connect_code").performTextInput("ANY")
        compose.onNodeWithTag("connect_phone_name").performTextInput("Front counter")
        // Plain http to a real host is never allowed.
        compose.onNodeWithTag("connect_button").assertIsNotEnabled()

        compose.onNodeWithTag("connect_address").performTextClearance()
        compose.onNodeWithTag("connect_address").performTextInput(shop.baseUrl)
        compose.onNodeWithTag("connect_code").performTextClearance()
        compose.onNodeWithTag("connect_code").performTextInput(shop.auth.newSetupCode("test"))
        compose.onNodeWithTag("connect_button").performClick()

        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("signin_name_Sam")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("signin_name_Sam").performClick()
        compose.onNodeWithTag("signin_pin").performTextInput("1234")
        compose.onNodeWithTag("signin_button").performClick()

        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("tab_scanner")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sign_out_button").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("signin_button")).fetchSemanticsNodes().isNotEmpty() }
    }
}
