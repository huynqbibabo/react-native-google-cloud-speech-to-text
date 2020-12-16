import { NativeModules } from 'react-native';

type GoogleCloudSpeechToTextType = {
  init(apiKey: string, languageCode: string): Promise<void>;
  multiply(a: number, b: number): Promise<number>;
  setApiKey(key: string): Promise<void>;
  start(): Promise<void>;
  stop(): Promise<void>;
};

const { GoogleCloudSpeechToText } = NativeModules;

export default GoogleCloudSpeechToText as GoogleCloudSpeechToTextType;
