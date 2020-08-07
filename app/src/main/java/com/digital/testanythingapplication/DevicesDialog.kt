package com.digital.testanythingapplication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.digital.appadapter.AppAdapter
import com.digital.appktx.getAdapter
import com.digital.appktx.toSafetyString
import com.digital.appui.widget.AppTextView
import kotlinx.android.synthetic.main.devices_dialog.*

class DevicesDialog : DialogFragment() {

	private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
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
					if (!device?.name.isNullOrEmpty() && !device?.address.isNullOrEmpty())
						addDevice(DeviceModel(device!!.name, device!!.address))
				}
			}
		}
	}
	private val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
	private var cb: ((device: DeviceModel) -> Unit)? = null
	private fun addDevice(deviceModel: DeviceModel) {
		devicesRecycler.getAdapter<AppAdapter<DeviceModel>>()?.let { adapter ->
			(adapter.list as MutableList).add(deviceModel)
			adapter.notifyItemInserted(adapter.list.size - 1)
		}
	}

	fun setCallBack(cb: (DeviceModel) -> Unit): DevicesDialog {
		this.cb = cb
		return this
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View? {
		return inflater.inflate(R.layout.devices_dialog, container, false)
	}


	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		devicesRecycler.adapter = AppAdapter<DeviceModel>(R.layout.devices_adapter_item) {
			get<AppTextView>(R.id.deviceName).text = getItem()?.name
			get<AppTextView>(R.id.deviceAddress).text = getItem()?.address
		}
			.setList(getPairedDevices() ?: mutableListOf())
			.setCallback { itemView, position, model, any ->
				//request connect
				cb?.invoke(model)
			}
		connectBtn.setOnClickListener { }
		cancelBtn.setOnClickListener {
			dismiss()
		}


		dialog?.setOnDismissListener {
			bluetoothAdapter?.cancelDiscovery()
		}
		context?.registerReceiver(receiver, filter)
		if (bluetoothAdapter?.isDiscovering != true)
			bluetoothAdapter?.startDiscovery()
	}


	private fun getPairedDevices(): List<DeviceModel>? {
		val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
		return pairedDevices?.filterNot { it.name.isNullOrEmpty() }
			?.map { DeviceModel(it.name.toSafetyString(), it.address.toSafetyString()) }
			?.toMutableList()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		context?.unregisterReceiver(receiver)
	}
}


data class DeviceModel(
	val name: String,
	val address: String,
	val device: BluetoothDevice? = null
)