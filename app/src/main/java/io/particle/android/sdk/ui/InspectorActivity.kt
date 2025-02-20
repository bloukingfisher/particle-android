package io.particle.android.sdk.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayout
import io.particle.android.sdk.cloud.ParticleCloudSDK
import io.particle.android.sdk.cloud.ParticleDevice
import io.particle.android.sdk.cloud.ParticleDevice.ParticleDeviceType.ARGON
import io.particle.android.sdk.cloud.ParticleDevice.ParticleDeviceType.A_SOM
import io.particle.android.sdk.cloud.ParticleDevice.ParticleDeviceType.BORON
import io.particle.android.sdk.cloud.ParticleDevice.ParticleDeviceType.B_SOM
import io.particle.android.sdk.cloud.ParticleDevice.ParticleDeviceType.XENON
import io.particle.android.sdk.cloud.ParticleDevice.ParticleDeviceType.X_SOM
import io.particle.android.sdk.cloud.ParticleEventVisibility
import io.particle.android.sdk.cloud.exceptions.ParticleCloudException
import io.particle.android.sdk.cloud.models.DeviceStateChange
import io.particle.android.sdk.utils.Async
import io.particle.android.sdk.utils.ui.Ui
import io.particle.commonui.DeviceInfoBottomSheetController
import io.particle.mesh.setup.flow.Scopes
import io.particle.mesh.ui.controlpanel.ControlPanelActivity
import io.particle.sdk.app.R
import mu.KotlinLogging
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe


private const val EXTRA_DEVICE = "EXTRA_DEVICE"


/** An activity representing the Inspector screen for a Device */
class InspectorActivity : BaseActivity() {

    companion object {

        fun buildIntent(ctx: Context, device: ParticleDevice): Intent {
            return Intent(ctx, InspectorActivity::class.java)
                .putExtra(EXTRA_DEVICE, device)
        }
    }

    private val log = KotlinLogging.logger {}

    private val syncStatus = object : Runnable {
        override fun run() {
            invalidateOptionsMenu()
            handler.postDelayed(this, 1000 * 60L)
        }
    }

    private lateinit var device: ParticleDevice
    private val cloud = ParticleCloudSDK.getCloud()
    private val handler = Handler()

    private lateinit var deviceInfoController: DeviceInfoBottomSheetController

    private val scopes = Scopes()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inspector)

        device = intent.getParcelableExtra(EXTRA_DEVICE)
        
        // Show the Up button in the action bar.
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        title = device.name

        setupInspectorPages()
        handler.postDelayed(syncStatus, 1000 * 60L)

        deviceInfoController = DeviceInfoBottomSheetController(
            this,
            scopes,
            findViewById(R.id.device_info_bottom_sheet),
            device
        )
        deviceInfoController.initializeBottomSheet()
    }

    public override fun onResume() {
        super.onResume()
        EventBus.getDefault().register(this)
        try {
            device.subscribeToSystemEvents()
        } catch (ignore: ParticleCloudException) {
            //minor issue if we don't update online/offline states
        }

        scopes.onWorker {
            val owned = cloud.userOwnsDevice(device.id)
            if (!owned) {
                scopes.onMain { finish() }
            } else {
                device.refresh()
            }
        }
    }

    public override fun onPause() {
        EventBus.getDefault().unregister(this)
        try {
            device.unsubscribeFromSystemEvents()
        } catch (ignore: ParticleCloudException) {
        }

//        action_signal_device.isChecked = false

        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        scopes.cancelAll()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            android.R.id.home -> finish()
//            R.id.action_event_publish -> presentPublishDialog()
            R.id.action_launchcontrol_panel -> {
                startActivity(ControlPanelActivity.buildIntent(this, device))
            }
            else -> {
                val actionId = item.itemId

                return DeviceActionsHelper.takeActionForDevice(actionId, this, device) ||
                        DeviceMenuUrlHandler.handleActionItem(this, actionId, item.title) ||
                        super.onOptionsItemSelected(item)
            }
        }

        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        val menuRes = when (device.deviceType) {
            ARGON, A_SOM, BORON, B_SOM, XENON, X_SOM -> R.menu.inspector_gen3
            else -> R.menu.inspector
        }

        menuInflater.inflate(menuRes, menu)

        return true
    }

    override fun onBackPressed() {
        if (deviceInfoController.sheetBehaviorState == BottomSheetBehavior.STATE_EXPANDED) {
            deviceInfoController.sheetBehaviorState = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            super.onBackPressed()
        }
    }

    @Subscribe
    fun onEvent(device: ParticleDevice) {
        //update device and UI
        //TODO update more fields
        this.device = device
        runOnUiThread {
            title = device.name
            deviceInfoController.updateDeviceDetails()
        }
    }

    @Subscribe
    fun onEvent(deviceStateChange: DeviceStateChange) {
        //reload menu to display online/offline
        invalidateOptionsMenu()
        runOnUiThread {
            deviceInfoController.updateDeviceDetails()
        }
    }

    private fun setupInspectorPages() {
        val viewPager = findViewById<ViewPager>(R.id.viewPager)
        viewPager.adapter = InspectorPager(supportFragmentManager, device)
        viewPager.offscreenPageLimit = 3
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.setupWithViewPager(viewPager)
        //hiding keyboard on tab changed
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {}

            override fun onPageScrollStateChanged(state: Int) {
                val view = currentFocus
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (view != null && imm != null) {
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        })
    }


    private fun presentPublishDialog() {
        val publishDialogView = View.inflate(this, R.layout.publish_event, null)

        AlertDialog.Builder(
            this,
            R.style.ParticleSetupTheme_DialogNoDimBackground
        )
            .setView(publishDialogView)
            .setPositiveButton(R.string.publish_positive_action) { _, _ ->
                val nameView = Ui.findView<TextView>(publishDialogView, R.id.eventName)
                val valueView = Ui.findView<TextView>(publishDialogView, R.id.eventValue)
                val privateEventRadio: RadioButton =
                    Ui.findView(publishDialogView, R.id.privateEvent)

                val name = nameView.text.toString()
                val value = valueView.text.toString()
                val eventVisibility = if (privateEventRadio.isChecked)
                    ParticleEventVisibility.PRIVATE
                else
                    ParticleEventVisibility.PUBLIC

                publishEvent(name, value, eventVisibility)
            }
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(true)
            .setOnCancelListener { it.dismiss() }
            .show()
    }

    private fun publishEvent(name: String, value: String, eventVisibility: Int) {
        try {
            Async.executeAsync(device, object : Async.ApiProcedure<ParticleDevice>() {
                @Throws(ParticleCloudException::class)
                override fun callApi(particleDevice: ParticleDevice): Void? {
                    particleDevice.cloud.publishEvent(name, value, eventVisibility, 600)
                    return null
                }

                override fun onFailure(exception: ParticleCloudException) {
                    Toast.makeText(
                        this@InspectorActivity, "Failed to publish '" + name +
                                "' event", Toast.LENGTH_SHORT
                    ).show()
                }
            })
        } catch (e: ParticleCloudException) {
            Toast.makeText(
                this, "Failed to publish '" + name +
                        "' event", Toast.LENGTH_SHORT
            ).show()
        }
    }

}
