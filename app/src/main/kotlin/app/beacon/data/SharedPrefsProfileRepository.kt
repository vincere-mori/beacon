package app.beacon.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.beacon.core.model.ProxyProfile
import app.beacon.core.model.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SharedPrefsProfileRepository(context: Context) : ProfileRepository {
    private val prefs = context.getSharedPreferences("beacon_profiles", Context.MODE_PRIVATE)
    private val securePrefs = SecurePrefs(prefs)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val profiles = MutableStateFlow(loadProfiles())
    private val subscriptions = MutableStateFlow(loadSubscriptions())
    private val activeProfileId = MutableStateFlow(securePrefs.getString(KEY_ACTIVE_PROFILE))
    private val settings = MutableStateFlow(loadSettings())

    override fun observeProfiles(): Flow<List<ProxyProfile>> = profiles.asStateFlow()

    override fun observeSubscriptions(): Flow<List<Subscription>> = subscriptions.asStateFlow()

    override fun observeActiveProfileId(): Flow<String?> = activeProfileId.asStateFlow()

    override fun observeSettings(): Flow<BeaconSettings> = settings.asStateFlow()

    override suspend fun saveProfile(profile: ProxyProfile) {
        val updated = profiles.value
            .filterNot { it.id == profile.id }
            .plus(profile)
        writeProfiles(updated)
        setActiveProfile(profile.id)
    }

    override suspend fun deleteProfile(profileId: String) {
        val updated = profiles.value.filterNot { it.id == profileId }
        writeProfiles(updated)

        if (activeProfileId.value == profileId) {
            val nextId = updated.firstOrNull()?.id
            if (nextId == null) {
                securePrefs.remove(KEY_ACTIVE_PROFILE)
            } else {
                securePrefs.putString(KEY_ACTIVE_PROFILE, nextId)
            }
            activeProfileId.value = nextId
        }
    }

    override suspend fun saveSubscription(subscription: Subscription) {
        val updated = subscriptions.value
            .filterNot { it.id == subscription.id }
            .plus(subscription)
        writeSubscriptions(updated)
    }

    override suspend fun deleteSubscription(subscriptionId: String) {
        val removed = subscriptions.value.firstOrNull { it.id == subscriptionId }
        val updated = subscriptions.value.filterNot { it.id == subscriptionId }
        writeSubscriptions(updated)

        if (removed?.profiles?.any { it.id == activeProfileId.value } == true) {
            val nextId = profiles.value.firstOrNull()?.id
                ?: updated.flatMap { it.profiles }.firstOrNull()?.id
            if (nextId == null) securePrefs.remove(KEY_ACTIVE_PROFILE)
            else securePrefs.putString(KEY_ACTIVE_PROFILE, nextId)
            activeProfileId.value = nextId
        }
    }

    override suspend fun setActiveProfile(profileId: String) {
        securePrefs.putString(KEY_ACTIVE_PROFILE, profileId)
        activeProfileId.value = profileId
    }

    override suspend fun updateSettings(settings: BeaconSettings) {
        securePrefs.putString(KEY_SETTINGS, json.encodeToString(settings))
        this.settings.value = settings
    }

    override suspend fun currentActiveProfile(): ProxyProfile? {
        val activeId = activeProfileId.value
        val all = profiles.value + subscriptions.value.flatMap { it.profiles }
        return all.firstOrNull { it.id == activeId } ?: all.firstOrNull()
    }

    override suspend fun currentSettings(): BeaconSettings = settings.value

    private fun loadProfiles(): List<ProxyProfile> {
        val raw = securePrefs.getString(KEY_PROFILES) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ProxyProfile>>(raw) }.getOrDefault(emptyList())
    }

    private fun loadSubscriptions(): List<Subscription> {
        val raw = securePrefs.getString(KEY_SUBSCRIPTIONS) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Subscription>>(raw) }.getOrDefault(emptyList())
    }

    private fun writeSubscriptions(value: List<Subscription>) {
        securePrefs.putString(KEY_SUBSCRIPTIONS, json.encodeToString(value))
        subscriptions.value = value
    }

    private fun loadSettings(): BeaconSettings {
        val raw = securePrefs.getString(KEY_SETTINGS) ?: return BeaconSettings()
        val decoded = runCatching {
            json.decodeFromString<BeaconSettings>(raw)
        }.getOrDefault(BeaconSettings())
        return decoded.copy(routing = decoded.routing.ensureDefaults())
    }

    private fun writeProfiles(value: List<ProxyProfile>) {
        securePrefs.putString(KEY_PROFILES, json.encodeToString(value))
        profiles.value = value
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        const val KEY_ACTIVE_PROFILE = "active_profile"
        const val KEY_SETTINGS = "settings"
    }
}

private class SecurePrefs(
    private val prefs: android.content.SharedPreferences
) {
    fun getString(key: String): String? {
        val value = prefs.getString(key, null) ?: return null
        return runCatching { decrypt(value) }
            .getOrElse {
                putString(key, value)
                value
            }
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, encrypt(value)).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val encrypted = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "beacon_profiles_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
