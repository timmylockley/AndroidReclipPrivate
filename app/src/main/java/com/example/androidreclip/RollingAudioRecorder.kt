package com.example.androidreclip

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class RollingAudioRecorder {

    @Volatile
    var isRecording = false
        private set

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bytesPerSample = 2

    private val maxBufferSeconds = 120
    private val bufferSizeBytes = sampleRate * bytesPerSample * maxBufferSeconds

    private val circularBuffer = ByteArray(bufferSizeBytes)
    private var writeOffset = 0
    private var totalBytesRecorded = 0L

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize.coerceAtLeast(bufferSizeBytes / 10)
            )

            audioRecord?.startRecording()
            isRecording = true
            writeOffset = 0
            totalBytesRecorded = 0L

            recordingThread = thread(start = true) {
                val tempBuffer = ByteArray(2048)
                while (isRecording) {
                    val read = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: 0
                    if (read > 0) {
                        synchronized(circularBuffer) {
                            for (i in 0 until read) {
                                circularBuffer[writeOffset] = tempBuffer[i]
                                writeOffset = (writeOffset + 1) % bufferSizeBytes
                            }
                            totalBytesRecorded += read
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingThread?.interrupt()
        recordingThread = null

        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun clipLastTwoMinutes(outputFile: File): Boolean {
        synchronized(circularBuffer) {
            if (totalBytesRecorded == 0L) return false

            val validBytes = totalBytesRecorded.coerceAtMost(bufferSizeBytes.toLong()).toInt()
            val pcmData = ByteArray(validBytes)

            if (totalBytesRecorded < bufferSizeBytes) {
                System.arraycopy(circularBuffer, 0, pcmData, 0, validBytes)
            } else {
                val firstPart = bufferSizeBytes - writeOffset
                System.arraycopy(circularBuffer, writeOffset, pcmData, 0, firstPart)
                System.arraycopy(circularBuffer, 0, pcmData, firstPart, writeOffset)
            }

            return writeWavFile(outputFile, pcmData, sampleRate, 1)
        }
    }

    private fun writeWavFile(outputFile: File, pcmData: ByteArray, sampleRate: Int, channels: Int): Boolean {
        return try {
            FileOutputStream(outputFile).use { out ->
                val totalDataLen = pcmData.size + 36
                val byteRate = sampleRate * channels * bytesPerSample

                val header = ByteArray(44)
                header[0] = 'R'.code.toByte()
                header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte()
                header[3] = 'F'.code.toByte()
                header[4] = (totalDataLen and 0xff).toByte()
                header[5] = ((totalDataLen shr 8) and 0xff).toByte()
                header[6] = ((totalDataLen shr 16) and 0xff).toByte()
                header[7] = ((totalDataLen shr 24) and 0xff).toByte()
                header[8] = 'W'.code.toByte()
                header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte()
                header[11] = 'E'.code.toByte()
                header[12] = 'f'.code.toByte()
                header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte()
                header[15] = ' '.code.toByte()
                header[16] = 16
                header[17] = 0
                header[18] = 0
                header[19] = 0
                header[20] = 1
                header[21] = 0
                header[22] = channels.toByte()
                header[23] = 0
                header[24] = (sampleRate and 0xff).toByte()
                header[25] = ((sampleRate shr 8) and 0xff).toByte()
                header[26] = ((sampleRate shr 16) and 0xff).toByte()
                header[27] = ((sampleRate shr 24) and 0xff).toByte()
                header[28] = (byteRate and 0xff).toByte()
                header[29] = ((byteRate shr 8) and 0xff).toByte()
                header[30] = ((byteRate shr 16) and 0xff).toByte()
                header[31] = ((byteRate shr 24) and 0xff).toByte()
                header[32] = (channels * bytesPerSample).toByte()
                header[33] = 0
                header[34] = (bytesPerSample * 8).toByte()
                header[35] = 0
                header[36] = 'd'.code.toByte()
                header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte()
                header[39] = 'a'.code.toByte()
                header[40] = (pcmData.size and 0xff).toByte()
                header[41] = ((pcmData.size shr 8) and 0xff).toByte()
                header[42] = ((pcmData.size shr 16) and 0xff).toByte()
                header[43] = ((pcmData.size shr 24) and 0xff).toByte()

                out.write(header, 0, 44)
                out.write(pcmData)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}