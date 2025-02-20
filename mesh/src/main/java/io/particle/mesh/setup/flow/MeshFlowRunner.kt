package io.particle.mesh.setup.flow

import android.app.Application
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import com.snakydesign.livedataextensions.liveDataOf
import com.squareup.okhttp.OkHttpClient
import io.particle.android.sdk.cloud.ParticleCloud
import io.particle.android.sdk.cloud.ParticleDevice
import io.particle.mesh.bluetooth.connecting.BluetoothConnectionManager
import io.particle.mesh.common.android.livedata.awaitUpdate
import io.particle.mesh.common.android.livedata.castAndPost
import io.particle.mesh.common.android.livedata.nonNull
import io.particle.mesh.ota.FirmwareUpdateManager
import io.particle.mesh.setup.BarcodeData.CompleteBarcodeData
import io.particle.mesh.setup.connection.ProtocolTransceiverFactory
import io.particle.mesh.setup.connection.security.SecurityManager
import io.particle.mesh.setup.flow.context.SetupContexts
import kotlinx.coroutines.Job
import mu.KotlinLogging


fun buildFlowManager(
    app: Application,
    cloud: ParticleCloud,
    dialogTool: DialogTool,
    flowUi: FlowUiDelegate,
    flowTerminator: MeshFlowTerminator,
    okHttpClient: OkHttpClient = OkHttpClient(),
    securityManager: SecurityManager = SecurityManager()
): MeshFlowRunner {
    val btConMan = BluetoothConnectionManager(app)
    val transceiverFactory = ProtocolTransceiverFactory(securityManager)
    val deviceConnector = DeviceConnector(cloud, btConMan, transceiverFactory)

    val fwUpdateManager = FirmwareUpdateManager(cloud, okHttpClient)

    val deps = StepDeps(
        cloud,
        deviceConnector,
        fwUpdateManager,
        dialogTool,
        flowUi
    )

    return MeshFlowRunner(deps, app, flowTerminator)
}


class MeshFlowTerminator {

    val shouldTerminateFlowLD: LiveData<Boolean> = liveDataOf(false)

    fun terminateFlow() {
        shouldTerminateFlowLD.castAndPost(true)
    }
}


enum class FlowIntent {
    FIRST_TIME_SETUP,
    SINGLE_TASK_FLOW
}


enum class FlowType {
    PREFLOW,
    JOINER_FLOW,
    INTERNET_CONNECTED_PREFLOW,
    ETHERNET_FLOW,
    WIFI_FLOW,
    CELLULAR_FLOW,
    NETWORK_CREATOR_POSTFLOW,
    STANDALONE_POSTFLOW,
    CONTROL_PANEL_CELLULAR_PRESENT_OPTIONS_FLOW,
    CONTROL_PANEL_CELLULAR_SET_NEW_DATA_LIMIT,
    CONTROL_PANEL_CELLULAR_SIM_DEACTIVATE,
    CONTROL_PANEL_CELLULAR_SIM_REACTIVATE,
    CONTROL_PANEL_CELLULAR_SIM_UNPAUSE,
    CONTROL_PANEL_CELLULAR_SIM_ACTION_POSTFLOW,
    CONTROL_PANEL_MESH_INSPECT_NETWORK_FLOW,
    CONTROL_PANEL_MESH_LEAVE_NETWORK_FLOW,
    CONTROL_PANEL_WIFI_INSPECT_NETWORK_FLOW,
    SINGLE_TASK_POSTFLOW
}


class MeshFlowRunner(
    private val deps: StepDeps,
    private val everythingNeedsAContext: Application,
    flowTerminator: MeshFlowTerminator
) {

    private val log = KotlinLogging.logger {}

    // set up a placeholder listener that will be overwritten when a new flow starts
    val listener: FlowRunnerUiListener
        get() = flowExecutor.listener

    private val flowExecutor = MeshFlowExecutor(deps, flowTerminator)


    @MainThread
    fun startFlow() {
        log.info { "startFlow()" }
        flowExecutor.executeNewFlow(FlowIntent.FIRST_TIME_SETUP, listOf(FlowType.PREFLOW))
    }

    @MainThread
    fun startNewFlowWithCommissioner() {
        log.info { "startNewFlowWithCommissioner()" }

//        val oldContexts = contexts!!
        flowExecutor.executeNewFlow(FlowIntent.FIRST_TIME_SETUP, listOf(FlowType.PREFLOW))
//        val newContexts = contexts!!
    }

    @MainThread
    fun startControlPanelWifiConfigFlow(device: ParticleDevice, barcode: CompleteBarcodeData) {
        val scopes = Scopes()
        scopes.onMain {
            val ctxs = initContextWithDeviceIdAndBarcode(device, barcode, scopes)

            ctxs.cloud.updatePricingImpactConfirmed(true)
            ctxs.cloud.updateShouldConnectToDeviceCloudConfirmed(true)
            val deviceName = ctxs.targetDevice.transceiverLD.value?.bleBroadcastName
            ctxs.singleStepCongratsMessage = "Wi-Fi credentials were successfully added to $deviceName"

            flowExecutor.executeNewFlow(
                FlowIntent.SINGLE_TASK_FLOW,
                listOf(FlowType.PREFLOW, FlowType.WIFI_FLOW),
                ctxs
            )
        }
    }

    @MainThread
    fun startControlPanelInspectCurrentWifiNetworkFlow(
        device: ParticleDevice,
        barcode: CompleteBarcodeData
    ) {
        val scopes = Scopes()
        scopes.onMain {
            val ctxs = initContextWithDeviceIdAndBarcode(device, barcode, scopes)

            flowExecutor.executeNewFlow(
                FlowIntent.SINGLE_TASK_FLOW,
                listOf(FlowType.PREFLOW, FlowType.CONTROL_PANEL_WIFI_INSPECT_NETWORK_FLOW),
                ctxs
            )
        }
    }

    @MainThread
    fun startShowControlPanelCellularOptionsFlow(device: ParticleDevice) {
        val ctxs = initContextForSimFlow(device)

        flowExecutor.executeNewFlow(
            FlowIntent.SINGLE_TASK_FLOW,
            listOf(FlowType.CONTROL_PANEL_CELLULAR_PRESENT_OPTIONS_FLOW),
            ctxs
        )
    }

    @MainThread
    fun startControlPanelMeshInspectCurrentNetworkFlow(
        device: ParticleDevice,
        barcode: CompleteBarcodeData
    ) {
        val scopes = Scopes()
        scopes.onMain {
            val ctxs = initContextWithDeviceIdAndBarcode(device, barcode, scopes)

            flowExecutor.executeNewFlow(
                FlowIntent.SINGLE_TASK_FLOW,
                listOf(FlowType.PREFLOW, FlowType.CONTROL_PANEL_MESH_INSPECT_NETWORK_FLOW),
                ctxs
            )
        }
    }

    @MainThread
    fun startSimDeactivateFlow(device: ParticleDevice) {
        val ctxs = initContextForSimFlow(device)
        // FIXME: review this text with David, et al
        ctxs.snackbarMessage = "SIM deactivation requested"

        val flows = listOf(
            FlowType.CONTROL_PANEL_CELLULAR_SIM_DEACTIVATE,
            FlowType.CONTROL_PANEL_CELLULAR_SIM_ACTION_POSTFLOW
        )
        ctxs.scopes.onMain {
            flowExecutor.executeNewFlow(FlowIntent.SINGLE_TASK_FLOW, flows, ctxs)
        }
    }

    @MainThread
    fun startSimReactivateFlow(device: ParticleDevice) {
        val ctxs = initContextForSimFlow(device)
        // FIXME: review this text with David, et al
        ctxs.snackbarMessage = "SIM reactivation requested"

        val flows = listOf(
            FlowType.CONTROL_PANEL_CELLULAR_SIM_REACTIVATE,
            FlowType.CONTROL_PANEL_CELLULAR_SIM_ACTION_POSTFLOW
        )
        ctxs.scopes.onMain {
            flowExecutor.executeNewFlow(FlowIntent.SINGLE_TASK_FLOW, flows, ctxs)
        }
    }

    @MainThread
    fun startSimUnpauseFlow(device: ParticleDevice) {
        val ctxs = initContextForSimFlow(device)
        ctxs.snackbarMessage = "SIM unpause requested"
        ctxs.cellular.popOwnBackStackOnSelectingDataLimit = true

        val flows = listOf(
            FlowType.CONTROL_PANEL_CELLULAR_SIM_UNPAUSE,
            FlowType.CONTROL_PANEL_CELLULAR_SIM_ACTION_POSTFLOW
        )

        ctxs.scopes.onWorker {
            deps.flowUi.showGlobalProgressSpinner(true)
            ctxs.targetDevice.dataUsedInMB = device.getCurrentDataUsage()

            ctxs.scopes.onMain {
                flowExecutor.executeNewFlow(FlowIntent.SINGLE_TASK_FLOW, flows, ctxs)
            }
        }
    }

    @MainThread
    fun startSetNewDataLimitFlow(device: ParticleDevice) {
        val ctxs = initContextForSimFlow(device)

        ctxs.scopes.onWorker {
            deps.flowUi.showGlobalProgressSpinner(true)
            ctxs.targetDevice.dataUsedInMB = device.getCurrentDataUsage()

            ctxs.scopes.onMain {
                flowExecutor.executeNewFlow(
                    FlowIntent.SINGLE_TASK_FLOW,
                    listOf(FlowType.CONTROL_PANEL_CELLULAR_SET_NEW_DATA_LIMIT),
                    ctxs
                )
            }
        }
    }

    @MainThread
    private suspend fun initContextWithDeviceIdAndBarcode(
        device: ParticleDevice,
        barcode: CompleteBarcodeData,
        scopes: Scopes
    ): SetupContexts {

        val ctxs = SetupContexts(scopes)
        ctxs.updateGetReadyNextButtonClicked(true)

        ctxs.targetDevice.deviceId = device.id

        ctxs.scopes.onMain {
            val onWorker: Job = ctxs.scopes.onWorker {
                deps.flowUi.showGlobalProgressSpinner(true)
                ctxs.targetDevice.updateBarcode(barcode, deps.cloud)
                ctxs.targetDevice.barcode.nonNull(ctxs.scopes).awaitUpdate(ctxs.scopes)
            }
            onWorker.join()
        }

        return ctxs
    }

    private fun initContextForSimFlow(device: ParticleDevice): SetupContexts {
        // get any current context to grab the SIM
        val sim = flowExecutor.contexts?.targetDevice?.sim

        return SetupContexts().apply {
            updateGetReadyNextButtonClicked(true)
            targetDevice.deviceId = device.id
            targetDevice.iccid = device.iccid
            targetDevice.sim = sim
        }
    }

    // FIXME: disambiguate this vs ending the current *flow*
    fun endSetup() {
        flowExecutor.endSetup()
    }

    fun endCurrentFlow() {
        log.info { "endCurrentFlow()" }
        flowExecutor.contexts?.let {
            log.info { "Clearing state and cancelling scopes..." }
            it.clearState()
        }
    }

    fun getString(@StringRes stringRes: Int): String {
        return everythingNeedsAContext.getString(stringRes)
    }

    fun getString(@StringRes stringRes: Int, vararg formatArgs: String): String {
        return everythingNeedsAContext.getString(stringRes, formatArgs)
    }

}