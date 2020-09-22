package com.digital.testanythingapplication

import android.animation.Animator
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModelProvider
import com.digital.appktx.removeFirstItemIf
import kotlinx.android.synthetic.main.activity_main.*

import kotlinx.android.synthetic.main.content_main.*
import java.util.*

class MainActivity : AppCompatActivity(), View.OnClickListener {
	//0000111f-0000-1000-8000-00805f9b34fb //laptop success.
	//0000111e-0000-1000-8000-00805f9b34fb //BT-06 success.
	private val ANGRY = "angry"
	private val SAD = "sad"
	private val HAPPY = "happy"
	private val CALM = "calm"
	private val UPSET = "upset"
	private var bluetoothHeadset: BluetoothHeadset? = null
	val REQUEST_ENABLE_BT = 10
	val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
	private var connectedDevices = mutableListOf<MPair<DeviceModel, Thread?>>()
	private var clientSocket: BluetoothSocket? = null
	private val uuidName = "00001101-0000-1000-8000-00805F9B34FB"
	private val filter = IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
	private val receiver = object : BroadcastReceiver() {

		override fun onReceive(context: Context, intent: Intent) {
			onConnectStatusChange()

//			val status: Int = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,-1)!!
//			when (status) {
//				BluetoothAdapter.STATE_CONNECTED -> {
//				println("BluetoothAdapter.STATE_CONNECTED")
//				}
//				else->{
//					println("BluetoothAdapter.STATE_ $status")
//				}
//			}
		}
	}

	// Register for broadcasts when a device is discovered.
	private val profileListener = object : BluetoothProfile.ServiceListener {

		override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
			println(profile)
			println(profile == BluetoothProfile.A2DP)
			if (profile == BluetoothProfile.HEADSET) {
				println("$profile is HEADSET")
				bluetoothHeadset = proxy as BluetoothHeadset
			}
		}

		override fun onServiceDisconnected(profile: Int) {
			if (profile == BluetoothProfile.HEADSET) {
				bluetoothHeadset = null
			}
		}
	}

	val vm: MainActivityVM by lazy {
		ViewModelProvider(
			this,
			ViewModelProvider.NewInstanceFactory()
		).get(MainActivityVM::class.java)
	}
	val handler = Handler()
	val animationListener: Animator.AnimatorListener = object : Animator.AnimatorListener {
		override fun onAnimationRepeat(animation: Animator?) {

		}

		override fun onAnimationEnd(animation: Animator?) {
			kotlin.runCatching { handler.postDelayed({ menuImg.playAnimation() }, 5000) }
		}

		override fun onAnimationCancel(animation: Animator?) {

		}

		override fun onAnimationStart(animation: Animator?) {

		}

	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		registerReceiver(receiver, filter)
		bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
		menuImg.setMinFrame(200)
		menuImg.setMaxFrame(294)
		menuImg.speed = 0.5f
		menuImg.addAnimatorListener(animationListener)
		//disConnect
		menuImg.setOnClickListener(this)
		settingMenu.setOnClickListener(this)


	}

	private fun disConnectAll() {
		if (bluetoothAdapter?.isDiscovering == true)
			bluetoothAdapter?.cancelDiscovery()
		clearConnectThread()
		onConnectStatusChange()
		connectedDevices = mutableListOf()
	}

	@UiThread
	private fun checkAndRequestPerRequiredBluetooth(): Boolean {
		return if (bluetoothAdapter == null) {
			// Device doesn't support Bluetooth
			runOnUiThread {
				disableAll()
				toast(getString(R.string.its_seem_this_device_dosnt_support_blutooth))
			}
			false
		} else if (bluetoothAdapter?.isEnabled == false) {
			setBluetoothDisableStatus()
			val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
			false
		} else {
			runOnUiThread {
				enableAll()
			}
			true
		}

	}


	private fun getNewConnectThread(deviceModel: DeviceModel): Thread {
		return Thread {
			if (deviceModel.socket?.isConnected != true) {
				intiDeviceConnection(deviceModel)
			}
			if (deviceModel.socket?.isConnected == true)
				startReading(deviceModel.socket!!)
		}
	}

	@Synchronized
	private fun intiDeviceConnection(deviceModel: DeviceModel) {
		val uuids = if (deviceModel.device?.uuids.isNullOrEmpty()) listOf(UUID.fromString(uuidName))
		else deviceModel.device!!.uuids!!.map { it.uuid }.toList().plus(UUID.fromString(uuidName))
		run loop@{
			var connectFailed = false
			uuids.forEachIndexed { index, it ->
				runCatching {
//					deviceModel.socket = deviceModel.device?.createRfcommSocketToServiceRecord(it)
					deviceModel.socket = bluetoothAdapter?.getRemoteDevice(deviceModel.address)
						?.createRfcommSocketToServiceRecord(it)
					deviceModel.socket?.connect()
					onConnectStatusChange()
					onConnectSuccess()
					return@loop
				}.onFailure {
					deviceModel.socket?.close()
					deviceModel.socket = null
					connectFailed = true
				}
			}
			if (connectFailed) {
				clearSingleDeviceConnectThread(deviceModel)
				onConnectFailed()
			}
		}
	}

	@WorkerThread
	private fun onConnectFailed() {
		toast(getString(R.string.connecting_failed_check_target_device_bluetooth))
	}

	@WorkerThread
	private fun onConnectSuccess() {

	}


	private fun enableAll() {
		menuImg.isEnabled = true
		statusImg.cancelAnimation()
		statusImg.clearAnimation()
		statusTV.text = ""
		if (connectedDevices.isEmpty()) {
			setNoDeviceConnectedStatus()
		} else {
			startConnect()
		}
	}

	private fun setNoDeviceConnectedStatus() {
		statusImg.setAnimation("bluetooth_connecting.json")
		statusImg.playAnimation()
		statusTV.setText(R.string.no_device_connecetd)
	}

	private fun disableAll() {
		clearConnectThread()
		connectedDevices = mutableListOf()
		menuImg.isEnabled = false
		statusImg.cancelAnimation()
		setBluetoothDisableStatus()
		//onConnectStatusChange()
	}

	private fun setBluetoothDisableStatus() {
		runOnUiThread {
			statusImg.setAnimation("bluetooth_disable_enable.json")
			statusImg.playAnimation()
			statusTV.text = getString(R.string.kindly_enable_bluetooth)
		}
	}

	private fun startConnect() {
		bluetoothAdapter?.cancelDiscovery()
//		clearConnectThread()
		connectedDevices.filter { it.second == null || it.first.socket?.isConnected != true }.forEach {
			if (it.second != null) clearSingleDeviceConnectThread(it.first)
			it.second = getNewConnectThread(it.first)
			it.second?.start()
		}
	}


	private fun startReading(socket: BluetoothSocket) {
		Log.d("mud", "--listening--")
		val reader = socket.inputStream.bufferedReader()
		var errorSkip = 3
		while (true) {
			runCatching {
				if (connectedDevices.find { it.second == Thread.currentThread() } != null)
					onHandleNewValue(reader.readLine())
				else throw Resources.NotFoundException("thread was removed from connected devices list.")
			}.onFailure {
				Log.d("mud", "----- stop reading,${it.message}")
				errorSkip--
				if (errorSkip <= 0) {
					clearSingleDeviceConnectThread(socket)
					//notifyUser
					//toast("Reading Failed, ${it.message}")
					return
				}
			}
		}
		Log.d("mud", "--Done--")

	}

	private fun clearConnectThread() {
		connectedDevices.forEach {
			it.second = null
			it.first.socket?.close()
			it.first.socket = null
		}
	}

	private fun clearSingleDeviceConnectThread(socket: BluetoothSocket) {
		val deviceWasConnected = socket.isConnected
		socket.close()
		connectedDevices.find { it.first.socket == socket }?.let {
			it.second = null
			it.first.socket?.close()
			it.first.socket = null
			if (deviceWasConnected)
				onConnectStatusChange()
		}
	}

	private fun clearSingleDeviceConnectThread(deviceModel: DeviceModel) {
		connectedDevices.find { it.first == deviceModel }?.let {
			it.second = null
			if (it.first.socket?.isConnected == true) {
				onConnectStatusChange()
			}
			it.first.socket?.close()
			it.first.socket = null
		}
	}

	private fun onHandleNewValue(text: String?) {
		runOnUiThread {
			supportActionBar?.title = text
//			statusImg
			statusImg.cancelAnimation()

			when (text?.toLowerCase(Locale.ENGLISH)) {
				SAD -> {
					statusTV.text = getString(R.string.saddens)
					statusImg.setAnimation("crying.json")
					statusImg.playAnimation()
				}
				ANGRY -> {
					statusTV.text = getString(R.string.angry)
					statusImg.setAnimation("angry.json")
					statusImg.playAnimation()
				}
				HAPPY -> {
					statusTV.text = getString(R.string.happy)
					statusImg.setAnimation("smiley.json")
					statusImg.playAnimation()
				}
				CALM -> {
					statusTV.text = getString(R.string.comfortable)
					statusImg.setAnimation("comfort.json")
					statusImg.playAnimation()
				}
				UPSET -> {
					statusTV.text = getString(R.string.nervous)
					statusImg.setAnimation("nervous.json")
					statusImg.playAnimation()
				}
				else -> {
					statusTV.text = getString(R.string.happy)
					statusImg.setAnimation("smiley.json")
					statusImg.playAnimation()
				}

			}
		}
	}

	private fun onConnectStatusChange() {
		val status = if (connectedDevices.find { it.first.socket?.isConnected == true } == null) {
			//no connect device
			checkAndRequestPerRequiredBluetooth()
			"disConnected"
		} else {
			//at least one device connected.
			"Connected"
		}
		toast(status)
	}


	fun requestDeviceToBeDISCOVERABLE() {
		val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
			putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
		}
		startActivity(discoverableIntent)
	}


	override fun onClick(v: View) {
		when (v.id) {
			R.id.menuImg -> {
				if (checkAndRequestPerRequiredBluetooth())
					openMenuDialog()
			}
			R.id.settingMenu -> {
				showDisConnectMenu()
			}
		}
	}

	private fun openMenuDialog() {
		DevicesDialog()
			.setCallBack { model ->
				if (connectedDevices.find { it.first == model } == null) {
					connectedDevices.add(MPair(model, null))
					startConnect()
				}

			}
			.show(supportFragmentManager, "sfma")
	}

	private fun toast(text: String) {
		runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
	}

	fun showDisConnectMenu() {
		var hasItem = false
		val popUp = PopupMenu(this, settingMenu, Gravity.BOTTOM).apply {
			menu.add(0, 0, 0, "disConnect All")
			connectedDevices.filter { it.second != null && it.first.socket?.isConnected == true }
				.forEach {
					hasItem = true
					menu.add(1, it.first.address.hashCode(), 0, "disConnect ${it.first.name}")
				}
			setOnMenuItemClickListener { item ->
				if (item.groupId == 0) {
					disConnectAll()
				} else {
					connectedDevices?.find { it.first.address.hashCode() == item.itemId }?.let {
						disConnectDevice(it.first)
					}
				}
				false
			}
		}
		if (hasItem)
			popUp.show()
		else
			toast(getString(R.string.no_device_connecetd))

	}

	private fun disConnectDevice(model: DeviceModel) {
		if (model.socket != null) {
			clearSingleDeviceConnectThread(model.socket!!)
			connectedDevices.removeFirstItemIf { it.first == model }
		}
	}

	override fun onStart() {
		super.onStart()
		checkAndRequestPerRequiredBluetooth()
		if (connectedDevices.isNotEmpty()) {
			startConnect()
		}
	}

	override fun onStop() {
		super.onStop()
		clearConnectThread()
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (resultCode == RESULT_OK)
			when (requestCode) {
				REQUEST_ENABLE_BT -> enableAll()
			}
	}


	override fun onDestroy() {
		super.onDestroy()
		menuImg.removeAnimatorListener(animationListener)
	}

}


data class MPair<A, B>(var first: A, var second: B)