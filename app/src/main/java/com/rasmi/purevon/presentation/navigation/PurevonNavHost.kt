package com.rasmi.purevon.presentation.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.rasmi.purevon.presentation.screen.dialer.DialerScreen
import com.rasmi.purevon.presentation.screen.contacts.ContactsScreen
import com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailScreen
import com.rasmi.purevon.presentation.screen.addcontact.AddContactScreen
import com.rasmi.purevon.presentation.screen.history.HistoryScreen
import com.rasmi.purevon.presentation.screen.messages.MessagesScreen
import com.rasmi.purevon.presentation.screen.conversation.ConversationScreen
import com.rasmi.purevon.presentation.screen.settings.SettingsScreen
import com.rasmi.purevon.presentation.screen.settings.AboutScreen
import com.rasmi.purevon.presentation.screen.incall.InCallScreen
import com.rasmi.purevon.presentation.screen.statistics.StatisticsScreen
import com.rasmi.purevon.domain.model.Contact
import com.rasmi.purevon.R
import androidx.annotation.StringRes
import kotlin.reflect.KClass

/**
 * Bottom navigation item data class
 */
data class BottomNavItem(
    val screen: Screen,
    val routeClass: KClass<out Screen>,
    @StringRes val labelRes: Int,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector
)

/**
 * Main tab route classes — used for shouldShowBottomBar check
 */
private val mainTabRoutes: Set<KClass<out Screen>> = setOf(
    Screen.Dialer::class,
    Screen.Contacts::class,
    Screen.CallHistory::class,
    Screen.Messages::class,
    Screen.Settings::class
)

/**
 * List of bottom navigation items
 */
val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Dialer,
        routeClass = Screen.Dialer::class,
        labelRes = R.string.nav_tab_dialer,
        iconSelected = Icons.Filled.Call,
        iconUnselected = Icons.Outlined.Call
    ),
    BottomNavItem(
        screen = Screen.Contacts,
        routeClass = Screen.Contacts::class,
        labelRes = R.string.nav_tab_contacts,
        iconSelected = Icons.Filled.Contacts,
        iconUnselected = Icons.Outlined.Contacts
    ),
    BottomNavItem(
        screen = Screen.CallHistory,
        routeClass = Screen.CallHistory::class,
        labelRes = R.string.nav_tab_history,
        iconSelected = Icons.Filled.History,
        iconUnselected = Icons.Outlined.History
    ),
    BottomNavItem(
        screen = Screen.Messages,
        routeClass = Screen.Messages::class,
        labelRes = R.string.nav_tab_messages,
        iconSelected = Icons.AutoMirrored.Filled.Message,
        iconUnselected = Icons.AutoMirrored.Outlined.Message
    ),
    BottomNavItem(
        screen = Screen.Settings,
        routeClass = Screen.Settings::class,
        labelRes = R.string.nav_tab_settings,
        iconSelected = Icons.Filled.Settings,
        iconUnselected = Icons.Outlined.Settings
    )
)

/**
 * Main navigation host with bottom navigation bar (iOS Tab Bar style)
 */
@Composable
fun PurevonNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Screen.Dialer
) {
    // ✅ تتبع الصفحة الحالية لإخفاء Bottom Nav في الصفحات التفصيلية
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    // ✅ تحديد ما إذا كان يجب إظهار Bottom Nav (فقط في التبويبات الرئيسية)
    val shouldShowBottomBar = currentDestination?.let { dest ->
        mainTabRoutes.any { dest.hasRoute(it) }
    } ?: true
    
    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                IOSBottomNavigation(navController = navController)
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            // Use only top padding from Scaffold; bottom padding is fixed to the
            // pill height (64dp) so nav bar insets don't create extra blank space.
            // ✅ Only apply bottom padding if the bottom bar is shown
            modifier = Modifier.padding(
                top = paddingValues.calculateTopPadding(),
                bottom = if (shouldShowBottomBar) 64.dp else 0.dp
            )
        ) {
            // Main tabs — type-safe composable<T>
            composable<Screen.Dialer> {
                // ✅ Get dialer state from MainViewModel (no tight coupling to MainActivity)
                val mainViewModel: com.rasmi.purevon.presentation.main.MainViewModel = 
                    androidx.hilt.navigation.compose.hiltViewModel(
                        viewModelStoreOwner = androidx.compose.ui.platform.LocalContext.current as androidx.activity.ComponentActivity
                    )
                val initialPhoneNumber by mainViewModel.dialerPhoneNumber.collectAsState()
                
                // Clear the phone number after reading it
                androidx.compose.runtime.LaunchedEffect(initialPhoneNumber) {
                    if (initialPhoneNumber != null) {
                        mainViewModel.clearDialerPhoneNumber()
                    }
                }
                
                // ✅ Check if we should clear the dialer input
                val shouldClearInput by mainViewModel.shouldClearDialerInput.collectAsState()
                androidx.compose.runtime.LaunchedEffect(shouldClearInput) {
                    if (shouldClearInput) {
                        // Clear the flag immediately
                        mainViewModel.clearDialerInputFlag()
                    }
                }
                
                DialerScreen(
                    initialPhoneNumber = initialPhoneNumber,
                    shouldClearInput = shouldClearInput, // ✅ تمرير الـ flag
                    onAddToContacts = { phoneNumber ->
                        navController.navigate(Screen.AddContact(phoneNumber = phoneNumber)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<Screen.Contacts> {
                ContactsScreen(
                    onContactClick = { contact ->
                        navController.navigate(Screen.ContactDetail(contactId = contact.id)) {
                            launchSingleTop = true
                        }
                    },
                    onAddContactClick = {
                        navController.navigate(Screen.AddContact()) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            
            // Contact Detail Screen — type-safe args via toRoute<>()
            composable<Screen.ContactDetail> { backStackEntry ->
                val args = backStackEntry.toRoute<Screen.ContactDetail>()
                ContactDetailScreenWrapper(
                    contactId = args.contactId,
                    navController = navController
                )
            }
            
            // Add Contact Screen — type-safe args
            composable<Screen.AddContact> { backStackEntry ->
                val args = backStackEntry.toRoute<Screen.AddContact>()
                
                AddContactScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onContactSaved = {
                        navController.previousBackStackEntry?.savedStateHandle?.set("contact_updated", true)
                        navController.popBackStack()
                    },
                    initialPhoneNumber = args.phoneNumber,
                    initialName = args.name,
                    initialEmail = args.email,
                    contactId = args.contactId.takeIf { it != -1L }
                )
            }
            composable<Screen.CallHistory> {
                HistoryScreen(
                    onNavigateToContact = { contactId ->
                        navController.navigate(Screen.ContactDetail(contactId = contactId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToNewConversation = { phoneNumber ->
                        navController.navigate(Screen.NewConversation(phoneNumber = phoneNumber)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToAddContact = { phoneNumber ->
                        navController.navigate(Screen.AddContact(phoneNumber = phoneNumber)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<Screen.Messages> { backStackEntry ->
                // ✅ FIXED: Use backStackEntry to preserve ViewModel across recompositions
                // This ensures conversationsFlow cache is retained (no reload on return!)
                val viewModel: com.rasmi.purevon.presentation.screen.messages.MessagesViewModel = 
                    androidx.hilt.navigation.compose.hiltViewModel(backStackEntry)
                
                MessagesScreen(
                    viewModel = viewModel,
                    onConversationClick = { conversationId ->
                        navController.navigate(Screen.Conversation(conversationId = conversationId)) {
                            launchSingleTop = true
                        }
                    },
                    onStarredMessageClick = { threadId, messageId ->
                        navController.navigate(Screen.Conversation(conversationId = threadId, scrollToMessageId = messageId)) {
                            launchSingleTop = true
                        }
                    },
                    onNewMessageClick = {
                        navController.navigate(Screen.NewConversation()) {
                            launchSingleTop = true
                        }
                    },
                    onScheduledMessagesClick = {
                        navController.navigate(Screen.ScheduledMessages) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable<Screen.Settings> {
                SettingsScreen(
                    onNavigateToAbout = {
                        navController.navigate(Screen.AboutScreen) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            
            // About screen
            composable<Screen.AboutScreen> {
                AboutScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Statistics screen
            composable<Screen.Statistics> {
                StatisticsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            // New Conversation screen — type-safe args
            composable<Screen.NewConversation> { backStackEntry ->
                val args = backStackEntry.toRoute<Screen.NewConversation>()
                ConversationScreen(
                    conversationId = null, // ✅ محادثة جديدة
                    initialPhoneNumber = args.phoneNumber,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            // Conversation detail screen — type-safe args
            composable<Screen.Conversation> { backStackEntry ->
                val args = backStackEntry.toRoute<Screen.Conversation>()
                ConversationScreen(
                    conversationId = args.conversationId,
                    scrollToMessageId = args.scrollToMessageId.takeIf { it != -1L },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            
            // InCall screen
            composable<Screen.InCall> {
                InCallScreen()
            }

            // ✅ Fix #5: Scheduled Messages screen
            composable<Screen.ScheduledMessages> {
                com.rasmi.purevon.presentation.screen.scheduled.ScheduledMessagesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Wrapper for ContactDetailScreen to load contact data
 */
@Composable
private fun ContactDetailScreenWrapper(
    contactId: Long,
    navController: NavHostController
) {
    val viewModel: com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailViewModel = 
        androidx.hilt.navigation.compose.hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // ✅ تحديث البيانات عند العودة من شاشة التعديل
    val contactUpdated = navController.currentBackStackEntry
        ?.savedStateHandle
        ?.getStateFlow("contact_updated", false)
        ?.collectAsState()
    androidx.compose.runtime.LaunchedEffect(contactUpdated?.value) {
        if (contactUpdated?.value == true) {
            viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.RefreshContact)
            navController.currentBackStackEntry?.savedStateHandle?.set("contact_updated", false)
        }
    }
    
    // Handle deletion - navigate back
    androidx.compose.runtime.LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            navController.popBackStack()
        }
    }
    
    // Show success message
    androidx.compose.runtime.LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.DismissSuccessMessage)
        }
    }
    
    // Show error message
    androidx.compose.runtime.LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = androidx.compose.material3.SnackbarDuration.Short
            )
            viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.DismissError)
        }
    }
    
    // Permission launcher for CALL_PHONE
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var pendingCallSubId by remember { mutableStateOf<Int?>(null) }       // ✅ subscriptionId المعلق
    var showSimPickerDialog by remember { mutableStateOf(false) }          // ✅ حوار ASK
    var simPickerSims by remember { mutableStateOf<List<com.rasmi.purevon.util.sim.SimInfo>>(emptyList()) }
    var simPickerNumber by remember { mutableStateOf("") }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingCallNumber?.let { com.rasmi.purevon.util.PhoneUtil.makeCall(context, it, pendingCallSubId) }
        }
        pendingCallNumber = null
        pendingCallSubId = null
    }
    
    // ✅ معالجة نتيجة تحديد الشريحة
    androidx.compose.runtime.LaunchedEffect(uiState.simCallAction) {
        when (val action = uiState.simCallAction) {
            is com.rasmi.purevon.util.sim.SimCallAction.MakeCall -> {
                viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.ClearSimCallAction)
                if (com.rasmi.purevon.util.PhoneUtil.hasCallPermission(context)) {
                    com.rasmi.purevon.util.PhoneUtil.makeCall(context, action.phoneNumber, action.subscriptionId)
                } else {
                    pendingCallNumber = action.phoneNumber
                    pendingCallSubId = action.subscriptionId
                    callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                }
            }
            is com.rasmi.purevon.util.sim.SimCallAction.ShowSimPicker -> {
                viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.ClearSimCallAction)
                simPickerNumber = action.phoneNumber
                simPickerSims = action.availableSims
                showSimPickerDialog = true
            }
            null -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            
            uiState.contact != null -> {
                ContactDetailScreen(
                    contact = uiState.contact!!,
                    notes = uiState.notes,
                    callStatistics = uiState.callStatistics,
                    recentCalls = uiState.recentCalls,
                    showAllCalls = uiState.showAllCalls,
                    onToggleShowAllCalls = {
                        viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.ToggleShowAllCalls)
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onDeleteContact = {
                        viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.DeleteContact)
                    },
                    onToggleFavorite = {
                        viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.ToggleFavorite)
                    },
                    onBlockContact = {
                        viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.ToggleBlock)
                    },
                    onEditContact = {
                        navController.navigate(
                            Screen.AddContact(contactId = contactId)
                        ) { launchSingleTop = true }
                    },
                    onSaveContact = { firstName, lastName, phoneNumber, email, company ->
                        viewModel.onEvent(
                            com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.SaveContact(
                                firstName, lastName, phoneNumber, email, company
                            )
                        )
                    },
                    onDeleteNote = { noteId ->
                        viewModel.onEvent(com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.DeleteNote(noteId))
                    },
                    onCall = { phoneNumber ->
                        // ✅ مرر عبر ال ViewModel لتحديد الشريحة (يدعم SIM1/SIM2/ASK)
                        viewModel.onEvent(
                            com.rasmi.purevon.presentation.screen.contactdetail.ContactDetailUiEvent.PrepareCall(phoneNumber)
                        )
                    },
                    onMessage = { phoneNumber ->
                        // Navigate to new conversation with this phone number
                        navController.navigate(Screen.NewConversation(phoneNumber = phoneNumber)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            
            else -> {
                // Contact not found
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = uiState.error ?: "Contact not found",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        // Snackbar Host
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
    
    // ✅ حوار اختيار الشريحة (وضع ASK)
    if (showSimPickerDialog) {
        com.rasmi.purevon.presentation.component.SimSelectorDialog(
            availableSims = simPickerSims,
            selectedSimId = null,
            title = stringResource(com.rasmi.purevon.R.string.sim_picker_call_title),
            onSimSelected = { subscriptionId ->
                showSimPickerDialog = false
                if (com.rasmi.purevon.util.PhoneUtil.hasCallPermission(context)) {
                    com.rasmi.purevon.util.PhoneUtil.makeCall(context, simPickerNumber, subscriptionId)
                } else {
                    pendingCallNumber = simPickerNumber
                    pendingCallSubId = subscriptionId
                    callPermissionLauncher.launch(android.Manifest.permission.CALL_PHONE)
                }
            },
            onDismiss = { showSimPickerDialog = false }
        )
    }
}

/**
 * Compact modern bottom navigation bar with floating icons effect.
 * Wrapped in a Box with navigationBarsPadding() so the floating pill always sits
 * above the system navigation bar whether using gestures or 3-button nav.
 */
@Composable
fun IOSBottomNavigation(
    navController: NavHostController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isDark = isSystemInDarkTheme()
    val barColor = if (isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    }
    val shadowColor = if (isDark) {
        MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        NavigationBar(
            containerColor = barColor,
            contentColor = MaterialTheme.colorScheme.primary,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 18.dp, vertical = 9.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(36.dp),
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
                .clip(RoundedCornerShape(36.dp))
                .background(barColor)
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentDestination?.hasRoute(item.routeClass) == true

                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selected) item.iconSelected else item.iconUnselected,
                            contentDescription = stringResource(item.labelRes)
                        )
                    },
                    label = null,
                    selected = selected,
                    onClick = {
                        navController.navigate(item.screen) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    alwaysShowLabel = false
                )
            }
        }
    } // end Box
}
