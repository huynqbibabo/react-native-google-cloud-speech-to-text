package com.reactnativegooglecloudspeechtotext

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.IBinder
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream


class GoogleCloudSpeechToTextModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val TAG = "GoogleCloudSpeechToText"
  private var mSpeechService: SpeechService? = null
  private var mVoiceRecorder: VoiceRecorder? = null
  private var speechService: SpeechService? = null
  private var apiKey: String = ""
  private var serviceConnected = false
  private var languageCode: String = "en-US"

  private var mTempFiles: MutableMap<String, File> = HashMap()

  companion object ErrorCode {
    const val Unknown = "-1"
    const val FileMismatch = "3"
  }

  override fun getName(): String {
    return TAG
  }

  @ReactMethod
  fun setApiKey(key: String) {
    Log.d(TAG, "setApiKey: $key")
    apiKey = key
  }

  @ReactMethod
  fun start(options: ReadableMap, promise: Promise) {
    try {
      languageCode = options.getString("languageCode").toString()
      var fileId = ""
      var file: File? = null
      val speechToFile: Boolean = options.getBoolean("speechToFile")
      if (speechToFile) {
        fileId = System.currentTimeMillis().toString()
        file = buildFile(fileId, "pcm")
        Log.d(TAG, "start: $fileId")
        Log.d(TAG, "start: $file")
        mTempFiles[fileId] = file
      }

      if (speechService == null) {
        // Start listening to voices
        if (apiKey === "" ) {
          val keyId = reactApplicationContext.resources.getIdentifier("google_api_key", "string", reactApplicationContext.packageName)
          apiKey = reactApplicationContext.resources.getString(keyId)
        }
        val serviceIntent = Intent(reactApplicationContext, SpeechService::class.java)
        reactApplicationContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
        serviceConnected = true
//        startVoiceRecorder(null)
        startVoiceRecorder(file)
        val params = Arguments.createMap()
        params.putString("fileId", fileId)
        params.putString("tmpPath", file?.path)
        promise.resolve(params)

      } else {
        promise.reject(Unknown, "Another instance of SpeechService is already running")
      }
    } catch (e: Exception) {
      Log.e(TAG, "start: ", e)
      handleErrorEvent(e)
      promise.reject(Unknown, e.message)
    }
  }

  @ReactMethod
  fun stop(promise: Promise) {
    stopVoiceRecorder()
    promise.resolve(true)
  }

  @ReactMethod
  fun getAudioFile(fileId: String, configs: ReadableMap, promise: Promise) {
    try {
      val tmpFile = mTempFiles[fileId]
      if (tmpFile?.isFile != true) {
        promise.reject(FileMismatch, "File not found!")
        return
      }
      val inputStream = FileInputStream(tmpFile)
      val outPutFile = buildFile(fileId, "aac")
      val outputStream = FileOutputStream(outPutFile)
      val bufferSize = AudioRecord.getMinBufferSize(configs.getInt("sampleRate"), configs.getInt("channel"), AudioFormat.ENCODING_PCM_16BIT)
      val data = ByteArray(bufferSize)
      val encoderCallback: AACEncoder.Callback = object: AACEncoder.Callback() {
        override fun onByte(data: ByteArray?) {
          outputStream.write(data)
        }
      }
      val encoder = AACEncoder(configs, encoderCallback)
      while (inputStream.read(data) != -1) {
        encoder.encodeData(data)
      }
      Log.d(TAG, "file path: ${outPutFile.absolutePath}")
      Log.d(TAG, "file size:" + outputStream.channel.size())

      val params = Arguments.createMap()
      params.putDouble("size", outputStream.channel.size().toDouble())
      params.putString("path", outPutFile.absolutePath)

      inputStream.close()
      outputStream.close()
      mTempFiles.remove(fileId)
      tmpFile.delete()

      promise.resolve(params)
    } catch (e: Exception) {
      e.printStackTrace()
      promise.reject(e)
    }
  }

  @ReactMethod
  fun destroy(promise: Promise) {
    stopVoiceRecorder()
    // Stop Cloud Speech API
    if (mSpeechService !== null) {
      mSpeechService?.removeListener(mSpeechServiceListener)
      mSpeechService = null
    }
    if (serviceConnected) {
      reactApplicationContext.unbindService(mServiceConnection)
      serviceConnected = false
    }
    if (mTempFiles.isNotEmpty()) {
      mTempFiles.forEach(action = { it.value.delete()})
      mTempFiles.clear()
    }
    promise.resolve(true)
  }

  private fun handleErrorEvent(throwable: Throwable) {
    sendJSErrorEvent(throwable.message)
  }

  private fun sendJSErrorEvent(message: String?) {
    val params = Arguments.createMap()
    params.putString("message", message)
    sendJSEvent(reactApplicationContext, "onSpeechError", params)
  }

  private fun sendJSEvent(reactContext: ReactContext,
                          eventName: String,
                          params: WritableMap) {
    reactContext
      .getJSModule(RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  private val mVoiceCallback: VoiceRecorder.Callback = object: VoiceRecorder.Callback() {
    override fun onVoiceStart() {
      Log.d(TAG, "onVoiceStart: ")
      if (mSpeechService != null) {
        val params = Arguments.createMap()
        params.putInt("sampleRate", mVoiceRecorder!!.sampleRate)
        params.putInt("voiceRecorderState", mVoiceRecorder!!.state)
        sendJSEvent(reactApplicationContext, "onVoiceStart", params)
        mSpeechService!!.startRecognizing(mVoiceRecorder!!.sampleRate, apiKey, languageCode)

      }
    }

    override fun onVoice(data: ByteArray?, size: Int) {
      Log.d(TAG, "onVoice: ")
      if (mSpeechService != null) {
        val params = Arguments.createMap()
        params.putInt("size", size)
        sendJSEvent(reactApplicationContext, "onVoice", params)
        mSpeechService?.recognize(data, size)
      }
    }

    override fun onVoiceEnd() {
      Log.d(TAG, "onVoiceEnd: ")
      val params = Arguments.createMap()
      sendJSEvent(reactApplicationContext, "onVoiceEnd", params)
      if (mSpeechService != null) {
        mSpeechService!!.finishRecognizing()
      }
    }
  }

  private val mServiceConnection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
      Log.d(TAG, "ServiceConnected: ")
      mSpeechService = SpeechService.from(binder)
      mSpeechService?.addListener(mSpeechServiceListener)
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
      Log.d(TAG, "ServiceDisconnected: ")
      mSpeechService = null
    }
  }

  private fun startVoiceRecorder(file: File?) {
    Log.d(TAG, "StartVoiceRecorder: ")
    if (mVoiceRecorder != null) {
      mVoiceRecorder!!.stop()
    }
    mVoiceRecorder = VoiceRecorder(mVoiceCallback)
    mVoiceRecorder!!.start(file)
  }

  private fun stopVoiceRecorder() {
    if (mVoiceRecorder != null) {
      mVoiceRecorder!!.stop()
      mVoiceRecorder = null
    }
  }

  private val mSpeechServiceListener: SpeechService.Listener = object: SpeechService.Listener() {
    override fun onSpeechRecognized(text: String?, isFinal: Boolean) {
      Log.d(TAG, "onSpeechRecognized: $text")

      val params = Arguments.createMap()
      params.putString("transcript", text)
      params.putBoolean("isFinal", isFinal)
      if (isFinal) {
        sendJSEvent(reactApplicationContext, "onSpeechRecognized", params)
        mVoiceRecorder?.dismiss()
      } else {
        sendJSEvent(reactApplicationContext, "onSpeechRecognizing", params)
      }
    }
  }

  private fun buildFile(fileId: String, ext: String): File {
    return File(reactApplicationContext.cacheDir, "$fileId.$ext")
  }

  private fun deleteTempFile(tmpPath: String) {
    val file = File(tmpPath)
    file.delete()
  }
}
