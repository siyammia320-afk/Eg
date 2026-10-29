package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AccountEntity
import com.example.model.RangeItem
import com.example.ui.viewmodel.MainViewModel

// Flat Solid Theme (NO GLOW, NO NEON, CLEAN FLAT UI)
private val DarkCanvas = Color(0xFF13141A)
private val CardBackground = Color(0xFF1C1E26)
private val PrimaryFbBlue = Color(0xFF1877F2)
private val AccentGreen = Color(0xFF2E7D32)
private val DangerRed = Color(0xFFC62828)
private val BorderColor = Color(0xFF2A2D38)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9EA0AA)
private val TerminalBg = Color(0xFF0D0E12)
private val BadgeBackground = Color(0xFF242732)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var passwordInput by remember { mutableStateOf(uiState.customPassword) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.customPassword) {
        if (passwordInput != uiState.customPassword) {
            passwordInput = uiState.customPassword
        }
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
                    Column {
                        Text(
                            text = "PREMIUM AUTO CREATE FB",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "V1 Engine • GraphQL CAA Number Checker",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBackground
                ),
                actions = {
                    Button(
                        onClick = { showHistoryDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("open_history_button")
                    ) {
                        Text(
                            text = "HISTORY (${uiState.accountsHistory.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. LIVE STATUS BANNER CARD
            item {
                LiveStatusCard(
                    statusMessage = uiState.statusMessage,
                    isCreating = uiState.isCreatingAccount,
                    onDismiss = { viewModel.clearStatusMessage() }
                )
            }

            // 2. CUSTOM PASSWORD CARD (DIRECT INPUT LIKE PYTHON)
            item {
                CustomPasswordCard(
                    passwordValue = passwordInput,
                    onPasswordChange = {
                        passwordInput = it
                        viewModel.setCustomPassword(it)
                    },
                    onCopy = {
                        copyTextToClipboard(context, "PASSWORD", passwordInput)
                    }
                )
            }

            // 3. NUMBER CHECKER CARD (GRAPHQL CAA SEARCH)
            item {
                NumberCheckerCard(
                    phoneInput = uiState.manualCheckPhone,
                    onPhoneChange = { viewModel.setManualPhoneInput(it) },
                    onCheck = { viewModel.checkManualPhoneNumber() },
                    isChecking = uiState.isCheckingManualPhone,
                    result = uiState.manualCheckResult
                )
            }

            // 4. MAIN ACTION BUTTON: CREATE 1 ACCOUNT NOW
            item {
                Button(
                    onClick = { viewModel.createAccountNow(passwordInput, context) },
                    enabled = !uiState.isCreatingAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("create_now_button")
                ) {
                    if (uiState.isCreatingAccount) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "CREATING ACCOUNT (V1)...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CREATE 1 ACCOUNT NOW (V1)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // 5. LIVE TERMINAL LOGS CARD (MIRRORS PYTHON SCRIPT OUTPUT)
            item {
                TerminalLogsCard(
                    logs = uiState.terminalLogs,
                    onClearLogs = { viewModel.clearTerminalLogs() }
                )
            }

            // 6. LIVE FACEBOOK RANGES SELECTOR CARD
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

            // 7. QUICK RECENT ACCOUNTS PREVIEW
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT ACCOUNTS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
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
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                    ) {
                        Text(
                            text = "No accounts created yet. Select range and tap Create 1 Account Now.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(14.dp)
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
}

@Composable
private fun CustomPasswordCard(
    passwordValue: String,
    onPasswordChange: (String) -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
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
                Text(
                    text = "CUSTOM PASSWORD (ENTER PASSWORD)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = TextPrimary
                )

                Button(
                    onClick = onCopy,
                    colors = ButtonDefaults.buttonColors(containerColor = BadgeBackground),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("COPY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            OutlinedTextField(
                value = passwordValue,
                onValueChange = onPasswordChange,
                placeholder = { Text("Enter password for Facebook account", color = TextSecondary, fontSize = 13.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryFbBlue,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkCanvas,
                    unfocusedContainerColor = DarkCanvas
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_password_input")
            )

            Text(
                text = "Python স্ক্রিপ্টের মতো কাস্টম পাসওয়ার্ড দিন। খালি রাখলে ডিফল্ট পাসওয়ার্ড ব্যবহার হবে।",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun NumberCheckerCard(
    phoneInput: String,
    onPhoneChange: (String) -> Unit,
    onCheck: () -> Unit,
    isChecking: Boolean,
    result: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "GRAPHQL NUMBER CHECKER (CAA SEARCH)",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = TextPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = onPhoneChange,
                    placeholder = { Text("e.g. +447404333228", color = TextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryFbBlue,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkCanvas,
                        unfocusedContainerColor = DarkCanvas
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_phone_input")
                )

                Button(
                    onClick = onCheck,
                    enabled = !isChecking && phoneInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("check_phone_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CHECK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            if (!result.isNullOrBlank()) {
                val isFree = result.startsWith("FREE")
                Surface(
                    color = if (isFree) Color(0xFF14281A) else Color(0xFF2C1414),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isFree) AccentGreen else DangerRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isFree) "✔ $result" else "✖ $result",
                        color = if (isFree) Color(0xFF81C784) else Color(0xFFE57373),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalLogsCard(
    logs: List<String>,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalBg),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TERMINAL CONSOLE (LIVE FEED)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )

                Text(
                    text = "Clear",
                    fontSize = 11.sp,
                    color = PrimaryFbBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onClearLogs() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (logs.isEmpty()) {
                Text(
                    text = "Ready. Tap 'CREATE 1 ACCOUNT NOW' to start process...",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    items(logs) { line ->
                        val logColor = when {
                            line.contains("✔") || line.contains("SUCCESS") || line.contains("NEW OTP") -> Color(0xFF81C784)
                            line.contains("✖") || line.contains("FAIL") || line.contains("FAILED") -> Color(0xFFE57373)
                            line.contains("NUMBER:") -> Color(0xFF64B5F6)
                            else -> TextSecondary
                        }

                        Text(
                            text = line,
                            color = logColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
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
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                    fontSize = 13.sp,
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
                                    color = if (isSelected) Color(0xFF1E2838) else DarkCanvas,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) PrimaryFbBlue else BorderColor,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectRange(rangeItem.code) }
                                .padding(10.dp)
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
                                    fontSize = 13.sp
                                )
                                if (isSelected) {
                                    Text("SELECTED", color = AccentGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!liveMessage.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = BadgeBackground,
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "SMS: ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = liveMessage,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "লাইভ মেসেজের জন্য অপেক্ষা করা হচ্ছে...",
                                    fontSize = 10.sp,
                                    color = TextSecondary
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
                    fontSize = 14.sp,
                    color = TextPrimary
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_account_${account.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = DangerRed)
                }
            }

            val clipboardManager = LocalClipboardManager.current
            val context = LocalContext.current

            if (!account.otp.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OTP: ${account.otp}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
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
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("COPY OTP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                val isExpired = (System.currentTimeMillis() - account.timestamp) > (20 * 60 * 1000L)
                Text(
                    text = if (isExpired) "Expired" else "Waiting for OTP...",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isExpired) DangerRed else Color(0xFFFFB74D)
                )
            }

            // EXACT 3 COPY BUTTONS: NUMBER, COOKIE, UID
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
                        .height(34.dp)
                        .testTag("copy_number_button_${account.id}")
                ) {
                    Text("NUMBER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onCopyCookie,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .testTag("copy_cookie_button_${account.id}")
                ) {
                    Text("COOKIE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onCopyUid,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
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
                .height(540.dp),
            shape = RoundedCornerShape(8.dp),
            color = CardBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACCOUNTS HISTORY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onRefreshOtp,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("OTP REFRESH", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("CLOSE", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No accounts recorded.", color = TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onClearAll,
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLEAR ALL HISTORY", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
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
        else -> BorderColor
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = PrimaryFbBlue,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isCreating) "CREATION IN PROGRESS" else "STATUS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = borderColor
                    )
                }

                if (!isCreating) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                    }
                }
            }

            Text(
                text = statusMessage ?: "Processing request...",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal
            )
        }
    }
}
