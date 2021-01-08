/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reactnativegooglecloudspeechtotext

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.google.cloud.speech.v1.*
import com.google.cloud.speech.v1.SpeechGrpc.SpeechStub
import com.google.protobuf.ByteString
import io.grpc.*
import io.grpc.ClientInterceptors.CheckedForwardingClientCall
import io.grpc.internal.DnsNameResolverProvider
import io.grpc.okhttp.OkHttpChannelProvider
import io.grpc.stub.StreamObserver
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit


class SpeechService : Service() {
  abstract class Listener {
    /**
     * Called when a new piece of text was recognized by the Speech API.
     *
     * @param text    The text.
     * @param isFinal `true` when the API finished processing audio.
     */
    open fun onSpeechRecognized(text: String?, isFinal: Boolean){}
  }

  private val mBinder = SpeechBinder()
  private val mListeners = ArrayList<Listener>()
  private var mApi: SpeechStub? = null
  private var isRecognizing: Boolean? = null
  private val mResponseObserver: StreamObserver<StreamingRecognizeResponse> = object : StreamObserver<StreamingRecognizeResponse> {
    override fun onNext(response: StreamingRecognizeResponse) {
      Log.d(TAG, "onNext: $response")
      var text: String? = null
      var isFinal = false
      if (response.resultsCount > 0) {
        val result = response.getResults(0)
        isFinal = result.isFinal
        if (result.alternativesCount > 0) {
          val alternative = result.getAlternatives(0)
          text = alternative.transcript
        }
      }
      if (text != null) {
        for (listener in mListeners) {
          listener.onSpeechRecognized(text, isFinal)
        }
      }
    }

    override fun onError(t: Throwable) {
      Log.e(TAG, "Error calling the API.", t)
      isRecognizing = false
    }

    override fun onCompleted() {
      Log.d(TAG, "API completed.")
      isRecognizing = false
    }
  }
  private val mFileResponseObserver: StreamObserver<RecognizeResponse> = object : StreamObserver<RecognizeResponse> {
    override fun onNext(response: RecognizeResponse) {
      Log.d(TAG, "onNext: $response")
      var text: String? = null
      if (response.resultsCount > 0) {
        val result = response.getResults(0)
        if (result.alternativesCount > 0) {
          val alternative = result.getAlternatives(0)
          text = alternative.transcript
        }
      }
      if (text != null) {
        for (listener in mListeners) {
          listener.onSpeechRecognized(text, true)
        }
      }
    }

    override fun onError(t: Throwable) {
      Log.e(TAG, "Error calling the API.", t)
      isRecognizing = false
    }

    override fun onCompleted() {
      Log.d(TAG, "API completed.")
      isRecognizing = false
    }
  }
  private var mRequestObserver: StreamObserver<StreamingRecognizeRequest>? = null

  override fun onDestroy() {
    super.onDestroy()
    // Release the gRPC channel.
    if (mApi != null) {
      val channel = mApi!!.channel as ManagedChannel
      if (!channel.isShutdown) {
        try {
          channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
          Log.e(TAG, "Error shutting down the gRPC channel.", e)
        }
      }
      mApi = null
      isRecognizing = false
    }
    Log.d(TAG, "onDestroy: Services disconnected!")
  }

  override fun onBind(intent: Intent): IBinder {
    return mBinder
  }

  fun addListener(listener: Listener) {
    mListeners.add(listener)
  }

  fun removeListener(listener: Listener) {
    mListeners.remove(listener)
  }

  /**
   * Starts recognizing speech audio.
   *
   * @param sampleRate The sample rate of the audio.
   */
  fun startRecognizing(sampleRate: Int, apiKey: String, languageCode: String) {
    isRecognizing = true
    NameResolverRegistry.getDefaultRegistry().register(DnsNameResolverProvider())
    val channel = OkHttpChannelProvider()
      .builderForAddress(HOSTNAME, PORT)
      .intercept(GoogleCredentialsInterceptor(apiKey))
      .build()
    mApi = SpeechGrpc.newStub(channel)

    // Configure the API
    mRequestObserver = mApi!!.streamingRecognize(mResponseObserver)
    mRequestObserver!!.onNext(StreamingRecognizeRequest.newBuilder()
      .setStreamingConfig(StreamingRecognitionConfig.newBuilder()
        .setConfig(RecognitionConfig.newBuilder()
          .setLanguageCode(languageCode)
          .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
          .setSampleRateHertz(sampleRate)
          .build())
        .setInterimResults(true)
        .setSingleUtterance(true)
        .build())
      .build())
  }

  /**
   * Recognizes the speech audio. This method should be called every time a chunk of byte buffer
   * is ready.
   *
   * @param data The audio data.
   * @param size The number of elements that are actually relevant in the `data`.
   */
  fun recognize(data: ByteArray?, size: Int) {
    if (mRequestObserver == null) {
      return
    }
    // Call the streaming recognition API
    mRequestObserver!!.onNext(StreamingRecognizeRequest.newBuilder()
      .setAudioContent(ByteString.copyFrom(data, 0, size))
      .build())
  }

  /**
   * Finishes recognizing speech audio.
   */
  fun finishRecognizing() {
    if (mRequestObserver == null) {
      return
    }
    mRequestObserver!!.onCompleted()
    mRequestObserver = null
  }

  /**
   * Recognize all data from the specified [InputStream].
   *
   * @param stream The audio data.
   */
  fun recognizeInputStream(stream: InputStream, apiKey: String, languageCode: String) {
    try {
      if (mApi == null) {
        NameResolverRegistry.getDefaultRegistry().register(DnsNameResolverProvider())
        val channel = OkHttpChannelProvider()
          .builderForAddress(HOSTNAME, PORT)
          .intercept(GoogleCredentialsInterceptor(apiKey))
          .build()
        mApi = SpeechGrpc.newStub(channel)
      }
      isRecognizing = true
      mApi!!.recognize(
        RecognizeRequest.newBuilder()
          .setConfig(RecognitionConfig.newBuilder()
            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
            .setLanguageCode(languageCode)
            .setSampleRateHertz(16000)
            .build())
          .setAudio(RecognitionAudio.newBuilder()
            .setContent(ByteString.readFrom(stream))
            .build())
          .build(),
        mFileResponseObserver)
    } catch (e: IOException) {
      Log.e(TAG, "Error loading the input", e)
    }
  }

  private inner class SpeechBinder : Binder() {
    val service: SpeechService
      get() = this@SpeechService
  }

  /**
   * Authenticates the gRPC channel using the specified apiKey
   */
  private class GoogleCredentialsInterceptor(private val apiKey: String) : ClientInterceptor {
    override fun <ReqT, RespT> interceptCall(method: MethodDescriptor<ReqT, RespT>, callOptions: CallOptions, next: Channel): ClientCall<ReqT, RespT> {
      return object : CheckedForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
        override fun checkedStart(responseListener: Listener<RespT>,
                                  headers: Metadata) {
          val newHeaders = Metadata()
          val headerKey = Metadata.Key.of("X-Goog-Api-Key", Metadata.ASCII_STRING_MARSHALLER)
          newHeaders.put(headerKey, apiKey)
          delegate().start(responseListener, newHeaders)
        }
      }
    }
  }

  companion object {
    private const val TAG = "SpeechService"
    private const val HOSTNAME = "speech.googleapis.com"
    private const val PORT = 443
    fun from(binder: IBinder): SpeechService {
      return (binder as SpeechBinder).service
    }
  }
}
