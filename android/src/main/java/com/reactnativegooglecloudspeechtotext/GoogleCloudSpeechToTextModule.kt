package com.reactnativegooglecloudspeechtotext

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.os.IBinder
import android.util.Log
import android.util.TypedValue.TYPE_STRING
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter

class GoogleCloudSpeechToTextModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val TAG = "GoogleCloudSpeechToText";

  private val SUCCESS_CODE = "1";
  private val ERROR_CODE = "0";


  private var mSpeechService: SpeechService? = null

  private var mVoiceRecorder: VoiceRecorder? = null

  private var speechService: SpeechService? = null

  private var voiceStartJSCallback: Callback? = null
  private var voiceChangeJSCallback: Callback? = null
  private var voiceEndJSCallback: Callback? = null
  private var speechRecognizedJSCallback: Callback? = null
  private var apiKey: String = "";

  override fun getName(): String {
    return TAG
  }

  public fun setApiKey(key: String ) {
    Log.i(TAG, "setApiKey: $key")
    apiKey = key
  }

  @ReactMethod
  fun start(promise: Promise) {
    Log.i(TAG, "start: ")
    if (speechService == null) {
      // Start listening to voices
      val serviceIntent = Intent(reactApplicationContext, SpeechService::class.java)
      reactApplicationContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

      startVoiceRecorder()
      promise.resolve(true)

    } else {
      promise.reject(ERROR_CODE, "Unknow error!")
    }
  }

  @ReactMethod
  fun stop() {
    Log.i(TAG, "stop: ")
    stopVoiceRecorder()

    // Stop Cloud Speech API
    mSpeechService?.removeListener(mSpeechServiceListener)
    reactApplicationContext.unbindService(mServiceConnection)
    mSpeechService = null
  }

  @ReactMethod
  fun onVoiceStart(fn: Callback) {
    voiceStartJSCallback = fn
  }

  @ReactMethod
  fun onVoice(fn: Callback) {
    voiceChangeJSCallback = fn
  }

  @ReactMethod
  fun onVoiceEnd(fn: Callback) {
    voiceEndJSCallback = fn
  }

  @ReactMethod
  fun onSpeechRecognized(fn: Callback) {
    speechRecognizedJSCallback = fn
  }

  private fun handleErrorEvent(throwable: Throwable) {
    sendJSErrorEvent(throwable.message)
  }

  private fun sendJSErrorEvent(message: String?) {
    val params = Arguments.createMap()
    params.putString("message", message)
    sendJSEvent(reactApplicationContext, "onSpeechRecognizedError", params)
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
      // TODO: send JS voice start event
      Log.i(TAG, "onVoiceStart: ")
      if (mSpeechService != null) {
        val params = Arguments.createMap()
        params.putInt("sampleRate", mVoiceRecorder!!.sampleRate)
        params.putInt("state", mVoiceRecorder!!.state)
        sendJSEvent(reactApplicationContext, "onVoiceStart", params)
        // voiceStartJSCallback?.invoke(params)

        mSpeechService?.startRecognizing(mVoiceRecorder!!.sampleRate, apiKey)
      }
    }

    override fun onVoice(data: ByteArray?, size: Int) {
      Log.i(TAG, "onVoice: ")
      // TODO: send JS voice event
      if (mSpeechService != null) {
        val params = Arguments.createMap()
        params.putInt("size", size)
        sendJSEvent(reactApplicationContext, "onVoice", params)
        // voiceChangeJSCallback?.invoke(params)

        mSpeechService?.recognize(data, size)
      }
    }

    override fun onVoiceEnd() {
      Log.i(TAG, "onVoiceEnd: ")
      // TODO: send JS voice end event
      if (mSpeechService != null) {
        val params = Arguments.createMap()
        // voiceEndJSCallback?.invoke()
        sendJSEvent(reactApplicationContext, "onVoiceEnd", params)

        mSpeechService?.finishRecognizing()
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

  private fun startVoiceRecorder() {
    Log.i(TAG, "StartVoiceRecorder: ")
    if (mVoiceRecorder != null) {
      mVoiceRecorder?.stop()
    }
    mVoiceRecorder = VoiceRecorder(mVoiceCallback)
    mVoiceRecorder?.start()
  }

  private fun stopVoiceRecorder() {
    if (mVoiceRecorder != null) {
      Log.i(TAG, "StopVoiceRecorder: ")
      mVoiceRecorder?.stop()
      mVoiceRecorder = null
    }
  }

  private val mSpeechServiceListener: SpeechService.Listener = object : SpeechService.Listener {
    override fun onSpeechRecognized(text: String?, isFinal: Boolean) {
      Log.i(TAG, text)
      if (isFinal) {
        mVoiceRecorder?.dismiss()
        val params = Arguments.createMap()
        params.putString("transcript", text)
        sendJSEvent(reactApplicationContext, "onSpeechRecognized", params)
        // speechRecognizedJSCallback?.invoke(params)
      }
    }
  }

}
