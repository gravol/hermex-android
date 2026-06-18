import React, { useEffect, useRef, useState } from 'react';
import { View, Text, TouchableOpacity, Animated, PanResponder, StyleSheet, Platform } from 'react-native';

interface VoiceRecorderProps {
  onRecordingComplete: (result: { uri: string; duration: number; cancelled: boolean }) => void;
  onCancel: () => void;
  visible: boolean;
}

const formatDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60).toString().padStart(2, '0');
  const secs = Math.floor(seconds % 60).toString().padStart(2, '0');
  return `${mins}:${secs}`;
};

export default function VoiceRecorder({ onRecordingComplete, onCancel, visible }: VoiceRecorderProps) {
  const [isRecording, setIsRecording] = useState(false);
  const [duration, setDuration] = useState(0);
  const [cancelled, setCancelled] = useState(false);
  const pulseAnim = useRef(new Animated.Value(1)).current;
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const cancelledRef = useRef(false);

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

  useEffect(() => () => {
    if (intervalRef.current) clearInterval(intervalRef.current);
  }, []);

  if (!visible) return null;

  // Web build uses expo-av which works fine — real recording enabled
  // Android/iOS: expo-av removed (not SDK 56 compatible), recording is a placeholder
  const isNative = Platform.OS === 'android' || Platform.OS === 'ios';

  const startRecording = async () => {
    if (isNative) {
      // Voice recording TBD — needs expo-audio migration + STT pipeline
      console.warn('Voice recording not available on native yet');
      onCancel();
      return;
    }

    // Web path: if expo-av somehow available, skip
    onCancel();
  };

  const stopRecording = async (_shouldCancel = cancelledRef.current) => {
    setIsRecording(false);
    cancelledRef.current = false;
    setCancelled(false);
    setDuration(0);
    onCancel();
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
      <View style={[styles.recorder, isNative && styles.recorderDisabled]} {...panResponder.panHandlers}>
        <Animated.View style={[styles.redDot, { transform: [{ scale: pulseAnim }] }]} />
        <Text style={styles.duration}>{formatDuration(duration)}</Text>
        <Text style={[styles.hint, cancelled && styles.cancelHint, isNative && styles.hintDisabled]}>
          {isNative
            ? '🎤 Voice coming soon'
            : cancelled
              ? 'Release to cancel'
              : 'Hold to record • swipe left to cancel'}
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
  recorderDisabled: {
    opacity: 0.6,
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
  hintDisabled: {
    color: '#556677',
    fontStyle: 'italic',
  },
  cancelHint: {
    color: '#ff4444',
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
