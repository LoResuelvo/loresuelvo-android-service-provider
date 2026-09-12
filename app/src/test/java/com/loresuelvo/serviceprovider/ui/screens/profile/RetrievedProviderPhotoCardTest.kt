package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RetrievedProviderPhotoCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysProviderNameAndPhotoLabelWhenPhotoIsPresent() {
        composeTestRule.setContent {
            RetrievedProviderPhotoCard(
                photoUrl = "https://cdn.example/photo.jpg",
                providerName = "Carlos Gómez",
            )
        }

        composeTestRule.onNodeWithTag("retrieved_provider_photo_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retrieved_provider_photo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carlos Gómez").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retrieved_provider_photo_label").assertIsDisplayed()
    }

    @Test
    fun displaysNoPhotoLabelWhenPhotoIsNull() {
        composeTestRule.setContent {
            RetrievedProviderPhotoCard(
                photoUrl = null,
                providerName = "Carlos Gómez",
            )
        }

        composeTestRule.onNodeWithTag("retrieved_provider_photo_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carlos Gómez").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retrieved_provider_photo_label").assertIsDisplayed()
    }
}
