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
  SpeechRecognizeEvent,
  VoiceStartEvent,
  SpeechErrorEvent,
  VoiceEvent,
  SpeechStartEvent,
} from 'react-native-google-cloud-speech-to-text';
import { useEffect } from 'react';
// import { Player } from '@react-native-community/audio-toolkit';

const Separator = () => <View style={styles.separator} />;

export default function App() {
  const [transcript, setResult] = React.useState<string>('');

  const [fileId, setId] = React.useState<string>('');
  const [file, setFile] = React.useState<string>('');

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
    // GoogleCloudSpeechToText.setApiKey('key_____');
    GoogleCloudSpeechToText.onVoice(onVoice);
    GoogleCloudSpeechToText.onVoiceStart(onVoiceStart);
    GoogleCloudSpeechToText.onVoiceEnd(onVoiceEnd);
    GoogleCloudSpeechToText.onSpeechError(onSpeechError);
    GoogleCloudSpeechToText.onSpeechRecognized(onSpeechRecognized);
    GoogleCloudSpeechToText.onSpeechRecognizing(onSpeechRecognizing);
    return () => {
      GoogleCloudSpeechToText.removeListeners();
    };
  }, []);

  const onSpeechError = (_error: SpeechErrorEvent) => {
    console.log('onSpeechError: ', _error);
  };

  const onSpeechRecognized = (result: SpeechRecognizeEvent) => {
    console.log('onSpeechRecognized: ', result);
    setResult(result.transcript);
  };

  const onSpeechRecognizing = (result: SpeechRecognizeEvent) => {
    console.log('onSpeechRecognizing: ', result);
    setResult(result.transcript);
  };

  const onVoiceStart = (_event: VoiceStartEvent) => {
    console.log('onVoiceStart', _event);
  };

  const onVoice = (_event: VoiceEvent) => {
    console.log('onVoice', _event);
  };

  const onVoiceEnd = () => {
    console.log('onVoiceEnd: ');
  };

  const startRecognizing = async () => {
    const result: SpeechStartEvent = await GoogleCloudSpeechToText.start({
      speechToFile: true,
    });
    console.log('startRecognizing', result);
    if (result.fileId) setId(result.fileId);
  };

  const stopRecognizing = async () => {
    await GoogleCloudSpeechToText.stop();
  };

  const getAudioFile = async () => {
    if (fileId) {
      const result = await GoogleCloudSpeechToText.getAudioFile(fileId, {
        channel: 2,
        bitrate: 96000,
        sampleRate: 44100,
      });
      console.log(result);
      if (result.path) {
        setFile(result.path);
      }
    }
  };

  // const playAudio = () => {
  //   const player = new Player(file, {
  //     autoDestroy: true,
  //   }).prepare((err) => {
  //     if (err) {
  //       console.log(err);
  //     } else {
  //       player.play();
  //     }
  //   });
  // };

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
      <Separator />
      <View>
        <Text style={styles.title}>{file}</Text>
        <Button
          disabled={transcript === ''}
          title="Get file audio"
          color="#f194ff"
          onPress={getAudioFile}
        />
      </View>
      {/*{file && (*/}
      {/*  <>*/}
      {/*    <Separator />*/}
      {/*    <Text style={styles.title}>file</Text>*/}
      {/*    <View>*/}
      {/*      <Button*/}
      {/*        disabled={transcript === ''}*/}
      {/*        title="Play"*/}
      {/*        color="#f194ff"*/}
      {/*        onPress={playAudio}*/}
      {/*      />*/}
      {/*    </View>*/}
      {/*  </>*/}
      {/*)}*/}
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
