package com.zeno.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zeno.assistant.ui.ZenoApp
import com.zeno.assistant.ui.theme.ZenoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZenoTheme {
                ZenoApp()
            }
        }
    }
}
