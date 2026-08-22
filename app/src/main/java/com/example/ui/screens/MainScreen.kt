package com.example.ui.screens

import com.example.model.AccountEntity
import com.example.model.RangeItem
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.MainViewModel

// Flat High-Contrast Color Palette (NO GLOW, NO HEAVY ANIMATIONS)
private val DarkCanvas = Color(0xFF121218)
private val CardBackground = Color(0xFF1E1E28)
private val PrimaryFbBlue = Color(0xFF1877F2)
private val AccentGreen = Color(0xFF00C853)
private val DangerRed = Color(0xFFD50000)
private val BorderColor = Color(0xFF2C2C3A)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA0A0B0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var passwordInput by remember { mutableStateOf(uiState.savedPassword) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBotSetupDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    LaunchedEffect(uiState.savedPassword) {
        passwordInput = uiState.savedPassword
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "FB CREATOR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBackground
                ),
                actions = {
                    Button(
                        onClick = { showBotSetupDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = if (uiState.telegramChatId.isNotBlank()) AccentGreen else PrimaryFbBlue),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("bot_setup_button")
                    ) {
                        Text("🤖 BOT SETUP", fontSize = 11.sp, color = TextPrimary)
                    }
                    Button(
                        onClick = { showHistoryDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("open_history_button")
                    ) {
                        Text("HISTORY (${uiState.accountsHistory.size})", fontSize = 11.sp, color = TextPrimary)
                    }
                }
            )
        },
        containerColor = DarkCanvas
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. OVERLAY PERMISSION CARD (IF NEEDED)
            if (!uiState.isOverlayPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                item {
                    PermissionCard(
                        onGrantClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // 1.5 LIVE STATUS BANNER CARD
            item {
                LiveStatusCard(
                    statusMessage = uiState.statusMessage,
                    isCreating = uiState.isCreatingAccount,
                    onDismiss = { viewModel.clearStatusMessage() }
                )
            }

            // ============================================
            // FACEBOOK AUTOMATION FLOW
            // ============================================
            // 2. START & STOP SERVICE CONTROL CARD
            item {
                ServiceControlCard(
                    isRunning = uiState.isServiceRunning,
                    onToggleClick = { viewModel.toggleService(context) },
                    onCreateNowClick = { viewModel.createAccountNow(context) },
                    isCreating = uiState.isCreatingAccount
                )
            }

            // 3. ACCOUNT CREATION SETUP CARD (LANGUAGE, GENDER, AGE)
            item {
                AccountSetupCard(
                    currentLanguage = uiState.nameLanguage,
                    currentGender = uiState.genderConfig,
                    currentAge = uiState.ageFilter,
                    onLanguageChange = { viewModel.setNameLanguage(it) },
                    onGenderChange = { viewModel.setGenderConfig(it) },
                    onAgeChange = { viewModel.setAgeFilter(it) }
                )
            }

            // 4. TELEGRAM BOT SETUP CARD
            item {
                BotSetupCard(
                    chatId = uiState.telegramChatId,
                    username = uiState.telegramUsername,
                    onConfigureClick = { showBotSetupDialog = true }
                )
            }

            // 5. FIXED SYSTEM PASSWORD CARD (LOCKED)
            item {
                FixedPasswordCard(
                    fixedPassword = com.example.api.NetworkClient.FIXED_PASSWORD,
                    onCopyPassword = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("PASSWORD", com.example.api.NetworkClient.FIXED_PASSWORD)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied Password: ${com.example.api.NetworkClient.FIXED_PASSWORD}", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // 6. LIVE FACEBOOK RANGE SELECTOR CARD
            item {
                FacebookRangeCard(
                    ranges = uiState.facebookRanges,
                    rangeMessages = uiState.rangeMessages,
                    selectedRange = uiState.selectedRange,
                    isLoading = uiState.isLoadingRanges,
                    onRefreshClick = { viewModel.refreshFacebookRanges() },
                    onSelectRange = { viewModel.selectRange(it) }
                )
            }

            // 5. QUICK HISTORY PREVIEW
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT ACCOUNTS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Show All",
                        fontSize = 12.sp,
                        color = PrimaryFbBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { showHistoryDialog = true }
                            .padding(4.dp)
                    )
                }
            }

            if (uiState.accountsHistory.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(
                            text = "No accounts created yet. Tap Start or press floating button.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(uiState.accountsHistory.take(5), key = { it.id }) { account ->
                    AccountItemRow(
                        account = account,
                        onCopyNumber = { copyTextToClipboard(context, "NUMBER", account.phone) },
                        onCopyCookie = {
                            val cleanCookie = com.example.api.NetworkClient.formatCleanCookie(account.cookie, account.uid)
                            copyTextToClipboard(context, "COOKIE", cleanCookie)
                        },
                        onCopyUid = { copyTextToClipboard(context, "UID", account.uid) },
                        onDelete = { viewModel.deleteAccount(account.id) }
                    )
                }
            }
        }
    }

    if (showHistoryDialog) {
        HistoryDialogView(
            historyList = uiState.accountsHistory,
            onDismiss = { showHistoryDialog = false },
            onRefreshOtp = { viewModel.checkOtpsNow() },
            onCopyNumber = { copyTextToClipboard(context, "NUMBER", it) },
            onCopyCookie = { raw ->
                val matched = uiState.accountsHistory.find { it.cookie == raw || it.phone == raw || it.uid == raw }
                val clean = com.example.api.NetworkClient.formatCleanCookie(raw, matched?.uid ?: "")
                copyTextToClipboard(context, "COOKIE", clean)
            },
            onCopyUid = { copyTextToClipboard(context, "UID", it) },
            onDelete = { viewModel.deleteAccount(it) },
            onClearAll = { viewModel.clearHistory() }
        )
    }

    if (showBotSetupDialog) {
        BotSetupDialog(
            currentChatId = uiState.telegramChatId,
            currentUsername = uiState.telegramUsername,
            onDismiss = { showBotSetupDialog = false },
            onSave = { newChatId, newUsername ->
                viewModel.setTelegramBotConfig(newChatId, newUsername)
                showBotSetupDialog = false
                Toast.makeText(context, "Telegram Bot Config Saved!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun PermissionCard(onGrantClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF332000)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFFFB74D))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Display Overlay Permission Required",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB74D),
                    fontSize = 14.sp
                )
            }
            Text(
                text = "To enable the circular floating button on screen, please grant overlay permission.",
                color = Color.White,
                fontSize = 12.sp
            )
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().testTag("grant_permission_button")
            ) {
                Text("GRANT PERMISSION IN SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}



@Composable
private fun ServiceControlCard(
    isRunning: Boolean,
    onToggleClick: () -> Unit,
    onCreateNowClick: () -> Unit,
    isCreating: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isRunning) AccentGreen else BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FLOATING OVERLAY SERVICE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isRunning) "STATUS: ACTIVE (Floating button on screen)" else "STATUS: STOPPED",
                        fontSize = 12.sp,
                        color = if (isRunning) AccentGreen else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // START / STOP TOGGLE BUTTON
            Button(
                onClick = onToggleClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) DangerRed else AccentGreen
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("start_stop_button")
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "STOP OVERLAY SERVICE" else "START OVERLAY SERVICE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            // MANUAL CREATE BUTTON
            Button(
                onClick = onCreateNowClick,
                enabled = !isCreating,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("create_now_button")
            ) {
                Text(
                    text = if (isCreating) "CREATING ACCOUNT..." else "CREATE 1 ACCOUNT NOW",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun FixedPasswordCard(
    fixedPassword: String,
    onCopyPassword: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SYSTEM PASSWORD (LOCKED 🔒)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                }

                Button(
                    onClick = onCopyPassword,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("COPY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = DarkCanvas,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fixedPassword,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4DEAEA)
                    )
                    Text(
                        text = "ENCODED IN GraphQL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }

            Text(
                text = "ℹ️ পাসওয়ার্ড পরিবর্তন করার কোনো প্রয়োজন নেই। GraphQL CAA Payload-এর ভিতর পাসওয়ার্ড ইঙ্কোড সেট করা আছে। সকল একাউন্টের পাসওয়ার্ড: $fixedPassword",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun FacebookRangeCard(
    ranges: List<RangeItem>,
    rangeMessages: Map<String, String>,
    selectedRange: String,
    isLoading: Boolean,
    onRefreshClick: () -> Unit,
    onSelectRange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FACEBOOK RANGES (LIVE)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )

                IconButton(
                    onClick = onRefreshClick,
                    enabled = !isLoading,
                    modifier = Modifier.testTag("refresh_ranges_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Ranges",
                        tint = PrimaryFbBlue
                    )
                }
            }

            if (isLoading) {
                Text("Loading live ranges...", color = TextSecondary, fontSize = 12.sp)
            } else if (ranges.isEmpty()) {
                Text("No ranges available. Tap refresh.", color = TextSecondary, fontSize = 12.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ranges.forEach { rangeItem ->
                        val isSelected = rangeItem.code == selectedRange
                        val liveMessage = rangeMessages[rangeItem.code]?.ifBlank { null }
                            ?: rangeItem.message.ifBlank { null }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isSelected) Color(0xFF132B4F) else DarkCanvas,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) PrimaryFbBlue else BorderColor,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectRange(rangeItem.code) }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Range: ${rangeItem.code}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) PrimaryFbBlue else TextPrimary,
                                    fontSize = 14.sp
                                )
                                if (isSelected) {
                                    Text("SELECTED", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!liveMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "💬 Message: $liveMessage",
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color(0xFF80CBC4) else Color(0xFFFFB74D),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountItemRow(
    account: AccountEntity,
    onCopyNumber: () -> Unit,
    onCopyCookie: () -> Unit,
    onCopyUid: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NUMBER: ${account.phone}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF4DEAEA)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_account_${account.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = DangerRed)
                }
            }

            val clipboardManager = LocalClipboardManager.current
            val context = androidx.compose.ui.platform.LocalContext.current

            if (!account.otp.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OTP: ${account.otp}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AccentGreen
                    )
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(account.otp))
                            Toast.makeText(context, "OTP Copied: ${account.otp}", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("COPY OTP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            } else {
                val isExpired = (System.currentTimeMillis() - account.timestamp) > (20 * 60 * 1000L)
                Text(
                    text = if (isExpired) "Expired" else "Waiting for OTP...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isExpired) DangerRed else Color(0xFFFFB74D)
                )
            }

            // EXACT 3 COPY BUTTONS REQUIREMENT: NUMBER, COOKIE, UID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onCopyNumber,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("copy_number_button_${account.id}")
                ) {
                    Text("NUMBER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onCopyCookie,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("copy_cookie_button_${account.id}")
                ) {
                    Text("COOKIE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onCopyUid,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("copy_uid_button_${account.id}")
                ) {
                    Text("UID", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun HistoryDialogView(
    historyList: List<AccountEntity>,
    onDismiss: () -> Unit,
    onRefreshOtp: () -> Unit,
    onCopyNumber: (String) -> Unit,
    onCopyCookie: (String) -> Unit,
    onCopyUid: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(550.dp),
            shape = RoundedCornerShape(12.dp),
            color = CardBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACCOUNTS HISTORY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onRefreshOtp,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("OTP REFRESH", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("CLOSE", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No accounts recorded.", color = TextSecondary, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(historyList, key = { it.id }) { item ->
                            AccountItemRow(
                                account = item,
                                onCopyNumber = { onCopyNumber(item.phone) },
                                onCopyCookie = { onCopyCookie(item.cookie) },
                                onCopyUid = { onCopyUid(item.uid) },
                                onDelete = { onDelete(item.id) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onClearAll,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLEAR ALL HISTORY", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied $label: $text", Toast.LENGTH_SHORT).show()
}

@Composable
private fun AccountSetupCard(
    currentLanguage: String,
    currentGender: String,
    currentAge: String,
    onLanguageChange: (String) -> Unit,
    onGenderChange: (String) -> Unit,
    onAgeChange: (String) -> Unit
) {
    val sampleProfile = remember(currentLanguage, currentGender, currentAge) {
        com.example.data.NameGenerator.generateProfile(currentGender, currentLanguage, currentAge)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "⚙️ ACCOUNT SETUP & NAME GENERATOR",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = TextPrimary
            )

            // 1. NAME LANGUAGE SELECTOR
            Text(
                text = "Name Language (নামের ভাষা):",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipButton(
                    text = "🇧🇩 বাংলা",
                    isSelected = currentLanguage == "BANGLA",
                    onClick = { onLanguageChange("BANGLA") },
                    modifier = Modifier.weight(1f)
                )
                FilterChipButton(
                    text = "🇫🇷 French",
                    isSelected = currentLanguage == "FRENCH",
                    onClick = { onLanguageChange("FRENCH") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipButton(
                    text = "🇬🇧 English",
                    isSelected = currentLanguage == "ENGLISH",
                    onClick = { onLanguageChange("ENGLISH") },
                    modifier = Modifier.weight(1f)
                )
                FilterChipButton(
                    text = "🇸🇦 Arabic",
                    isSelected = currentLanguage == "ARABIC",
                    onClick = { onLanguageChange("ARABIC") },
                    modifier = Modifier.weight(1f)
                )
            }

            // 2. GENDER SELECTOR
            Text(
                text = "Gender (লিঙ্গ):",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipButton(
                    text = "👧 Female (মেয়ে)",
                    isSelected = currentGender == "FEMALE",
                    onClick = { onGenderChange("FEMALE") },
                    modifier = Modifier.weight(1f)
                )
                FilterChipButton(
                    text = "👦 Male (ছেলে)",
                    isSelected = currentGender == "MALE",
                    onClick = { onGenderChange("MALE") },
                    modifier = Modifier.weight(1f)
                )
                FilterChipButton(
                    text = "🎲 Random",
                    isSelected = currentGender == "RANDOM",
                    onClick = { onGenderChange("RANDOM") },
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. AGE FILTER
            Text(
                text = "Age Range (বয়সসীমা):",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipButton(
                    text = "18+ (১৮-৩৫ বছর)",
                    isSelected = currentAge == "18+",
                    onClick = { onAgeChange("18+") },
                    modifier = Modifier.weight(1f)
                )
                FilterChipButton(
                    text = "21+ (২১-৩৫ বছর)",
                    isSelected = currentAge == "21+",
                    onClick = { onAgeChange("21+") },
                    modifier = Modifier.weight(1f)
                )
            }

            // LIVE SAMPLE PREVIEW BADGE
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1E2638),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryFbBlue.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "LIVE NAME PREVIEW:",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${sampleProfile.fullName} (${if (sampleProfile.sexCode == "1") "Female" else "Male"}, Age Birth Year: ${sampleProfile.year})",
                        fontSize = 14.sp,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) PrimaryFbBlue else CardBackground
        ),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) PrimaryFbBlue else BorderColor
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        modifier = modifier.height(38.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun BotSetupCard(
    chatId: String,
    username: String,
    onConfigureClick: () -> Unit
) {
    val isConfigured = chatId.isNotBlank()
    val statusText = if (isConfigured) {
        if (username.isNotBlank()) "Active: $username (ID: $chatId)" else "Active (ID: $chatId)"
    } else {
        "Status: Not Configured (Click to set Chat ID & Username)"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🤖 TELEGRAM BOT SETUP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = if (isConfigured) AccentGreen else TextSecondary,
                    fontWeight = if (isConfigured) FontWeight.Bold else FontWeight.Normal
                )
            }

            Button(
                onClick = onConfigureClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConfigured) AccentGreen else PrimaryFbBlue
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isConfigured) "EDIT SETUP" else "SETUP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BotSetupDialog(
    currentChatId: String,
    currentUsername: String,
    onDismiss: () -> Unit,
    onSave: (chatId: String, username: String) -> Unit
) {
    var inputChatId by remember { mutableStateOf(currentChatId) }
    var inputUsername by remember { mutableStateOf(currentUsername) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = { onSave(inputChatId, inputUsername) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("SAVE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("CANCEL", color = TextSecondary)
            }
        },
        title = {
            Text(
                text = "🤖 Telegram Bot Setup",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter your Telegram Chat ID and Telegram Username below. Received OTPs will be automatically forwarded to your personal bot and group log.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = inputChatId,
                    onValueChange = { inputChatId = it },
                    label = { Text("Telegram Chat ID") },
                    placeholder = { Text("e.g. 123456789") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryFbBlue,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = inputUsername,
                    onValueChange = { inputUsername = it },
                    label = { Text("Telegram Username") },
                    placeholder = { Text("e.g. @arafat_bhai07") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryFbBlue,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF2A2010),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "⚠️ IMPORTANT INSTRUCTIONS:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFFFD54F)
                        )
                        Text(
                            text = "1. Start the Telegram Bot first: @FB_TOOL_OTP_BOT\n2. Group Forwarding is pre-configured to Group Log ID: -1004430983810 (Phone numbers in group will be auto-masked e.g. 880193**6272)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFF8E1)
                        )
                    }
                }
            }
        },
        containerColor = CardBackground,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun LiveStatusCard(
    statusMessage: String?,
    isCreating: Boolean,
    onDismiss: () -> Unit
) {
    if (statusMessage.isNullOrBlank() && !isCreating) return

    val isSuccess = statusMessage?.contains("✅") == true || statusMessage?.contains("CREATED") == true
    val isError = statusMessage?.contains("❌") == true || statusMessage?.contains("FAILED") == true

    val borderColor = when {
        isCreating -> PrimaryFbBlue
        isSuccess -> AccentGreen
        isError -> DangerRed
        else -> PrimaryFbBlue
    }

    val bgColor = when {
        isSuccess -> Color(0xFF132F1A)
        isError -> Color(0xFF3B1A1A)
        else -> Color(0xFF1A2638)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = PrimaryFbBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isCreating) "CREATION IN PROGRESS" else "LIVE STATUS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = borderColor
                    )
                }

                if (!isCreating) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                    }
                }
            }

            Text(
                text = statusMessage ?: "Processing request...",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
