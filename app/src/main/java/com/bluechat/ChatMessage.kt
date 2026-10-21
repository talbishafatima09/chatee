package com.bluechat

data class ChatMessage(val name: String, val message: String, val isMe: Boolean, val time: String = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date()), val isImage: Boolean = false)
