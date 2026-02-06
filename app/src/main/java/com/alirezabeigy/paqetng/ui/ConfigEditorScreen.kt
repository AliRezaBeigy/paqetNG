package com.alirezabeigy.paqetng.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alirezabeigy.paqetng.data.DEFAULT_TCP_FLAGS
import com.alirezabeigy.paqetng.data.KcpBlockOptions
import com.alirezabeigy.paqetng.data.KcpManualDefaults
import com.alirezabeigy.paqetng.data.KcpModeOptions
import com.alirezabeigy.paqetng.data.PaqetConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorScreen(
    config: PaqetConfig,
    onSave: (PaqetConfig) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(config.id) { mutableStateOf(config.name) }
    var serverAddr by remember(config.id) { mutableStateOf(config.serverAddr) }
    var kcpKey by remember(config.id) { mutableStateOf(config.kcpKey) }
    var kcpBlock by remember(config.id) {
        mutableStateOf(
            if (config.kcpBlock in KcpBlockOptions.all) config.kcpBlock else KcpBlockOptions.default
        )
    }
    var encryptionExpanded by remember(config.id) { mutableStateOf(false) }
    var conn by remember(config.id) { mutableStateOf(config.conn.toString()) }
    var kcpMode by remember(config.id) {
        mutableStateOf(
            if (config.kcpMode in KcpModeOptions.all) config.kcpMode else KcpModeOptions.default
        )
    }
    var modeExpanded by remember(config.id) { mutableStateOf(false) }
    var mtu by remember(config.id) { mutableStateOf(config.mtu.toString()) }
    val initialIsManualMode = config.kcpMode == "manual"
    var kcpNodelay by remember(config.id) { 
        mutableStateOf(config.kcpNodelay?.toString() ?: if (initialIsManualMode) KcpManualDefaults.nodelay.toString() else "")
    }
    var kcpInterval by remember(config.id) { 
        mutableStateOf(config.kcpInterval?.toString() ?: if (initialIsManualMode) KcpManualDefaults.interval.toString() else "")
    }
    var kcpResend by remember(config.id) { 
        mutableStateOf(config.kcpResend?.toString() ?: if (initialIsManualMode) KcpManualDefaults.resend.toString() else "")
    }
    var kcpNocongestion by remember(config.id) { 
        mutableStateOf(config.kcpNocongestion?.toString() ?: if (initialIsManualMode) KcpManualDefaults.nocongestion.toString() else "")
    }
    var kcpWdelay by remember(config.id) { 
        mutableStateOf(config.kcpWdelay?.toString() ?: if (initialIsManualMode) KcpManualDefaults.wdelay.toString() else "")
    }
    var kcpAcknodelay by remember(config.id) { 
        mutableStateOf(config.kcpAcknodelay?.toString() ?: if (initialIsManualMode) KcpManualDefaults.acknodelay.toString() else "")
    }
    var localFlag by remember(config.id) {
        mutableStateOf(
            when {
                config.localFlag == null || config.localFlag!!.isEmpty() -> DEFAULT_TCP_FLAGS
                else -> config.localFlag!!.joinToString(", ")
            }
        )
    }
    var remoteFlag by remember(config.id) {
        mutableStateOf(
            when {
                config.remoteFlag == null || config.remoteFlag!!.isEmpty() -> DEFAULT_TCP_FLAGS
                else -> config.remoteFlag!!.joinToString(", ")
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (config.id.isEmpty()) "Add config" else "Edit config") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val localList = localFlag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                .ifEmpty { listOf(DEFAULT_TCP_FLAGS) }
                            val remoteList = remoteFlag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                .ifEmpty { listOf(DEFAULT_TCP_FLAGS) }
                            val connValue = conn.toIntOrNull()?.coerceIn(1, 256) ?: 1
                            val mtuValue = mtu.toIntOrNull()?.coerceIn(50, 1500) ?: 1350
                            val isManualMode = kcpMode == "manual"
                            val nodelayValue = if (isManualMode) {
                                kcpNodelay.toIntOrNull()?.coerceIn(0, 1) ?: KcpManualDefaults.nodelay
                            } else null
                            val intervalValue = if (isManualMode) {
                                kcpInterval.toIntOrNull()?.coerceIn(10, 5000) ?: KcpManualDefaults.interval
                            } else null
                            val resendValue = if (isManualMode) {
                                kcpResend.toIntOrNull()?.coerceIn(0, 2) ?: KcpManualDefaults.resend
                            } else null
                            val nocongestionValue = if (isManualMode) {
                                kcpNocongestion.toIntOrNull()?.coerceIn(0, 1) ?: KcpManualDefaults.nocongestion
                            } else null
                            val wdelayValue = if (isManualMode) {
                                kcpWdelay.toBooleanStrictOrNull() ?: KcpManualDefaults.wdelay
                            } else null
                            val acknodelayValue = if (isManualMode) {
                                kcpAcknodelay.toBooleanStrictOrNull() ?: KcpManualDefaults.acknodelay
                            } else null
                            onSave(
                                config.copy(
                                    name = name,
                                    serverAddr = serverAddr,
                                    kcpKey = kcpKey,
                                    kcpBlock = kcpBlock,
                                    localFlag = localList,
                                    remoteFlag = remoteList,
                                    conn = connValue,
                                    kcpMode = kcpMode,
                                    mtu = mtuValue,
                                    kcpNodelay = nodelayValue,
                                    kcpInterval = intervalValue,
                                    kcpResend = resendValue,
                                    kcpNocongestion = nocongestionValue,
                                    kcpWdelay = wdelayValue,
                                    kcpAcknodelay = acknodelayValue
                                )
                            )
                        }
                    ) { Text("Save", color = MaterialTheme.colorScheme.onSurface) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = serverAddr,
                onValueChange = { serverAddr = it },
                label = { Text("Server address (host:port)") },
                modifier = Modifier.fillMaxWidth()
            )
            ExposedDropdownMenuBox(
                expanded = encryptionExpanded,
                onExpandedChange = { encryptionExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = kcpBlock,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Encryption") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = encryptionExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = encryptionExpanded,
                    onDismissRequest = { encryptionExpanded = false }
                ) {
                    KcpBlockOptions.all.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                kcpBlock = option
                                encryptionExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = kcpKey,
                onValueChange = { kcpKey = it },
                label = { Text("KCP key") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = conn,
                onValueChange = { conn = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Connections") },
                supportingText = { Text("Number of KCP connections (1-256, default: 1)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            ExposedDropdownMenuBox(
                expanded = modeExpanded,
                onExpandedChange = { modeExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = kcpMode,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("KCP mode") },
                    supportingText = { Text("normal, fast, fast2, fast3, manual (default: fast)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false }
                ) {
                    KcpModeOptions.all.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                kcpMode = option
                                modeExpanded = false
                                // When switching to manual mode, populate defaults if fields are empty
                                if (option == "manual") {
                                    if (kcpNodelay.isEmpty()) kcpNodelay = KcpManualDefaults.nodelay.toString()
                                    if (kcpInterval.isEmpty()) kcpInterval = KcpManualDefaults.interval.toString()
                                    if (kcpResend.isEmpty()) kcpResend = KcpManualDefaults.resend.toString()
                                    if (kcpNocongestion.isEmpty()) kcpNocongestion = KcpManualDefaults.nocongestion.toString()
                                    if (kcpWdelay.isEmpty()) kcpWdelay = KcpManualDefaults.wdelay.toString()
                                    if (kcpAcknodelay.isEmpty()) kcpAcknodelay = KcpManualDefaults.acknodelay.toString()
                                }
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = mtu,
                onValueChange = { mtu = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("MTU") },
                supportingText = { Text("Maximum transmission unit in bytes (50-1500, default: 1350)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (kcpMode == "manual") {
                OutlinedTextField(
                    value = kcpNodelay,
                    onValueChange = { kcpNodelay = it.filter { c -> c.isDigit() }.take(1) },
                    label = { Text("Nodelay") },
                    supportingText = { Text("0=disable (TCP-like), 1=enable (lower latency, aggressive retransmission)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kcpInterval,
                    onValueChange = { kcpInterval = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Interval (ms)") },
                    supportingText = { Text("Internal update timer interval (10-5000ms). Lower = more responsive but higher CPU") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kcpResend,
                    onValueChange = { kcpResend = it.filter { c -> c.isDigit() }.take(1) },
                    label = { Text("Resend") },
                    supportingText = { Text("Fast retransmit trigger (0-2). 0=disabled, 1=most aggressive, 2=aggressive") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kcpNocongestion,
                    onValueChange = { kcpNocongestion = it.filter { c -> c.isDigit() }.take(1) },
                    label = { Text("No Congestion") },
                    supportingText = { Text("Congestion control: 0=enabled (TCP-like), 1=disabled (maximum speed)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kcpWdelay,
                    onValueChange = { kcpWdelay = it },
                    label = { Text("Wdelay") },
                    supportingText = { Text("Write batching: false=flush immediately (low latency), true=batch writes (higher throughput)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = kcpAcknodelay,
                    onValueChange = { kcpAcknodelay = it },
                    label = { Text("Ack No Delay") },
                    supportingText = { Text("ACK sending: true=send immediately (lower latency), false=batch ACKs (bandwidth efficient)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = localFlag,
                onValueChange = { localFlag = it },
                label = { Text("Local TCP flags") },
                supportingText = { Text("Comma-separated, e.g. PA, S. Used to carry data (F,S,R,P,A,U,E,C,N).") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = remoteFlag,
                onValueChange = { remoteFlag = it },
                label = { Text("Remote TCP flags") },
                supportingText = { Text("Comma-separated. Default PA = Push+Ack.") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
