import * as React from 'react';
import { StyleSheet, View, Text } from 'react-native';
import GoogleCloudSpeechToText from 'react-native-google-cloud-speech-to-text';

export default function App() {
  const [result, setResult] = React.useState<number | undefined>();

  React.useEffect(() => {
    GoogleCloudSpeechToText.multiply(3, 7).then(setResult);
  }, []);

  return (
    <View style={styles.container}>
      <Text>Result: {result}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
