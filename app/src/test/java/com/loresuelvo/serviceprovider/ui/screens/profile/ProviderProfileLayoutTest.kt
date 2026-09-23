package com.loresuelvo.serviceprovider.ui.screens.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.profile.ProfilePaymentState
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w390dp-h1300dp-mdpi")
class ProviderProfileLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var renderView: View

    @Test
    fun profile_cards_render_long_identity_without_clipping() {
        compose.setContent {
            renderView = LocalView.current
            LoresuelvoTheme {
                ProviderProfileScreen(
                    ProviderProfileUiState.Ready(provider, ProfilePaymentState.Connected),
                    onBack = {},
                )
            }
        }

        saveRender("profile-light")
        assertTextFits("Diego Fernando Herrera")
        assertTextFits(provider.category.name)
        assertTextFits(provider.email)
        compose.onNodeWithTag("provider-profile-rating-demo-label").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "es-w320dp-h800dp-mdpi")
    fun compact_profile_supports_large_type_rtl_and_dark_surfaces() {
        compose.setContent {
            renderView = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    ProviderProfileScreen(
                        ProviderProfileUiState.Ready(provider, ProfilePaymentState.Pending),
                        onBack = {},
                    )
                }
            }
        }

        assertTextFits("Diego Fernando Herrera")
        assertTextFits(provider.category.name)
        saveRender("profile-large-type-rtl")
        assertTextFits(provider.email)
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.onNodeWithText(context.getString(R.string.mercadopago_connect_button))
            .performScrollTo().assertIsDisplayed()
    }

    private fun assertTextFits(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue("Expected text layout for $text", results.isNotEmpty())
        results.forEach {
            assertFalse("Clipped text: $text; size=${it.size}, lines=${it.lineCount}, " +
                "height=${it.multiParagraph.height}, width=${it.multiParagraph.width}", it.hasVisualOverflow)
        }
    }

    private fun saveRender(name: String) {
        val file = File("build/reports/profile-design/$name.png")
        file.parentFile.mkdirs()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(renderView.width, renderView.height, Bitmap.Config.ARGB_8888)
            renderView.draw(Canvas(bitmap))
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }

    private val provider = CurrentAccount.Provider(
        id = 1,
        name = "Diego Fernando",
        surname = "Herrera",
        email = "diego.herrera.servicios@gmail.com",
        category = Category(1, "Electricista matriculado"),
        profilePhotoUrl = null,
        identityVerificationStatus = IdentityVerificationStatus.Unverified,
    )
}
