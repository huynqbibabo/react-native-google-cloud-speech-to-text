package com.reactnativegooglecloudspeechtotext.voice_recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static com.reactnativegooglecloudspeechtotext.voice_recorder.VoiceUtils.isHearingVoice;

public class VoiceRecorder {

    private static final int[] SAMPLE_RATE_CANDIDATES = new int[]{16000, 11025, 22050, 44100};

    private static final int SPEECH_TIMEOUT_MILLIS = 2000;
    private static final int MAX_SPEECH_LENGTH_MILLIS = 30 * 1000;
    private static final int DEFAULT_SAMPLE_RATE = 16000;

    private PublishSubject<VoiceEvent> voiceEventPublishSubject = PublishSubject.create();

    private PublishSubject<Throwable> voiceErrorEventPublishSubject = PublishSubject.create();

    private Disposable voiceEventDisposable;

    private AudioRecord audioRecord;

    private byte[] buffer;

    /**
     * The timestamp of the last time that voice is heard.
     */
    private long lastVoiceHeardMillis = Long.MAX_VALUE;

    /**
     * The timestamp when the current voice is started.
     */
    private long voiceStartedMillis;

    /**
     * Starts recording audio.
     *
     * <p>The caller is responsible for calling {@link #stop()} later.</p>
     */
    public void start() {
        // Stop recording if it is currently ongoing.
        stop();
        // Try to create a new recording session.
        audioRecord = createAudioRecord();
        if (audioRecord == null) {
            voiceErrorEventPublishSubject.onNext(new RuntimeException("Cannot instantiate VoiceRecorder. Probably the android.permission.RECORD_AUDIO was not granted."));
            return;
        }
        // Start recording.
        audioRecord.startRecording();
        // Start processing the captured audio.
        voiceEventDisposable = createVoiceEventObservable()
                .subscribeOn(Schedulers.io())
                .subscribe(voiceEventPublishSubject::onNext);
    }

    /**
     * Stops recording audio.
     */
    public void stop() {
        if (voiceEventDisposable != null) {
            voiceEventDisposable.dispose();
        }
        dismiss();
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        buffer = null;
    }

    /**
     * Dismisses the currently ongoing utterance.
     */
    public void dismiss() {
        if (lastVoiceHeardMillis != Long.MAX_VALUE) {
            lastVoiceHeardMillis = Long.MAX_VALUE;
            voiceEventPublishSubject.onNext(VoiceEvent.end());
        }
    }

    /**
     * Retrieves the sample rate currently used to record audio.
     *
     * @return The sample rate of recorded audio.
     */
    public int getSampleRate() {
        if (audioRecord != null) {
            return audioRecord.getSampleRate();
        }
        return DEFAULT_SAMPLE_RATE;
    }

    public Observable<VoiceEvent> getVoiceEventObservable() {
        return voiceEventPublishSubject;
    }

    public Observable<Throwable> getVoiceErrorEventObservable() {
        return voiceErrorEventPublishSubject;
    }

    /**
     * Creates a new {@link AudioRecord}.
     *
     * @return A newly created {@link AudioRecord}, or null if it cannot be created (missing
     * permissions?).
     */
    private AudioRecord createAudioRecord() {
        for (int sampleRate : SAMPLE_RATE_CANDIDATES) {
            final int sizeInBytes = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            if (sizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
                continue;
            }
            final AudioRecord audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    sizeInBytes
            );
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                buffer = new byte[sizeInBytes];
                return audioRecord;
            } else {
                audioRecord.release();
            }
        }
        return null;
    }

    private Observable<VoiceEvent> createVoiceEventObservable() {
        return Observable.create(emitter -> {
            while (!emitter.isDisposed()) {
                final int size = audioRecord.read(buffer, 0, buffer.length);
                final long now = System.currentTimeMillis();
                if (isHearingVoice(buffer, size)) {
                    if (lastVoiceHeardMillis == Long.MAX_VALUE) {
                        voiceStartedMillis = now;
                        emitter.onNext(VoiceEvent.start());
                    }
                    emitter.onNext(VoiceEvent.voice(buffer, size));
                    lastVoiceHeardMillis = now;
                    if (now - voiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                        lastVoiceHeardMillis = Long.MAX_VALUE;
                        emitter.onNext(VoiceEvent.end());
                    }
                } else if (lastVoiceHeardMillis != Long.MAX_VALUE) {
                    emitter.onNext(VoiceEvent.voice(buffer, size));
                    if (now - lastVoiceHeardMillis > SPEECH_TIMEOUT_MILLIS) {
                        lastVoiceHeardMillis = Long.MAX_VALUE;
                        emitter.onNext(VoiceEvent.end());
                    }
                }
            }
        });
    }
}
