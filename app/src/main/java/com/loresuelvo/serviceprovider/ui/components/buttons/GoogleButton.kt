package com.loresuelvo.serviceprovider.ui.components.buttons

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.ui.theme.GoogleBlue
import com.loresuelvo.serviceprovider.ui.theme.GoogleText
import com.loresuelvo.serviceprovider.ui.theme.TextWhite

@Composable
fun GoogleButton(
    text: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = TextWhite,
            contentColor = GoogleText,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text(
            text = "G",
            color = GoogleBlue,
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.size(10.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}