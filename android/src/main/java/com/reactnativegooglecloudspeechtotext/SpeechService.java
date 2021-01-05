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

package com.reactnativegooglecloudspeechtotext;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechGrpc;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.okhttp.OkHttpChannelProvider;
import io.grpc.stub.StreamObserver;


public class SpeechService extends Service {

  public interface Listener {

    /**
     * Called when a new piece of text was recognized by the Speech API.
     *
     * @param text    The text.
     * @param isFinal {@code true} when the API finished processing audio.
     */
    void onSpeechRecognized(String text, boolean isFinal);
  }

  private static final String TAG = "SpeechService";
  private static final String HOSTNAME = "speech.googleapis.com";
  private static final int PORT = 443;
  private final SpeechBinder mBinder = new SpeechBinder();
  private final ArrayList<Listener> mListeners = new ArrayList<>();
  private SpeechGrpc.SpeechStub mApi;
  private Boolean isRecognizing;

  private final StreamObserver<StreamingRecognizeResponse> mResponseObserver
    = new StreamObserver<StreamingRecognizeResponse>() {
    @Override
    public void onNext(StreamingRecognizeResponse response) {
      Log.i(TAG, "onNext: " + response);
      String text = null;
      boolean isFinal = false;
      if (response.getResultsCount() > 0) {
        final StreamingRecognitionResult result = response.getResults(0);
        isFinal = result.getIsFinal();
        if (result.getAlternativesCount() > 0) {
          final SpeechRecognitionAlternative alternative = result.getAlternatives(0);
          text = alternative.getTranscript();
        }
      }
      if (text != null) {
        for (Listener listener : mListeners) {
          listener.onSpeechRecognized(text, isFinal);
        }
      }
    }

    @Override
    public void onError(Throwable t) {
      Log.e(TAG, "Error calling the API.", t);
      isRecognizing = false;
    }

    @Override
    public void onCompleted() {
      Log.i(TAG, "API completed.");
      isRecognizing = false;
    }

  };

  private final StreamObserver<RecognizeResponse> mFileResponseObserver
    = new StreamObserver<RecognizeResponse>() {
    @Override
    public void onNext(RecognizeResponse response) {
      Log.i(TAG, "onNext: " + response);
      String text = null;
      if (response.getResultsCount() > 0) {
        final SpeechRecognitionResult result = response.getResults(0);
        if (result.getAlternativesCount() > 0) {
          final SpeechRecognitionAlternative alternative = result.getAlternatives(0);
          text = alternative.getTranscript();
        }
      }
      if (text != null) {
        for (Listener listener : mListeners) {
          listener.onSpeechRecognized(text, true);
        }
      }
    }

    @Override
    public void onError(Throwable t) {
      Log.e(TAG, "Error calling the API.", t);
      isRecognizing = false;
    }

    @Override
    public void onCompleted() {
      Log.i(TAG, "API completed.");
      isRecognizing = false;
    }

  };

  private StreamObserver<StreamingRecognizeRequest> mRequestObserver;

  public static com.reactnativegooglecloudspeechtotext.SpeechService from(IBinder binder) {
    return ((SpeechBinder) binder).getService();
  }

  @Override
  public void onCreate() {
    super.onCreate();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    // Release the gRPC channel.
    if (mApi != null) {
      final ManagedChannel channel = (ManagedChannel) mApi.getChannel();
      if (channel != null && !channel.isShutdown()) {
        try {
          channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Log.e(TAG, "Error shutting down the gRPC channel.", e);
        }
      }
      mApi = null;
      isRecognizing = false;
    }
    Log.i(TAG, "onDestroy: Services disconnected!");
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }

  public void addListener(@NonNull Listener listener) {
    mListeners.add(listener);
  }

  public void removeListener(@NonNull Listener listener) {
    mListeners.remove(listener);
  }

  /**
   * Starts recognizing speech audio.
   *
   * @param sampleRate The sample rate of the audio.
   */
  public void startRecognizing(int sampleRate, String apiKey, String languageCode) {
    Log.i(TAG, "startRecognizing with api: " + apiKey);
    isRecognizing = true;
    final ManagedChannel channel = new OkHttpChannelProvider()
      .builderForAddress(HOSTNAME, PORT)
      .nameResolverFactory(new DnsNameResolverProvider())
      .intercept(new GoogleCredentialsInterceptor(apiKey))
      .build();
    mApi = SpeechGrpc.newStub(channel);

    // Configure the API
    Log.d(TAG, "startRecognizing: ");
    mRequestObserver = mApi.streamingRecognize(mResponseObserver);
    mRequestObserver.onNext(StreamingRecognizeRequest.newBuilder()
      .setStreamingConfig(StreamingRecognitionConfig.newBuilder()
        .setConfig(RecognitionConfig.newBuilder()
          .setLanguageCode(languageCode)
          .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
          .setSampleRateHertz(sampleRate)
          .build())
        .setInterimResults(true)
        .setSingleUtterance(true)
        .build())
      .build());
  }

  /**
   * Recognizes the speech audio. This method should be called every time a chunk of byte buffer
   * is ready.
   *
   * @param data The audio data.
   * @param size The number of elements that are actually relevant in the {@code data}.
   */
  public void recognize(byte[] data, int size) {
    if (mRequestObserver == null) {
      return;
    }
    // Call the streaming recognition API
    mRequestObserver.onNext(StreamingRecognizeRequest.newBuilder()
      .setAudioContent(ByteString.copyFrom(data, 0, size))
      .build());
  }

  /**
   * Finishes recognizing speech audio.
   */
  public void finishRecognizing() {
    if (mRequestObserver == null) {
      return;
    }
    mRequestObserver.onCompleted();
    mRequestObserver = null;
  }

  /**
   * Recognize all data from the specified {@link InputStream}.
   *
   * @param stream The audio data.
   */
  public void recognizeInputStream(InputStream stream) {
    try {
      mApi.recognize(
        RecognizeRequest.newBuilder()
          .setConfig(RecognitionConfig.newBuilder()
            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
            .setLanguageCode("en-US")
            .setSampleRateHertz(16000)
            .build())
          .setAudio(RecognitionAudio.newBuilder()
            .setContent(ByteString.readFrom(stream))
            .build())
          .build(),
        mFileResponseObserver);
    } catch (IOException e) {
      Log.e(TAG, "Error loading the input", e);
    }
  }

  private class SpeechBinder extends Binder {
    com.reactnativegooglecloudspeechtotext.SpeechService getService() {
      return com.reactnativegooglecloudspeechtotext.SpeechService.this;
    }
  }

  /**
   * Authenticates the gRPC channel using the specified {@link GoogleCredentials}.
   */
  private static class GoogleCredentialsInterceptor implements ClientInterceptor {
    private final String apiKey;

    GoogleCredentialsInterceptor(String apiKey) {
      this.apiKey = apiKey;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(final MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, final Channel next) {
      return new ClientInterceptors
        .CheckedForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
        @Override
        protected void checkedStart(Listener<RespT> responseListener,
                                    Metadata headers) {
          Metadata newHeaders = new Metadata();
          Metadata.Key<String> headerKey = Metadata.Key.of("X-Goog-Api-Key", Metadata.ASCII_STRING_MARSHALLER);
          newHeaders.put(headerKey, apiKey);
          delegate().start(responseListener, newHeaders);
        }
      };
    }
  }

}
