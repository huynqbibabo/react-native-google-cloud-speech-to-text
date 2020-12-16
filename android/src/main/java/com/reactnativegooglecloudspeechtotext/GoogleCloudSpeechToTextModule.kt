package com.reactnativegooglecloudspeechtotext

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.reactnativegooglecloudspeechtotext.speech_service.SpeechEvent
import com.reactnativegooglecloudspeechtotext.speech_service.SpeechService
import com.reactnativegooglecloudspeechtotext.voice_recorder.VoiceEvent
import com.reactnativegooglecloudspeechtotext.voice_recorder.VoiceRecorder
import io.reactivex.disposables.CompositeDisposable

class GoogleCloudSpeechToTextModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val moduleName = "GoogleCloudSpeechToText"
  private val keyText = "text"
  private val keyIsFinal = "isFinal"
  private val keyMessage = "message"
  private val onSpeechRecognized = "onSpeechRecognized"
  private val onSpeechRecognizedError = "onSpeechRecognizedError"

  private val voiceRecorder: VoiceRecorder = VoiceRecorder()
  private var speechService: SpeechService? = null
  private var compositeDisposable: CompositeDisposable? = null
  private var apiKey: String? = null
  private var languageCode: String? = null


  override fun getName(): String {
    return moduleName
  }

  // Example method
  // See https://facebook.github.io/react-native/docs/native-modules-android
  @ReactMethod
  fun multiply(a: Int, b: Int, promise: Promise) {

    promise.resolve(a * b)

  }

  private val serviceConnection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, service: IBinder) {
      speechService = SpeechService.from(service)
      speechService?.speechEventObservable
              ?.subscribe { speechEvent: SpeechEvent? ->
                if (speechEvent != null) {
                  this@GoogleCloudSpeechToTextModule.handleSpeechEvent(speechEvent)
                }
              }?.let {
          compositeDisposable?.add(
            it
          )
        }
      speechService?.speechErrorEventObservable
              ?.doAfterNext { stop() }
              ?.subscribe { throwable: Throwable? ->
                if (throwable != null) {
                  this@GoogleCloudSpeechToTextModule.handleErrorEvent(throwable)
                }
              }?.let {
          compositeDisposable?.add(
            it
          )
        }
      compositeDisposable?.add(
        voiceRecorder.voiceEventObservable
          .subscribe { event: VoiceEvent? ->
            if (event != null) {
              this@GoogleCloudSpeechToTextModule.handleVoiceEvent(event)
            }
          }
      )
      compositeDisposable?.add(
        voiceRecorder.voiceErrorEventObservable
          .doAfterNext { stop() }
          .subscribe { throwable: Throwable? ->
            if (throwable != null) {
              this@GoogleCloudSpeechToTextModule.handleErrorEvent(throwable)
            }
          }
      )
      voiceRecorder.start()
    }

    override fun onServiceDisconnected(name: ComponentName) {
      speechService = null
    }
  }


  @ReactMethod
  fun start() {
    Log.i(moduleName, "start")
    if (apiKey == null) {
      sendJSErrorEvent("call setApiKey() with valid access token before calling start()")
    }
    compositeDisposable?.dispose()
    compositeDisposable = CompositeDisposable()
    if (speechService == null) {
      val serviceIntent = Intent(reactApplicationContext, SpeechService::class.java)
      reactApplicationContext.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    } else {
      sendJSErrorEvent("Another instance of SpeechService is already running")
    }
  }

  @ReactMethod
  fun stop() {
    Log.i(moduleName, "stop")
    voiceRecorder.stop()
    compositeDisposable?.dispose()
    reactApplicationContext.unbindService(serviceConnection)
    speechService = null
  }

  @ReactMethod
  fun init(apiKey: String?, languageCode: String?) {
    Log.i(moduleName, "setApiKey")
    this.apiKey = apiKey
    this.languageCode = languageCode
  }

  @ReactMethod
  fun setApiKey(apiKey: String?) {
    Log.i(moduleName, "setApiKey")
    this.apiKey = apiKey
  }

  private fun handleVoiceEvent(event: VoiceEvent) {
    when (event.state) {
      VoiceEvent.State.START -> onVoiceStart()
      VoiceEvent.State.VOICE -> onVoice(event.data, event.size)
      VoiceEvent.State.END -> onVoiceEnd()
    }
  }

  private fun onVoiceStart() {
    Log.i(moduleName, "onVoiceStart")
    speechService?.startRecognizing(voiceRecorder.sampleRate, apiKey, languageCode)
  }

  private fun onVoice(data: ByteArray, size: Int) {
    Log.i(moduleName, "onVoice")
    speechService?.recognize(data, size)
  }

  private fun onVoiceEnd() {
    Log.i(moduleName, "onVoiceEnd")
    speechService?.finishRecognizing()
  }

  private fun handleSpeechEvent(speechEvent: SpeechEvent) {
    Log.i(moduleName, speechEvent.text.toString() + " " + speechEvent.isFinal)
    val params = Arguments.createMap()
    if (!TextUtils.isEmpty(speechEvent.text)) {
      params.putString(keyText, speechEvent.text)
    } else {
      params.putString(keyText, "")
    }
    params.putBoolean(keyIsFinal, speechEvent.isFinal)
    sendJSEvent(reactApplicationContext, onSpeechRecognized, params)
    if (speechEvent.isFinal) {
      stop()
    }
  }

  private fun handleErrorEvent(throwable: Throwable) {
    sendJSErrorEvent(throwable.message)
  }

  private fun sendJSErrorEvent(message: String?) {
    val params = Arguments.createMap()
    params.putString(keyMessage, message)
    sendJSEvent(reactApplicationContext, onSpeechRecognizedError, params)
  }

  private fun sendJSEvent(reactContext: ReactContext,
                          eventName: String,
                          params: WritableMap) {
    reactContext
      .getJSModule(RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

}
