package com.reactnativegooglecloudspeechtotext.voice_recorder;

public class VoiceUtils {

    private static final int AMPLITUDE_THRESHOLD = 1500;

    public static boolean isHearingVoice(byte[] buffer, int size) {
        if (buffer == null){
            return false;
        }
        for (int i = 0; i < size - 1; i += 2) {
            // The buffer has LINEAR16 in little endian.
            int s = buffer[i + 1];
            if (s < 0) s *= -1;
            s <<= 8;
            s += Math.abs(buffer[i]);
            if (s > AMPLITUDE_THRESHOLD) {
                return true;
            }
        }
        return false;
    }
}
