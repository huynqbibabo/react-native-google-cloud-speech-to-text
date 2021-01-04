package com.reactnativegooglecloudspeechtotext

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter

class GoogleCloudSpeechToTextModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val TAG = "GoogleCloudSpeechToText";
  private var mSpeechService: SpeechService? = null
  private var mVoiceRecorder: VoiceRecorder? = null
  private var speechService: SpeechService? = null
  private var apiKey: String = "";
  private var serviceConnected = false;

  private val documentDirectoryPath = reactApplicationContext.filesDir.absolutePath
  private val mTempPaths: MutableMap<Long, String> = TODO()

  object ErrorCode {
    const val Unknown = "-1"
    const val None = "0"
    const val PermissionDenied = "1"
    const val ApiKeyMissing = "2"
  }

  override fun getName(): String {
    return TAG
  }

  @ReactMethod
  fun setApiKey(key: String) {
    Log.i(TAG, "setApiKey: $key")
    apiKey = key
  }

  @ReactMethod
  fun start(withVoiceOutPut: Boolean?, promise: Promise) {
    var fileId: Long? = null
    var filePath: String? = null
    if (withVoiceOutPut == true) {
      fileId = System.currentTimeMillis()
      filePath = "$documentDirectoryPath/$fileId.pcm"
      mTempPaths[fileId] = filePath
    }
    try {
      if (speechService == null) {
        // Start listening to voices
        if (apiKey === "" ) {
          val keyId = reactApplicationContext.resources.getIdentifier("google_api_key", "string", reactApplicationContext.packageName)
          apiKey = reactApplicationContext.resources.getString(keyId)
        }
        val serviceIntent = Intent(reactApplicationContext, SpeechService::class.java)
        reactApplicationContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)
        serviceConnected = true
        startVoiceRecorder(filePath)
        promise.resolve(fileId)

      } else {
        promise.reject(ErrorCode.Unknown, "Another instance of SpeechService is already running")
      }
    } catch (e: Exception) {
      handleErrorEvent(e)
      promise.reject(ErrorCode.Unknown, e.message)
    }
  }

  @ReactMethod
  fun stop(fileId: Long?, promise: Promise) {
    stopVoiceRecorder()
    promise.resolve(true)
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

  private val mVoiceCallback: VoiceRecorder.Callback = object : VoiceRecorder.Callback() {
    override fun onVoiceStart() {
      Log.i(TAG, "onVoiceStart: ")
      if (mSpeechService != null) {
        val params = Arguments.createMap()
        params.putInt("sampleRate", mVoiceRecorder!!.sampleRate)
        params.putInt("state", mVoiceRecorder!!.state)
        mSpeechService?.startRecognizing(mVoiceRecorder!!.sampleRate, apiKey)

        sendJSEvent(reactApplicationContext, "onSpeechStart", params)
      }
    }

    override fun onVoice(data: ByteArray?, size: Int) {
      Log.i(TAG, "onVoice: ")
      if (mSpeechService != null) {
        val params = Arguments.createMap()
        params.putInt("size", size)
        mSpeechService?.recognize(data, size)

//        sendJSEvent(reactApplicationContext, "onSpeech", params)
      }
    }

    override fun onVoiceEnd() {
      Log.i(TAG, "onVoiceEnd: ")
      if (mSpeechService != null) {
        val params = Arguments.createMap()
        mSpeechService?.finishRecognizing()
        sendJSEvent(reactApplicationContext, "onVoiceEnd", params)
      }
    }
  }

  private val mServiceConnection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
      Log.i(TAG, "ServiceConnected: ")
      mSpeechService = SpeechService.from(binder)
      mSpeechService?.addListener(mSpeechServiceListener)
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
      Log.i(TAG, "ServiceDisconnected: ")
      mSpeechService = null
    }
  }

  private fun startVoiceRecorder(path: String?) {
    Log.i(TAG, "StartVoiceRecorder: ")
    if (mVoiceRecorder != null) {
      mVoiceRecorder?.stop()
    }
    mVoiceRecorder = VoiceRecorder(mVoiceCallback)
    mVoiceRecorder?.start(path)
  }

  private fun stopVoiceRecorder() {
    if (mVoiceRecorder != null) {
      mVoiceRecorder?.stop()
      mVoiceRecorder = null
    }
  }

  private val mSpeechServiceListener: SpeechService.Listener = SpeechService.Listener { text, isFinal ->
    Log.i(TAG, "onSpeechRecognized: $text")
    if (isFinal) {
      mVoiceRecorder?.dismiss()
      val params = Arguments.createMap()
      params.putString("transcript", text)
      params.putBoolean("isFinal", isFinal)
      sendJSEvent(reactApplicationContext, "onSpeechRecognized", params)
    }
  }


}
