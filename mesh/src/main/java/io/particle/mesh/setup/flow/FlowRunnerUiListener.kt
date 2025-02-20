package io.particle.mesh.setup.flow

import androidx.lifecycle.LiveData
import io.particle.android.sdk.cloud.ParticleNetwork
import io.particle.firmwareprotos.ctrl.mesh.Mesh
import io.particle.firmwareprotos.ctrl.wifi.WifiNew.ScanNetworksReply.Network
import io.particle.mesh.common.android.livedata.castAndPost
import io.particle.mesh.setup.flow.context.NetworkSetupType
import io.particle.mesh.setup.flow.context.SetupContexts
import io.particle.mesh.setup.flow.context.SetupDevice
import io.particle.mesh.setup.flow.meshsetup.TargetDeviceMeshNetworksScanner
import mu.KotlinLogging


class FlowRunnerUiListener(private val ctxs: SetupContexts) {

    val wifi = WifiData(ctxs)
    val deviceData = DeviceData(ctxs)
    val mesh = MeshData(ctxs)
    val cloud = CloudData(ctxs)
    val cellular = CellularData(ctxs)

    val targetDevice: SetupDevice
        get() = ctxs.targetDevice
    val commissioner: SetupDevice
        get() = ctxs.commissioner

    fun setNetworkSetupType(setupType: NetworkSetupType) {
        ctxs.device.updateNetworkSetupType(setupType)
    }

    fun onGetReadyNextButtonClicked() {
        ctxs.updateGetReadyNextButtonClicked(true)
    }

    fun updateShouldConnectToDeviceCloudConfirmed(confirmed: Boolean) {
        ctxs.cloud.updateShouldConnectToDeviceCloudConfirmed(confirmed)
    }

}

class CellularData(private val ctxs: SetupContexts) {

    val newSelectedDataLimitLD: LiveData<Int?> = ctxs.cellular.newSelectedDataLimitLD
    val popOwnBackStackOnSelectingDataLimit: Boolean
        get() = ctxs.cellular.popOwnBackStackOnSelectingDataLimit

    fun updateNewSelectedDataLimit(newLimit: Int) {
        ctxs.cellular.updateNewSelectedDataLimit(newLimit)
    }

    fun updateChangeSimStatusButtonClicked() {
        ctxs.cellular.updateChangeSimStatusButtonClicked(true)
    }
}


class MeshData(private val ctxs: SetupContexts) {

    val newNetworkIdLD: LiveData<String?> = ctxs.mesh.newNetworkIdLD
    val networkCreatedOnLocalDeviceLD: LiveData<Boolean?> = ctxs.mesh.networkCreatedOnLocalDeviceLD
    val commissionerStartedLD: LiveData<Boolean?> = ctxs.mesh.commissionerStartedLD
    val targetJoinedMeshNetworkLD: LiveData<Boolean?> = ctxs.mesh.targetJoinedMeshNetworkLD
    val showNewNetworkOptionInScanner
        get() = ctxs.mesh.showNewNetworkOptionInScanner

    val currentlyJoinedNetwork: Mesh.NetworkInfo?
        get() = ctxs.mesh.currentlyJoinedNetwork

    fun getTargetDeviceVisibleMeshNetworksLD(): LiveData<List<Mesh.NetworkInfo>?> {
        return TargetDeviceMeshNetworksScanner(ctxs.targetDevice.transceiverLD, ctxs.scopes)
    }

    fun updateMeshNetworkToJoinCommissionerPassword(password: String) {
        ctxs.mesh.updateTargetDeviceMeshNetworkToJoinCommissionerPassword(password)
    }

    fun updateNewNetworkName(name: String) {
        ctxs.mesh.updateNewNetworkName(name)
    }

    fun updateNewNetworkPassword(password: String) {
        ctxs.mesh.updateNewNetworkPassword(password)
    }

    fun updateNetworkSetupType(networkSetupType: NetworkSetupType) {
        ctxs.device.updateNetworkSetupType(networkSetupType)
    }

    fun onUserSelectedCreateNewNetwork() {
        ctxs.mesh.onUserSelectedCreateNewNetwork()
    }

    fun updateSelectedMeshNetworkToJoin(networkInfo: Mesh.NetworkInfo) {
        ctxs.mesh.updateSelectedMeshNetworkToJoin(networkInfo)
    }

}


class DeviceData(private val ctxs: SetupContexts) {

    val networkSetupTypeLD: LiveData<NetworkSetupType?> = ctxs.device.networkSetupTypeLD
    val bleUpdateProgress: LiveData<Int?> = ctxs.device.bleOtaProgress
    var shouldDetectEthernet: Boolean
        get() = ctxs.device.shouldDetectEthernet
        set(value) {
            ctxs.device.shouldDetectEthernet = value
        }
    val firmwareUpdateCount get() = ctxs.device.firmwareUpdateCount

    fun updateUserConsentedToFirmwareUpdate(consented: Boolean) {
        ctxs.device.updateUserConsentedToFirmwareUpdate(consented)
    }

}


class CloudData(private val ctxs: SetupContexts) {

    val pricingImpact
        get() = ctxs.cloud.pricingImpact
    val meshNetworksFromAPI
        get() = ctxs.cloud.apiNetworks

    fun updateTargetDeviceNameToAssign(name: String) {
        ctxs.cloud.updateTargetDeviceNameToAssign(name)
    }

    fun updatePricingImpactConfirmed(confirmed: Boolean) {
        ctxs.cloud.updatePricingImpactConfirmed(confirmed)
    }

}


class WifiData(private val ctxs: SetupContexts) {

    val targetWifiNetworkJoinedLD: LiveData<Boolean?> = ctxs.wifi.targetWifiNetworkJoinedLD

    val wifiNetworkToConfigure: Network?
        get() = ctxs.wifi.targetWifiNetworkLD.value


    fun getWifiScannerForTargetDevice(): LiveData<List<WifiScanData>?> {
        return WifiNetworksScannerLD(
            ctxs.targetDevice.transceiverLD,
            ctxs.scopes
        )
    }

    fun setWifiNetworkToConfigure(network: Network) {
        ctxs.wifi.updateTargetWifiNetwork(network)
    }

    fun setPasswordForWifiNetworkToConfigure(password: String) {
        ctxs.wifi.updateTargetWifiNetworkPassword(password)
    }

}
