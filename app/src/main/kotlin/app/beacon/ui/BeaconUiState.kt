package app.beacon.ui

import app.beacon.core.model.ProxyProfile
import app.beacon.core.model.Subscription
import app.beacon.data.BeaconSettings
import app.beacon.vpn.VpnStatus

data class BeaconUiState(
    val selectedTab: BeaconTab = BeaconTab.Home,
    val profiles: List<ProxyProfile> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val activeProfile: ProxyProfile? = null,
    val draftKey: String = "",
    val status: VpnStatus = VpnStatus.Disconnected,
    val statusText: String = ConnectionStatusText.from(VpnStatus.Disconnected),
    val lastError: String? = null,
    val settings: BeaconSettings = BeaconSettings(),
    val isBusy: Boolean = false,
    val trafficUpBytesPerSec: Long = 0,
    val trafficDownBytesPerSec: Long = 0,
    val pingResults: Map<String, Long?> = emptyMap(),
    val pingingIds: Set<String> = emptySet()
)
