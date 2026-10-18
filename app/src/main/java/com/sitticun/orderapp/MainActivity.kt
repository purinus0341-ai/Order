package com.sitticun.orderapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.sitticun.orderapp.ui.navigation.OrderAppNavGraph
import com.sitticun.orderapp.ui.theme.OrderAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OrderAppTheme {
                OrderAppNavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Active Profile Guard check on app resume
        lifecycleScope.launch {
            if (OrderApplication.instance.authRepository.isLoggedIn) {
                OrderApplication.instance.authRepository.verifyProfileActiveGuard()
                OrderApplication.instance.outboxRepository.syncPendingItems()
            }
        }
    }
}
