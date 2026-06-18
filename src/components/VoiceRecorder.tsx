import React, { useEffect, useRef, useState } from 'react';
import { View, Text, TouchableOpacity, Animated, PanResponder, StyleSheet } from 'react-native';
import { RecordingPresets, requestRecordingPermissionsAsync, setAudioModeAsync, useAudioRecorder, useAudioRecorderState } from 'expo-audio';
import * as FileSystem from 'expo-file-system';

interface VoiceRecorderProps {
  onRecordingComplete: (result: {
    uri: string;
    duration: number;
    cancelled: boolean;
    base64?: string;
    mimeType?: string;
  }) => void;
  onCancel: () => void;
  visible: boolean;
}

const formatDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
  const secs = Math.floor(seconds % 60).toString().padStart(2, '0');
  return `${mins}:${secs}`;
};

const getMimeType = (uri: string | null) => {
  if (!uri) return 'audio/mp4';
  const lowered = uri.toLowerCase();
  if (lowered.endsWith('.webm')) return 'audio/webm';
  if (lowered.endsWith('.wav')) return 'audio/wav';
  if (lowered.endsWith('.mp3')) return 'audio/mpeg';
  if (lowered.endsWith('.m4a')) return 'audio/mp4';
  if (lowered.endsWith('.3gp')) return 'audio/3gpp';
  return 'audio/mp4';
};

export default function VoiceRecorder({ onRecordingComplete, onCancel, visible }: VoiceRecorderProps) {
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder, 250);
  const [isRecording, setIsRecording] = useState(false);
  const [cancelled, setCancelled] = useState(false);
  const [error, setError] = useState('');
  const pulseAnim = useRef(new Animated.Value(1)).current;
  const cancelledRef = useRef(false);
  const stoppingRef = useRef(false);

  const duration = Math.max(
    Math.round((recorderState.durationMillis || 0) / 1000),
    Math.round(recorder.currentTime || 0)
  );

  useEffect(() => {
    if (isRecording) {
      const animation = Animated.loop(
        Animated.sequence([
          Animated.timing(pulseAnim, { toValue: 1.5, duration: 500, useNativeDriver: true }),
          Animated.timing(pulseAnim, { toValue: 1, duration: 500, useNativeDriver: true }),
        ])
      );
      animation.start();
      return () => animation.stop();
    }
    pulseAnim.setValue(1);
  }, [isRecording, pulseAnim]);

  useEffect(() => {
    if (!visible && isRecording) {
      stopRecording(true);
    }
  }, [visible]);

  if (!visible) return null;

  const reset = () => {
    stoppingRef.current = false;
    cancelledRef.current = false;
    setCancelled(false);
    setIsRecording(false);
  };

  const startRecording = async () => {
    if (isRecording || stoppingRef.current) return;

    try {
      setError('');
      const permission = await requestRecordingPermissionsAsync();
      if (!permission.granted) {
        setError('Microphone permission denied');
        onCancel();
        return;
      }

      await setAudioModeAsync({
        allowsRecording: true,
        playsInSilentMode: true,
      });
      await recorder.prepareToRecordAsync();
      recorder.record();
      setIsRecording(true);
    } catch (err: any) {
      setError(err?.message || 'Unable to start recording');
      reset();
      onCancel();
    }
  };

  const stopRecording = async (shouldCancel = cancelledRef.current) => {
    if (stoppingRef.current) return;
    stoppingRef.current = true;

    try {
      if (isRecording || recorder.isRecording) {
        await recorder.stop();
      }

      const uri = recorder.uri;
      const recordedDuration = Math.max(duration, Math.round(recorder.currentTime || 0));

      if (shouldCancel || !uri) {
        reset();
        onRecordingComplete({ uri: uri || '', duration: recordedDuration, cancelled: true });
        return;
      }

      const base64 = await FileSystem.readAsStringAsync(uri, {
        encoding: FileSystem.EncodingType.Base64,
      });

      reset();
      onRecordingComplete({
        uri,
        duration: recordedDuration,
        cancelled: false,
        base64,
        mimeType: getMimeType(uri),
      });
    } catch (err: any) {
      setError(err?.message || 'Unable to finish recording');
      reset();
      onCancel();
    }
  };

  const panResponder = PanResponder.create({
    onStartShouldSetPanResponder: () => true,
    onMoveShouldSetPanResponder: () => true,
    onPanResponderGrant: () => {
      startRecording();
    },
    onPanResponderMove: (_, gestureState) => {
      const shouldCancel = gestureState.dx < -80;
      cancelledRef.current = shouldCancel;
      setCancelled(shouldCancel);
    },
    onPanResponderRelease: () => {
      stopRecording(cancelledRef.current);
    },
    onPanResponderTerminate: () => {
      cancelledRef.current = true;
      stopRecording(true);
    },
  });

  return (
    <View style={styles.overlay}>
      <View style={styles.recorder} {...panResponder.panHandlers}>
        <Animated.View style={[styles.redDot, isRecording && { transform: [{ scale: pulseAnim }] }]} />
        <Text style={styles.duration}>{formatDuration(duration)}</Text>
        <Text style={[styles.hint, cancelled && styles.cancelHint, error ? styles.errorHint : null]}>
          {error || (cancelled ? 'Release to cancel' : isRecording ? 'Recording… release to send • swipe left to cancel' : 'Hold to record')}
        </Text>
        <TouchableOpacity activeOpacity={0.8} style={[styles.micButton, isRecording && styles.micButtonActive]}>
          <Text style={styles.micText}>🎤</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 64,
    alignItems: 'center',
    paddingHorizontal: 12,
  },
  recorder: {
    width: '100%',
    minHeight: 72,
    backgroundColor: '#111128',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: '#252542',
    paddingHorizontal: 14,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  redDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: '#ff4444',
    opacity: 0.9,
  },
  duration: {
    color: '#e0e0e0',
    fontSize: 16,
    fontVariant: ['tabular-nums'],
    minWidth: 52,
  },
  hint: {
    flex: 1,
    color: '#8899aa',
    fontSize: 13,
  },
  cancelHint: {
    color: '#ff4444',
  },
  errorHint: {
    color: '#ff6b6b',
  },
  micButton: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#1a1a2e',
  },
  micButtonActive: {
    backgroundColor: '#3a1a1a',
  },
  micText: {
    fontSize: 24,
  },
});
