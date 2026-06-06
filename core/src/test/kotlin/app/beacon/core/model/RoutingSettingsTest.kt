package app.beacon.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutingSettingsTest {
    @Test
    fun defaultsPreserveExistingBypassAndWarpPolicies() {
        val settings = RoutingSettings.defaults()

        assertEquals(RoutingMode.ProxyAllExcept, settings.mode)
        assertTrue("ru" in settings.exceptionDomains)
        assertTrue("google.com" in settings.warpDomains)
        assertTrue(settings.defaultsInitialized)
    }

    @Test
    fun userCanSaveEmptyListsWithoutDefaultsReturning() {
        val settings = RoutingSettings.defaults()
            .copy(exceptionDomains = emptyList(), warpDomains = emptyList())
            .asUserConfigured()

        assertTrue(settings.ensureDefaults().exceptionDomains.isEmpty())
        assertTrue(settings.ensureDefaults().warpDomains.isEmpty())
    }

    @Test
    fun migratesLegacyDirectRules() {
        val settings = RoutingSettings(
            directDomains = listOf("example.org"),
            androidPackagesDirect = listOf("org.example.app")
        ).ensureDefaults()

        assertEquals(RoutingMode.ProxyAllExcept, settings.mode)
        assertEquals(listOf("example.org"), settings.exceptionDomains)
        assertEquals(listOf("org.example.app"), settings.androidPackages)
    }
}
