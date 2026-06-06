package app.beacon.core.singbox

import app.beacon.core.model.DnsMode
import app.beacon.core.model.ProxyProfile
import app.beacon.core.model.RoutingMode
import app.beacon.core.model.RoutingSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SingBoxConfigBuilder(
    private val json: Json = Json {
        prettyPrint = true
        explicitNulls = false
    }
) {
    fun build(profile: ProxyProfile, settings: SingBoxConfigSettings = SingBoxConfigSettings()): String {
        val vless = profile.vless ?: error("vless profile missing")
        val routing = settings.routing.ensureDefaults()
        val proxySupportsUdp = vless.flow == null
        val config = buildJsonObject {
            put("log", buildJsonObject {
                put("level", "info")
                put("timestamp", true)
            })
            put("dns", dns(settings, routing, vless.server))
            if (settings.warpEnabled && settings.warpPrivateKey.isNotBlank()) {
                put("endpoints", buildJsonArray {
                    add(warpEndpoint(settings))
                })
            }
            put("inbounds", buildJsonArray {
                add(
                    when (settings.inboundMode) {
                        InboundMode.Tun -> tun(settings)
                        InboundMode.Mixed -> mixed(settings)
                    }
                )
            })
            put("outbounds", buildJsonArray {
                add(vlessOutbound(profile))
                add(taggedOutbound("direct", "direct"))
            })
            put("route", route(settings, routing, proxySupportsUdp))
            put("experimental", buildJsonObject {
                put("clash_api", buildJsonObject {
                    put("external_controller", "127.0.0.1:${settings.clashApiPort}")
                    put("default_mode", "rule")
                })
            })
        }

        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun dns(
        settings: SingBoxConfigSettings,
        routing: RoutingSettings,
        proxyServer: String
    ): JsonObject {
        val customDns = settings.customDnsServers.mapNotNull(::parseCustomDnsServer)
        val remoteDns = customDns.firstOrNull()
        val bootstrapServer = customDns.firstOrNull { !it.needsDomainResolver }?.server ?: "1.1.1.1"
        val directDomains = if (routing.mode == RoutingMode.ProxyAllExcept) {
            (routing.exceptionDomains + RoutingSettings.builtInLocalDomains()).distinct()
        } else {
            emptyList()
        }
        val proxyDomains = if (routing.mode == RoutingMode.DirectAllExcept) {
            routing.exceptionDomains
        } else {
            emptyList()
        }
        val directDnsTag = if (settings.platform == RoutingPlatform.Android) "local" else "direct-dns"

        return buildJsonObject {
            put("strategy", if (settings.ipv6Enabled) "prefer_ipv4" else "ipv4_only")
            put("servers", buildJsonArray {
                add(buildJsonObject {
                    put("type", "udp")
                    put("tag", "bootstrap")
                    put("server", bootstrapServer)
                })
                add(buildJsonObject {
                    put("type", remoteDns?.type ?: "https")
                    put("tag", "remote")
                    put("server", remoteDns?.server ?: settings.dnsMode.server)
                    val path = remoteDns?.path ?: settings.dnsMode.path
                    if ((remoteDns?.type ?: "https") == "https") {
                        put("path", path)
                    }
                    if (
                        remoteDns?.needsDomainResolver == true ||
                        (remoteDns == null && settings.dnsMode.needsDomainResolver)
                    ) {
                        put("domain_resolver", "bootstrap")
                    }
                    put("detour", "proxy")
                })
                if (settings.platform == RoutingPlatform.Android) {
                    add(buildJsonObject {
                        put("type", "local")
                        put("tag", directDnsTag)
                    })
                } else {
                    add(buildJsonObject {
                        put("type", "udp")
                        put("tag", directDnsTag)
                        put("server", "77.88.8.8")
                    })
                }
            })
            put("rules", buildJsonArray {
                if (!proxyServer.isIpAddress()) {
                    add(buildJsonObject {
                        put("domain", JsonArray(listOf(JsonPrimitive(proxyServer))))
                        put("server", "bootstrap")
                    })
                }
                if (directDomains.isNotEmpty()) {
                    add(buildJsonObject {
                        put("domain_suffix", jsonValues(directDomains))
                        put("server", directDnsTag)
                    })
                }
                if (proxyDomains.isNotEmpty()) {
                    add(buildJsonObject {
                        put("domain_suffix", jsonValues(proxyDomains))
                        put("server", "remote")
                    })
                }
            })
            put("final", if (routing.mode == RoutingMode.DirectAllExcept) directDnsTag else "remote")
        }
    }

    private fun tun(settings: SingBoxConfigSettings): JsonObject {
        val address = if (settings.ipv6Enabled) {
            listOf("172.19.0.1/30", "fdfe:dcba:9876::1/126")
        } else {
            listOf("172.19.0.1/30")
        }
        val mtu = if (settings.platform == RoutingPlatform.Android) 1400 else 9000
        val stack = if (settings.platform == RoutingPlatform.Android) "system" else "mixed"

        return buildJsonObject {
            put("type", "tun")
            put("tag", "tun-in")
            put("address", JsonArray(address.map(::JsonPrimitive)))
            put("mtu", mtu)
            put("auto_route", true)
            put("strict_route", true)
            put("stack", stack)
            // Required for Discord voice / WebRTC / online games — without it
            // sing-box looks like a symmetric NAT and STUN cannot pair peers.
            put("endpoint_independent_nat", true)
            put("udp_timeout", "5m")
        }
    }

    private fun mixed(settings: SingBoxConfigSettings): JsonObject {
        return buildJsonObject {
            put("type", "mixed")
            put("tag", "mixed-in")
            put("listen", settings.mixedListenHost)
            put("listen_port", settings.mixedListenPort)
        }
    }

    private fun vlessOutbound(profile: ProxyProfile): JsonObject {
        val vless = profile.vless ?: error("vless profile missing")
        return buildJsonObject {
            put("type", "vless")
            put("tag", "proxy")
            put("server", vless.server)
            put("server_port", vless.port)
            put("uuid", vless.uuid)
            if (!vless.server.isIpAddress()) {
                put("domain_resolver", "bootstrap")
            }
            vless.flow?.let { put("flow", it) }
            put("network", "tcp")
            // xtls-rprx-vision на сервере (Xray) не пропускает UDP. Если flow
            // не задан — пакуем UDP в TCP через xudp, чтобы Discord/QUIC/игры
            // ходили через основной туннель без ошибок "UDP is not supported".
            if (vless.flow == null) {
                put("packet_encoding", "xudp")
            }
            put("tls", buildJsonObject {
                put("enabled", true)
                put("server_name", vless.serverName)
                put("utls", buildJsonObject {
                    put("enabled", true)
                    put("fingerprint", vless.fingerprint)
                })
                put("reality", buildJsonObject {
                    put("enabled", true)
                    put("public_key", vless.publicKey)
                    vless.shortId?.let { put("short_id", it) }
                })
            })
        }
    }

    private fun taggedOutbound(type: String, tag: String): JsonObject {
        return buildJsonObject {
            put("type", type)
            put("tag", tag)
        }
    }

    private fun warpEndpoint(settings: SingBoxConfigSettings): JsonObject {
        val (epHost, epPort) = parseEndpoint(settings.warpEndpoint)
        return buildJsonObject {
            put("type", "wireguard")
            put("tag", "warp")
            put("detour", "proxy")
            // "system" is NOT a valid field for endpoints[] in sing-box 1.11+
            // (it was only for the old outbounds[] wireguard type — removed to avoid parse issues)
            put("mtu", 1280)
            put("address", buildJsonArray {
                add(JsonPrimitive("${settings.warpLocalAddressV4}/32"))
                if (settings.warpLocalAddressV6.isNotBlank()) {
                    add(JsonPrimitive("${settings.warpLocalAddressV6}/128"))
                }
            })
            put("private_key", settings.warpPrivateKey)
            put("peers", buildJsonArray {
                add(buildJsonObject {
                    put("address", epHost)
                    put("port", epPort)
                    put("public_key", settings.warpPeerPublicKey)
                    put("allowed_ips", buildJsonArray {
                        add(JsonPrimitive("0.0.0.0/0"))
                        add(JsonPrimitive("::/0"))
                    })
                    put("persistent_keepalive_interval", 30)
                    put("reserved", JsonArray(normalizedWarpReserved(settings).map(::JsonPrimitive)))
                })
            })
        }
    }

    private fun normalizedWarpReserved(settings: SingBoxConfigSettings): List<Int> {
        return (settings.warpReserved.map { it.coerceIn(0, 255) } + listOf(0, 0, 0)).take(3)
    }

    private fun parseEndpoint(endpoint: String): Pair<String, Int> {
        val text = endpoint.trim()
        if (text.startsWith("[")) {
            val host = text.substringAfter("[").substringBefore("]")
            val port = text.substringAfter("]:", "").toIntOrNull() ?: 2408
            return host to port
        }

        val host = text.substringBeforeLast(":", text)
        val port = text.substringAfterLast(":", "").toIntOrNull() ?: 2408
        return host to port
    }

    private fun route(
        settings: SingBoxConfigSettings,
        routing: RoutingSettings,
        proxySupportsUdp: Boolean
    ): JsonObject {
        val proxyOnlySelected = routing.mode == RoutingMode.DirectAllExcept
        val finalOutbound = if (proxyOnlySelected) "direct" else "proxy"
        val warpActive = settings.warpEnabled && settings.warpPrivateKey.isNotBlank()

        return buildJsonObject {
            put("auto_detect_interface", true)
            put("default_domain_resolver", "remote")
            if (routing.androidPackages.isNotEmpty() || routing.desktopProcesses.isNotEmpty()) {
                put("find_process", true)
            }
            put("rules", buildJsonArray {
                add(buildJsonObject { put("action", "sniff") })
                add(buildJsonObject { put("protocol", "dns"); put("action", "hijack-dns") })
                add(buildJsonObject {
                    put("ip_cidr", jsonValues(RoutingSettings.builtInLocalCidrs()))
                    put("action", "route")
                    put("outbound", "direct")
                })
                add(buildJsonObject { put("ip_is_private", true); put("action", "route"); put("outbound", "direct") })

                if (warpActive) {
                    addDomainOrIpRule(this, routing.warpDomains, routing.warpCidrs, "warp")
                    if (!proxySupportsUdp) {
                        add(buildJsonObject {
                            put("network", "udp")
                            put("action", "route")
                            put("outbound", "warp")
                        })
                    }
                } else if (!proxySupportsUdp) {
                    if (proxyOnlySelected) {
                        addPlatformRejectRule(this, settings.platform, routing)
                        addDomainOrIpRejectRule(this, routing.exceptionDomains, routing.exceptionCidrs)
                    } else {
                        add(buildJsonObject {
                            put("protocol", jsonValues(listOf("quic")))
                            put("action", "reject")
                        })
                    }
                }

                if (proxyOnlySelected) {
                    addPlatformRule(this, settings.platform, routing, "proxy")
                    addDomainOrIpRule(
                        this,
                        routing.exceptionDomains,
                        routing.exceptionCidrs,
                        "proxy"
                    )
                } else {
                    addPlatformRule(this, settings.platform, routing, "direct")
                    addDomainOrIpRule(
                        this,
                        (routing.exceptionDomains + RoutingSettings.builtInLocalDomains()).distinct(),
                        routing.exceptionCidrs,
                        "direct"
                    )
                }
            })
            put("final", finalOutbound)
        }
    }

    private fun addPlatformRule(
        array: kotlinx.serialization.json.JsonArrayBuilder,
        platform: RoutingPlatform,
        routing: RoutingSettings,
        outbound: String
    ) {
        val values = when (platform) {
            RoutingPlatform.Android -> routing.androidPackages
            RoutingPlatform.Desktop -> routing.desktopProcesses
        }
        if (values.isEmpty()) return
        array.add(buildJsonObject {
            put(
                if (platform == RoutingPlatform.Android) "package_name" else "process_name",
                jsonValues(values)
            )
            put("action", "route")
            put("outbound", outbound)
        })
    }

    private fun addPlatformRejectRule(
        array: kotlinx.serialization.json.JsonArrayBuilder,
        platform: RoutingPlatform,
        routing: RoutingSettings
    ) {
        val values = when (platform) {
            RoutingPlatform.Android -> routing.androidPackages
            RoutingPlatform.Desktop -> routing.desktopProcesses
        }
        if (values.isEmpty()) return
        array.add(buildJsonObject {
            put("protocol", jsonValues(listOf("quic")))
            put(
                if (platform == RoutingPlatform.Android) "package_name" else "process_name",
                jsonValues(values)
            )
            put("action", "reject")
        })
    }

    private fun addDomainOrIpRejectRule(
        array: kotlinx.serialization.json.JsonArrayBuilder,
        domains: List<String>,
        cidrs: List<String>
    ) {
        if (domains.isNotEmpty()) {
            array.add(buildJsonObject {
                put("protocol", jsonValues(listOf("quic")))
                put("domain_suffix", jsonValues(domains))
                put("action", "reject")
            })
        }
        if (cidrs.isNotEmpty()) {
            array.add(buildJsonObject {
                put("protocol", jsonValues(listOf("quic")))
                put("ip_cidr", jsonValues(cidrs))
                put("action", "reject")
            })
        }
    }

    private fun addDomainOrIpRule(
        array: kotlinx.serialization.json.JsonArrayBuilder,
        domains: List<String>,
        cidrs: List<String>,
        outbound: String
    ) {
        if (domains.isNotEmpty()) {
            array.add(buildJsonObject {
                put("domain_suffix", jsonValues(domains))
                put("action", "route")
                put("outbound", outbound)
            })
        }
        if (cidrs.isNotEmpty()) {
            array.add(buildJsonObject {
                put("ip_cidr", jsonValues(cidrs))
                put("action", "route")
                put("outbound", outbound)
            })
        }
    }

    private fun jsonValues(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

    private fun parseCustomDnsServer(raw: String): ConfiguredDnsServer? {
        val value = raw.trim().removeSuffix("/")
        if (value.isEmpty()) return null
        if (value.startsWith("https://", ignoreCase = true)) {
            val withoutScheme = value.substringAfter("://")
            val server = withoutScheme.substringBefore('/').trim()
            if (server.isEmpty()) return null
            return ConfiguredDnsServer(
                type = "https",
                server = server,
                path = "/" + withoutScheme.substringAfter('/', "dns-query")
                    .trimStart('/')
                    .ifBlank { "dns-query" },
                needsDomainResolver = !server.isIpAddress()
            )
        }
        return ConfiguredDnsServer(
            type = "udp",
            server = value,
            needsDomainResolver = !value.isIpAddress()
        )
    }

    private fun String.isIpAddress(): Boolean {
        return matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) || contains(":")
    }
}

private data class ConfiguredDnsServer(
    val type: String,
    val server: String,
    val path: String? = null,
    val needsDomainResolver: Boolean = false
)

data class SingBoxConfigSettings(
    val dnsMode: DnsMode = DnsMode.Cloudflare,
    val customDnsServers: List<String> = emptyList(),
    val ipv6Enabled: Boolean = false,
    val inboundMode: InboundMode = InboundMode.Tun,
    val mixedListenHost: String = "127.0.0.1",
    val mixedListenPort: Int = 2080,
    val clashApiPort: Int = 9095,
    val warpEnabled: Boolean = false,
    val warpPrivateKey: String = "",
    val warpLocalAddressV4: String = "",
    val warpLocalAddressV6: String = "",
    val warpPeerPublicKey: String = "",
    val warpEndpoint: String = "",
    val warpReserved: List<Int> = listOf(0, 0, 0),
    val routing: RoutingSettings = RoutingSettings.defaults(),
    val platform: RoutingPlatform = RoutingPlatform.Desktop
)

enum class RoutingPlatform {
    Android,
    Desktop
}
