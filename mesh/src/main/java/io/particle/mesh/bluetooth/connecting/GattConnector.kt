package io.particle.mesh.bluetooth.connecting

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import com.snakydesign.livedataextensions.filter
import com.snakydesign.livedataextensions.first
import io.particle.mesh.bluetooth.BLELiveDataCallbacks
import io.particle.mesh.bluetooth.btAdapter
import io.particle.mesh.common.android.SimpleLifecycleOwner
import io.particle.mesh.setup.flow.Scopes
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


private val INITIAL_CONNECTION_TIMEOUT = TimeUnit.SECONDS.toMillis(5)


class GattConnector(private val ctx: Context) {

    private val lifecycleOwner = SimpleLifecycleOwner()

    private val log = KotlinLogging.logger {}

    suspend fun createGattConnection(
            device: BluetoothDevice,
            scopes: Scopes
    ): Pair<BluetoothGatt, BLELiveDataCallbacks>? {
        lifecycleOwner.setNewState(Lifecycle.State.RESUMED)

        this.ctx.btAdapter.cancelDiscovery()

        val callbacks = BLELiveDataCallbacks()
        val gatt = try {
            scopes.withMain(INITIAL_CONNECTION_TIMEOUT) {
                doCreateGattConnection(device, ctx, callbacks)
            }
        } catch (ex: Exception) {
            return null
        }

        try {
            return Pair(gatt, callbacks)
        } finally {
            lifecycleOwner.setNewState(Lifecycle.State.DESTROYED)
            callbacks.connectionStateChangedLD.removeObservers(lifecycleOwner)
        }
    }

    private suspend fun doCreateGattConnection(
            device: BluetoothDevice,
            ctx: Context,
            callbacks: BLELiveDataCallbacks
    ): BluetoothGatt {
        return suspendCoroutine { continuation: Continuation<BluetoothGatt> ->
            doCreateGattConnection(device, ctx, callbacks) { continuation.resume(it) }
        }
    }


    private fun doCreateGattConnection(
            device: BluetoothDevice,
            ctx: Context,
            liveDataCallbacks: BLELiveDataCallbacks,
            callback: (BluetoothGatt) -> Unit
    ) {
        log.info { "About to connect to $device" }
        val gattRef = device.connectGatt(ctx.applicationContext, false, liveDataCallbacks)
        log.info { "Called connectGatt for $gattRef" }
        liveDataCallbacks.connectionStateChangedLD
            .filter { it == ConnectionState.CONNECTED }
            .first()
            .observe(lifecycleOwner, Observer {
                log.debug { "Connection state updated to $it" }
                if (it == ConnectionState.CONNECTED) {
                    callback(gattRef)
                } else {
                    log.error { "Connection status not CONNECTED?! it=$it" }
                }
            })
    }

}