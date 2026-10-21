package com.bluechat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(private val devices:List<BluetoothDevice>,private val onClick:(BluetoothDevice)->Unit):RecyclerView.Adapter<DeviceAdapter.Holder>() {
    class Holder(val text:TextView):RecyclerView.ViewHolder(text)
    override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):Holder=Holder(TextView(parent.context).apply { setPadding(18,10,18,10);setTextColor(android.graphics.Color.WHITE);textSize=14f })
    override fun getItemCount()=devices.size
    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder:Holder,position:Int){val device=devices[position];holder.text.text="${device.name ?: "Unknown"} (${device.address})";holder.text.setOnClickListener{onClick(device)}}
}
