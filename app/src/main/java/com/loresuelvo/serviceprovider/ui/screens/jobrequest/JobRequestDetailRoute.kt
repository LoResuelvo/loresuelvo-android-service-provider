package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailViewModel

@Composable
fun JobRequestDetailRoute(
    navController: NavHostController,
    viewModel: JobRequestDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    JobRequestDetailScreen(
        uiState = uiState,
        onClose = { navController.popBackStack() },
        onRetry = viewModel::retry,
    )
}
