package com.sitticun.orderapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.sitticun.orderapp.OrderApplication
import com.sitticun.orderapp.ui.screens.admin.AdminScreen
import com.sitticun.orderapp.ui.screens.auth.LoginScreen
import com.sitticun.orderapp.ui.screens.counter.ImageCounterScreen
import com.sitticun.orderapp.ui.screens.customers.CustomersScreen
import com.sitticun.orderapp.ui.screens.history.OrderHistoryScreen
import com.sitticun.orderapp.ui.screens.pos.PosScreen
import com.sitticun.orderapp.ui.screens.stock.StockScreen
import com.sitticun.orderapp.ui.theme.*

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Login : Screen("login", "เข้าสู่ระบบ", Icons.Default.Lock)
    object Pos : Screen("pos", "ขายสินค้า (POS)", Icons.Default.PointOfSale)
    object Stock : Screen("stock", "สต็อก", Icons.Default.Inventory2)
    object Counter : Screen("counter", "นับสต็อกรูป", Icons.Default.CameraAlt)
    object Customers : Screen("customers", "ลูกค้า/แต้ม", Icons.Default.People)
    object History : Screen("history", "ประวัติการขาย", Icons.Default.ReceiptLong)
    object Admin : Screen("admin", "ผู้ดูแล/ซิงค์", Icons.Default.Tune)
}

val bottomNavItems = listOf(
    Screen.Pos,
    Screen.Stock,
    Screen.Counter,
    Screen.Customers,
    Screen.History,
    Screen.Admin
)

@Composable
fun OrderAppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isLoggedIn = OrderApplication.instance.authRepository.isLoggedIn
    val startDestination = if (isLoggedIn) Screen.Pos.route else Screen.Login.route

    Scaffold(
        bottomBar = {
            if (currentRoute != Screen.Login.route) {
                NavigationBar(
                    containerColor = CardWhite,
                    tonalElevation = 6.dp,
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PastelMintDark,
                                selectedTextColor = PastelMintDark,
                                indicatorColor = PastelMintLight,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Pos.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Pos.route) {
                PosScreen()
            }

            composable(Screen.Stock.route) {
                StockScreen()
            }

            composable(Screen.Counter.route) {
                ImageCounterScreen()
            }

            composable(Screen.Customers.route) {
                CustomersScreen()
            }

            composable(Screen.History.route) {
                OrderHistoryScreen()
            }

            composable(Screen.Admin.route) {
                AdminScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
