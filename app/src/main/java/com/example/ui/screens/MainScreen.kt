package com.example.ui.screens

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.model.AccountEntity
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

    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }

    LaunchedEffect(uiState.savedPassword) {
        passwordInput = uiState.savedPassword
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
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
                        onClick = { showHistoryDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("open_history_button")
                    ) {
                        Text("HISTORY (${uiState.accountsHistory.size})", fontSize = 12.sp, color = TextPrimary)
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

            // 2. START & STOP SERVICE CONTROL CARD
            item {
                ServiceControlCard(
                    isRunning = uiState.isServiceRunning,
                    onToggleClick = { viewModel.toggleService(context) },
                    onCreateNowClick = { viewModel.createAccountNow(context) },
                    isCreating = uiState.isCreatingAccount
                )
            }

            // 3. PASSWORD MANAGEMENT CARD
            item {
                PasswordCard(
                    passwordValue = passwordInput,
                    onPasswordChange = { passwordInput = it },
                    onSaveClick = {
                        if (passwordInput.isNotBlank()) {
                            viewModel.savePassword(passwordInput)
                        } else {
                            Toast.makeText(context, "Please enter a valid password", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 4. LIVE FACEBOOK RANGE SELECTOR CARD
            item {
                FacebookRangeCard(
                    ranges = uiState.facebookRanges,
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
                            val cookie = account.cookie.ifEmpty { "c_user=${account.uid}; phone=${account.phone}; pass=${account.password}" }
                            copyTextToClipboard(context, "COOKIE", cookie)
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
            onCopyCookie = { copyTextToClipboard(context, "COOKIE", it) },
            onCopyUid = { copyTextToClipboard(context, "UID", it) },
            onDelete = { viewModel.deleteAccount(it) },
            onClearAll = { viewModel.clearHistory() }
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
private fun PasswordCard(
    passwordValue: String,
    onPasswordChange: (String) -> Unit,
    onSaveClick: () -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = PrimaryFbBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SAVE PASSWORD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            }

            OutlinedTextField(
                value = passwordValue,
                onValueChange = onPasswordChange,
                label = { Text("Account Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryFbBlue,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input_field")
            )

            Button(
                onClick = onSaveClick,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryFbBlue),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_password_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE PASSWORD (PERSISTENT)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FacebookRangeCard(
    ranges: List<String>,
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
                        val isSelected = rangeItem == selectedRange
                        Row(
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
                                .clickable { onSelectRange(rangeItem) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Range: $rangeItem",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) PrimaryFbBlue else TextPrimary,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Text("SELECTED", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

            if (!account.otp.isNullOrEmpty()) {
                Text(
                    text = "OTP: ${account.otp}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = AccentGreen
                )
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
                                onCopyCookie = {
                                    val cookieStr = item.cookie.ifEmpty { "c_user=${item.uid}; phone=${item.phone}; pass=${item.password}" }
                                    onCopyCookie(cookieStr)
                                },
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
