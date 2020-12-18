import { NativeModules } from 'react-native';

export interface VoiceStartEvent {
  sampleRate: number;
  state: number;
}

export interface VoiceEvent {
  size: number;
}

export interface SpeechEvent {
  success: boolean;
  messages?: string;
}

export interface SpeechRecognizedEvent {
  isFinal: boolean;
  transcript: string;
}

interface GoogleCloudSpeechToTextModule {
  start(): Promise<SpeechEvent>;

  stop(): Promise<SpeechEvent>;

  onVoiceStart(fn: (data: VoiceStartEvent) => void): Promise<void>;

  onVoice(fn: (data: VoiceEvent) => void): Promise<void>;

  onVoiceEnd(fn: () => void): Promise<void>;

  onSpeechRecognized(fn: (data: SpeechRecognizedEvent) => void): Promise<void>;
}

const { GoogleCloudSpeechToText } = NativeModules;

export default GoogleCloudSpeechToText as GoogleCloudSpeechToTextModule;
