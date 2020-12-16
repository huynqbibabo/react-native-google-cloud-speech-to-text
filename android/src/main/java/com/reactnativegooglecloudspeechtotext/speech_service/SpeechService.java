package com.reactnativegooglecloudspeechtotext.speech_service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechGrpc;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.internal.DnsNameResolverProvider;
import io.grpc.okhttp.OkHttpChannelProvider;
import io.grpc.stub.StreamObserver;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;

public class SpeechService extends Service {

    private static final String TAG = "SpeechService";
    private static final List<String> SCOPE =
            Collections.singletonList("https://www.googleapis.com/auth/cloud-platform");
    private static final String HOSTNAME = "speech.googleapis.com";
    private static final int PORT = 443;
    private static final String LANGUAGE_CODE = "ja-JP";
    private static final int TERMINATION_TIMEOUT_SECONDS = 5;

    private final SpeechBinder binder = new SpeechBinder();
    private SpeechGrpc.SpeechStub speechStub;
    private StreamObserver<StreamingRecognizeRequest> requestObserver;
    private PublishSubject<SpeechEvent> speechEventPublishSubject = PublishSubject.create();
    private PublishSubject<Throwable> speechErrorEventPublishSubject = PublishSubject.create();

    private final StreamObserver<StreamingRecognizeResponse> responseObserver
            = new StreamObserver<StreamingRecognizeResponse>() {
        @Override
        public void onNext(StreamingRecognizeResponse response) {
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
                speechEventPublishSubject.onNext(new SpeechEvent(text, isFinal));
            }
        }

        @Override
        public void onError(Throwable t) {
            speechErrorEventPublishSubject.onNext(t);
        }

        @Override
        public void onCompleted() {
            Log.i(TAG, "API completed.");
        }

    };

    public static SpeechService from(IBinder binder) {
        return ((SpeechBinder) binder).getService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Release the gRPC channel.
        if (speechStub != null) {
            final ManagedChannel channel = (ManagedChannel) speechStub.getChannel();
            if (channel != null && !channel.isShutdown()) {
                try {
                    channel.shutdown()
                            .awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    speechErrorEventPublishSubject.onNext(e);
                }
            }
            speechStub = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public Observable<SpeechEvent> getSpeechEventObservable() {
        return speechEventPublishSubject;
    }

    public Observable<Throwable> getSpeechErrorEventObservable() {
        return speechErrorEventPublishSubject;
    }

    /**
     * Starts recognizing speech audio.
     *
     * @param sampleRate The sample rate of the audio.
     */
    public void startRecognizing(int sampleRate, String apiKey, String languageCode) {
        final ManagedChannel channel = new OkHttpChannelProvider()
                .builderForAddress(HOSTNAME, PORT)
                .nameResolverFactory(new DnsNameResolverProvider())
                .intercept(new GoogleCredentialsInterceptor(apiKey))
                .build();
        speechStub = SpeechGrpc.newStub(channel);
        requestObserver = speechStub.streamingRecognize(responseObserver);
        requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
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
        if (requestObserver == null) {
            return;
        }
        // Call the streaming recognition API
        requestObserver.onNext(StreamingRecognizeRequest.newBuilder()
                .setAudioContent(ByteString.copyFrom(data, 0, size))
                .build());
    }

    /**
     * Finishes recognizing speech audio.
     */
    public void finishRecognizing() {
        if (requestObserver == null) {
            return;
        }
        requestObserver.onCompleted();
        requestObserver = null;
    }

    private class SpeechBinder extends Binder {

        SpeechService getService() {
            return SpeechService.this;
        }

    }
}
