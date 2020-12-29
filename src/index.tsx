import { NativeModules, NativeEventEmitter } from 'react-native';

const { GoogleCloudSpeechToText } = NativeModules;

const VoiceEmitter = new NativeEventEmitter(GoogleCloudSpeechToText);

type SpeechEvent = keyof SpeechEvents;

export interface SpeechEvents {
  onSpeechStart?: (e: SpeechStartEvent) => void;
  onSpeech?: (e: OnSpeechEvent) => void;
  onSpeechEnd?: () => void;
  onSpeechError?: (e: SpeechErrorEvent) => void;

  onSpeechRecognized?: (e: SpeechRecognizedEvent) => void;
}

export interface SpeechStartEvent {
  sampleRate: number;
  state: number;
}

export interface OnSpeechEvent {
  size: number;
}

export interface SpeechErrorEvent {
  error?: {
    code?: string;
    message?: string;
  };
}

export interface SpeechRecognizedEvent {
  isFinal: boolean;
  transcript: string;
}

class GCSpeechToText {
  private readonly _events: Required<SpeechEvents>;
  private _listeners: any[] | null;

  constructor() {
    this._listeners = null;
    this._events = {
      onSpeech: () => undefined,
      onSpeechEnd: () => undefined,
      onSpeechError: () => undefined,
      onSpeechStart: () => undefined,
      onSpeechRecognized: () => undefined,
    };
  }

  async start(): Promise<void> {
    if (!this._listeners) {
      this._listeners = (Object.keys(
        this._events
      ) as SpeechEvent[]).map((key) =>
        VoiceEmitter.addListener(key, this._events[key])
      );
    }

    return await GoogleCloudSpeechToText.start();
  }

  async stop(): Promise<void> {
    await GoogleCloudSpeechToText.stop();
  }

  async setApiKey(apiKey: string): Promise<void> {
    await GoogleCloudSpeechToText.setApiKey(apiKey);
  }

  async removeListeners(): Promise<void> {
    if (this._listeners) {
      this._listeners.map((listener) => listener.remove());
      this._listeners = null;
    }
    this._listeners = null;
    await GoogleCloudSpeechToText.destroy();
  }

  onSpeechStart(fn: (data: SpeechStartEvent) => void) {
    this._events.onSpeechStart = fn;
  }

  onSpeech(fn: (data: OnSpeechEvent) => void) {
    this._events.onSpeech = fn;
  }

  onSpeechEnd(fn: () => void) {
    this._events.onSpeechEnd = fn;
  }

  onSpeechError(fn: (error: SpeechErrorEvent) => void) {
    this._events.onSpeechError = fn;
  }

  onSpeechRecognized(fn: (data: SpeechRecognizedEvent) => void) {
    this._events.onSpeechRecognized = fn;
  }
}

export default new GCSpeechToText();
