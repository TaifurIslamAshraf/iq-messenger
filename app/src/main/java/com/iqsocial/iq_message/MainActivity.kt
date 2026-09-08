package com.iqsocial.iq_message

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.iqsocial.iq_message.ui.theme.IQMessageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars; each screen sets its own icon contrast via SystemBarIcons.
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent)
        )
        setContent {
            IQMessageTheme {
                IqApp()
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 915)
@Composable
private fun AppPreview() {
    IQMessageTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            IqApp()
        }
    }
}
