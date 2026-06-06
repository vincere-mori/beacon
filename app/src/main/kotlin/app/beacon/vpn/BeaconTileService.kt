package app.beacon.vpn

import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.beacon.MainActivity
import app.beacon.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BeaconTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        when (BeaconVpnEvents.state.value.status) {
            VpnStatus.Connected, VpnStatus.Connecting -> scope.launch {
                AndroidVpnController.disconnect(applicationContext)
                updateTile()
            }
            VpnStatus.Disconnected, VpnStatus.Disconnecting -> {
                if (VpnService.prepare(this) != null) {
                    openAppForConnection()
                } else {
                    scope.launch {
                        runCatching {
                            AndroidVpnController.connect(applicationContext)
                        }.onFailure {
                            openAppForConnection()
                        }
                        updateTile()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_beacon_tile)
        when (BeaconVpnEvents.state.value.status) {
            VpnStatus.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Beacon"
                setSubtitle(tile, "VPN включён")
            }
            VpnStatus.Connecting -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Beacon"
                setSubtitle(tile, "Подключение")
            }
            VpnStatus.Disconnecting -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.label = "Beacon"
                setSubtitle(tile, "Отключение")
            }
            VpnStatus.Disconnected -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Beacon"
                setSubtitle(tile, "VPN выключен")
            }
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppForConnection() {
        val intent = MainActivity.connectFromTileIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    1001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun setSubtitle(tile: Tile, value: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = value
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            requestListeningState(
                context,
                android.content.ComponentName(context, BeaconTileService::class.java)
            )
        }
    }
}
