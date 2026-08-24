package com.loresuelvo.serviceprovider.ui.screens.auth.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme

/**
 * Horizontally scrollable row of illustrative service categories.
 * Chips are display-only (disabled) on the Welcome screen: they make
 * the product scope tangible without promising pre-login navigation.
 * The interactive grid lives on the Home screen — see
 * `ui/screens/home/components/CategoryGrid.kt` once that lands.
 */
@Composable
fun CategoryChipRow(
    categories: List<String>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { category ->
            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        text = category,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledLabelColor = MaterialTheme.colorScheme.onBackground,
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryChipRowPreview() {
    LoresuelvoTheme {
        CategoryChipRow(
            categories = stringArrayResource(R.array.welcome_categories).toList(),
        )
    }
}