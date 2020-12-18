package com.reactnativegooglecloudspeechtotext

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
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

  override fun getName(): String {
    return TAG
  }

  // See https://facebook.github.io/react-native/docs/native-modules-android

  @ReactMethod
  fun start(promise: Promise) {
    Log.i(TAG, "start: ")
    if (speechService == null) {
      // Start listening to voices
      when {
        ActivityCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.RECORD_AUDIO)
          == PackageManager.PERMISSION_GRANTED -> {
          Log.i(TAG, "PERMISSION_GRANTED")

          val serviceIntent = Intent(reactApplicationContext, SpeechService::class.java)
          reactApplicationContext.bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE)

          startVoiceRecorder()
          promise.resolve(true)
        }
        else -> {
          promise.reject(ERROR_CODE, "AUDIO_PERMISSION_REQUIRED")
//          reactApplicationContext.currentActivity?.let {
//            ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO),
//              REQUEST_RECORD_AUDIO_PERMISSION)
//          }
        }
      }
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
        voiceStartJSCallback?.invoke(params)

        mSpeechService?.startRecognizing(mVoiceRecorder!!.sampleRate)
      }
    }

    override fun onVoice(data: ByteArray?, size: Int) {
      Log.i(TAG, "onVoice: ")
      // TODO: send JS voice event
      if (mSpeechService != null) {
        val params = Arguments.createMap()
        params.putInt("size", size)
        voiceChangeJSCallback?.invoke(params)

        mSpeechService?.recognize(data, size)
      }
    }

    override fun onVoiceEnd() {
      Log.i(TAG, "onVoiceEnd: ")
      // TODO: send JS voice end event
      if (mSpeechService != null) {
        // val params = Arguments.createMap()
        voiceEndJSCallback?.invoke()

        mSpeechService?.finishRecognizing()
      }
    }
  }

  private val mServiceConnection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
      Log.i(TAG, "onServiceConnected: ")
      mSpeechService = SpeechService.from(binder)
      mSpeechService?.addListener(mSpeechServiceListener)
    }

    override fun onServiceDisconnected(componentName: ComponentName) {
      mSpeechService = null
    }
  }

  private fun startVoiceRecorder() {
    Log.i(TAG, "startVoiceRecorder: ")
    if (mVoiceRecorder != null) {
      mVoiceRecorder?.stop()
    }
    mVoiceRecorder = VoiceRecorder(mVoiceCallback)
    mVoiceRecorder?.start()
  }

  private fun stopVoiceRecorder() {
    if (mVoiceRecorder != null) {
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
        speechRecognizedJSCallback?.invoke(params)
      }
    }
  }

}
