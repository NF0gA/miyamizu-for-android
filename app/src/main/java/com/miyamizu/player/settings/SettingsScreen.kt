package com.miyamizu.player.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
nimport androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miyamizu.player.ui.theme.MiyamizuTheme
import kotlinx.coroutines.launch

/**
 * Miyamizu設定画面。
 * リサンプラー設定・出力モード・RAM再生・テレメトリ・テーマを設定可能。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val resamplerEnabled by viewModel.resamplerEnabled.collectAsStateWithLifecycle()
    val resamplerEngine by viewModel.resamplerEngine.collectAsStateWithLifecycle()
    val targetRate by viewModel.resamplerTargetRate.collectAsStateWithLifecycle()
    val soxrPhase by viewModel.resamplerSoxrPhase.collectAsStateWithLifecycle()
    val soxrBandwidth by viewModel.resamplerSoxrBandwidth.collectAsStateWithLifecycle()
    val soxrQuality by viewModel.resamplerSoxrQuality.collectAsStateWithLifecycle()
    val r8brainPreset by viewModel.resamplerR8brainPreset.collectAsStateWithLifecycle()
    val outputMode by viewModel.outputMode.collectAsStateWithLifecycle()
    val ramPlaybackEnabled by viewModel.ramPlaybackEnabled.collectAsStateWithLifecycle()
    val telemetryEnabled by viewModel.telemetryEnabled.collectAsStateWithLifecycle()
    val followSystemTheme by viewModel.followSystemTheme.collectAsStateWithLifecycle()
    val darkTheme by viewModel.darkTheme.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ===== 出力設定 =====
            SettingsSectionTitle("Output")

            // 出力モード
            var expandedOutput by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedOutput,
                onExpandedChange = { expandedOutput = it }
            ) {
                OutlinedTextField(
                    value = outputMode.replaceFirstChar { it.uppercase() },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Output Mode") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOutput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expandedOutput,
                    onDismissRequest = { expandedOutput = false }
                ) {
                    listOf("auto", "aaudio", "usb").forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                scope.launch {
                                    viewModel.setOutputMode(mode)
                                }
                                expandedOutput = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // RAM再生
            SettingsSwitch(
                title = "RAM Playback",
                subtitle = "Pre-decode tracks to RAM for lower CPU usage during playback",
                checked = ramPlaybackEnabled,
                onCheckedChange = {
                    scope.launch { viewModel.setRamPlaybackEnabled(it) }
                }
            )

            // テレメトリ
            SettingsSwitch(
                title = "Show Telemetry",
                subtitle = "Display jitter and xrun stats on player screen",
                checked = telemetryEnabled,
                onCheckedChange = {
                    scope.launch { viewModel.setTelemetryEnabled(it) }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // ===== リサンプラー設定 =====
            SettingsSectionTitle("Resampler")

            SettingsSwitch(
                title = "Enable Resampler",
                subtitle = "Resample audio to target sample rate",
                checked = resamplerEnabled,
                onCheckedChange = {
                    scope.launch { viewModel.setResamplerEnabled(it) }
                }
            )

            if (resamplerEnabled) {
                Spacer(modifier = Modifier.height(8.dp))

                // エンジン選択
                Text("Engine", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(Modifier.selectableGroup()) {
                    listOf("soxr" to "soxr", "r8brain" to "r8brain-free-src").forEach { (value, label) ->
                        Row(
                            Modifier
                                .selectable(
                                    selected = resamplerEngine == value,
                                    onClick = {
                                        scope.launch { viewModel.setResamplerEngine(value) }
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = resamplerEngine == value,
                                onClick = null
                            )
                            Text(
                                text = label,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ターゲットサンプルレート
                var expandedRate by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedRate,
                    onExpandedChange = { expandedRate = it }
                ) {
                    OutlinedTextField(
                        value = "${targetRate} Hz",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Sample Rate") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRate) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedRate,
                        onDismissRequest = { expandedRate = false }
                    ) {
                        listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000).forEach { rate ->
                            DropdownMenuItem(
                                text = { Text("$rate Hz") },
                                onClick = {
                                    scope.launch { viewModel.setResamplerTargetRate(rate) }
                                    expandedRate = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // エンジン別詳細設定
                if (resamplerEngine == "soxr") {
                    // soxr Phase
                    Text("Phase Response", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Row(Modifier.selectableGroup()) {
                        listOf(
                            0 to "Linear (0%)",
                            1 to "Intermediate (50%)",
                            2 to "Minimum (100%)"
                        ).forEach { (value, label) ->
                            Row(
                                Modifier
                                    .selectable(
                                        selected = soxrPhase == value,
                                        onClick = {
                                            scope.launch { viewModel.setResamplerSoxrPhase(value) }
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = soxrPhase == value, onClick = null)
                                Text(label, modifier = Modifier.padding(start = 8.dp), fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // soxr Bandwidth
                    Text("Bandwidth", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Row(Modifier.selectableGroup()) {
                        listOf(
                            0 to "Standard",
                            1 to "Wide"
                        ).forEach { (value, label) ->
                            Row(
                                Modifier
                                    .selectable(
                                        selected = soxrBandwidth == value,
                                        onClick = {
                                            scope.launch { viewModel.setResamplerSoxrBandwidth(value) }
                                        },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = soxrBandwidth == value, onClick = null)
                                Text(label, modifier = Modifier.padding(start = 8.dp), fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // soxr Quality
                    Text("Quality: ${"%.1f".format(soxrQuality)}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Slider(
                        value = soxrQuality.toFloat(),
                        onValueChange = {
                            scope.launch { viewModel.setResamplerSoxrQuality(it.toDouble()) }
                        },
                        valueRange = 0f..10f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("0=Quick, 4=Low, 6=Medium, 8=High, 10=VHQ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                } else {
                    // r8brain preset
                    Text("Preset", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    listOf(
                        0 to "MinPhase Fast (lower CPU)",
                        1 to "MinPhase High (default)",
                        2 to "Linear Fast",
                        3 to "Linear High"
                    ).forEach { (value, label) ->
                        Row(
                            Modifier
                                .selectable(
                                    selected = r8brainPreset == value,
                                    onClick = {
                                        scope.launch { viewModel.setResamplerR8brainPreset(value) }
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = r8brainPreset == value, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 8.dp), fontSize = 13.sp)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // ===== テーマ設定 =====
            SettingsSectionTitle("Appearance")

            SettingsSwitch(
                title = "Follow System Theme",
                subtitle = "Use Android system dark/light mode setting",
                checked = followSystemTheme,
                onCheckedChange = {
                    scope.launch { viewModel.setFollowSystemTheme(it) }
                }
            )

            if (!followSystemTheme) {
                SettingsSwitch(
                    title = "Dark Theme",
                    subtitle = "Use dark color scheme",
                    checked = darkTheme,
                    onCheckedChange = {
                        scope.launch { viewModel.setDarkTheme(it) }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // ===== アプリ情報 =====
            SettingsSectionTitle("About")

            ListItem(
                headlineContent = { Text("Miyamizu for Android") },
                supportingContent = { Text("Version 1.0.0") },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
            )

            Text(
                "Miyamizu is an audio-quality-first music player. " +
                "It minimizes data transmission jitter and delivers bit-perfect audio to your DAC.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
