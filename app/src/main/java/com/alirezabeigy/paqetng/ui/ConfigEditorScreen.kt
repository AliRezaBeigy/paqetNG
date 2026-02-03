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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alirezabeigy.paqetng.data.DEFAULT_TCP_FLAGS
import com.alirezabeigy.paqetng.data.KcpBlockOptions
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
                            onSave(
                                config.copy(
                                    name = name,
                                    serverAddr = serverAddr,
                                    kcpKey = kcpKey,
                                    kcpBlock = kcpBlock,
                                    localFlag = localList,
                                    remoteFlag = remoteList
                                )
                            )
                        }
                    ) { Text("Save") }
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
