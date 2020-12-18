import * as React from 'react';
import {
  StyleSheet,
  View,
  Text,
  Button,
  SafeAreaView,
  PermissionsAndroid,
} from 'react-native';
import GoogleCloudSpeechToText, {
  SpeechRecognizedEvent,
  VoiceEvent,
  VoiceStartEvent,
} from 'react-native-google-cloud-speech-to-text';
import { useEffect } from 'react';

const Separator = () => <View style={styles.separator} />;

export default function App() {
  const [transcript, setResult] = React.useState<string>('');

  useEffect(() => {
    PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO, {
      title: 'Cool Photo App Camera Permission',
      message:
        'Cool Photo App needs access to your camera ' +
        'so you can take awesome pictures.',
      buttonNeutral: 'Ask Me Later',
      buttonNegative: 'Cancel',
      buttonPositive: 'OK',
    });
  }, []);

  useEffect(() => {
    GoogleCloudSpeechToText.onSpeechRecognized(onSpeechRecognized);
  }, []);

  const onSpeechRecognized = (result: SpeechRecognizedEvent) => {
    // console.log(result);
    setResult(result.transcript);
  };

  useEffect(() => {
    GoogleCloudSpeechToText.onVoiceStart(onVoiceStart);
  }, []);

  const onVoiceStart = (_event: VoiceStartEvent) => {
    // console.log('onVoiceStart', event);
  };

  useEffect(() => {
    GoogleCloudSpeechToText.onVoice(onVoice);
  }, []);

  const onVoice = (_event: VoiceEvent) => {
    // console.log('onVoice', event);
  };

  useEffect(() => {
    GoogleCloudSpeechToText.onVoiceEnd(onVoiceEnd);
  }, []);

  const onVoiceEnd = () => {
    // console.log('onVoiceEnd');
  };

  const startRecognizing = () => {
    GoogleCloudSpeechToText.start();
  };

  const stopRecognizing = () => {
    GoogleCloudSpeechToText.stop();
  };

  return (
    <SafeAreaView style={styles.container}>
      <View>
        <Text style={styles.title}>{transcript}</Text>
        <Button title="Start me" onPress={startRecognizing} />
      </View>
      <Separator />
      <View>
        <Text style={styles.title}>
          Adjust the color in a way that looks standard on each platform. On
          iOS, the color prop controls the color of the text. On Android, the
          color adjusts the background color of the button.
        </Text>
        <Button title="Stop me" color="#f194ff" onPress={stopRecognizing} />
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    marginHorizontal: 16,
  },
  title: {
    textAlign: 'center',
    marginVertical: 8,
  },
  fixToText: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  separator: {
    marginVertical: 8,
    borderBottomColor: '#737373',
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
});
