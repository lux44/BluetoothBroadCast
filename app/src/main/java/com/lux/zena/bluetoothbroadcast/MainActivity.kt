package com.lux.zena.bluetoothbroadcast

import com.lux.zena.bluetoothbroadcast.R
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.InetAddresses
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.lux.zena.bluetoothbroadcast.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {

    val binding:ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    // 1. 저전력 블루투스를 지원하지 않는 기기에도 앱을 제공하고 싶을 때 저전력 블루투스의 가용성 지정
    private fun PackageManager.missingSystemFeature(name:String):Boolean = !hasSystemFeature(name)

    // 2. 다중 퍼미션 배열 - 동적 퍼미션 필요 (advertise, connect, scan)
    private val permissions:Array<String> = arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN)

    // 2-1. 퍼미션 체크 함수
    private fun runtimeCheckSelfPermission(context: Context, permissions:Array<String>):Boolean = permissions.all {
        ActivityCompat.checkSelfPermission(context,it) == PackageManager.PERMISSION_GRANTED
    }
    private val multipleCode = 1000

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            multipleCode->{
                if (grantResults.isNotEmpty()&& grantResults[0]== PackageManager.PERMISSION_GRANTED){
                    Log.i("권한 테스트","사용자가 권한 부여")
                }else{
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package ${applicationContext.packageName}"))
                    startActivity(intent)
                }
            }
        }
    }

    // 3. 블루투스 어댑터
    val bluetoothAdapter: BluetoothAdapter? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        }else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    // 4. 블루투스 스캔
    // 4-1. 블루투스 스캔 관련 변수들
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanning = false
    private var handler = Handler(Looper.getMainLooper())

    // 4-1-1. scan filter를 담는 배열
    var filter: MutableList<ScanFilter> = mutableListOf()

    // GATT Client 객체
    private var bluetoothGatt:BluetoothGatt? = null

    // Device Mac Address
    private var deviceMacAddresses: String=""

    // 4-2. 블루투스 스캔 콜백 메소드
    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            Log.i("blog","$callbackType, $result")

            if (result!=null){
                Log.i("blog","${result.device}")
                deviceMacAddresses = result.device.toString()
                Log.i("blog","$deviceMacAddresses")

                val device:BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceMacAddresses)
                bluetoothGatt = device?.connectGatt(this@MainActivity,true,bluetoothGattCallback)
            }
        }
    }

    // 4-3. 블루투스 스캔 함수
    private fun scanLeDevice(){
        if (!scanning){
            handler.postDelayed({
                scanning=false
                if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED){
                    bluetoothLeScanner.stopScan(leScanCallback)
                    Log.i("over","scan over")
                    if (deviceMacAddresses!=null){
                        val device:BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceMacAddresses)
                        bluetoothGatt = device?.connectGatt(this, true,bluetoothGattCallback)

                        Log.e("blog","CONNECT GATT")
                    }
                }
            },3000L)
            scanning=true
            // Mac 주소 or UUID or Device name을 이용해 기기 찾기
            val scanFilter:ScanFilter = ScanFilter.Builder()
                .setDeviceAddress("C6:AF:2E:CA:EE:1E")
                .build()

            // 스캔 설정
            val scanSettings = ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_POWER or ScanSettings.SCAN_MODE_LOW_LATENCY
                ).build()

            filter.add(scanFilter)
            bluetoothLeScanner.startScan(filter,scanSettings,leScanCallback)
        }else{
            scanning=false
            bluetoothLeScanner.stopScan(leScanCallback)
            Log.e("stop","STOP SCAN")
        }
    }

    // 5. 데이터 읽어들이기

    //5-1. 데이터 담을 변수
    private lateinit var service:BluetoothGattService
    private lateinit var readCharacteristic: BluetoothGattCharacteristic

    // 5-2. 블루투스 콜백 메소드
    private val bluetoothGattCallback = object : BluetoothGattCallback(){
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.i("blog","discovered")
            if (status == BluetoothGatt.GATT_SUCCESS){
                Log.i("blog","success")
                if (gatt!=null){
                    Log.e("TAG","${gatt.services}, $gatt @@@@@@@")

                    displayGattServices(gatt.services, gatt)
                    Log.i("blog", "after read")
                }
            }
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("blog","connected")
                    runOnUiThread { Toast.makeText(this@MainActivity, "connect", Toast.LENGTH_SHORT).show() }
                    if(ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED){
                        Log.e("TAG","@@@@@@@@@@@@@@@@@@@@@@@@@@@")
                        gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("blog","disconnected")
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.i("blog","Characteristic Read")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.i("blog"," Characteristic Write")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.i("blog","Characteristic Changed : ${characteristic.toString()}" )
        }
    }

    @SuppressLint("MissingPermission")
    private fun displayGattServices(gattService: List<BluetoothGattService>?, gatt: BluetoothGatt){
        if (gattService==null){
            Log.i("blog","gattService == null")
            return
        }
        var serviceUUID:String?
        var characteristicUUID:String?

        // Loops through available GATT services
        // 값이 제대로 들어갔나 확인
        gattService.forEach {
            serviceUUID=it.uuid.toString()
            val gattCharacteristic:MutableList<BluetoothGattCharacteristic> = it.characteristics
            Log.i("blog", "Service : $serviceUUID")

            gattCharacteristic.forEach {    gattCharacteristics->
                characteristicUUID = gattCharacteristics.uuid.toString()
                Log.i("blog","Characteristic : $characteristicUUID")
                Log.i("blog","${gattCharacteristics.writeType}, ${gattCharacteristics.permissions}, ${gattCharacteristics.properties}")

                gattCharacteristics.descriptors.forEach { descriptor->
                    Log.i("blog","descriptor: ${descriptor.uuid}")
                }
                gatt.readCharacteristic(gattCharacteristics)
                Log.i("blog", "read")
            }
            Log.i("blog","~~~~~~~~~~~~~~~~~~")
        }

        service= bluetoothGatt!!.getService(UUID.fromString("00001809-0000-1000-8000-00805f9b34fb"))
        readCharacteristic = service.getCharacteristic(UUID.fromString("00002a1c-0000-1000-8000-00805f9b34fb"))

        val notifyDescriptor = readCharacteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
        notifyDescriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE

        bluetoothGatt?.apply {
            Log.i("blog","write Descriptor")
            writeDescriptor(notifyDescriptor)
            setCharacteristicNotification(readCharacteristic,true)
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        setContentView(binding.root)

        // 1-1. 저전력 블루투스 지원
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show()
            finish()
        }

        // 2-2. 퍼미션 체크
        if (!runtimeCheckSelfPermission(this,permissions)){
            ActivityCompat.requestPermissions(this,permissions,multipleCode)
        }else Log.i("권한 테스트","권한 있음.")

        // 3. 블루투스 기능 켜기
        // 3-1. registerForActivity 사용하기 위한 resultLauncher 객체 만들기
        val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
                if (result.resultCode == Activity.RESULT_OK) Log.i("BT","result ok")
                else Log.i("BT","result cancel")
        }

        // 3-2. 블루투스 기능 활성화
        if (bluetoothAdapter == null){
            Toast.makeText(this, "Device does not support bluetooth", Toast.LENGTH_SHORT).show()
        }else {
            if (!bluetoothAdapter!!.isEnabled){
                var enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
                    if (it.resultCode == RESULT_OK) enableIntent = it.data!!
                }
                resultLauncher.launch(enableIntent)
            }else Toast.makeText(this, "Bluetooth Enable", Toast.LENGTH_SHORT).show()
        }

        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        binding.tvScan.setOnClickListener { scanLeDevice() }


    }
}