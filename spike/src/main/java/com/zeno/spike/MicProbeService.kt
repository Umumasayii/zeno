package com.zeno.spike

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Microphone foreground-service probe (SPIKE-01 screen-off listening, SPIKE-02 boot behaviour).
 * Opens the mic for a fixed time, computes loudness per second and DISCARDS the samples.
 * Logs whether Android silenced the client (all zeros / isClientSilenced) — that is how Android
 * enforces "while-in-use" microphone restrictions on background-started services.
 */
class MicProbeService : Service() {

    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trigger = intent?.getStringExtra(EXTRA_TRIGGER) ?: "unknown"
        val seconds = intent?.getIntExtra(EXTRA_SECONDS, 60)?.coerceIn(5, 600) ?: 60
        if (running) {
            SpikeLog.write(this, "MIC_PROBE ignored, already running (trigger=$trigger)")
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            SpikeLog.write(this, "MIC_PROBE startForeground OK trigger=$trigger seconds=$seconds")
        } catch (t: Throwable) {
            SpikeLog.write(this, "MIC_PROBE startForeground FAILED trigger=$trigger ${t.javaClass.simpleName}: ${t.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        thread(name = "mic-probe") { probe(trigger, seconds) }
        return START_NOT_STICKY
    }

    private fun probe(trigger: String, seconds: Int) {
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                SpikeLog.write(this, "MIC_PROBE no RECORD_AUDIO permission (trigger=$trigger)")
                return
            }
            val rate = 16_000
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate * 2))
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                SpikeLog.write(this, "MIC_PROBE AudioRecord not initialized (trigger=$trigger)")
                record.release()
                return
            }
            record.startRecording()
            SpikeLog.write(this, "MIC_PROBE recordingState=${record.recordingState} (3=recording)")
            val buf = ShortArray(rate) // one second
            var silentSeconds = 0
            for (s in 1..seconds) {
                if (!running) break
                var read = 0
                while (read < buf.size && running) {
                    val n = record.read(buf, read, buf.size - read)
                    if (n <= 0) break
                    read += n
                }
                var sum = 0.0
                var peak = 0
                for (i in 0 until read) {
                    val v = buf[i].toInt()
                    sum += v.toDouble() * v
                    peak = maxOf(peak, abs(v))
                }
                buf.fill(0) // discard audio immediately
                val rms = if (read > 0) sqrt(sum / read) else 0.0
                if (peak == 0) silentSeconds++
                val silenced = record.activeRecordingConfiguration?.isClientSilenced
                if (s == 1 || s % 5 == 0 || s == seconds) {
                    SpikeLog.write(this, "MIC_PROBE t=${s}s rms=${"%.1f".format(rms)} peak=$peak clientSilenced=$silenced silentSecondsSoFar=$silentSeconds")
                }
            }
            record.stop()
            record.release()
            SpikeLog.write(this, "MIC_PROBE done trigger=$trigger silentSeconds=$silentSeconds/$seconds")
        } catch (t: Throwable) {
            SpikeLog.write(this, "MIC_PROBE error ${t.javaClass.simpleName}: ${t.message}")
        } finally {
            running = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Mic probe", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Zeno spike: microphone probe running")
            .setContentText("Measuring loudness only. Nothing is recorded or saved.")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "mic_probe"
        private const val NOTIFICATION_ID = 42
        const val EXTRA_TRIGGER = "trigger"
        const val EXTRA_SECONDS = "seconds"

        fun start(context: Context, trigger: String, seconds: Int) {
            val i = Intent(context, MicProbeService::class.java)
                .putExtra(EXTRA_TRIGGER, trigger)
                .putExtra(EXTRA_SECONDS, seconds)
            try {
                context.startForegroundService(i)
                SpikeLog.write(context, "MIC_PROBE startForegroundService accepted trigger=$trigger")
            } catch (t: Throwable) {
                SpikeLog.write(context, "MIC_PROBE startForegroundService REJECTED trigger=$trigger ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }
}
