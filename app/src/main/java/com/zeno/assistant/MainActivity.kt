package com.zeno.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zeno.assistant.ui.ZenoRoot
import com.zeno.assistant.ui.ZenoViewModel
import com.zeno.assistant.ui.theme.ZenoTheme

class MainActivity : ComponentActivity() {

    private val vm: ZenoViewModel by viewModels {
        val c = (application as ZenoApplication).container
        viewModelFactory {
            initializer {
                ZenoViewModel(
                    repo = c.settings,
                    platform = c.platform,
                    scanModels = c.models::scan,
                    modelsLocation = c.models.location,
                    wakeWordEngineBuilt = c.wakeWordEngineBuilt,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZenoTheme {
                ZenoRoot(vm)
            }
        }
    }
}
