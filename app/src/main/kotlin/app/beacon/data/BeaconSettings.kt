package app.beacon.data

import app.beacon.core.model.DnsMode
import app.beacon.core.model.RoutingSettings
import kotlinx.serialization.Serializable

@Serializable
data class BeaconSettings(
    val dnsMode: DnsMode = DnsMode.Cloudflare,
    val customDnsServers: List<String> = emptyList(),
    val ipv6Enabled: Boolean = false,
    val routing: RoutingSettings = RoutingSettings.defaults()
)
