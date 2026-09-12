package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.components.buttons.PrimaryButton
import com.loresuelvo.serviceprovider.ui.components.inputs.PrimaryTextField
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.profile.CategoriesLoadState
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileEffect
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.CompleteProviderProfileViewModel
import com.loresuelvo.serviceprovider.ui.profile.ProfileFormError

/**
 * Stateful destination wrapper for the provider profile completion flow.
 * Connects the [CompleteProviderProfileViewModel] with navigation effects.
 */
@Composable
fun CompleteProviderProfileRoute(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    viewModel: CompleteProviderProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.onPhotoSelected(uri.toString())
        } else {
            viewModel.onPhotoSelectionCancelled()
        }
    }

    LaunchedEffect(viewModel, navController) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CompleteProviderProfileEffect.NavigateToMercadoPago -> {
                    navController.navigate(Route.MercadoPagoConnect.path) {
                        popUpTo(Route.CompleteProviderProfile.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is CompleteProviderProfileEffect.NavigateToWelcome -> {
                    navController.navigate(Route.Welcome.path) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    CompleteProviderProfileScreen(
        uiState = uiState,
        onNameChanged = viewModel::onNameChanged,
        onSurnameChanged = viewModel::onSurnameChanged,
        onCategorySelected = viewModel::onCategorySelected,
        onRetryCategories = viewModel::retryLoadingCategories,
        onSelectPhoto = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

/**
 * Stateless screen for entering provider name, surname, and primary category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProviderProfileScreen(
    modifier: Modifier = Modifier,
    uiState: CompleteProviderProfileUiState = CompleteProviderProfileUiState(),
    onNameChanged: (String) -> Unit = {},
    onSurnameChanged: (String) -> Unit = {},
    onCategorySelected: (Category) -> Unit = {},
    onRetryCategories: () -> Unit = {},
    onSelectPhoto: () -> Unit = {},
    onUploadPhoto: () -> Unit = {},
    onSubmit: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Text(
                text = stringResource(R.string.provider_profile_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.provider_profile_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Global / Server error banner
            when (val error = uiState.error) {
                is ProfileFormError.AlreadyRegistered -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.provider_profile_already_registered_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                is ProfileFormError.Network, is ProfileFormError.Server -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.provider_profile_generic_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                else -> Unit
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Name Field
            PrimaryTextField(
                value = uiState.name,
                onValueChange = onNameChanged,
                label = stringResource(R.string.provider_profile_name_label),
                placeholder = stringResource(R.string.provider_profile_name_placeholder),
                isError = uiState.error is ProfileFormError.MissingName,
                errorMessage = stringResource(R.string.provider_profile_name_error),
                enabled = !uiState.loading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
            )

            // Surname Field
            PrimaryTextField(
                value = uiState.surname,
                onValueChange = onSurnameChanged,
                label = stringResource(R.string.provider_profile_surname_label),
                placeholder = stringResource(R.string.provider_profile_surname_placeholder),
                isError = uiState.error is ProfileFormError.MissingSurname,
                errorMessage = stringResource(R.string.provider_profile_surname_error),
                enabled = !uiState.loading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )

            // Category Selector
            when (val categoriesState = uiState.categoriesState) {
                is CategoriesLoadState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.provider_profile_category_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is CategoriesLoadState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.provider_profile_category_load_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            onClick = onRetryCategories,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(text = stringResource(R.string.provider_profile_retry))
                        }
                    }
                }
                is CategoriesLoadState.Ready -> {
                    CategoryDropdown(
                        categories = categoriesState.categories,
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelected = onCategorySelected,
                        isError = uiState.error is ProfileFormError.MissingCategory,
                        errorMessage = stringResource(R.string.provider_profile_category_error),
                        enabled = !uiState.loading,
                    )
                }
            }

            // Profile photo section
            ProfilePhotoSection(
                selectedPhoto = uiState.selectedPhoto,
                isPhotoConfirmed = uiState.isPhotoConfirmed,
                photoLoading = uiState.photoLoading,
                photoError = uiState.photoError,
                onSelectPhoto = onSelectPhoto,
                onUploadPhoto = onUploadPhoto,
            )

            // Disclaimer / Information
            Text(
                text = stringResource(R.string.provider_profile_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Submit Button
            PrimaryButton(
                text = if (uiState.loading) {
                    stringResource(R.string.provider_profile_submitting)
                } else {
                    stringResource(R.string.provider_profile_submit)
                },
                onClick = onSubmit,
                enabled = !uiState.loading,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category) -> Unit,
    isError: Boolean,
    errorMessage: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedCategory?.name ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(text = stringResource(R.string.provider_profile_category_label)) },
            placeholder = { Text(text = stringResource(R.string.provider_profile_category_placeholder)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = isError,
            supportingText = if (isError && errorMessage != null) {
                {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else null,
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(text = category.name) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    },
                )
            }
        }
    }
}
