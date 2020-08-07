package com.digital.testanythingapplication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Checkable
import android.widget.Toast
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
						addDevice(DeviceModel(device!!.name, device.address, device = device))
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
		isCancelable = false
		return inflater.inflate(R.layout.devices_dialog, container, false)
	}


	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		val selectedBgColor = Color.parseColor("#F4F4F4")
		devicesRecycler.adapter = AppAdapter<DeviceModel>(R.layout.devices_adapter_item) {
			get<AppTextView>(R.id.deviceName).text = getItem()?.name
			get<AppTextView>(R.id.deviceAddress).text = getItem()?.address
			setOnItemClick(itemView)
			if (getItem()?.isChecked == true)
				itemView.setBackgroundColor(selectedBgColor)
			else
				itemView.setBackgroundColor(Color.WHITE)
		}
			.setList(getPairedDevices() ?: mutableListOf())
			.setCallback { itemView, position, model, any ->
				//request connect
				val list = devicesRecycler.getAdapter<AppAdapter<DeviceModel>>()?.list
				list?.filter { it.isChecked }?.let {
					it.forEach { it.isChecked = false }
					devicesRecycler.adapter?.notifyItemRangeChanged(0,list.size - 1)
				}
				devicesRecycler?.post {
					model.toggle()
					devicesRecycler.adapter?.notifyItemChanged(position)
				}
			}
		connectBtn.setOnClickListener {
			val list = devicesRecycler.getAdapter<AppAdapter<DeviceModel>>()?.list
			val device = list?.find { it.isChecked }
			if (device != null) {
				cb?.invoke(device)
				dismiss()
			} else {
				Toast.makeText(context, getString(R.string.kindly_select_device_first), Toast.LENGTH_LONG)
					.show()
			}
		}
		cancelBtn.setOnClickListener {
			dismiss()
		}


		dialog?.setOnDismissListener {
			println("onDismiss-isDiscovering:${bluetoothAdapter?.isDiscovering}")
			bluetoothAdapter?.cancelDiscovery()
		}
		context?.registerReceiver(receiver, filter)
		if (bluetoothAdapter?.isDiscovering != true)
			bluetoothAdapter?.startDiscovery()
	}


	private fun getPairedDevices(): List<DeviceModel>? {
		val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
		return pairedDevices?.filterNot { it.name.isNullOrEmpty() }
			?.map { DeviceModel(it.name.toSafetyString(), it.address.toSafetyString(), true, it) }
			?.toMutableList()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		context?.unregisterReceiver(receiver)
	}

	override fun onStart() {
		super.onStart()
		val lp = WindowManager.LayoutParams()
		lp.copyFrom(dialog?.window?.attributes)
		lp.width = (resources.displayMetrics.widthPixels * 0.92).toInt()
		lp.height = WindowManager.LayoutParams.MATCH_PARENT
		dialog?.window?.attributes = lp
	}
}


data class DeviceModel(
	val name: String,
	val address: String,
	val paired: Boolean = false,
	val device: BluetoothDevice? = null,
	var socket: BluetoothSocket? = null
) : Checkable {
	private var checked: Boolean = false
	override fun isChecked() = checked

	override fun toggle() {
		checked = !checked
	}

	override fun setChecked(checked: Boolean) {
		this.checked = checked
	}
}