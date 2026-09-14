package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var selectedImageIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    JobRequestDetailScreen(
        uiState = uiState,
        onClose = { navController.popBackStack() },
        onRetry = viewModel::retry,
        selectedImageIndex = selectedImageIndex,
        onImageSelected = { selectedImageIndex = it },
        onImageViewerClose = { selectedImageIndex = null },
    )
}
