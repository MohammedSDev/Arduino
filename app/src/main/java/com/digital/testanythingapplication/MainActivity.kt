package com.digital.testanythingapplication

import android.animation.Animator
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModelProvider
import com.digital.appktx.isNullValue

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
	private var bluetoothHeadset: BluetoothHeadset? = null
	val REQUEST_ENABLE_BT = 10
	val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
	private var selectedDevice: BluetoothDevice? = null
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
	private var connectThread: Thread? = null
		get() {
			if (field == null) field = getNewConnectThread()
			return field
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
		registerReceiver(receiver,filter)
		bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
		menuImg.setMinFrame(200)
		menuImg.setMaxFrame(294)
		menuImg.speed = 0.5f
		menuImg.addAnimatorListener(animationListener)
		//disConnect
		menuImg.setOnClickListener(this)
		fab1.setOnClickListener {
			if (bluetoothAdapter?.isDiscovering == true)
				bluetoothAdapter?.cancelDiscovery()
			clearConnectThread()
			selectedDevice = null
		}

	}

	private fun checkAndRequestPerRequiredBluetooth(): Boolean {
		return if (bluetoothAdapter == null) {
			// Device doesn't support Bluetooth
			disableAll()
			toast(getString(R.string.its_seem_this_device_dosnt_support_blutooth))
			false
		} else if (bluetoothAdapter?.isEnabled == false) {
			setBluetoothDisableStatus()
			val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
			false
		} else {
			enableAll()
			true
		}
	}


	private fun getNewConnectThread(): Thread {
		return Thread {
			if (clientSocket?.isConnected != true) {
				val uuids = if (selectedDevice?.uuids.isNullOrEmpty()) listOf(UUID.fromString(uuidName))
				else selectedDevice!!.uuids!!.map { it.uuid }.toList().plus(UUID.fromString(uuidName))
				run loop@{
					uuids.forEachIndexed { index, it ->
						runCatching {
							clientSocket = selectedDevice?.createRfcommSocketToServiceRecord(it)
							clientSocket?.connect()
							onConnectStatusChange()
							onConnectSuccess()
							return@loop
						}.onFailure {
							clientSocket?.close()
							clientSocket = null
							onConnectFailed()
							onConnectStatusChange()
						}
					}
				}
			}
			if (clientSocket != null)
				startReading(clientSocket!!)
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
		if (selectedDevice.isNullValue()){
			setNoDeviceConnectedStatus()
		}
	}

	private fun setNoDeviceConnectedStatus() {
		statusImg.setAnimation("bluetooth_connecting.json")
		statusImg.playAnimation()
		statusTV.setText(R.string.no_device_connecetd)
	}

	private fun disableAll() {
		selectedDevice = null
		clearConnectThread()
		menuImg.isEnabled = false
		statusImg.cancelAnimation()
		setBluetoothDisableStatus()
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
		clearConnectThread()
		connectThread?.start()
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menu.add(0, 1, 0, "start Discovering")
		menu.add(0, 2, 0, "start Discovering")
		return super.onCreateOptionsMenu(menu)
	}

	private fun startReading(socket: BluetoothSocket) {
		Log.d("mud", "--listening--")
		val reader = socket.inputStream.bufferedReader()
		var errorSkip = 3
		while (true) {
			runCatching {
				onHandleNewValue(reader.readLine())
			}.onFailure {
				Log.d("mud", "----- stop reading,${it.message}")
				errorSkip--
				if (errorSkip <= 0) {
					clearConnectThread()
					//notifyUser
					toast("Reading Failed, ${it.message}")
					return
				}
			}
		}
		Log.d("mud", "--Done--")

	}

	private fun clearConnectThread() {
		connectThread = null
		clientSocket?.close()
		clientSocket = null
		onConnectStatusChange()
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
				CALM -> {
					statusTV.text = getString(R.string.angry)
					statusImg.setAnimation("calm.json")
					statusImg.playAnimation()
				}
				HAPPY -> {
					statusTV.text = getString(R.string.happy)
					statusImg.setAnimation("smiley.json")
					statusImg.playAnimation()
				}
				else -> {
					statusTV.text = getString(R.string.happy)
					statusImg.setAnimation("smiley.json")
					statusImg.playAnimation()
				}

			}
//				statusImg.setAnimation("angry.json")
		}
	}

	private fun onConnectStatusChange() {
		val status = if (clientSocket?.isConnected == true) "Connected" else {
			checkAndRequestPerRequiredBluetooth()
			"disConnected"
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
			else -> {
				val device = v.tag as? BluetoothDevice
				selectedDevice = bluetoothAdapter?.getRemoteDevice(device?.address)
				toast(v.tag.toString())
			}
		}
	}

	private fun openMenuDialog() {
		DevicesDialog()
			.setCallBack {
				selectedDevice = it.device
				startConnect()
			}
			.show(supportFragmentManager, "sfma")
	}

	private fun toast(text: String) {
		runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
	}


	override fun onStart() {
		super.onStart()
		checkAndRequestPerRequiredBluetooth()
		if (selectedDevice != null) {
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


