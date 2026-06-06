package app.beacon.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.beacon.MainViewModel
import app.beacon.core.geo.CountryDetector
import app.beacon.core.model.DnsMode
import app.beacon.core.model.ProfileKind
import app.beacon.core.model.ProxyProfile
import app.beacon.core.model.RoutingMode
import app.beacon.core.model.RoutingSettings
import app.beacon.core.model.Subscription
import androidx.core.graphics.drawable.toBitmap
import app.beacon.vpn.VpnStatus

@Composable
fun BeaconApp(
    viewModel: MainViewModel,
    onConnectRequested: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BeaconTheme {
        BeaconScreen(
            state = state,
            onConnectRequested = onConnectRequested,
            onDisconnect = viewModel::disconnect,
            onTabSelected = viewModel::selectTab,
            onDraftChanged = viewModel::setDraftKey,
            onSaveDraft = viewModel::saveDraftProfile,
            onSelectProfile = viewModel::selectProfile,
            onDeleteProfile = viewModel::deleteProfile,
            onSaveDnsSettings = viewModel::saveDnsSettings,
            onIpv6Changed = viewModel::setIpv6Enabled,
            onAddSubscription = viewModel::addSubscription,
            onRefreshSubscription = viewModel::refreshSubscription,
            onDeleteSubscription = viewModel::deleteSubscription,
            onPingSubscription = viewModel::pingSubscription,
            onPingServer = viewModel::pingServer,
            onSaveRouting = viewModel::saveRoutingSettings
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BeaconScreen(
    state: BeaconUiState,
    onConnectRequested: () -> Unit,
    onDisconnect: () -> Unit,
    onTabSelected: (BeaconTab) -> Unit,
    onDraftChanged: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveDnsSettings: (DnsMode, String, Boolean) -> Unit,
    onIpv6Changed: (Boolean) -> Unit,
    onAddSubscription: (String) -> Unit,
    onRefreshSubscription: (Subscription) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onPingSubscription: (Subscription) -> Unit,
    onPingServer: (ProxyProfile) -> Unit,
    onSaveRouting: (RoutingSettings) -> Unit
) {
    Scaffold(
        modifier = Modifier.beaconBackground(),
        containerColor = Color.Transparent,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Beacon", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = state.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = BeaconColors.Text
                )
            )
        },
        bottomBar = {
            BeaconBottomBar(
                selectedTab = state.selectedTab,
                onTabSelected = onTabSelected
            )
        }
    ) { padding ->
        when (state.selectedTab) {
            BeaconTab.Home -> HomeTab(
                state = state,
                padding = padding,
                onConnectRequested = onConnectRequested,
                onDisconnect = onDisconnect,
                onAddProfile = { onTabSelected(BeaconTab.Profiles) }
            )
            BeaconTab.Profiles -> ProfilesTab(
                state = state,
                padding = padding,
                onDraftChanged = onDraftChanged,
                onSaveDraft = onSaveDraft,
                onSelectProfile = onSelectProfile,
                onDeleteProfile = onDeleteProfile
            )
            BeaconTab.Subscriptions -> SubscriptionsTab(
                state = state,
                padding = padding,
                onAddSubscription = onAddSubscription,
                onRefreshSubscription = onRefreshSubscription,
                onDeleteSubscription = onDeleteSubscription,
                onPingSubscription = onPingSubscription,
                onPingServer = onPingServer,
                onSelectProfile = onSelectProfile
            )
            BeaconTab.Settings -> SettingsTab(
                state = state,
                padding = padding,
                onSaveDnsSettings = onSaveDnsSettings,
                onIpv6Changed = onIpv6Changed,
                onSaveRouting = onSaveRouting
            )
        }
    }
}

@Composable
private fun HomeTab(
    state: BeaconUiState,
    padding: PaddingValues,
    onConnectRequested: () -> Unit,
    onDisconnect: () -> Unit,
    onAddProfile: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            LighthouseHero(
                connected = state.status == VpnStatus.Connected,
                connecting = state.status == VpnStatus.Connecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(MaterialTheme.shapes.medium)
            )
        }
        item {
            StatusRow(state.status, state.statusText)
        }
        item {
            ConnectButton(
                state = state,
                onConnectRequested = onConnectRequested,
                onDisconnect = onDisconnect,
                onAddProfile = onAddProfile
            )
        }
        item {
            ActiveProfileCard(profile = state.activeProfile)
        }
        if (state.status == VpnStatus.Connected) {
            item {
                TrafficStatsCard(
                    downBytesPerSec = state.trafficDownBytesPerSec,
                    upBytesPerSec = state.trafficUpBytesPerSec
                )
            }
        }
        state.lastError?.let { error ->
            item {
                ErrorCard(error)
            }
        }
    }
}

@Composable
private fun StatusRow(status: VpnStatus, text: String) {
    val color = when (status) {
        VpnStatus.Connected -> BeaconColors.Success
        VpnStatus.Connecting, VpnStatus.Disconnecting -> BeaconColors.Warn
        VpnStatus.Disconnected -> BeaconColors.Muted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (status == VpnStatus.Disconnected) BeaconColors.TextDim else color
        )
    }
}

@Composable
private fun ConnectButton(
    state: BeaconUiState,
    onConnectRequested: () -> Unit,
    onDisconnect: () -> Unit,
    onAddProfile: () -> Unit
) {
    val container = when {
        state.status == VpnStatus.Connected -> BeaconColors.Danger
        state.status == VpnStatus.Connecting || state.status == VpnStatus.Disconnecting -> BeaconColors.Warn
        else -> BeaconColors.Accent
    }
    Button(
        onClick = {
            if (state.activeProfile == null) {
                onAddProfile()
            } else if (state.status == VpnStatus.Connected) {
                onDisconnect()
            } else {
                onConnectRequested()
            }
        },
        enabled = !state.isBusy && state.status != VpnStatus.Connecting && state.status != VpnStatus.Disconnecting,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(vertical = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.PowerSettingsNew,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (state.activeProfile == null) "Добавить ключ" else primaryActionText(state.status),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ActiveProfileCard(profile: ProxyProfile?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = "Активный ключ",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = profile?.name ?: "Нет ключа",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = profile?.let { "${it.host}:${it.port}" } ?: "Добавь VLESS Reality ключ",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ErrorCard(error: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TrafficStatsCard(
    downBytesPerSec: Long,
    upBytesPerSec: Long
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrafficValue(
                label = "Входящий",
                value = formatBytesPerSec(downBytesPerSec),
                modifier = Modifier.weight(1f)
            )
            TrafficValue(
                label = "Исходящий",
                value = formatBytesPerSec(upBytesPerSec),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TrafficValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfilesTab(
    state: BeaconUiState,
    padding: PaddingValues,
    onDraftChanged: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AddProfileCard(
                value = state.draftKey,
                onValueChanged = onDraftChanged,
                onSave = onSaveDraft,
                enabled = !state.isBusy
            )
        }
        items(state.profiles, key = { it.id }) { profile ->
            ProfileRow(
                profile = profile,
                selected = profile.id == state.activeProfile?.id,
                onSelect = { onSelectProfile(profile.id) },
                onDelete = { onDeleteProfile(profile.id) }
            )
        }
    }
}

@Composable
private fun AddProfileCard(
    value: String,
    onValueChanged: (String) -> Unit,
    onSave: () -> Unit,
    enabled: Boolean
) {
    Card(shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ключ") },
                minLines = 4,
                maxLines = 8
            )
            Button(
                onClick = onSave,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Key, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: ProxyProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = profile.kind.label() + " · " + profile.hostPort(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab(
    state: BeaconUiState,
    padding: PaddingValues,
    onSaveDnsSettings: (DnsMode, String, Boolean) -> Unit,
    onIpv6Changed: (Boolean) -> Unit,
    onSaveRouting: (RoutingSettings) -> Unit
) {
    val savedRouting = state.settings.routing.ensureDefaults()
    var selectedDnsMode by remember(state.settings.dnsMode) {
        mutableStateOf(state.settings.dnsMode)
    }
    var useCustomDns by remember(state.settings.customDnsServers) {
        mutableStateOf(state.settings.customDnsServers.isNotEmpty())
    }
    var customDnsText by remember(state.settings.customDnsServers) {
        mutableStateOf(state.settings.customDnsServers.joinToString("\n"))
    }
    var routingMode by remember(savedRouting.mode) {
        mutableStateOf(savedRouting.mode)
    }
    var proxyDomains by remember(savedRouting) {
        mutableStateOf(
            if (savedRouting.mode == RoutingMode.ProxyAllExcept) {
                RoutingSettings.toMultiline(savedRouting.exceptionDomains)
            } else {
                RoutingSettings.toMultiline(RoutingSettings.defaultProxyBypassDomains())
            }
        )
    }
    var proxyCidrs by remember(savedRouting) {
        mutableStateOf(
            if (savedRouting.mode == RoutingMode.ProxyAllExcept) {
                RoutingSettings.toMultiline(savedRouting.exceptionCidrs)
            } else {
                ""
            }
        )
    }
    var proxyPackages by remember(savedRouting) {
        mutableStateOf(
            if (savedRouting.mode == RoutingMode.ProxyAllExcept) {
                savedRouting.androidPackages
            } else {
                emptyList()
            }
        )
    }
    var directDomains by remember(savedRouting) {
        mutableStateOf(
            if (savedRouting.mode == RoutingMode.DirectAllExcept) {
                RoutingSettings.toMultiline(savedRouting.exceptionDomains)
            } else {
                ""
            }
        )
    }
    var directCidrs by remember(savedRouting) {
        mutableStateOf(
            if (savedRouting.mode == RoutingMode.DirectAllExcept) {
                RoutingSettings.toMultiline(savedRouting.exceptionCidrs)
            } else {
                ""
            }
        )
    }
    var directPackages by remember(savedRouting) {
        mutableStateOf(
            if (savedRouting.mode == RoutingMode.DirectAllExcept) {
                savedRouting.androidPackages
            } else {
                emptyList()
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(16.dp)) {
                    Text("DNS", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Запросы идут через VPN. Для сайтов-исключений используется системный DNS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        DnsMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = !useCustomDns && selectedDnsMode == mode,
                                onClick = {
                                    selectedDnsMode = mode
                                    useCustomDns = false
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = DnsMode.entries.size
                                )
                            ) {
                                Text(mode.title)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Свой DNS", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "IP-адрес или DoH через https://",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useCustomDns,
                            onCheckedChange = { useCustomDns = it }
                        )
                    }
                    if (useCustomDns) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customDnsText,
                            onValueChange = { customDnsText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("DNS-сервер") },
                            placeholder = { Text("9.9.9.9 или https://dns.example/dns-query") },
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            onSaveDnsSettings(selectedDnsMode, customDnsText, useCustomDns)
                        },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Применить DNS")
                    }
                }
            }
        }
        item {
            Card(shape = MaterialTheme.shapes.medium) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("IPv6", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (state.settings.ipv6Enabled) "Включён" else "Выключен",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.settings.ipv6Enabled,
                        onCheckedChange = onIpv6Changed
                    )
                }
            }
        }
        item {
            Card(shape = MaterialTheme.shapes.medium) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Маршрутизация", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Выбери, какой трафик должен идти через VPN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(
                            RoutingMode.ProxyAllExcept to "VPN для всех",
                            RoutingMode.DirectAllExcept to "Только выбранное"
                        ).forEachIndexed { index, (mode, title) ->
                            SegmentedButton(
                                selected = routingMode == mode,
                                onClick = { routingMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)
                            ) {
                                Text(title)
                            }
                        }
                    }
                    Text(
                        if (routingMode == RoutingMode.ProxyAllExcept) {
                            "Домены, сети и приложения ниже пойдут напрямую."
                        } else {
                            "Только домены, сети и приложения ниже пойдут через VPN."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RoutingTextField(
                        label = "Домены, по одному на строку",
                        value = if (routingMode == RoutingMode.ProxyAllExcept) {
                            proxyDomains
                        } else {
                            directDomains
                        },
                        onValueChange = {
                            if (routingMode == RoutingMode.ProxyAllExcept) {
                                proxyDomains = it
                            } else {
                                directDomains = it
                            }
                        }
                    )
                    RoutingTextField(
                        label = "IP/CIDR, по одному на строку",
                        value = if (routingMode == RoutingMode.ProxyAllExcept) {
                            proxyCidrs
                        } else {
                            directCidrs
                        },
                        onValueChange = {
                            if (routingMode == RoutingMode.ProxyAllExcept) {
                                proxyCidrs = it
                            } else {
                                directCidrs = it
                            }
                        }
                    )
                    AndroidPackagePicker(
                        selectedPackages = if (routingMode == RoutingMode.ProxyAllExcept) {
                            proxyPackages
                        } else {
                            directPackages
                        },
                        onSelectionChange = {
                            if (routingMode == RoutingMode.ProxyAllExcept) {
                                proxyPackages = it
                            } else {
                                directPackages = it
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                if (routingMode == RoutingMode.ProxyAllExcept) {
                                    proxyDomains = RoutingSettings.toMultiline(
                                        RoutingSettings.defaultProxyBypassDomains()
                                    )
                                    proxyCidrs = ""
                                    proxyPackages = emptyList()
                                } else {
                                    directDomains = ""
                                    directCidrs = ""
                                    directPackages = emptyList()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Сбросить")
                        }
                        Button(
                            onClick = {
                                val domains = if (routingMode == RoutingMode.ProxyAllExcept) {
                                    proxyDomains
                                } else {
                                    directDomains
                                }
                                val cidrs = if (routingMode == RoutingMode.ProxyAllExcept) {
                                    proxyCidrs
                                } else {
                                    directCidrs
                                }
                                val packages = if (routingMode == RoutingMode.ProxyAllExcept) {
                                    proxyPackages
                                } else {
                                    directPackages
                                }
                                onSaveRouting(
                                    savedRouting.copy(
                                        mode = routingMode,
                                        exceptionDomains = RoutingSettings.parseMultiline(
                                            domains,
                                            lowercase = true
                                        ),
                                        exceptionCidrs = RoutingSettings.parseMultiline(cidrs),
                                        androidPackages = packages
                                    )
                                )
                            },
                            enabled = !state.isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Применить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = 2,
        maxLines = 5
    )
}

@Composable
private fun AndroidPackagePicker(
    selectedPackages: List<String>,
    onSelectionChange: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val installedApps = remember { loadLaunchableApps(context.packageManager) }
    var dialogOpen by remember { mutableStateOf(false) }
    var query by remember(dialogOpen) { mutableStateOf("") }
    var draftSelection by remember(dialogOpen, selectedPackages) {
        mutableStateOf(selectedPackages.toSet())
    }
    val labels = remember(installedApps) { installedApps.associate { it.packageName to it.label } }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Приложения", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            onClick = {
                draftSelection = selectedPackages.toSet()
                dialogOpen = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (selectedPackages.isEmpty()) {
                    "Выбрать приложения"
                } else {
                    "Выбрано: ${selectedPackages.size}"
                }
            )
        }
        if (selectedPackages.isNotEmpty()) {
            Text(
                selectedPackages.joinToString(limit = 4, truncated = "…") {
                    labels[it] ?: it
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (!dialogOpen) return
    val filtered = remember(installedApps, query, draftSelection) {
        val normalizedQuery = query.trim().lowercase()
        installedApps
            .filter {
                normalizedQuery.isEmpty() ||
                    it.label.lowercase().contains(normalizedQuery) ||
                    it.packageName.lowercase().contains(normalizedQuery)
            }
            .sortedWith(
                compareByDescending<InstalledAppOption> { it.packageName in draftSelection }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            )
    }

    AlertDialog(
        onDismissRequest = { dialogOpen = false },
        title = { Text("Приложения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Поиск") },
                    singleLine = true
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        val checked = app.packageName in draftSelection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draftSelection = if (checked) {
                                        draftSelection - app.packageName
                                    } else {
                                        draftSelection + app.packageName
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    draftSelection = if (checked) {
                                        draftSelection - app.packageName
                                    } else {
                                        draftSelection + app.packageName
                                    }
                                }
                            )
                            Image(
                                bitmap = remember(app.packageName) {
                                    app.icon.toBitmap(48, 48).asImageBitmap()
                                },
                                contentDescription = null,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(MaterialTheme.shapes.small)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    app.packageName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelectionChange(draftSelection.sorted())
                    dialogOpen = false
                }
            ) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = { dialogOpen = false }) {
                Text("Отмена")
            }
        }
    )
}

private data class InstalledAppOption(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

private fun loadLaunchableApps(packageManager: PackageManager): List<InstalledAppOption> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
    }.getOrDefault(emptyList())
        .asSequence()
        .mapNotNull { info ->
            val application = info.activityInfo.applicationInfo
            runCatching {
                InstalledAppOption(
                    label = application.loadLabel(packageManager).toString().ifBlank {
                        application.packageName
                    },
                    packageName = application.packageName,
                    icon = application.loadIcon(packageManager)
                )
            }.getOrNull()
        }
        .distinctBy(InstalledAppOption::packageName)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledAppOption::label))
        .toList()
}

@Composable
private fun BeaconBottomBar(
    selectedTab: BeaconTab,
    onTabSelected: (BeaconTab) -> Unit
) {
    NavigationBar(
        containerColor = BeaconColors.BgTop,
        contentColor = BeaconColors.TextDim
    ) {
        BeaconTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(tab.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = BeaconColors.AccentLight,
                    indicatorColor = BeaconColors.Accent,
                    unselectedIconColor = BeaconColors.Muted,
                    unselectedTextColor = BeaconColors.Muted
                )
            )
        }
    }
}

private fun primaryActionText(status: VpnStatus): String {
    return when (status) {
        VpnStatus.Connected -> "Отключить"
        VpnStatus.Connecting -> "Подключение"
        VpnStatus.Disconnecting -> "Отключение"
        VpnStatus.Disconnected -> "Подключить"
    }
}

private fun ProfileKind.label(): String {
    return when (this) {
        ProfileKind.VlessReality -> "VLESS Reality"
    }
}

private fun ProxyProfile.hostPort(): String {
    return if (port > 0) "$host:$port" else host
}

private fun BeaconTab.icon(): ImageVector {
    return when (this) {
        BeaconTab.Home -> Icons.Outlined.Home
        BeaconTab.Profiles -> Icons.Outlined.Key
        BeaconTab.Subscriptions -> Icons.Outlined.Public
        BeaconTab.Settings -> Icons.Outlined.Settings
    }
}

private fun flagEmoji(code: String?): String {
    if (code == null || code.length != 2) return "🌐"
    val upper = code.uppercase()
    if (upper.any { it !in 'A'..'Z' }) return "🌐"
    val builder = StringBuilder()
    upper.forEach { builder.appendCodePoint(0x1F1E6 + (it - 'A')) }
    return builder.toString()
}

@Composable
private fun SubscriptionsTab(
    state: BeaconUiState,
    padding: PaddingValues,
    onAddSubscription: (String) -> Unit,
    onRefreshSubscription: (Subscription) -> Unit,
    onDeleteSubscription: (String) -> Unit,
    onPingSubscription: (Subscription) -> Unit,
    onPingServer: (ProxyProfile) -> Unit,
    onSelectProfile: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AddSubscriptionCard(
                onAdd = onAddSubscription,
                enabled = !state.isBusy
            )
        }
        state.lastError?.let { error ->
            item { ErrorCard(error) }
        }
        if (state.subscriptions.isEmpty()) {
            item {
                Text(
                    text = "Подписок пока нет. Вставь ссылку подписки выше — она развернётся в список серверов.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(state.subscriptions, key = { it.id }) { subscription ->
            SubscriptionCard(
                subscription = subscription,
                activeProfileId = state.activeProfile?.id,
                pingResults = state.pingResults,
                pingingIds = state.pingingIds,
                busy = state.isBusy,
                onRefresh = { onRefreshSubscription(subscription) },
                onDelete = { onDeleteSubscription(subscription.id) },
                onPingAll = { onPingSubscription(subscription) },
                onPingServer = onPingServer,
                onSelectServer = { onSelectProfile(it.id) }
            )
        }
    }
}

@Composable
private fun AddSubscriptionCard(
    onAdd: (String) -> Unit,
    enabled: Boolean
) {
    var url by remember { mutableStateOf("") }
    Card(shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ссылка подписки") },
                placeholder = { Text("https://...") },
                singleLine = true
            )
            Button(
                onClick = {
                    onAdd(url)
                    url = ""
                },
                enabled = enabled && url.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить подписку")
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    activeProfileId: String?,
    pingResults: Map<String, Long?>,
    pingingIds: Set<String>,
    busy: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onPingAll: () -> Unit,
    onPingServer: (ProxyProfile) -> Unit,
    onSelectServer: (ProxyProfile) -> Unit
) {
    Card(shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = subscription.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${subscription.profiles.size} серверов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPingAll, enabled = !busy) {
                    Icon(Icons.Outlined.Bolt, contentDescription = "Пинг всех")
                }
                IconButton(onClick = onRefresh, enabled = !busy) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
                }
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                }
            }
            if (subscription.profiles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }
            subscription.profiles.forEach { server ->
                ServerRow(
                    server = server,
                    selected = server.id == activeProfileId,
                    pinging = server.id in pingingIds,
                    hasPing = pingResults.containsKey(server.id),
                    pingMs = pingResults[server.id],
                    onSelect = { onSelectServer(server) },
                    onPing = { onPingServer(server) }
                )
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: ProxyProfile,
    selected: Boolean,
    pinging: Boolean,
    hasPing: Boolean,
    pingMs: Long?,
    onSelect: () -> Unit,
    onPing: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = flagEmoji(CountryDetector.detect(server.name)),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${server.host}:${server.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onPing) {
            Text(pingText(pinging, hasPing, pingMs))
        }
        if (selected) {
            AssistChip(onClick = onSelect, label = { Text("активен") })
        } else {
            OutlinedButton(onClick = onSelect) {
                Text("выбрать")
            }
        }
    }
}

private fun pingText(pinging: Boolean, hasPing: Boolean, pingMs: Long?): String = when {
    pinging -> "…"
    !hasPing -> "пинг"
    pingMs == null -> "—"
    else -> "$pingMs ms"
}

private fun formatBytesPerSec(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B/s"
    bytes < 1024 * 1024 -> "%.1f KB/s".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB/s".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB/s".format(bytes / (1024.0 * 1024 * 1024))
}
