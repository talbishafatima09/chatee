package com.bluechat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.util.UUID
import kotlin.concurrent.thread

/** Experimental direct Bluetooth Classic voice channel. Not encrypted. */
class BluetoothCallService(private val context: Context, private val onStatus: (String) -> Unit) {
    private val uuid = UUID.fromString("6df1a1a2-bc53-4c2e-86e1-a0b2c3d4e5f6")
    @Volatile private var running = false
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var server: BluetoothServerSocket? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private val sampleRate = 16000
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @SuppressLint("MissingPermission")
    fun host(adapter: BluetoothAdapter) {
        thread {
            try {
                server = adapter.listenUsingRfcommWithServiceRecord("BlueChat Voice", uuid)
                onStatus("Voice ready: waiting for incoming call")
                val incoming = server?.accept() ?: return@thread
                if (socket != null) { incoming.close(); return@thread }
                startAudio(incoming)
            } catch (e: Exception) { if (running) onStatus("Voice host: ${e.message}") }
        }
    }

    @SuppressLint("MissingPermission")
    fun call(device: BluetoothDevice) {
        if (socket != null) return
        thread {
            try {
                onStatus("Calling...")
                val outgoing = device.createRfcommSocketToServiceRecord(uuid)
                outgoing.connect()
                startAudio(outgoing)
            } catch (e: Exception) { onStatus("Call failed: ${e.message}") }
        }
    }

    private fun startAudio(s: BluetoothSocket) {
        socket = s
        running = true
        onStatus("Voice connected — tap call to hang up")
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        val minRecord = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat)
        val minPlay = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat)
        if (minRecord <= 0 || minPlay <= 0) { onStatus("Audio unsupported"); endCall(); return }
        try {
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat, minRecord.coerceAtLeast(2048))
            val track = AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRate, AudioFormat.CHANNEL_OUT_MONO, audioFormat, minPlay.coerceAtLeast(2048), AudioTrack.MODE_STREAM)
            recorder = record; player = track
            record.startRecording(); track.play()
            thread {
                val buffer = ByteArray(minRecord.coerceAtLeast(2048))
                try { while (running) { val n = record.read(buffer, 0, buffer.size); if (n > 0) s.outputStream.write(buffer, 0, n) } }
                catch (_: Exception) {} finally { endCall() }
            }
            thread {
                val buffer = ByteArray(minPlay.coerceAtLeast(2048))
                try { while (running) { val n = s.inputStream.read(buffer); if (n < 0) break; if (n > 0) track.write(buffer, 0, n) } }
                catch (_: Exception) {} finally { endCall() }
            }
        } catch (e: Exception) { onStatus("Audio failed: ${e.message}"); endCall() }
    }

    @Synchronized fun endCall() {
        if (!running && socket == null) return
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).mode = AudioManager.MODE_NORMAL
        onStatus("Voice call ended")
    }
    fun isInCall() = socket != null
    fun close() { endCall(); try { server?.close() } catch (_: Exception) {}; server = null }
}
