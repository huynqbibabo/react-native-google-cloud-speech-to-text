import { NativeModules } from 'react-native';

type GoogleCloudSpeechToTextType = {
  multiply(a: number, b: number): Promise<number>;
};

const { GoogleCloudSpeechToText } = NativeModules;

export default GoogleCloudSpeechToText as GoogleCloudSpeechToTextType;
