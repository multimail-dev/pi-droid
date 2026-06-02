package dev.anthropic.pidroid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthropic.pidroid.demo.ui.AppNavigation
import dev.anthropic.pidroid.demo.ui.ChatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val chatViewModel: ChatViewModel = viewModel()

                // Wire ChatViewModel to runtime once (identity-guarded in attachRuntime)
                LaunchedEffect(Unit) {
                    PiDroidDemoApp.runtime?.let { runtime ->
                        chatViewModel.attachRuntime(runtime)
                    }
                }

                AppNavigation()
            }
        }
    }
}
