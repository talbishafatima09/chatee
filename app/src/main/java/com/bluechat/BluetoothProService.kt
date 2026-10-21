package com.bluechat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Base64
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlin.concurrent.thread

// Bluetooth RFCOMM transport. This is NOT end-to-end encrypted; do not treat it as secure messaging.
class BluetoothProService(private val onMessage: (String, String, Boolean) -> Unit, private val onStatus: (String) -> Unit) {
    private val uuid: UUID = UUID.fromString("b9c1b5aa-3f34-4a9e-9d0d-48d1efdd5f62")
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var running = true
    private var serverSocket: android.bluetooth.BluetoothServerSocket? = null
    private val writeLock = Any()
    @SuppressLint("MissingPermission")
    fun startServer(adapter: BluetoothAdapter) {
        thread {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("BlueChatPro", uuid)
                onStatus("Waiting for connection")
                val accepted = serverSocket!!.accept()
                attach(accepted)
            } catch(e: Exception) { if(running) onStatus("Host error: ${e.message}") }
        }
    }
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        thread {
            try {
                onStatus("Connecting...")
                val s=device.createRfcommSocketToServiceRecord(uuid)
                s.connect()
                attach(s)
            } catch(e: Exception) { onStatus("Connection failed: ${e.message}") }
        }
    }
    @SuppressLint("MissingPermission")
    private fun attach(s: BluetoothSocket) {
        socket?.close(); socket=s
        serverSocket?.close(); serverSocket=null
        val name=s.remoteDevice.name ?: "Bluetooth device"
        onStatus("Connected to $name")
        try {
            val input=DataInputStream(s.inputStream)
            while(running && socket===s) {
                val length=input.readInt()
                if(length !in 1..1_500_000) throw IllegalArgumentException("Invalid message size")
                val bytes=ByteArray(length)
                input.readFully(bytes)
                onMessage(name, String(bytes, Charsets.UTF_8), false)
            }
        } catch(e: Exception) { if(running) onStatus("Disconnected") }
        finally { if(socket===s) socket=null; try{s.close()}catch(_:Exception){} }
    }
    fun sendEncrypted(message: String) = send(message) // Legacy API name; payload is NOT encrypted.
    fun sendImage(base64: String) = send("IMG::"+base64)
    private fun send(message: String) {
        thread {
            val s=socket
            if(s==null) { onStatus("Connect to a device first"); return@thread }
            try {
                val bytes=message.toByteArray(Charsets.UTF_8)
                require(bytes.size<=1_500_000) { "Message too large" }
                synchronized(writeLock) { val output=DataOutputStream(s.outputStream); output.writeInt(bytes.size); output.write(bytes); output.flush() }
                onMessage("Me",message,true)
            } catch(e: Exception) { onStatus("Send failed: ${e.message}") }
        }
    }
    fun close() { running=false; try{serverSocket?.close()}catch(_:Exception){};try{socket?.close()}catch(_:Exception){} }
}
