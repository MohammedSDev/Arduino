package com.digital.testanythingapplication

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainActivityVM :ViewModel(){


	val startDiscoveryLD:LiveData<Boolean> = MutableLiveData()

	fun requestStartDiscovery(){
		(startDiscoveryLD as MutableLiveData).value = true
	}
	fun requestStopDiscovery(){
		(startDiscoveryLD as MutableLiveData).value = false
	}
}