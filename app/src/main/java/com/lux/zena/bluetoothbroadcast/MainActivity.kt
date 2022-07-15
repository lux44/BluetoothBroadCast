package com.lux.zena.bluetoothbroadcast

import com.lux.zena.bluetoothbroadcast.R
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
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

class MainActivity : AppCompatActivity() {

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
    val bluetoothAdapter: BluetoothAdapter? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }else {
        @Suppress("DEPRECATION")
        BluetoothAdapter.getDefaultAdapter()
    }

    // 4. 블루투스 스캔
    // 4-1. 블루투스 스캔 관련 변수들
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanning = false
    private var handler = Handler(Looper.getMainLooper())

    // Device Mac Address
    lateinit var deviceMacAddresses: String

    // 4-2. 블루투스 스캔 콜백 메소드
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            Log.i("blog","$callbackType, $result")

            if (result!=null){
                deviceMacAddresses = result.device.address
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
            if (!bluetoothAdapter.isEnabled){
                var enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
                    if (it.resultCode == RESULT_OK) enableIntent = it.data!!
                }
                resultLauncher.launch(enableIntent)
            }else Toast.makeText(this, "Bluetooth Enable", Toast.LENGTH_SHORT).show()
        }


    }
}