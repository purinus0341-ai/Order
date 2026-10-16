package com.sitticun.orderapp.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Login : Screen("login", "เข้าสู่ระบบ", Icons.Filled.Lock, Icons.Outlined.Lock)
    object Pos : Screen("pos", "หน้าร้าน", Icons.Filled.Storefront, Icons.Outlined.Storefront)
    object Stock : Screen("stock", "สต็อก", Icons.Filled.Inventory2, Icons.Outlined.Inventory2)
    object Counter : Screen("counter", "นับรูป", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt)
    object Customers : Screen("customers", "สมาชิก", Icons.Filled.People, Icons.Outlined.People)
    object History : Screen("history", "บิลขาย", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong)
    object Admin : Screen("admin", "ตั้งค่า", Icons.Filled.Settings, Icons.Outlined.Settings)
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
        containerColor = IosGroupedBackground,
        bottomBar = {
            if (currentRoute != Screen.Login.route) {
                IosTabBar(
                    items = bottomNavItems,
                    currentRoute = currentRoute,
                    onItemSelected = { screen ->
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
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

/**
 * Authentic iOS UITabBar with translucent white surface, 0.5dp top hairline separator,
 * no Material pill indicator, and crisp SF-style active/inactive tinting.
 */
@Composable
private fun IosTabBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemSelected: (Screen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(IosTranslucentBar)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(
            color = IosSeparator.copy(alpha = 0.55f),
            thickness = 0.5.dp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { screen ->
                val selected = currentRoute == screen.route
                val tintColor = if (selected) IosBlue else IosSystemGray

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelected(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selected) screen.icon else screen.unselectedIcon,
                        contentDescription = screen.title,
                        tint = tintColor,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = screen.title,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = tintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = (-0.1).sp
                    )
                }
            }
        }
    }
}
