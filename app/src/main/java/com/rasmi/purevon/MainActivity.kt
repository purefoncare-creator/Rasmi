package com.rasmi.purevon

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.rasmi.purevon.data.preferences.SettingsDataStore
import androidx.lifecycle.lifecycleScope
import androidx.core.os.LocaleListCompat
import androidx.core.os.ConfigurationCompat
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.rasmi.purevon.presentation.main.MainViewModel
import com.rasmi.purevon.presentation.navigation.PurevonNavHost
import com.rasmi.purevon.presentation.navigation.Screen
import com.rasmi.purevon.presentation.screen.permissions.DataDisclosureScreen
import com.rasmi.purevon.presentation.screen.permissions.PermissionRequestScreen
import com.rasmi.purevon.presentation.screen.permissions.SystemSpecificPermissionsScreen
import com.rasmi.purevon.presentation.theme.PurevonTheme
import com.rasmi.purevon.util.DefaultAppManager
import com.rasmi.purevon.util.DisclosurePreferences
import com.rasmi.purevon.util.PermissionManager
import com.rasmi.purevon.util.telecom.PhoneAccountManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main Activity - Entry point of the app
 * Uses Jetpack Compose for UI and Hilt for Dependency Injection
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    @Inject
    lateinit var defaultAppManager: DefaultAppManager
    
    @Inject
    lateinit var phoneAccountManager: PhoneAccountManager
    
    @Inject
    lateinit var settingsDataStore: SettingsDataStore
    
    @Inject
    lateinit var inCallServiceBridge: com.rasmi.purevon.domain.call.InCallServiceBridge
    
    private val mainViewModel: MainViewModel by viewModels()
    
    private var needsRefresh by mutableStateOf(false)
    
    private val roleDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "Dialer role result: ${result.resultCode}")
        
        // Register PhoneAccount after becoming default dialer
        if (defaultAppManager.isDefaultDialer()) {
            registerPhoneAccountIfNeeded()
        }
        
        needsRefresh = true
    }
    
    private val roleSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "SMS role result: ${result.resultCode}")
        needsRefresh = true
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen
        installSplashScreen()
        
        super.onCreate(savedInstanceState)

        // Apply persisted language preference on start
        lifecycleScope.launch {
            val savedLanguage = settingsDataStore.appLanguage.first()
            val localeList = if (savedLanguage == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(savedLanguage)
            }
            val currentLocales = AppCompatDelegate.getApplicationLocales()
            if (currentLocales != localeList) {
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Check for active call and redirect
        if (inCallServiceBridge.getCurrentCall() != null) {
            val intent = Intent(this, com.rasmi.purevon.presentation.screen.incall.InCallActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
            return
        }
        
        // Register PhoneAccount if app is already default dialer
        if (defaultAppManager.isDefaultDialer()) {
            registerPhoneAccountIfNeeded()
        }
        
        setContent {
            // Read theme settings from DataStore
            val isDarkMode by settingsDataStore.isDarkMode.collectAsState(initial = false)
            val autoTheme by settingsDataStore.autoTheme.collectAsState(initial = true)
            val appLanguage by settingsDataStore.appLanguage.collectAsState(initial = "system")
            
            // RTL layout direction is determined dynamically by the active language preference or system default
            val systemLocale = androidx.core.os.ConfigurationCompat.getLocales(androidx.compose.ui.platform.LocalConfiguration.current).get(0)
            val activeLanguage = if (appLanguage == "system") (systemLocale?.language ?: "en") else appLanguage
            val isRtl = activeLanguage == "ar" || activeLanguage == "fa" || activeLanguage == "ur" || activeLanguage == "he"
            
            // Determine dark theme based on settings
            val useDarkTheme = if (autoTheme) {
                androidx.compose.foundation.isSystemInDarkTheme()
            } else {
                isDarkMode
            }
            
            PurevonTheme(darkTheme = useDarkTheme) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl
                        else androidx.compose.ui.unit.LayoutDirection.Ltr
                ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showDisclosureScreen by remember {
                        mutableStateOf(!DisclosurePreferences.isAccepted(this@MainActivity))
                    }
                    var showPermissionScreen by remember { mutableStateOf(checkNeedsPermissions()) }
                    var showSystemSpecificPermissions by remember { mutableStateOf(false) }
                    var startDestination: Any by remember { mutableStateOf(getStartDestination()) }
                    val navController = rememberNavController()
                    
                    // Check if default apps status changed when activity resumes
                    DisposableEffect(Unit) {
                        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                // Notify ViewModel to check sync again (e.g. after permissions granted)
                                mainViewModel.onResume()

                                // Check if we lost default app status
                                val needsPermissions = checkNeedsPermissions()
                                if (needsPermissions && !showPermissionScreen) {
                                    // Lost default app status, show permission screen again
                                    Log.d("MainActivity", "Lost default app status, showing permission screen")
                                    showPermissionScreen = true
                                }
                            }
                        }
                        lifecycle.addObserver(lifecycleObserver)
                        onDispose {
                            lifecycle.removeObserver(lifecycleObserver)
                        }
                    }
                    
                    // Handle navigation from intents (notifications, etc.)
                    LaunchedEffect(needsRefresh) {
                        if (needsRefresh) {
                            // ✅ FIX: Capture and clear intent extras immediately to prevent
                            // re-consumption on config change (race condition)
                            val capturedDestination = getStartDestination()
                            val capturedNavigateTo = intent?.getStringExtra("navigate_to")
                            intent?.replaceExtras(android.os.Bundle.EMPTY)
                            intent?.data = null
                            
                            showPermissionScreen = checkNeedsPermissions()
                            if (!showPermissionScreen) {
                                Log.d("MainActivity", "Navigating to: $capturedDestination, navigate_to: $capturedNavigateTo (from needsRefresh)")
                                
                                // Only navigate if destination is not the default Dialer
                                if (capturedDestination !is Screen.Dialer) {
                                    when {
                                        // From notification to conversation: Messages → Conversation
                                        capturedDestination is Screen.Conversation -> {
                                            Log.d("MainActivity", "Navigation: Messages → Conversation")
                                            navController.navigate(Screen.Messages) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                            navController.navigate(capturedDestination) {
                                                launchSingleTop = true
                                            }
                                        }
                                        // From notification to history (missed call, spam)
                                        capturedNavigateTo == "history" -> {
                                            Log.d("MainActivity", "Navigation: History (from notification)")
                                            navController.navigate(Screen.CallHistory) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        // From in-call screen to contacts tab
                                        capturedNavigateTo == "contacts" -> {
                                            Log.d("MainActivity", "Navigation: Contacts (from in-call)")
                                            navController.navigate(Screen.Contacts) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        // Other destinations
                                        else -> {
                                            Log.d("MainActivity", "Navigation: $capturedDestination (normal)")
                                            navController.navigate(capturedDestination) {
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                            }
                            needsRefresh = false

                        }
                    }
                    
                    when {
                        showDisclosureScreen -> {
                            DataDisclosureScreen(
                                onAccepted = {
                                    showDisclosureScreen = false
                                    showPermissionScreen = checkNeedsPermissions()
                                    (application as PurevonApp).onPermissionsGranted()
                                }
                            )
                        }
                        showPermissionScreen -> {
                            PermissionRequestScreen(
                                onPermissionsGranted = {
                                    (application as PurevonApp).onPermissionsGranted()
                                    showPermissionScreen = false
                                    showSystemSpecificPermissions = true
                                },
                                onRequestDefaultDialer = {
                                    requestDefaultDialerRole()
                                },
                                onRequestDefaultSms = {
                                    requestDefaultSmsRole()
                                }
                            )
                        }
                        showSystemSpecificPermissions -> {
                            SystemSpecificPermissionsScreen(
                                onComplete = {
                                    showSystemSpecificPermissions = false
                                }
                            )
                        }
                        else -> {
                            PurevonNavHost(
                                navController = navController,
                                startDestination = startDestination
                            )
                        }
                    }
                }
                } // CompositionLocalProvider
            }
        }
    }
    
    /**
     * Register PhoneAccount with TelecomManager
     * This is required for the app to handle calls
     */
    private fun registerPhoneAccountIfNeeded() {
        try {
            if (!phoneAccountManager.isPhoneAccountRegistered()) {
                val registered = phoneAccountManager.registerPhoneAccount()
                Log.d("MainActivity", "PhoneAccount registration: $registered")
                
                if (registered) {
                    phoneAccountManager.enablePhoneAccount(true)
                    Log.d("MainActivity", "PhoneAccount enabled successfully")
                }
            } else {
                Log.d("MainActivity", "PhoneAccount already registered")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error registering PhoneAccount", e)
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val navigateTo = intent.getStringExtra("navigate_to")
        val threadId = intent.getLongExtra("thread_id", -1L)
        val clearInput = intent.getBooleanExtra("clear_input", false) // ✅ قراءة clear_input flag
        
        Log.d("MainActivity", "onNewIntent: action=${intent.action}, navigate_to=$navigateTo, thread_id=$threadId, clear_input=$clearInput")
        setIntent(intent)
        
        // ✅ تعيين shouldClearDialerInput إذا كان clearInput مفعلاً
        if (clearInput && navigateTo == "dialer") {
            mainViewModel.setShouldClearDialerInput(true)
        }
        
        // Handle navigation from notifications or intents
        when (navigateTo) {
            "conversation" -> {
                if (threadId > 0) {
                    Log.d("MainActivity", "Navigating to conversation from notification: $threadId")
                    needsRefresh = true
                }
            }
            "incall" -> {
                Log.d("MainActivity", "Received InCall intent, updating state")
                needsRefresh = true
            }
            "new_conversation", "add_contact", "history", "messages", "contacts" -> {
                Log.d("MainActivity", "Received $navigateTo intent, updating state")
                needsRefresh = true
            }
        }
        
        // Handle notification with thread_id (even without navigate_to)
        if (threadId > 0 && navigateTo == null) {
            Log.d("MainActivity", "Navigating to conversation from notification (no navigate_to): $threadId")
            needsRefresh = true
        }
    }
    
    private fun getStartDestination(): Any {
        val navigateTo = intent?.getStringExtra("navigate_to")
        val threadId = intent?.getLongExtra("thread_id", -1L) ?: -1L
        val phoneNumber = intent?.getStringExtra("phone_number")
        
        // Check for tel: or sms: scheme from external apps
        val intentData = intent?.data
        val intentAction = intent?.action
        val schemePhoneNumber = extractPhoneNumberFromUri(intentData)
        
        Log.d("MainActivity", "getStartDestination: action=$intentAction, data=$intentData, navigate_to=$navigateTo, phone_number=$phoneNumber, schemePhoneNumber=$schemePhoneNumber, thread_id=$threadId")
        
        // Handle notification with thread_id (even without navigate_to)
        if (threadId > 0 && navigateTo == null) {
            Log.d("MainActivity", "Opening conversation from notification, threadId=$threadId")
            return Screen.Conversation(conversationId = threadId)
        }
        
        // Handle vCard intent
        if (intentAction == Intent.ACTION_VIEW && intent?.type?.contains("vcard", ignoreCase = true) == true) {
             val vCardUri = intentData
             if (vCardUri != null) {
                 val (name, phone, email) = parseVCard(vCardUri)
                 Log.d("MainActivity", "Parsed vCard: name=${com.rasmi.purevon.util.DebugLogger.maskName(name)}, phone=${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(phone ?: "")}")
                 return Screen.AddContact(phoneNumber = phone, name = name, email = email)
             }
        }
        
        // Handle tel: scheme for dialing
        if (intentAction == Intent.ACTION_DIAL || intentAction == Intent.ACTION_VIEW || intentAction == Intent.ACTION_CALL) {
            if (intentData?.scheme == "tel" && schemePhoneNumber != null) {
                Log.d("MainActivity", "Starting with Dialer screen from tel: scheme, phoneNumber=${com.rasmi.purevon.util.DebugLogger.maskPhoneNumber(schemePhoneNumber)}")
                // Store the phone number to pass to the dialer
                mainViewModel.setDialerPhoneNumber(schemePhoneNumber)
                return Screen.Dialer
            }
        }
        
        // Handle sms/smsto: scheme for messaging
        if (intentAction == Intent.ACTION_VIEW || intentAction == Intent.ACTION_SENDTO) {
            if (intentData?.scheme in listOf("sms", "smsto", "mms", "mmsto") && schemePhoneNumber != null) {
                Log.d("MainActivity", "Starting with NewConversation screen from sms: scheme, phoneNumber=$schemePhoneNumber")
                return Screen.NewConversation(phoneNumber = schemePhoneNumber)
            }
        }
        
        return when (navigateTo) {
            "incall" -> {
                Log.d("MainActivity", "Starting with InCall screen")
                Screen.InCall
            }
            "conversation" -> {
                if (threadId > 0) {
                    Log.d("MainActivity", "Starting with Conversation screen, threadId=$threadId")
                    Screen.Conversation(conversationId = threadId)
                } else {
                    Screen.Messages
                }
            }
            "new_conversation" -> {
                if (phoneNumber != null) {
                    Log.d("MainActivity", "Starting with NewConversation screen, phoneNumber=$phoneNumber")
                    Screen.NewConversation(phoneNumber = phoneNumber)
                } else {
                    Log.d("MainActivity", "Starting with NewConversation screen")
                    Screen.NewConversation()
                }
            }
            "add_contact" -> {
                if (phoneNumber != null) {
                    Log.d("MainActivity", "Starting with AddContact screen, phoneNumber=$phoneNumber")
                    Screen.AddContact(phoneNumber = phoneNumber)
                } else {
                    Log.d("MainActivity", "Starting with AddContact screen")
                    Screen.AddContact()
                }
            }
            "history" -> {
                Log.d("MainActivity", "Starting with History screen")
                Screen.CallHistory
            }
            "messages" -> {
                Log.d("MainActivity", "Starting with Messages screen")
                Screen.Messages
            }
            "contacts" -> {
                Log.d("MainActivity", "Starting with Contacts screen")
                Screen.Contacts
            }
            else -> Screen.Dialer
        }
    }
    
    /**
     * Extract phone number from tel: or sms: URI
     */
    private fun extractPhoneNumberFromUri(uri: android.net.Uri?): String? {
        if (uri == null) return null
        
        return when (uri.scheme) {
            "tel", "sms", "smsto", "mms", "mmsto" -> {
                // Get the scheme-specific part (the phone number)
                uri.schemeSpecificPart?.replace(Regex("[^0-9+*#]"), "")?.takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }
    
    // ✅ Dialer state is now managed by MainViewModel (no tight coupling)
    
    private fun requestDefaultDialerRole() {
        Log.d("MainActivity", "requestDefaultDialerRole() called")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use RoleManager for Android 10+
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            val isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            Log.d("MainActivity", "Is ROLE_DIALER held: $isRoleHeld")
            
            if (!isRoleHeld) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                Log.d("MainActivity", "Launching role request for DIALER")
                try {
                    roleDialerLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to request ROLE_DIALER", e)
                }
            } else {
                Log.d("MainActivity", "Already holding ROLE_DIALER")
            }
        } else {
            // Fallback for older Android versions
            requestDefaultDialerLegacy()
        }
    }
    
    private fun requestDefaultSmsRole() {
        Log.d("MainActivity", "requestDefaultSmsRole() called")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use RoleManager for Android 10+
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            val isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            Log.d("MainActivity", "Is ROLE_SMS held: $isRoleHeld")
            
            if (!isRoleHeld) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                Log.d("MainActivity", "Launching role request for SMS")
                try {
                    roleSmsLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to request ROLE_SMS", e)
                }
            } else {
                Log.d("MainActivity", "Already holding ROLE_SMS")
            }
        } else {
            // Fallback for older Android versions
            requestDefaultSmsLegacy()
        }
    }
    
    private fun requestDefaultDialerLegacy() {
        val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager
        val currentDefault = telecomManager.defaultDialerPackage
        
        if (packageName != currentDefault) {
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            }
            try {
                roleDialerLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to launch dialer intent", e)
            }
        }
    }
    
    private fun requestDefaultSmsLegacy() {
        val currentDefault = Telephony.Sms.getDefaultSmsPackage(this)
        
        if (packageName != currentDefault) {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            }
            try {
                roleSmsLauncher.launch(intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to launch SMS intent", e)
            }
        }
    }
    
    private fun checkNeedsPermissions(): Boolean {
        // Check if critical permissions are granted
        val hasPhonePermissions = PermissionManager.hasPhonePermissions(this)
        val hasContactsPermissions = PermissionManager.hasContactsPermissions(this)
        val hasSmsPermissions = PermissionManager.hasSmsPermissions(this)
        val isDefaultApps = defaultAppManager.areBothDefaultAppsSet()
        
        return !(hasPhonePermissions && hasContactsPermissions && hasSmsPermissions && isDefaultApps)
    }
    
    fun requestDefaultApps() {
        defaultAppManager.requestDefaultApps(this, 1000)
    }
    
    /**
     * Parse a vCard (.vcf) file from a URI.
     * ✅ Fix QQ: Improved regex patterns to handle:
     *   - FN/N/TEL/EMAIL with parameters (e.g. TEL;TYPE=WORK;VALUE=uri:tel:+1234)
     *   - Group prefixes (e.g. item1.TEL:+1234)
     *   - Quoted-printable line continuations (=\n)
     *   - Multiple phone numbers (returns first)
     */
    private fun parseVCard(uri: android.net.Uri): Triple<String?, String?, String?> {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val rawContent = inputStream.bufferedReader().readText()
                // Unfold line continuations (RFC 6350: CRLF + space/tab = continuation)
                val content = rawContent
                    .replace("\r\n ", "")
                    .replace("\r\n\t", "")
                    .replace("=\r\n", "") // Quoted-printable soft line break
                    .replace("=\n", "")   // Quoted-printable soft line break (LF only)

                // Parse Name: FN property (handles parameters like FN;CHARSET=UTF-8:Name)
                var name = Regex("(?:^|\\n)(?:\\w+\\.)?FN[^:]*:(.+)", RegexOption.IGNORE_CASE)
                    .find(content)?.groupValues?.get(1)?.trim()
                if (name.isNullOrBlank()) {
                    // Try N property: N:Family;Given;Middle;Prefix;Suffix
                    val nMatch = Regex("(?:^|\\n)(?:\\w+\\.)?N[^:]*:(.+)", RegexOption.IGNORE_CASE)
                        .find(content)?.groupValues?.get(1)
                    if (nMatch != null) {
                        val nParts = nMatch.split(";")
                        if (nParts.size >= 2) {
                            val family = nParts[0].trim()
                            val given = nParts[1].trim()
                            name = if (given.isNotEmpty()) "$given $family".trim() else family
                        }
                    }
                }
                
                // Parse Phone: TEL property (handles TYPE=, VALUE=uri:tel:, etc.)
                val telMatch = Regex("(?:^|\\n)(?:\\w+\\.)?TEL[^:]*:(.+)", RegexOption.IGNORE_CASE)
                    .find(content)?.groupValues?.get(1)?.trim()
                var phone = telMatch
                    ?.removePrefix("tel:")  // URI format: tel:+1234
                    ?.replace(Regex("[\\s\\-()]"), "") // Strip whitespace, dashes, parens
                
                // Parse Email
                val email = Regex("(?:^|\\n)(?:\\w+\\.)?EMAIL[^:]*:(.+)", RegexOption.IGNORE_CASE)
                    .find(content)?.groupValues?.get(1)?.trim()
                
                return Triple(name, phone, email)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error parsing vCard", e)
        }
        return Triple(null, null, null)
    }
}
