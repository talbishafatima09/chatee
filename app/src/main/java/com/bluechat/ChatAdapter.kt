package com.bluechat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val messages: MutableList<ChatMessage>): RecyclerView.Adapter<ChatAdapter.Holder>() {
    class Holder(v: View): RecyclerView.ViewHolder(v)
    override fun getItemViewType(position: Int) = if (messages[position].isMe) 1 else 2
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(LayoutInflater.from(parent.context).inflate(if(viewType==1) R.layout.item_message_sent else R.layout.item_message_received, parent, false))
    override fun getItemCount() = messages.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val msg=messages[position]
        holder.itemView.findViewById<TextView>(R.id.tvMessage).text = if(msg.isImage) "Photo received" else msg.message
        holder.itemView.findViewById<TextView>(R.id.tvTime).text = msg.time
        if(!msg.isMe) holder.itemView.findViewById<TextView>(R.id.tvName).text = msg.name
    }
    fun addMessage(msg: ChatMessage) { messages.add(msg); notifyItemInserted(messages.lastIndex) }
}
