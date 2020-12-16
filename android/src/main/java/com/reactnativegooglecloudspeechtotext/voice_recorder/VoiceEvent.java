package com.reactnativegooglecloudspeechtotext.voice_recorder;

public class VoiceEvent {

    private State state;
    private byte[] data;
    private int size;

    public static VoiceEvent start() {
        return new VoiceEvent(State.START, null, 0);
    }

    public static VoiceEvent voice(byte[] data, int size) {
        return new VoiceEvent(State.VOICE, data, size);
    }

    public static VoiceEvent end() {
        return new VoiceEvent(State.END, null, 0);
    }

    private VoiceEvent(State state, byte[] data, int size) {
        this.state = state;
        this.data = data;
        this.size = size;
    }

    public State getState() {
        return state;
    }

    public byte[] getData() {
        return data;
    }

    public int getSize() {
        return size;
    }

    @Override
    public String toString() {
        return state.toString();
    }

    public enum State {
        START, VOICE, END
    }
}
