package com.bluechat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream

class MainActivityPro: AppCompatActivity() {
    private val adapter by lazy { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    private lateinit var service: BluetoothProService
    private lateinit var callService: BluetoothCallService
    private var selectedDevice: BluetoothDevice? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) toggleCall() else status("Microphone permission required") }
    private val messages=mutableListOf<ChatMessage>()
    private val chatAdapter=ChatAdapter(messages)
    private val devices=mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: DeviceAdapter
    private var receiverRegistered=false
    private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if(it.values.all { granted->granted }) scanDevices() else status("Bluetooth permissions required") }
    private val imagePicker=registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) try {
            val bytes=contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@registerForActivityResult
            // Keep transfer under Bluetooth framing limit. Large images are rejected rather than silently truncated.
            if(bytes.size>850_000) { status("Choose an image smaller than 850 KB"); return@registerForActivityResult }
            service.sendImage(Base64.encodeToString(bytes,Base64.NO_WRAP))
        } catch(e:Exception) { status("Image error: ${e.message}") }
    }
    private fun status(s:String)=runOnUiThread { findViewById<TextView>(R.id.tvStatus).text=s }
    @SuppressLint("MissingPermission")
    private val receiver=object: BroadcastReceiver() {
        override fun onReceive(context:Context?, intent:Intent?) {
            if(intent?.action==BluetoothDevice.ACTION_FOUND) {
                val d=if(Build.VERSION.SDK_INT>=33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE,BluetoothDevice::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if(d!=null && devices.none { it.address==d.address }) { devices.add(d); deviceAdapter.notifyItemInserted(devices.lastIndex) }
            }
        }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);setContentView(R.layout.activity_main_pro)
        val chat=findViewById<RecyclerView>(R.id.rvChat)
        chat.layoutManager=LinearLayoutManager(this);chat.adapter=chatAdapter
        deviceAdapter=DeviceAdapter(devices) { d -> selectedDevice = d; adapter?.cancelDiscovery();service.connect(d) }
        findViewById<RecyclerView>(R.id.rvDevices).apply { layoutManager=LinearLayoutManager(this@MainActivityPro);adapter=deviceAdapter }
        callService = BluetoothCallService(this, ::status)
        service=BluetoothProService({name,msg,isMe -> runOnUiThread { chatAdapter.addMessage(ChatMessage(name,msg,isMe,isImage=msg.startsWith("IMG::")));chat.scrollToPosition(messages.lastIndex) } },::status)
        findViewById<TextView>(R.id.btnScan).setOnClickListener { requestAndScan() }
        findViewById<TextView>(R.id.btnServer).setOnClickListener { requestAndHost() }
        findViewById<android.widget.LinearLayout>(R.id.btnSend).setOnClickListener { val input=findViewById<EditText>(R.id.etMsg);val s=input.text.toString().trim();if(s.isNotEmpty()){service.sendEncrypted(s);input.text.clear()} }
        findViewById<TextView>(R.id.btnAttach).setOnClickListener { imagePicker.launch("image/*") }
        findViewById<TextView>(R.id.btnCall).setOnClickListener { if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micPermission.launch(Manifest.permission.RECORD_AUDIO) else toggleCall() }
    }
    private fun neededPermissions():Array<String> = if(Build.VERSION.SDK_INT>=31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun hasPermissions()=neededPermissions().all { ContextCompat.checkSelfPermission(this,it)==PackageManager.PERMISSION_GRANTED }
    private fun requestAndScan() { if(!hasPermissions()) permissionLauncher.launch(neededPermissions()) else scanDevices() }
    @SuppressLint("MissingPermission")
    private fun scanDevices() {
        if(adapter==null){status("Bluetooth unavailable");return}
        if(!adapter!!.isEnabled){startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));status("Enable Bluetooth then scan again");return}
        devices.clear();deviceAdapter.notifyDataSetChanged()
        adapter!!.bondedDevices?.forEach { if(devices.none { d->d.address==it.address }) devices.add(it) }
        deviceAdapter.notifyDataSetChanged()
        if(!receiverRegistered){ContextCompat.registerReceiver(this,receiver,IntentFilter(BluetoothDevice.ACTION_FOUND),ContextCompat.RECEIVER_EXPORTED);receiverRegistered=true}
        adapter!!.startDiscovery();status("Scanning; tap a device to connect")
    }
    @SuppressLint("MissingPermission")
    private fun requestAndHost() {
        if(!hasPermissions()){permissionLauncher.launch(neededPermissions());status("Grant permissions, then tap HOST again");return}
        if(adapter?.isEnabled!=true){startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));return}
        startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300))
        service.startServer(adapter!!)
        callService.host(adapter!!)
    }
    private fun toggleCall() {
        if (callService.isInCall()) { callService.endCall(); return }
        val device = selectedDevice
        if (device == null) { status("Select and connect to a device first"); return }
        if (!hasPermissions()) { status("Grant Bluetooth permissions first"); return }
        callService.call(device)
    }
    override fun onDestroy(){super.onDestroy();if(receiverRegistered)unregisterReceiver(receiver);service.close();callService.close()}
}
