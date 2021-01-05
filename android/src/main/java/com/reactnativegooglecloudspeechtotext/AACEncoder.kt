package com.reactnativegooglecloudspeechtotext

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import com.facebook.react.bridge.ReadableMap
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.*


class AACEncoder(configs: ReadableMap, private val mCallback: Callback) {

  private val TAG = "AACEncoder"

  //Bit rate
  private val KEY_BIT_RATE = 96000

  //The maximum number of bytes of data read
  private val KEY_MAX_INPUT_SIZE = 1024 * 1024

  //Number of channels
  private val CHANNEL_COUNT = 2

  private var mediaCodec: MediaCodec? = null;
  private var encodeInputBuffers: Array<ByteBuffer>? = null
  private var encodeOutputBuffers: Array<ByteBuffer>? = null
  private var encodeBufferInfo: MediaCodec.BufferInfo? = null

  init {
    try {
      //Parameter correspondence -> mime type, sampling rate, number of channels
      val encodeFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, configs.getInt("sampleRate"), CHANNEL_COUNT)
      //Bit rate
      encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, KEY_BIT_RATE)
      encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, KEY_MAX_INPUT_SIZE)
      mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
      mediaCodec!!.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

      mediaCodec!!.start()
      encodeBufferInfo = MediaCodec.BufferInfo()
    } catch (e: Exception) {
      Log.e(TAG, "AACEncoder: ", e)
    }
  }

  /**
   * @param data
   */
  fun encodeData(data: ByteArray) {
    //dequeueInputBuffer(time) needs to pass in a time value, -1 means waiting forever, 0 means not waiting, there may be frame loss, others means how many milliseconds to wait
    //Get the index of the input buffer
    val inputIndex = mediaCodec!!.dequeueInputBuffer(-1)
    if (inputIndex >= 0) {
      val inputByteBuf: ByteBuffer = encodeInputBuffers!![inputIndex]
      inputByteBuf.clear()
      //adding data
      inputByteBuf.put(data)
      //Limit the access length of ByteBuffer
      inputByteBuf.limit(data.size)
      //Push the input buffer back to MediaCodec
      mediaCodec!!.queueInputBuffer(inputIndex, 0, data.size, 0, 0)
    }
    //Get the index of the output cache
    var outputIndex = mediaCodec!!.dequeueOutputBuffer(encodeBufferInfo!!, 0)
    while (outputIndex >= 0) {
      //Get the length of the cache information
      val byteBufSize = encodeBufferInfo!!.size
      //Add the length after the ADTS header
      val bytePacketSize = byteBufSize + 7
      //Get the output Buffer
      val outPutBuf: ByteBuffer = encodeOutputBuffers!![outputIndex]
      outPutBuf.position(encodeBufferInfo!!.offset)
      outPutBuf.limit(encodeBufferInfo!!.offset + encodeBufferInfo!!.size)
      val aacData = ByteArray(bytePacketSize)
      //Add ADTS header
      addADTStoPacket(aacData, bytePacketSize)
      /**
       * get(byte[] dst, int offset, int length): ByteBuffer is read from the position, length bytes are read, and written to dst
       * Mark the area from offset to offset + length
       */
      outPutBuf.get(aacData, 7, byteBufSize)
      outPutBuf.position(encodeBufferInfo!!.offset)

      //Encoding success
      mCallback.onByte(aacData)

      //freed
      mediaCodec!!.releaseOutputBuffer(outputIndex, false)
      outputIndex = mediaCodec!!.dequeueOutputBuffer(encodeBufferInfo!!, 0)
    }
  }

  /**
   * Add ADTS header
   */
  private fun addADTStoPacket(packet: ByteArray, packetLen: Int) {
    // AAC LC
    val profile = 2
    // 44.1KHz
    val freqIdx = 4
    // CPE
    val chanCfg = 2
    // fill in ADTS data
    packet[0] = 0xFF.toByte()
    packet[1] = 0xF9.toByte()
    packet[2] = ((profile - 1 shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
    packet[3] = ((chanCfg and 3 shl 6) + (packetLen shr 11)).toByte()
    packet[4] = (packetLen and 0x7FF shr 3).toByte()
    packet[5] = ((packetLen and 7 shl 5) + 0x1F).toByte()
    packet[6] = 0xFC.toByte()
  }

  abstract class Callback {
    open fun onByte(data: ByteArray?){}
  }
}
