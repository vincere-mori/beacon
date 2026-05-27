package app.beacon.desktop

import app.beacon.core.model.ProxyProfile
import app.beacon.core.model.Subscription
import app.beacon.core.model.DnsMode
import app.beacon.core.singbox.InboundMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

class DesktopProfileStore(
    private val file: Path = DesktopPaths.profilesFile,
    private val secretBox: DesktopSecretBox = DesktopSecretBox(),
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }
) {
    fun load(): DesktopProfileState {
        if (!file.exists()) return DesktopProfileState()
        return runCatching {
            json.decodeFromString<DesktopProfileState>(secretBox.unprotect(Files.readString(file)))
        }.getOrDefault(DesktopProfileState())
    }

    fun save(state: DesktopProfileState) {
        file.parent?.let { Files.createDirectories(it) }
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        tmp.writeText(secretBox.protect(json.encodeToString(state)))
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
}

@Serializable
data class DesktopProfileState(
    val profiles: List<ProxyProfile> = emptyList(),
    val subscriptions: List<Subscription> = emptyList(),
    val activeProfileId: String? = null,
    val dnsMode: DnsMode = DnsMode.Cloudflare,
    val ipv6Enabled: Boolean = false,
    val inboundMode: InboundMode = InboundMode.Mixed,
    val warpEnabled: Boolean = false,
    val warpCredentials: WarpCredentials? = null,
    val trayEnabled: Boolean = true,
    val language: AppLanguage = AppLanguage.RU,
    val trayNoticeShown: Boolean = false
) {
    /** Every server the user has — standalone keys and subscription servers. */
    val allProfiles: List<ProxyProfile>
        get() = profiles + subscriptions.flatMap { it.profiles }

    val activeProfile: ProxyProfile?
        get() = allProfiles.let { all -> all.firstOrNull { it.id == activeProfileId } ?: all.firstOrNull() }
}
