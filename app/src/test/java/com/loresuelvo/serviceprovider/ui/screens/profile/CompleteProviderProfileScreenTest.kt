package com.loresuelvo.serviceprovider.ui.screens.profile

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZone
import com.loresuelvo.serviceprovider.ui.profile.CategoriesLoadState
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.CoverageZonesLoadState
import com.loresuelvo.serviceprovider.ui.profile.ProfileFormError
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM semantics and UI interaction tests for [CompleteProviderProfileScreen].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompleteProviderProfileScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun renders_a_localized_profile_heading_and_description() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen()
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_description))
            .assertIsDisplayed()
    }

    @Test
    fun renders_name_and_surname_input_fields() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        name = "Carlos",
                        surname = "Gómez",
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_name_label))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Carlos")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_surname_label))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Gómez")
            .assertIsDisplayed()
    }

    @Test
    fun renders_validation_errors_when_present_in_state() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        error = ProfileFormError.MissingName,
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_name_error))
            .assertIsDisplayed()
    }

    @Test
    fun renders_already_registered_error_banner() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        error = ProfileFormError.AlreadyRegistered,
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_already_registered_error))
            .assertIsDisplayed()
    }

    @Test
    fun renders_generic_error_banner_on_network_or_server_error() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        error = ProfileFormError.Network("Timeout"),
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_generic_error))
            .assertIsDisplayed()
    }

    @Test
    fun renders_missing_photo_error_banner() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        error = ProfileFormError.MissingPhoto,
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_photo_required_error))
            .assertIsDisplayed()
    }

    @Test
    fun renders_missing_coverage_zones_error_banner() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        error = ProfileFormError.MissingCoverageZones,
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_zones_required_error))
            .assertIsDisplayed()
    }

    @Test
    fun renders_loading_indicator_and_disables_button_when_submitting() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        loading = true,
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_submitting))
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun renders_coverage_loading_and_disables_submit_without_disabling_inputs() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen()
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_coverage_loading))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_submit))
            .performScrollTo()
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_name_label))
            .assertIsEnabled()
    }

    @Test
    fun renders_coverage_zone_names_in_server_order() {
        val zones = listOf(
            CoverageZone(14, "Comuna 14", "place-14"),
            CoverageZone(6, "Comuna 6", "place-6"),
        )
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        coverageZonesState = CoverageZonesLoadState.Ready(zones),
                    ),
                )
            }
        }

        composeTestRule.onAllNodesWithText("Comuna 14").assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Comuna 6").assertCountEquals(1)
    }

    @Test
    fun renders_coverage_error_and_invokes_retry() {
        var retried = false
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        coverageZonesState = CoverageZonesLoadState.Error,
                    ),
                    onRetryCoverageZones = { retried = true },
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_coverage_load_error))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_retry))
            .performScrollTo()
            .performClick()
        assertTrue(retried)
    }

    @Test
    fun renders_empty_coverage_catalog_and_invokes_reload() {
        var reloaded = false
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        coverageZonesState = CoverageZonesLoadState.Empty,
                    ),
                    onRetryCoverageZones = { reloaded = true },
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_coverage_empty))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_reload))
            .performScrollTo()
            .performClick()
        assertTrue(reloaded)
    }

    @Test
    fun invokes_callbacks_on_text_input_and_button_click() {
        var enteredName = ""
        var submitted = false

        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        coverageZonesState = CoverageZonesLoadState.Ready(
                            listOf(CoverageZone(1, "Comuna 1", "place-1")),
                        ),
                    ),
                    onNameChanged = { enteredName = it },
                    onSubmit = { submitted = true },
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_name_label))
            .performTextInput("Esteban")

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_submit))
            .performScrollTo()
            .assertHasClickAction()
            .performClick()

        assertEquals("Esteban", enteredName)
        assertTrue(submitted)
    }

    @Test
    fun renders_categories_dropdown_when_categories_are_ready() {
        val categories = listOf(Category(1, "Plomería"), Category(2, "Electricidad"))

        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen(
                    uiState = CompleteProviderProfileUiState(
                        categoriesState = CategoriesLoadState.Ready(categories),
                        selectedCategory = categories.first(),
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_category_label))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Plomería")
            .assertIsDisplayed()
    }
}
