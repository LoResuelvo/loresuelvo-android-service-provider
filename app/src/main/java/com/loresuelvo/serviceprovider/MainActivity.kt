package com.loresuelvo.serviceprovider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.loresuelvo.serviceprovider.ui.screens.home.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LoResuelvoApp() }
    }
}

@Composable
private fun LoResuelvoApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        HomeScreen(
            title = stringResource(R.string.home_title),
            greeting = stringResource(R.string.home_greeting),
            modifier = Modifier.padding(padding),
        )
    }
}
