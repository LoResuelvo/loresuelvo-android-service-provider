package com.loresuelvo.serviceprovider.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import com.loresuelvo.serviceprovider.R

data class ProviderBottomDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {

    companion object {
        val Home = ProviderBottomDestination(
            route = Route.Home.path,
            labelRes = R.string.provider_bottom_nav_home,
            icon = Icons.Outlined.Home,
        )
        val Messages = ProviderBottomDestination(
            route = Route.Messages.path,
            labelRes = R.string.provider_bottom_nav_messages,
            icon = Icons.Outlined.Message,
        )
        val all = listOf(Home, Messages)

        fun shouldShow(currentRoute: String?): Boolean = when (currentRoute) {
            Route.Home.path, Route.Messages.path -> true
            else -> false
        }
    }
}

@Composable
fun ProviderBottomBar(
    currentRoute: String?,
    onNavigate: (ProviderBottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!ProviderBottomDestination.shouldShow(currentRoute)) return

    NavigationBar(
        modifier = modifier.testTag(PROVIDER_BOTTOM_BAR_TAG),
    ) {
        ProviderBottomDestination.all.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                modifier = Modifier.testTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + destination.route),
            )
        }
    }
}

const val PROVIDER_BOTTOM_BAR_TAG = "provider-bottom-bar"
const val PROVIDER_BOTTOM_BAR_ITEM_PREFIX = "provider-bottom-bar-item-"
