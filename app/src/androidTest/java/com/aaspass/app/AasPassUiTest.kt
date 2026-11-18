package com.aaspass.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.aaspass.app.ui.AasPassTheme
import androidx.compose.material3.Text
import org.junit.Rule
import org.junit.Test

class AasPassUiTest {
    @get:Rule val composeRule = createComposeRule()
    @Test fun brandTextVisible() {
        composeRule.setContent { AasPassTheme { Text("AasPass") } }
        composeRule.onNodeWithText("AasPass").assertIsDisplayed()
    }
}
