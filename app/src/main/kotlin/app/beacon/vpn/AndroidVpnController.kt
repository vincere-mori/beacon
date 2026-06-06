package app.beacon.vpn

import android.content.Context
import app.beacon.core.singbox.RoutingPlatform
import app.beacon.core.singbox.SingBoxConfigBuilder
import app.beacon.core.singbox.SingBoxConfigSettings
import app.beacon.data.SharedPrefsProfileRepository

object AndroidVpnController {
    suspend fun connect(context: Context) {
        val appContext = context.applicationContext
        val repository = SharedPrefsProfileRepository(appContext)
        val profile = repository.currentActiveProfile()
            ?: throw IllegalStateException("сначала добавь ключ")
        val settings = repository.currentSettings()
        val config = SingBoxConfigBuilder().build(
            profile = profile,
            settings = SingBoxConfigSettings(
                dnsMode = settings.dnsMode,
                customDnsServers = settings.customDnsServers,
                ipv6Enabled = settings.ipv6Enabled,
                routing = settings.routing.ensureDefaults(),
                platform = RoutingPlatform.Android
            )
        )
        SingBoxVpnGateway(appContext).connect(config)
    }

    suspend fun disconnect(context: Context) {
        SingBoxVpnGateway(context.applicationContext).disconnect()
    }
}
