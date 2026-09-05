package com.rasmi.purevon.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.rasmi.purevon.presentation.theme.PurevonBackground
import com.rasmi.purevon.presentation.theme.PurevonBorder
import com.rasmi.purevon.presentation.theme.PurevonOnPrimary
import com.rasmi.purevon.presentation.theme.PurevonPrimary
import com.rasmi.purevon.presentation.theme.PurevonTextTertiary
import kotlin.reflect.KClass

/**
 * Fixed bottom navigation bar — 56dp, icon-only, active tab gets a
 * circular Bluesky-blue background with a white icon.
 */
data class BottomNavItem(
    val screen: Screen,
    val routeClass: KClass<out Screen>,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Dialer,
        routeClass = Screen.Dialer::class,
        iconSelected = Icons.Filled.Phone,
        iconUnselected = Icons.Outlined.Phone
    ),
    BottomNavItem(
        screen = Screen.Contacts,
        routeClass = Screen.Contacts::class,
        iconSelected = Icons.Filled.Person,
        iconUnselected = Icons.Outlined.Person
    ),
    BottomNavItem(
        screen = Screen.Messages,
        routeClass = Screen.Messages::class,
        iconSelected = Icons.AutoMirrored.Filled.Message,
        iconUnselected = Icons.AutoMirrored.Filled.Message
    ),
    BottomNavItem(
        screen = Screen.CallHistory,
        routeClass = Screen.CallHistory::class,
        iconSelected = Icons.Filled.History,
        iconUnselected = Icons.Outlined.History
    ),
    BottomNavItem(
        screen = Screen.Settings,
        routeClass = Screen.Settings::class,
        iconSelected = Icons.Filled.Settings,
        iconUnselected = Icons.Outlined.Settings
    )
)

@Composable
fun PurevonBottomNav(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(PurevonBackground)
    ) {
        HorizontalDivider(thickness = 1.dp, color = PurevonBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentDestination?.hasRoute(item.routeClass) == true
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) PurevonPrimary else PurevonBackground)
                        .clickable {
                            navController.navigate(item.screen) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected) item.iconSelected else item.iconUnselected,
                        contentDescription = null,
                        tint = if (selected) PurevonOnPrimary else PurevonTextTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
