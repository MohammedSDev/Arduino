package com.digital.testanythingapplication

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
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.lifecycle.ViewModelProvider
import com.digital.appui.dialog.AppDialog

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.util.*

class MainActivity : AppCompatActivity(), View.OnClickListener {
	//0000111f-0000-1000-8000-00805f9b34fb //laptop success.
	//0000111e-0000-1000-8000-00805f9b34fb //BT-06 success.
	private val ANGRY = "angry"
	private val SAD = "sad"
	private val HAPPY = "happy"
	private var bluetoothHeadset: BluetoothHeadset? = null
	val REQUEST_ENABLE_BT = 10
	val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
	private var selectedDevice: BluetoothDevice? = null
	private var clientSocket: BluetoothSocket? = null
	private val uuidName = "00001101-0000-1000-8000-00805F9B34FB"

	// Register for broadcasts when a device is discovered.
	private val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
	private val receiver = object : BroadcastReceiver() {

		override fun onReceive(context: Context, intent: Intent) {
			val action: String = intent.action!!
			when (action) {
				BluetoothDevice.ACTION_FOUND -> {
					// Discovery has found a device. Get the BluetoothDevice
					// object and its info from the Intent.
					val device: BluetoothDevice? =
						intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
					//add to ui
					addView(device)
				}
			}
		}
	}
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

	}

	@WorkerThread
	private fun onConnectSuccess() {

	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)


		registerReceiver(receiver, filter)
		bluetoothAdapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)
		setSupportActionBar(toolbar)
		if (bluetoothAdapter == null) {
			// Device doesn't support Bluetooth
		} else if (bluetoothAdapter?.isEnabled == false) {
			val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
		}
		//paired devices
		setPairedDevices()


		//disConnect
		fab1.setOnClickListener {
			if (bluetoothAdapter?.isDiscovering == true)
				bluetoothAdapter?.cancelDiscovery()
			clearConnectThread()
			selectedDevice = null
		}

		//start connect
		fab.setOnClickListener { view ->
			startConnect()
		}

		fab2.setOnClickListener {
			bluetoothAdapter?.startDiscovery()
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

			if (text?.toIntOrNull() ?: 0 < 120) {
				statusTV.text = "Happy"
				statusImg.setAnimation("angry.json")//smiley
				statusImg.playAnimation()
			} else {
				statusTV.text = "Saddens"
				statusImg.setAnimation("crying.json")
				statusImg.playAnimation()
			}
		}
	}

	private fun onConnectStatusChange() {
		val status = if (clientSocket?.isConnected == true) "Connected" else "diConnected"
		toast(status)
	}


	private fun addView(device: BluetoothDevice?, paired: Boolean = false) {
		val name = device?.name
		val address = device?.address // MAC address

		if (name.isNullOrEmpty()) {
			println("null device.address: $address")
			return
		}
		val v = TextView(this).apply {
			text = ("${if (paired) "pair" else ""}$name,$address")
			setPadding(15, 15, 15, 15)
			textSize = 22f
			tag = device
			setOnClickListener(this@MainActivity)
			layoutParams = ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		}
		container.addView(v)
	}

	private fun setPairedDevices() {
		val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
		pairedDevices?.forEach { device ->
			addView(device, true)
		}
	}

	fun requestDeviceToBeDISCOVERABLE() {
		val discoverableIntent: Intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
			putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
		}
		startActivity(discoverableIntent)
	}


	override fun onClick(v: View) {
		when(v.id){
			R.id.menu ->{
				openMenuDialog()
			}
			else ->{
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
			.show(supportFragmentManager,"sfma")
	}

	private fun toast(text: String) {
		runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
	}


	override fun onStart() {
		super.onStart()
		if (selectedDevice != null) {
			fab.callOnClick()
		}
	}

	override fun onStop() {
		super.onStop()
		clearConnectThread()
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(receiver)

	}
}


