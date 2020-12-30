# react-native-google-cloud-speech-to-text

Google cloud speech to text streaming GRPC module

## Installation

```sh
yarn add @bibabovn/react-native-google-cloud-speech-to-text
```

## Usage

```js
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
  OnSpeechEvent,
  SpeechStartEvent,
  SpeechErrorEvent,
} from '@bibabovn/react-native-google-cloud-speech-to-text';
import { useEffect } from 'react';

const Separator = () => <View style={styles.separator} />;

export default function App() {
  const [transcript, setResult] = React.useState<string>('');

  useEffect(() => {
    PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.RECORD_AUDIO, {
      title: 'Cool App Permission',
      message:
        'Cool Photo App needs access to your microphone ' +
        'so you can take awesome pictures.',
      buttonNeutral: 'Ask Me Later',
      buttonNegative: 'Cancel',
      buttonPositive: 'OK',
    });
  }, []);

  useEffect(() => {
    GoogleCloudSpeechToText.setApiKey('YOUR-API-KEY');
    GoogleCloudSpeechToText.onSpeech(onSpeech);
    GoogleCloudSpeechToText.onSpeechStart(onSpeechStart);
    GoogleCloudSpeechToText.onSpeechEnd(onSpeechEnd);
    GoogleCloudSpeechToText.onSpeechError(onSpeechError);
    GoogleCloudSpeechToText.onSpeechRecognized(onSpeechRecognized);
    return () => {
      GoogleCloudSpeechToText.removeListeners();
    };
  }, []);

  const onSpeechRecognized = (result: SpeechRecognizedEvent) => {
    console.log(result);
    setResult(result.transcript);
  };

  const onSpeechStart = (_event: SpeechStartEvent) => {
    console.log('onSpeechStart', _event);
  };

  const onSpeech = (_event: OnSpeechEvent) => {
    console.log('onSpeech', _event);
  };

  const onSpeechEnd = () => {
    console.log('onSpeechEnd: ');
  };

  const onSpeechError = (_error: SpeechErrorEvent) => {
    console.log('onSpeechError: ', _error);
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

```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
