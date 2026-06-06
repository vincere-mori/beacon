package app.beacon.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.system.OsConstants
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.annotation.RequiresApi
import app.beacon.BuildConfig
import app.beacon.R
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification as BoxNotification
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import io.nekohasekai.libbox.NetworkInterface as BoxNetworkInterface

class BeaconVpnService : VpnService(), PlatformInterface, CommandServerHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private val transitionMutex = Mutex()
    private var commandServer: CommandServer? = null
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val interfaceMonitorListeners = linkedSetOf<InterfaceUpdateListener>()
    private var lastDefaultInterfaceState: DefaultInterfaceState? = null
    private val connectivity by lazy {
        getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val config = intent.getStringExtra(EXTRA_CONFIG).orEmpty()
                scope.launch { connect(config) }
            }
            ACTION_DISCONNECT -> {
                scope.launch { disconnect() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent) = super.onBind(intent)

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        unregisterNetworkCallback()
        // Clean up resources synchronously before cancelling the scope so
        // the coroutines launched in onStartCommand always get a chance to finish.
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        runCatching { tunDescriptor?.close() }
        tunDescriptor = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        scope.launch { disconnect() }
        super.onRevoke()
    }

    private suspend fun connect(configJson: String) = transitionMutex.withLock {
        if (configJson.isBlank()) {
            disconnectInternal("конфиг пустой")
            return@withLock
        }

        updateState(VpnConnectionState(VpnStatus.Connecting))
        registerNetworkCallbackIfNeeded()
        withContext(Dispatchers.Main) {
            showForeground("Подключение")
        }

        runCatching {
            ensureLibbox()
            val server = commandServer ?: CommandServer(this, this).also {
                it.start()
                commandServer = it
            }
            server.startOrReloadService(configJson, OverrideOptions())
        }.onSuccess {
            updateState(VpnConnectionState(VpnStatus.Connected))
            withContext(Dispatchers.Main) {
                showForeground("Подключено")
            }
        }.onFailure {
            disconnectInternal(it.message ?: "не удалось подключиться")
        }
    }

    private suspend fun disconnect(error: String? = null) = transitionMutex.withLock {
        disconnectInternal(error)
    }

    private suspend fun disconnectInternal(error: String? = null) {
        updateState(VpnConnectionState(VpnStatus.Disconnecting))

        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null

        runCatching { tunDescriptor?.close() }
        tunDescriptor = null

        withContext(Dispatchers.Main) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        updateState(VpnConnectionState(VpnStatus.Disconnected, error))
    }

    private fun ensureLibbox() {
        if (initialized.get()) return

        synchronized(initialized) {
            if (initialized.get()) return

            filesDir.mkdirs()
            cacheDir.mkdirs()
            getExternalFilesDir(null)?.mkdirs()

            val stderr = File(cacheDir, "sing-box-stderr.log").apply {
                if (exists()) delete()
                createNewFile()
            }

            Libbox.setup(
                SetupOptions().apply {
                    basePath = filesDir.path
                    workingPath = externalCacheDir?.path ?: filesDir.path
                    tempPath = cacheDir.path
                    debug = BuildConfig.DEBUG
                    fixAndroidStack = true
                    logMaxLines = 500
                }
            )
            Libbox.redirectStderr(stderr.path)
            Libbox.setMemoryLimit(true)
            initialized.set(true)
        }
    }

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("android: missing vpn permission")

        tunDescriptor?.close()
        val builder = Builder()
            .setSession("Beacon")
            .setMtu(options.mtu.takeIf { it > 0 } ?: 9000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val includePackages = options.includePackage.toList()
        val excludePackages = options.excludePackage.toList()
        val inet4Addresses = options.inet4Address.toList()
        val inet6Addresses = options.inet6Address.toList()
        val inet4Routes = options.inet4RouteRange.toList()
        val inet6Routes = options.inet6RouteRange.toList()

        inet4Addresses.addAddresses(builder)
        inet6Addresses.addAddresses(builder)

        if (options.autoRoute) {
            runCatching { builder.addDnsServer(options.dnsServerAddress.value) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val inet4RouteAddresses = options.inet4RouteAddress.toList()
                val inet6RouteAddresses = options.inet6RouteAddress.toList()
                val inet4RouteExcludes = options.inet4RouteExcludeAddress.toList()
                val inet6RouteExcludes = options.inet6RouteExcludeAddress.toList()

                if (inet4RouteAddresses.isEmpty()) {
                    builder.addRoute(IpPrefix(InetAddress.getByName("0.0.0.0"), 0))
                } else {
                    inet4RouteAddresses.forEach { builder.addRoute(it.toIpPrefix()) }
                }
                if (inet6RouteAddresses.isEmpty() && inet6Addresses.isNotEmpty()) {
                    builder.addRoute(IpPrefix(InetAddress.getByName("::"), 0))
                } else {
                    inet6RouteAddresses.forEach { builder.addRoute(it.toIpPrefix()) }
                }
                inet4RouteExcludes.forEach { builder.excludeRoute(it.toIpPrefix()) }
                inet6RouteExcludes.forEach { builder.excludeRoute(it.toIpPrefix()) }
            } else {
                inet4Routes.addRoutes(builder)
                inet6Routes.addRoutes(builder)
                if (inet4Routes.isEmpty()) {
                    builder.addRoute("0.0.0.0", 0)
                }
                if (inet6Addresses.isNotEmpty() && inet6Routes.isEmpty()) {
                    builder.addRoute("::", 0)
                }
            }
        }

        if (includePackages.isNotEmpty()) {
            includePackages.addAllowedPackages(builder)
        } else {
            (excludePackages + packageName).distinct().addDisallowedPackages(builder)
        }

        val descriptor = builder.establish()
            ?: error("android: vpn permission revoked")
        tunDescriptor = descriptor
        return descriptor.fd
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun clearDNSCache() = Unit
    override fun localDNSTransport(): LocalDNSTransport = AndroidLocalDnsTransport
    override fun readWIFIState(): WIFIState? = null

    @Suppress("DEPRECATION")
    override fun getInterfaces(): NetworkInterfaceIterator {
        val javaInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val boxInterfaces = connectivity.allNetworks.mapNotNull { network ->
            val link = connectivity.getLinkProperties(network) ?: return@mapNotNull null
            val caps = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            val name = link.interfaceName ?: return@mapNotNull null
            val javaInterface = javaInterfaces.firstOrNull { it.name == name } ?: return@mapNotNull null

            BoxNetworkInterface().apply {
                this.name = name
                index = javaInterface.index
                mtu = runCatching { javaInterface.mtu }.getOrDefault(1500)
                type = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                    else -> Libbox.InterfaceTypeOther
                }
                dnsServer = BoxStringIterator(link.dnsServers.mapNotNull { it.hostAddress })
                addresses = BoxStringIterator(javaInterface.interfaceAddresses.map { it.toPrefix() })
                flags = buildInterfaceFlags(javaInterface, caps)
                metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        }

        return BoxNetworkInterfaceIterator(boxInterfaces)
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner {
        val owner = ConnectionOwner()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return owner

        val uid = runCatching {
            connectivity.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort)
            )
        }.getOrDefault(Process.INVALID_UID)

        if (uid != Process.INVALID_UID) {
            owner.userId = uid
            owner.androidPackageName = packageManager.getPackagesForUid(uid)?.firstOrNull().orEmpty()
        }

        return owner
    }

    override fun systemCertificates(): StringIterator {
        val certificates = mutableListOf<String>()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null, null)
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                val cert = keyStore.getCertificate(aliases.nextElement())
                val body = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
                certificates += "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----"
            }
        }
        return BoxStringIterator(certificates)
    }

    override fun sendNotification(notification: BoxNotification) {
        Log.d(TAG, "${notification.typeName}: ${notification.body}")
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        synchronized(interfaceMonitorListeners) {
            interfaceMonitorListeners += listener
        }
        registerNetworkCallbackIfNeeded()
        dispatchDefaultInterfaceUpdate(force = true)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        synchronized(interfaceMonitorListeners) {
            interfaceMonitorListeners -= listener
        }
    }

    override fun getSystemProxyStatus(): SystemProxyStatus {
        return SystemProxyStatus().apply {
            available = false
            enabled = false
        }
    }

    override fun setSystemProxyEnabled(isEnabled: Boolean) = Unit
    override fun serviceReload() = Unit
    override fun serviceStop() {
        scope.launch { disconnect() }
    }

    override fun writeDebugMessage(message: String?) {
        if (!message.isNullOrBlank()) Log.d(TAG, message)
    }

    private fun registerNetworkCallbackIfNeeded() {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = dispatchDefaultInterfaceUpdate()
            override fun onLost(network: Network) = dispatchDefaultInterfaceUpdate(force = true)
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = dispatchDefaultInterfaceUpdate()

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties
            ) = dispatchDefaultInterfaceUpdate()
        }
        runCatching {
            connectivity.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private fun dispatchDefaultInterfaceUpdate(force: Boolean = false) {
        val resolved = chooseBestDefaultNetwork()
        currentDefaultNetwork = resolved?.network
        val listeners = synchronized(interfaceMonitorListeners) {
            interfaceMonitorListeners.toList()
        }
        val state = resolved?.state ?: return
        if (!force && state == lastDefaultInterfaceState) return
        lastDefaultInterfaceState = state
        listeners.forEach { listener ->
            runCatching {
                listener.updateDefaultInterface(
                    state.name,
                    state.index,
                    state.isExpensive,
                    state.isConstrained
                )
            }
        }
    }

    private fun chooseBestDefaultNetwork(): ResolvedDefaultInterface? {
        val activeNetwork = runCatching { connectivity.activeNetwork }.getOrNull()
        return connectivity.allNetworks
            .mapNotNull { network ->
                val caps = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
                val link = connectivity.getLinkProperties(network) ?: return@mapNotNull null
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return@mapNotNull null
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return@mapNotNull null
                }
                val name = link.interfaceName ?: return@mapNotNull null
                val networkInterface = runCatching {
                    NetworkInterface.getByName(name)
                }.getOrNull() ?: return@mapNotNull null
                if (networkInterface.index <= 0) return@mapNotNull null
                val score =
                    (if (network == activeNetwork) 100 else 0) +
                        (if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 10 else 0) +
                        (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) 3 else 0) +
                        (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) 2 else 0)
                ResolvedDefaultInterface(
                    network = network,
                    score = score,
                    state = DefaultInterfaceState(
                        name = name,
                        index = networkInterface.index,
                        isExpensive = !caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_NOT_METERED
                        ),
                        isConstrained = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            !caps.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED
                            )
                        } else {
                            false
                        }
                    )
                )
            }
            .maxByOrNull(ResolvedDefaultInterface::score)
    }

    private fun buildInterfaceFlags(
        networkInterface: NetworkInterface,
        capabilities: NetworkCapabilities
    ): Int {
        var result = 0
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            result = result or OsConstants.IFF_UP or OsConstants.IFF_RUNNING
        }
        if (runCatching { networkInterface.isLoopback }.getOrDefault(false)) {
            result = result or OsConstants.IFF_LOOPBACK
        }
        if (runCatching { networkInterface.isPointToPoint }.getOrDefault(false)) {
            result = result or OsConstants.IFF_POINTOPOINT
        }
        if (runCatching { networkInterface.supportsMulticast() }.getOrDefault(false)) {
            result = result or OsConstants.IFF_MULTICAST
        }
        return result
    }

    private fun updateState(state: VpnConnectionState) {
        BeaconVpnEvents.update(state)
        BeaconTileService.requestUpdate(this)
    }

    private fun showForeground(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Beacon",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Beacon")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun io.nekohasekai.libbox.RoutePrefixIterator.toList(): List<io.nekohasekai.libbox.RoutePrefix> {
        val values = mutableListOf<io.nekohasekai.libbox.RoutePrefix>()
        while (hasNext()) values += next()
        return values
    }

    private fun List<io.nekohasekai.libbox.RoutePrefix>.addAddresses(builder: Builder) {
        forEach { builder.addAddress(it.address(), it.prefix()) }
    }

    private fun List<io.nekohasekai.libbox.RoutePrefix>.addRoutes(builder: Builder) {
        forEach { builder.addRoute(it.address(), it.prefix()) }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun io.nekohasekai.libbox.RoutePrefix.toIpPrefix(): IpPrefix {
        return IpPrefix(InetAddress.getByName(address()), prefix())
    }

    private fun StringIterator.toList(): List<String> {
        val values = mutableListOf<String>()
        while (hasNext()) values += next()
        return values
    }

    private fun Iterable<String>.addAllowedPackages(builder: Builder) {
        forEach { runCatching { builder.addAllowedApplication(it) } }
    }

    private fun Iterable<String>.addDisallowedPackages(builder: Builder) {
        forEach { runCatching { builder.addDisallowedApplication(it) } }
    }

    private fun InterfaceAddress.toPrefix(): String {
        return if (address is Inet6Address) {
            "${Inet6Address.getByAddress(address.address).hostAddress}/$networkPrefixLength"
        } else {
            "${address.hostAddress}/$networkPrefixLength"
        }
    }

    companion object {
        private const val TAG = "BeaconVpnService"
        private const val CHANNEL_ID = "beacon_vpn"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_CONNECT = "app.beacon.vpn.CONNECT"
        private const val ACTION_DISCONNECT = "app.beacon.vpn.DISCONNECT"
        private const val EXTRA_CONFIG = "config"
        @Volatile
        private var activeInstance: BeaconVpnService? = null
        @Volatile
        private var currentDefaultNetwork: Network? = null

        internal fun currentUnderlyingNetwork(): Network? {
            val instance = activeInstance ?: return currentDefaultNetwork
            return instance.chooseBestDefaultNetwork()?.network ?: currentDefaultNetwork
        }

        fun connectIntent(context: Context, configJson: String): Intent {
            return Intent(context, BeaconVpnService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_CONFIG, configJson)
        }

        fun disconnectIntent(context: Context): Intent {
            return Intent(context, BeaconVpnService::class.java)
                .setAction(ACTION_DISCONNECT)
        }
    }

    private data class DefaultInterfaceState(
        val name: String,
        val index: Int,
        val isExpensive: Boolean,
        val isConstrained: Boolean
    )

    private data class ResolvedDefaultInterface(
        val network: Network,
        val score: Int,
        val state: DefaultInterfaceState
    )
}
