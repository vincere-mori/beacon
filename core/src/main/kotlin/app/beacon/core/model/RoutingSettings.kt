package app.beacon.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class RoutingMode {
    ProxyAllExcept,
    DirectAllExcept
}

@Serializable
data class RoutingSettings(
    val mode: RoutingMode = RoutingMode.ProxyAllExcept,
    val exceptionDomains: List<String> = emptyList(),
    val exceptionCidrs: List<String> = emptyList(),
    val warpDomains: List<String> = emptyList(),
    val warpCidrs: List<String> = emptyList(),
    val androidPackages: List<String> = emptyList(),
    val desktopProcesses: List<String> = emptyList(),
    val defaultsInitialized: Boolean = false,
    val proxyDomains: List<String> = emptyList(),
    val directDomains: List<String> = emptyList(),
    val proxyCidrs: List<String> = emptyList(),
    val directCidrs: List<String> = emptyList(),
    val androidPackagesProxy: List<String> = emptyList(),
    val androidPackagesDirect: List<String> = emptyList(),
    val desktopProcessesProxy: List<String> = emptyList(),
    val desktopProcessesDirect: List<String> = emptyList()
) {
    fun normalized(): RoutingSettings {
        val migrated = migrateLegacyIfNeeded()
        return migrated.copy(
            exceptionDomains = migrated.exceptionDomains.normalize(lowercase = true)
                .filterNot { it in builtInLocalDomains() },
            exceptionCidrs = migrated.exceptionCidrs.normalize().filterNot(::isBuiltInLocalCidr),
            warpDomains = migrated.warpDomains.normalize(lowercase = true),
            warpCidrs = migrated.warpCidrs.normalize(),
            androidPackages = migrated.androidPackages.normalize(),
            desktopProcesses = migrated.desktopProcesses.normalize(),
            proxyDomains = emptyList(),
            directDomains = emptyList(),
            proxyCidrs = emptyList(),
            directCidrs = emptyList(),
            androidPackagesProxy = emptyList(),
            androidPackagesDirect = emptyList(),
            desktopProcessesProxy = emptyList(),
            desktopProcessesDirect = emptyList()
        )
    }

    fun ensureDefaults(): RoutingSettings {
        val migrated = migrateLegacyIfNeeded()
        if (migrated.defaultsInitialized) return migrated.normalized()
        return migrated.copy(
            exceptionDomains = if (
                migrated.mode == RoutingMode.ProxyAllExcept &&
                migrated.exceptionDomains.isEmpty()
            ) {
                defaultProxyBypassDomains()
            } else {
                migrated.exceptionDomains
            },
            warpDomains = migrated.warpDomains.ifEmpty(::defaultWarpDomains),
            defaultsInitialized = true
        ).normalized()
    }

    fun asUserConfigured(): RoutingSettings = copy(defaultsInitialized = true).normalized()

    private fun migrateLegacyIfNeeded(): RoutingSettings {
        if (
            exceptionDomains.isNotEmpty() ||
            exceptionCidrs.isNotEmpty() ||
            androidPackages.isNotEmpty() ||
            desktopProcesses.isNotEmpty() ||
            defaultsInitialized
        ) {
            return this
        }

        val hasLegacyDirect = directDomains.isNotEmpty() || directCidrs.isNotEmpty() ||
            androidPackagesDirect.isNotEmpty() || desktopProcessesDirect.isNotEmpty()
        val hasLegacyProxy = proxyDomains.isNotEmpty() || proxyCidrs.isNotEmpty() ||
            androidPackagesProxy.isNotEmpty() || desktopProcessesProxy.isNotEmpty()

        return when {
            hasLegacyDirect && !hasLegacyProxy -> copy(
                mode = RoutingMode.ProxyAllExcept,
                exceptionDomains = directDomains,
                exceptionCidrs = directCidrs,
                androidPackages = androidPackagesDirect,
                desktopProcesses = desktopProcessesDirect
            )
            hasLegacyProxy && !hasLegacyDirect -> copy(
                mode = RoutingMode.DirectAllExcept,
                exceptionDomains = proxyDomains,
                exceptionCidrs = proxyCidrs,
                androidPackages = androidPackagesProxy,
                desktopProcesses = desktopProcessesProxy
            )
            else -> this
        }
    }

    companion object {
        fun defaults(): RoutingSettings = RoutingSettings().ensureDefaults()

        fun defaultProxyBypassDomains(): List<String> = listOf(
            "ru",
            "xn--p1ai",
            "su",
            "vk.com",
            "vk.me",
            "vk.cc",
            "vkuserid.com",
            "vkuservideo.net",
            "mycdn.me",
            "okko.tv",
            "more.tv",
            "rt.com",
            "yandex.com",
            "yandex.net",
            "yandex.by",
            "yandex.kz",
            "ya.ru"
        )

        fun defaultWarpDomains(): List<String> = listOf(
            "google.com",
            "googleapis.com",
            "googleusercontent.com",
            "gstatic.com",
            "ggpht.com",
            "gvt1.com",
            "gvt2.com",
            "gvt3.com",
            "recaptcha.net",
            "youtube.com",
            "youtubei.googleapis.com",
            "googlevideo.com",
            "ytimg.com",
            "ai.google.dev",
            "discord.com",
            "discord.gg",
            "discord.media",
            "discordapp.com",
            "discordapp.net"
        )

        fun builtInLocalDomains(): List<String> = listOf("local")

        fun builtInLocalCidrs(): List<String> = listOf(
            "0.0.0.0/8",
            "10.0.0.0/8",
            "100.64.0.0/10",
            "127.0.0.0/8",
            "169.254.0.0/16",
            "172.16.0.0/12",
            "192.168.0.0/16",
            "::1/128",
            "fc00::/7",
            "fe80::/10"
        )

        fun parseMultiline(text: String, lowercase: Boolean = false): List<String> {
            return text
                .lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .map { if (lowercase) it.lowercase() else it }
                .distinct()
                .toList()
        }

        fun toMultiline(values: List<String>): String = values.joinToString("\n")

        private fun List<String>.normalize(lowercase: Boolean = false): List<String> =
            parseMultiline(joinToString("\n"), lowercase)

        private fun isBuiltInLocalCidr(value: String): Boolean = value in builtInLocalCidrs()
    }
}
