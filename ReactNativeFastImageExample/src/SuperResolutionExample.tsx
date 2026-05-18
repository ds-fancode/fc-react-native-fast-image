import React, {useState, useCallback} from 'react';
import {StyleSheet, Switch, Text, View} from 'react-native';
import FastImage from '@d11/react-native-fast-image';
import Section from './Section';
import SectionFlex from './SectionFlex';
import FeatureText from './FeatureText';
import {useCacheBust} from './useCacheBust';

const BASE_URL = 'https://unsplash.it/200/200';

const ImageWithLabel = ({
  url,
  superResolution,
  label,
}: {
  url: string;
  superResolution: boolean;
  label: string;
}) => (
  <View style={styles.imageField}>
    <FastImage
      style={styles.image}
      source={{uri: url, superResolution}}
      resizeMode="cover"
    />
    <Text style={styles.label}>{label}</Text>
  </View>
);

export const SuperResolutionExample = () => {
  const [srEnabled, setSrEnabled] = useState(true);
  const {url, bust} = useCacheBust(BASE_URL);

  const toggle = useCallback((value: boolean) => {
    setSrEnabled(value);
    bust();
  }, [bust]);

  return (
    <View>
      <Section>
        <FeatureText text="• Super resolution (2x upscale via on-device ML)." />
        <View style={styles.toggleRow}>
          <Text style={styles.toggleLabel}>Super resolution</Text>
          <Switch value={srEnabled} onValueChange={toggle} />
        </View>
      </Section>
      <SectionFlex>
        <ImageWithLabel
          url={url}
          superResolution={false}
          label="Original"
        />
        <ImageWithLabel
          url={url}
          superResolution={srEnabled}
          label={srEnabled ? 'SR on' : 'SR off'}
        />
      </SectionFlex>
    </View>
  );
};

const styles = StyleSheet.create({
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 8,
    width: '100%',
  },
  toggleLabel: {
    color: '#222',
  },
  imageField: {
    flex: 1,
    alignItems: 'center',
    margin: 10,
  },
  image: {
    width: 120,
    height: 120,
  },
  label: {
    marginTop: 4,
    fontSize: 11,
    color: '#555',
  },
});
