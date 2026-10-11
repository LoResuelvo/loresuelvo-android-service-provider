package com.loresuelvo.serviceprovider.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.ui.screens.statistics.*
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w390dp-h800dp-mdpi")
class PerformanceRefreshScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun assertPull(screen: @Composable (() -> Unit) -> Unit) {
        var refreshes = 0
        compose.setContent { LoresuelvoTheme { screen { refreshes++ } } }
        compose.onRoot().performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun activity_supports_pull_refresh() = assertPull {
        ProviderActivityScreen(ProviderActivityUiState.Ready(activityFixture()), it)
    }
    @Test fun collections_supports_pull_refresh() = assertPull {
        ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(collectionsFixture()), it)
    }
    @Test fun reputation_supports_pull_refresh() = assertPull {
        ProviderReputationScreen(ProviderReputationUiState.Ready(reputationFixture()), {}, onRefresh = it)
    }
    @Test fun conversion_supports_pull_refresh() = assertPull {
        ProviderConversionScreen(ProviderConversionUiState.Ready(conversionFixture()), onRetry = it)
    }
}
