package com.loresuelvo.serviceprovider.ui.screens.profile

import android.content.Context
import android.view.MotionEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderLogoutScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ready = ProviderProfileUiState.Ready(CurrentAccount.Provider(
        1, "Carlos", "Gomez", "provider@example.test", Category(1, "Plumbing"), null,
    ))

    @Test fun bottom_action_opens_confirmation_with_localized_title_and_both_controls() {
        val visible = mutableStateOf(false)
        var confirmations = 0
        compose.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(ready, onBack = {},
                    logoutConfirmationVisible = visible.value,
                    onRequestLogout = { visible.value = true },
                    onDismissLogout = { visible.value = false },
                    onConfirmLogout = { confirmations += 1 })
            }
        }
        compose.onNodeWithTag(PROVIDER_LOGOUT_ACTION_TAG).performScrollTo().assertIsDisplayed()
        val calendar = compose.onNodeWithTag(PROFILE_CALENDAR_STATUS_TAG).fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag(PROVIDER_LOGOUT_ACTION_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("Logout must follow Calendar in the scroll content", action.top > calendar.top)
        compose.onNodeWithTag(PROVIDER_LOGOUT_ACTION_TAG).performClick()
        compose.onNodeWithText(context.getString(R.string.provider_logout_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_logout_body)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_logout_cancel)).assertIsDisplayed().performClick()
        compose.onNodeWithTag(PROVIDER_LOGOUT_CONFIRM_TAG).assertDoesNotExist()
        assertEquals(0, confirmations)
        compose.onNodeWithTag(PROVIDER_LOGOUT_ACTION_TAG).performClick()
        compose.onNodeWithTag(PROVIDER_LOGOUT_CONFIRM_TAG).assertIsDisplayed().performClick()
        assertEquals(1, confirmations)
    }

    @Test fun system_back_dismisses_confirmation_without_confirming() {
        verifyDismissal { ShadowDialog.getLatestDialog().onBackPressed() }
    }

    @Test fun outside_touch_dismisses_confirmation_without_confirming() {
        verifyDismissal {
            val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_OUTSIDE, 0f, 0f, 0)
            try { ShadowDialog.getLatestDialog().onTouchEvent(event) } finally { event.recycle() }
        }
    }

    private fun verifyDismissal(dismiss: () -> Unit) {
        val visible = mutableStateOf(true)
        var confirmations = 0
        var dismissals = 0
        compose.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(ready, onBack = {}, logoutConfirmationVisible = visible.value,
                    onDismissLogout = { dismissals += 1; visible.value = false },
                    onConfirmLogout = { confirmations += 1 })
            }
        }
        compose.onNodeWithTag(PROVIDER_LOGOUT_CONFIRM_TAG).assertIsDisplayed()
        compose.runOnIdle(dismiss)
        compose.onNodeWithTag(PROVIDER_LOGOUT_CONFIRM_TAG).assertDoesNotExist()
        assertEquals(1, dismissals)
        assertEquals(0, confirmations)
    }
}
