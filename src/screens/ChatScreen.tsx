/**
 * Chat screen — talk to Hermes via the API server (HTTP streaming, port 8650).
 * Full tools, memory, skills, streaming — no bridge needed.
 */
import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, FlatList, ScrollView,
  StyleSheet, KeyboardAvoidingView, Platform, AppState, Animated, Image,
  Keyboard, useWindowDimensions, type KeyboardEvent, ToastAndroid,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import * as Clipboard from 'expo-clipboard';
import { getHermesAPI, ConnectionStatus } from '../services/api';
import { showLocalNotification } from '../services/notifications';
import CommandShortcuts from '../components/CommandShortcuts';
import VoiceRecorder from '../components/VoiceRecorder';

interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system' | 'error';
  content: string;
  timestamp: number;
  imageUri?: string;
  audioUri?: string;
  audioDuration?: number;
}

const isSameCalendarDay = (a: number, b: number) => {
  const first = new Date(a);
  const second = new Date(b);
  return first.getFullYear() === second.getFullYear()
    && first.getMonth() === second.getMonth()
    && first.getDate() === second.getDate();
};

const formatTime = (timestamp: number) => (
  new Date(timestamp).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
);

const formatVoiceDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60).toString().padStart(2, '0');
  return `${mins}:${secs}`;
};

const formatDateSeparator = (timestamp: number) => {
  const messageDate = new Date(timestamp);
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);

  if (isSameCalendarDay(messageDate.getTime(), today.getTime())) {
    return 'Today';
  }

  if (isSameCalendarDay(messageDate.getTime(), yesterday.getTime())) {
    return 'Yesterday';
  }

  return messageDate.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
};

const renderInlineMarkdown = (content: string, onCopy: (value: string, kind?: 'url' | 'code' | 'message') => void) => {
  const codeBlockSegments = content.split(/(```[\s\S]*?```)/g);

  return codeBlockSegments.map((blockSegment, blockIndex) => {
    if (!blockSegment) return null;

    if (blockSegment.startsWith('```') && blockSegment.endsWith('```')) {
      const code = blockSegment.slice(3, -3).replace(/^\w+\n/, '').trim();
      return (
        <Text
          key={`code-block-${blockIndex}`}
          style={styles.markdownCodeBlock}
          onPress={() => onCopy(code, 'code')}>
          {code}
        </Text>
      );
    }

    const segments = blockSegment.split(/(`[^`\n]+`|\*\*[^*]+\*\*|\*[^*]+\*|https?:\/\/[^\s)]+)/g);

    return segments.map((segment, index) => {
    if (!segment) return null;

    if (segment.startsWith('`') && segment.endsWith('`')) {
      const code = segment.slice(1, -1);
      return (
        <Text
          key={`code-${blockIndex}-${index}`}
          style={styles.markdownCode}
          onPress={() => onCopy(code, 'code')}>
          {code}
        </Text>
      );
    }

    if (/^https?:\/\/[^\s)]+$/.test(segment)) {
      return (
        <Text
          key={`url-${blockIndex}-${index}`}
          style={styles.markdownLink}
          onPress={() => onCopy(segment, 'url')}>
          {segment}
        </Text>
      );
    }

    if (segment.startsWith('**') && segment.endsWith('**')) {
      return (
        <Text key={`bold-${blockIndex}-${index}`} style={styles.markdownBold}>
          {segment.slice(2, -2)}
        </Text>
      );
    }

    if (segment.startsWith('*') && segment.endsWith('*')) {
      return (
        <Text key={`italic-${blockIndex}-${index}`} style={styles.markdownItalic}>
          {segment.slice(1, -1)}
        </Text>
      );
    }

    return segment;
    });
  });
};

const useAndroidKeyboardInset = (windowHeight: number) => {
  const [keyboardInset, setKeyboardInset] = useState(0);

  useEffect(() => {
    if (Platform.OS !== 'android') return undefined;

    const calculateInset = (event: KeyboardEvent) => {
      const { height, screenY } = event.endCoordinates;
      const insetFromScreenY = screenY > 0 ? Math.max(0, windowHeight - screenY) : 0;
      // Android 15/16 edge-to-edge devices can report a non-resized window even
      // with adjustResize. Prefer the native keyboard height, but keep the
      // screenY-derived fallback for devices that report more accurate insets.
      setKeyboardInset(Math.max(height || 0, insetFromScreenY));
    };

    const showSubscription = Keyboard.addListener('keyboardDidShow', calculateInset);
    const hideSubscription = Keyboard.addListener('keyboardDidHide', () => setKeyboardInset(0));

    return () => {
      showSubscription.remove();
      hideSubscription.remove();
    };
  }, [windowHeight]);

  return keyboardInset;
};

const EMOJI_CATEGORIES = [
  {
    name: 'Smileys',
    emojis: ['😀', '😃', '😄', '😁', '😅', '😂', '🤣', '😊', '😇', '🙂', '😉', '😌', '😍', '🥰', '😘', '😗', '😋', '😛', '😜', '🤪', '😝', '🤑', '🤗', '🤭', '🤫', '🤔', '🤐', '🤨', '😐', '😑', '😶', '😏', '😒', '🙄', '😬', '🤥', '😔', '😪', '😴', '🤤', '😷', '🤒', '🤕', '🤢', '🤮', '🥴', '😵', '🤯', '🥳']
  },
  {
    name: 'Gestures',
    emojis: ['👍', '👎', '👊', '✊', '🤛', '🤜', '👏', '🙌', '👐', '🤲', '🤝', '🙏', '✌️', '🤟', '🤘', '👌', '😎', '💪', '🖕', '✋', '🤚', '👋', '🤙', '💅', '👀', '🫡', '🫶']
  },
  {
    name: 'Hearts',
    emojis: ['❤️', '🧡', '💛', '💚', '💙', '💜', '🖤', '🤍', '🤎', '💕', '💞', '💗', '💖', '💘', '💝', '❣️', '💟', '♥️', '💌', '💋']
  },
  {
    name: 'Nature',
    emojis: ['☀️', '🌤️', '⛅', '🌧️', '⛈️', '🌨️', '❄️', '🔥', '💧', '🌊', '🌈', '☁️', '🌪️', '🌫️', '☂️', '🌺', '🌸', '🌻', '🌹', '🌷', '🌿', '🍀', '🌵', '🌲', '🌳', '🍄', '⭐', '🌟', '✨', '💫', '⚡']
  },
  {
    name: 'Food',
    emojis: ['🍎', '🍊', '🍋', '🍌', '🍉', '🍇', '🍓', '🫐', '🍈', '🍒', '🍑', '🥭', '🍍', '🥝', '🍆', '🥑', '🥦', '🥬', '🥒', '🌽', '🥕', '🧅', '🥔', '🍞', '🧀', '🍔', '🍟', '🌭', '🍕', '🥪', '🥙', '🌮', '🌯', '🥗', '🥘', '🍝', '🍜', '🍲', '🍛', '🍣', '🍱', '🍚', '🍙', '🍘', '🍢', '🍡', '🍧', '🍨', '🍦', '🎂', '🍰', '🧁', '🍫', '🍬', '🍭', '🍩', '🍪', '☕', '🍵', '🍺', '🍻', '🥂', '🍷', '🥃', '🍸']
  },
  {
    name: 'Travel',
    emojis: ['🚗', '🚕', '🚙', '🚌', '🚎', '🏎️', '🚓', '🚑', '🚒', '🚐', '🛻', '🚚', '🚛', '🚲', '🛴', '🏍️', '✈️', '🚀', '🛸', '🚁', '⛵', '🚢', '⚓', '🗺️', '🏔️', '🏖️', '🏜️', '🏝️']
  },
  {
    name: 'Objects',
    emojis: ['💡', '🔦', '🕯️', '📱', '💻', '⌨️', '🖥️', '🖨️', '🖱️', '💾', '💿', '📷', '🎥', '🎧', '🎤', '📚', '📌', '✏️', '📝', '📦', '🔑', '🔒', '🔧', '🔨', '⚙️', '🧰', '🎁', '🛒']
  }
];

export default function ChatScreen() {
  const { height: windowHeight } = useWindowDimensions();
  const androidKeyboardInset = useAndroidKeyboardInset(windowHeight);
  const androidKeyboardSpacer = Platform.OS === 'android' ? androidKeyboardInset : 0;
  const keyboardAwareMessageContainerStyle = styles.messageContainer;
  const keyboardAwareInputBarStyle = styles.inputBar;
  const keyboardAwareMediaOptionsStyle = useMemo(() => [
    styles.mediaOptions,
    androidKeyboardSpacer > 0 ? { bottom: androidKeyboardSpacer + 60 } : null,
  ], [androidKeyboardSpacer]);

  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [isTyping, setIsTyping] = useState(false);
  const [showMediaOptions, setShowMediaOptions] = useState(false);
  const [showVoiceRecorder, setShowVoiceRecorder] = useState(false);
  const [showEmoji, setShowEmoji] = useState(false);
  const [copyFeedback, setCopyFeedback] = useState('');
  const [selection, setSelection] = useState({ start: 0, end: 0 });
  const [historyLoadTrigger, setHistoryLoadTrigger] = useState(0);
  const [dot1] = useState(new Animated.Value(0));
  const [dot2] = useState(new Animated.Value(0));
  const [dot3] = useState(new Animated.Value(0));
  const flatListRef = useRef<FlatList<Message>>(null);
  const inputRef = useRef<TextInput>(null);
  const appStateRef = useRef(AppState.currentState);
  const assistantResponseRef = useRef('');
  const historyLoadedSessionRef = useRef<string | null>(null);
  const api = getHermesAPI();

  useEffect(() => {
    const subscription = AppState.addEventListener('change', nextAppState => {
      appStateRef.current = nextAppState;
    });

    return () => subscription.remove();
  }, []);

  useEffect(() => {
    const unsub = api.onMessage((data) => {
      switch (data.type) {
        case 'response_chunk':
          assistantResponseRef.current += data.content;
          setMessages(prev => {
            const copy = [...prev];
            const last = copy[copy.length - 1];
            if (last && last.role === 'assistant') {
              copy[copy.length - 1] = { ...last, content: last.content + data.content };
            } else {
              copy.push({ id: Date.now().toString(), role: 'assistant', content: data.content, timestamp: Date.now() });
            }
            return copy;
          });
          break;
        case 'response_end':
          if (appStateRef.current !== 'active' && assistantResponseRef.current.trim()) {
            const trimmedResponse = assistantResponseRef.current.trim();
            const preview = trimmedResponse.slice(0, 80);
            showLocalNotification('Hermes replied', `${preview}${trimmedResponse.length > 80 ? '...' : ''}`);
          }
          assistantResponseRef.current = '';
          setIsTyping(false);
          break;
        case 'status':
          if (data.content === 'thinking') setIsTyping(true);
          break;
        case 'error':
          addMessage('error', data.content);
          if (appStateRef.current !== 'active') {
            showLocalNotification('Hermes error', data.content.slice(0, 120));
          }
          assistantResponseRef.current = '';
          setIsTyping(false);
          break;
        case 'connected':
          addMessage('system', 'Connected to Hermes');
          setHistoryLoadTrigger(prev => prev + 1);
          break;
      }
    });

    const unsubStatus = api.onStatus(setStatus);
    setStatus(api.status);
    if (api.status === 'connected') {
      setHistoryLoadTrigger(prev => prev + 1);
    }
    return () => {
      unsub();
      unsubStatus();
    };
  }, []);

  useEffect(() => {
    const loadHistory = async () => {
      if (status !== 'connected') return;
      const sessionId = api.getCurrentApiSessionId();
      const lastResponseId = api.getLastResponseId();
      if (!sessionId && !lastResponseId) return;
      if (!sessionId || historyLoadedSessionRef.current === sessionId) return;

      try {
        const messages = await api.loadSessionMessages(sessionId);
        if (messages.length > 0) {
          const converted: Message[] = messages.map((m, index) => {
            const timestamp = m.timestamp || Date.now();
            return {
              id: String(m.id || `${timestamp}-${index}`),
              role: m.role === 'user' ? 'user' : (m.role === 'assistant' ? 'assistant' : 'system'),
              content: m.content || '',
              timestamp: timestamp < 1000000000000 ? timestamp * 1000 : timestamp,
            };
          });
          const visibleMessages = converted.filter(m => m.role === 'user' || m.role === 'assistant');
          setMessages(visibleMessages);
          requestAnimationFrame(() => flatListRef.current?.scrollToEnd({ animated: false }));
        }
        historyLoadedSessionRef.current = sessionId;
      } catch {
        // Silent fail — history is best-effort
      }
    };

    loadHistory();
  }, [status, historyLoadTrigger]);

  useEffect(() => {
    if (isTyping) {
      const animate = (dot: Animated.Value, delay: number) =>
        Animated.loop(
          Animated.sequence([
            Animated.delay(delay),
            Animated.timing(dot, { toValue: -4, duration: 300, useNativeDriver: true }),
            Animated.timing(dot, { toValue: 0, duration: 300, useNativeDriver: true }),
          ])
        );

      const animation1 = animate(dot1, 0);
      const animation2 = animate(dot2, 150);
      const animation3 = animate(dot3, 300);

      animation1.start();
      animation2.start();
      animation3.start();

      return () => {
        animation1.stop();
        animation2.stop();
        animation3.stop();
        dot1.setValue(0);
        dot2.setValue(0);
        dot3.setValue(0);
      };
    }
  }, [isTyping, dot1, dot2, dot3]);

  const addMessage = (role: Message['role'], content: string, media?: Partial<Message>) => {
    setMessages(prev => [...prev, {
      id: `${Date.now()}-${prev.length}`,
      role,
      content,
      timestamp: Date.now(),
      ...media,
    }]);
  };

  const copyToClipboard = async (value: string, kind: 'url' | 'code' | 'message' = 'message') => {
    const text = value.trim();
    if (!text) return;

    if (kind === 'url') {
      await Clipboard.setUrlAsync(text);
    } else {
      await Clipboard.setStringAsync(text);
    }

    const label = kind === 'url' ? 'Link copied' : kind === 'code' ? 'Code copied' : 'Message copied';
    if (Platform.OS === 'android') {
      ToastAndroid.show(label, ToastAndroid.SHORT);
    }
    setCopyFeedback(label);
    setTimeout(() => setCopyFeedback(''), 1200);
  };

  const handleSend = () => {
    const text = input.trim();
    if (!text) return;

    addMessage('user', text);
    if (text.startsWith('/queue')) {
      addMessage('system', '📋 Queued for next turn');
    }
    assistantResponseRef.current = '';
    setInput('');
    setIsTyping(true);
    api.sendMessage(text);
    inputRef.current?.focus();
  };

  const handleCommandSelect = (cmd: string) => {
    setInput(cmd + ' ');
  };

  const insertEmoji = (emoji: string) => {
    const start = Math.min(selection.start, input.length);
    const end = Math.min(selection.end, input.length);
    const nextInput = `${input.slice(0, start)}${emoji}${input.slice(end)}`;
    const nextCursor = start + emoji.length;
    setInput(nextInput);
    setSelection({ start: nextCursor, end: nextCursor });
    setShowEmoji(false);
    requestAnimationFrame(() => inputRef.current?.focus());
  };

  const renderEmojiPicker = () => (
    <View style={styles.emojiContainer}>
      <ScrollView keyboardShouldPersistTaps="handled">
        {EMOJI_CATEGORIES.map(category => (
          <View key={category.name}>
            <Text style={styles.emojiCategoryTitle}>{category.name}</Text>
            <View style={styles.emojiRow}>
              {category.emojis.map((emoji, index) => (
                <TouchableOpacity
                  key={`${category.name}-${emoji}-${index}`}
                  style={styles.emojiItem}
                  onPress={() => insertEmoji(emoji)}>
                  <Text style={styles.emojiText}>{emoji}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        ))}
      </ScrollView>
    </View>
  );

  const closeEmojiPicker = () => {
    if (showEmoji) setShowEmoji(false);
  };

  const toggleEmojiPicker = () => {
    setShowMediaOptions(false);
    setShowEmoji(prev => !prev);
  };

  const sendSelectedImage = async (asset: ImagePicker.ImagePickerAsset) => {
    if (!asset.base64) {
      addMessage('error', 'Selected image did not include base64 data.');
      return;
    }

    addMessage('user', 'Look at this image', { imageUri: asset.uri });
    assistantResponseRef.current = '';
    setIsTyping(true);
    await api.sendImage(asset.base64, asset.mimeType || 'image/jpeg');
  };

  const handlePickImage = async (source: 'camera' | 'gallery') => {
    setShowMediaOptions(false);
    setShowEmoji(false);
    try {
      if (source === 'camera') {
        const permission = await ImagePicker.requestCameraPermissionsAsync();
        if (!permission.granted) {
          addMessage('error', 'Camera permission denied.');
          return;
        }
      } else {
        const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
        if (!permission.granted) {
          addMessage('error', 'Photo library permission denied.');
          return;
        }
      }

      const pickerOptions: ImagePicker.ImagePickerOptions = {
        quality: 0.7,
        base64: true,
        mediaTypes: ImagePicker.MediaTypeOptions.Images,
      };
      const result = source === 'camera'
        ? await ImagePicker.launchCameraAsync(pickerOptions)
        : await ImagePicker.launchImageLibraryAsync(pickerOptions);

      if (!result.canceled && result.assets[0]) {
        await sendSelectedImage(result.assets[0]);
      }
    } catch (error: any) {
      addMessage('error', `Image selection failed: ${error?.message || 'Unknown error'}`);
      setIsTyping(false);
    }
  };

  const handleVoiceComplete = async (result: { uri: string; duration: number; cancelled: boolean; base64?: string; mimeType?: string }) => {
    setShowVoiceRecorder(false);
    if (result.cancelled) return;

    const durationLabel = formatVoiceDuration(result.duration);
    addMessage('user', `🎤 Voice note (${durationLabel})`, {
      audioUri: result.uri,
      audioDuration: result.duration,
    });
    assistantResponseRef.current = '';
    setIsTyping(true);
    if (result.base64) {
      await api.sendAudio(result.base64, result.mimeType || 'audio/mp4', `Voice note (${durationLabel})`);
    } else {
      await api.sendMessage(`🎤 Voice note (${durationLabel})`);
    }
  };

  const renderDateSeparator = (timestamp: number) => (
    <View style={styles.dateSeparator}>
      <Text style={styles.dateSeparatorText}>{formatDateSeparator(timestamp)}</Text>
    </View>
  );

  const renderMessage = ({ item, index }: { item: Message; index: number }) => {
    const isUser = item.role === 'user';
    const isError = item.role === 'error';
    const isSystem = item.role === 'system';
    const shouldShowDateSeparator = index === 0
      || !isSameCalendarDay(messages[index - 1].timestamp, item.timestamp);

    return (
      <View>
        {shouldShowDateSeparator && renderDateSeparator(item.timestamp)}
        <View style={[
          styles.messageRow,
          isUser ? styles.userMessageRow : styles.assistantMessageRow,
          isSystem ? styles.systemMessageRow : null,
        ]}>
          <TouchableOpacity
            activeOpacity={0.78}
            delayLongPress={250}
            onLongPress={() => copyToClipboard(item.content, 'message')}
            style={[
              styles.bubble,
              isUser ? styles.userBubble : styles.assistantBubble,
              isError ? styles.errorBubble : null,
              isSystem ? styles.systemBubble : null,
            ]}>
            {item.imageUri && (
              <Image source={{ uri: item.imageUri }}
                style={styles.chatImage}
                resizeMode="cover" />
            )}
            {item.audioUri ? (
              <View style={styles.voiceBubble}>
                <View style={styles.voicePlayButton}>
                  <Text style={styles.voicePlayText}>▶️</Text>
                </View>
                <Text style={styles.voiceWaveform}>▁▃▅▇▅▃▁▂▆▃</Text>
                <Text style={styles.voiceDuration}>{formatVoiceDuration(item.audioDuration || 0)}</Text>
              </View>
            ) : (
              <Text style={[
                styles.bubbleText,
                isUser ? styles.userText : styles.assistantText,
                isError ? styles.errorText : null,
                isSystem ? styles.systemText : null,
              ]}>
                {renderInlineMarkdown(item.content, copyToClipboard)}
              </Text>
            )}
          </TouchableOpacity>
          <Text style={[
            styles.timestamp,
            isUser ? styles.userTimestamp : styles.assistantTimestamp,
            isSystem ? styles.systemTimestamp : null,
          ]}>
            {formatTime(item.timestamp)}
          </Text>
        </View>
      </View>
    );
  };

  const renderTypingIndicator = () => (
    <View style={styles.typingRow}>
      <View style={[styles.bubble, styles.assistantBubble, styles.typingBubble]}>
        <Animated.Text style={[styles.typingDot, { transform: [{ translateY: dot1 }] }]}>•</Animated.Text>
        <Animated.Text style={[styles.typingDot, { transform: [{ translateY: dot2 }] }]}>•</Animated.Text>
        <Animated.Text style={[styles.typingDot, { transform: [{ translateY: dot3 }] }]}>•</Animated.Text>
      </View>
    </View>
  );

  const content = (
    <>
      <View style={styles.statusBar}>
        <Text style={[styles.statusDot, {
          color: status === 'connected' ? '#4caf50' : status === 'connecting' ? '#ff9800' : '#f44336'
        }]}>●</Text>
        <Text style={styles.statusText}>
          {status === 'connected' ? 'Online' : status === 'connecting' ? 'Connecting...' :
           status === 'error' ? 'Connection error' : 'Offline'}
        </Text>
      </View>

      <FlatList ref={flatListRef}
        data={messages} renderItem={renderMessage}
        keyExtractor={item => item.id}
        style={styles.messageList}
        contentContainerStyle={keyboardAwareMessageContainerStyle}
        onTouchStart={closeEmojiPicker}
        onContentSizeChange={() => flatListRef.current?.scrollToEnd()} />

      {isTyping && renderTypingIndicator()}

      {!!copyFeedback && (
        <View style={styles.copyFeedback} pointerEvents="none">
          <Text style={styles.copyFeedbackText}>{copyFeedback}</Text>
        </View>
      )}

      <CommandShortcuts onSelect={handleCommandSelect} />

      {showEmoji && renderEmojiPicker()}

      {showMediaOptions && (
        <View style={keyboardAwareMediaOptionsStyle}>
          <TouchableOpacity style={styles.mediaOption} onPress={() => handlePickImage('camera')}>
            <Text style={styles.mediaOptionText}>📷 Camera</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.mediaOption} onPress={() => handlePickImage('gallery')}>
            <Text style={styles.mediaOptionText}>🖼 Gallery</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.mediaOption} onPress={() => { setShowMediaOptions(false); setShowEmoji(false); setShowVoiceRecorder(true); }}>
            <Text style={styles.mediaOptionText}>🎤 Voice</Text>
          </TouchableOpacity>
        </View>
      )}

      <VoiceRecorder
        visible={showVoiceRecorder}
        onRecordingComplete={handleVoiceComplete}
        onCancel={() => setShowVoiceRecorder(false)} />

      <View style={keyboardAwareInputBarStyle}>
        <TouchableOpacity style={styles.mediaButton}
          onPress={() => { setShowEmoji(false); setShowMediaOptions(prev => !prev); }}>
          <Text style={styles.mediaButtonText}>➕</Text>
        </TouchableOpacity>
        <TouchableOpacity style={styles.emojiButton} onPress={toggleEmojiPicker}>
          <Text style={styles.emojiButtonText}>😀</Text>
        </TouchableOpacity>
        <View style={styles.inputPill}>
          <TextInput ref={inputRef} style={styles.input}
            value={input} onChangeText={setInput}
            placeholder="Message..." placeholderTextColor="#556677"
            multiline={false} maxLength={4000}
            returnKeyType="send"
            selection={selection}
            onSelectionChange={({ nativeEvent }) => setSelection(nativeEvent.selection)}
            onFocus={closeEmojiPicker}
            onSubmitEditing={handleSend} blurOnSubmit={false} />
        </View>
        <TouchableOpacity style={[styles.sendButton, !input.trim() && styles.sendButtonDisabled]}
          onPress={handleSend} disabled={!input.trim()}>
          <Text style={[styles.sendText, !input.trim() && styles.sendTextDisabled]}>➤</Text>
        </TouchableOpacity>
      </View>
    </>
  );

  if (Platform.OS === 'ios') {
    return (
      <KeyboardAvoidingView style={styles.container} behavior="padding" keyboardVerticalOffset={0}>
        {content}
      </KeyboardAvoidingView>
    );
  }

  return <View style={styles.container}>{content}</View>;
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0a0a1a' },
  statusBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 7,
    backgroundColor: '#0a0a1a',
    borderBottomWidth: 1,
    borderBottomColor: '#151525',
  },
  statusDot: { fontSize: 10, marginRight: 8 },
  statusText: { color: '#8899aa', fontSize: 12 },
  messageList: { flex: 1, backgroundColor: '#0a0a1a' },
  messageContainer: { paddingHorizontal: 10, paddingTop: 8, paddingBottom: 6 },
  dateSeparator: {
    alignSelf: 'center',
    backgroundColor: '#1a1a2e',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 4,
    marginTop: 8,
    marginBottom: 10,
  },
  dateSeparatorText: { color: '#667788', fontSize: 12 },
  messageRow: { marginBottom: 7 },
  userMessageRow: { alignItems: 'flex-end' },
  assistantMessageRow: { alignItems: 'flex-start' },
  systemMessageRow: { alignItems: 'center' },
  bubble: {
    maxWidth: '75%',
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderRadius: 14,
  },
  userBubble: {
    alignSelf: 'flex-end',
    backgroundColor: '#2b5278',
    borderBottomRightRadius: 4,
  },
  assistantBubble: {
    alignSelf: 'flex-start',
    backgroundColor: '#1c1c2e',
    borderBottomLeftRadius: 4,
  },
  errorBubble: {
    alignSelf: 'flex-start',
    backgroundColor: '#3a1a1a',
    borderBottomLeftRadius: 4,
  },
  systemBubble: {
    alignSelf: 'center',
    backgroundColor: 'transparent',
    maxWidth: '85%',
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  bubbleText: { fontSize: 15, lineHeight: 21 },
  userText: { color: '#ffffff' },
  assistantText: { color: '#e0e0e0' },
  errorText: { color: '#ff6b6b' },
  systemText: { color: '#667788', fontSize: 12, lineHeight: 17, textAlign: 'center' },
  markdownBold: { fontWeight: '700' },
  markdownItalic: { fontStyle: 'italic' },
  markdownCode: {
    backgroundColor: '#111827',
    borderRadius: 4,
    color: '#f8fafc',
    fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' }),
    fontSize: 14,
    paddingHorizontal: 4,
    paddingVertical: 1,
  },
  markdownCodeBlock: {
    backgroundColor: '#111827',
    borderColor: '#2d3748',
    borderRadius: 8,
    borderWidth: 1,
    color: '#f8fafc',
    fontFamily: Platform.select({ ios: 'Menlo', android: 'monospace', default: 'monospace' }),
    fontSize: 14,
    lineHeight: 20,
    marginVertical: 4,
    paddingHorizontal: 8,
    paddingVertical: 6,
  },
  markdownLink: {
    color: '#4ea3ff',
    textDecorationLine: 'underline',
  },
  copyFeedback: {
    position: 'absolute',
    bottom: 120,
    alignSelf: 'center',
    backgroundColor: 'rgba(20, 20, 35, 0.96)',
    borderColor: '#B8860B',
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 8,
    zIndex: 20,
  },
  copyFeedbackText: { color: '#f8fafc', fontSize: 13, fontWeight: '600' },
  timestamp: { fontSize: 11, color: '#556677', marginTop: 2 },
  userTimestamp: { textAlign: 'right', alignSelf: 'flex-end' },
  assistantTimestamp: { textAlign: 'left', alignSelf: 'flex-start' },
  systemTimestamp: { textAlign: 'center', alignSelf: 'center' },
  typingRow: { alignItems: 'flex-start', paddingHorizontal: 10, paddingVertical: 4 },
  typingBubble: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 5,
    minWidth: 54,
  },
  typingDot: { color: '#8899aa', fontSize: 20, letterSpacing: 2, lineHeight: 22 },
  inputBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingTop: 8,
    paddingBottom: 8,
    backgroundColor: '#0a0a1a',
    borderTopWidth: 1,
    borderTopColor: '#151525',
  },
  mediaButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#1a1a2e',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 4,
  },
  mediaButtonText: { fontSize: 20 },
  emojiContainer: {
    backgroundColor: '#111128',
    borderTopWidth: 1,
    borderTopColor: '#1a1a2e',
    maxHeight: 200,
    padding: 8,
  },
  emojiCategoryTitle: {
    color: '#8899aa',
    fontSize: 12,
    fontWeight: '600',
    marginHorizontal: 4,
    marginTop: 6,
    marginBottom: 4,
  },
  emojiRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'flex-start',
    marginBottom: 4,
  },
  emojiItem: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emojiText: {
    fontSize: 24,
  },
  emojiButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#1a1a2e',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 4,
  },
  emojiButtonText: {
    fontSize: 20,
  },
  mediaOptions: {
    flexDirection: 'row',
    position: 'absolute',
    bottom: 60,
    left: 8,
    right: 8,
    backgroundColor: '#111128',
    borderRadius: 12,
    padding: 8,
    zIndex: 10,
  },
  mediaOption: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 8,
  },
  mediaOptionText: { color: '#e0e0e0', fontSize: 14 },
  inputPill: {
    flex: 1,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#1a1a2e',
    borderWidth: 1,
    borderColor: '#2a2a3e',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  input: { color: '#e0e0e0', fontSize: 15, padding: 0 },
  sendButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    marginLeft: 8,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#B8860B',
  },
  sendButtonDisabled: { backgroundColor: '#333' },
  sendText: { color: '#ffffff', fontWeight: 'bold', fontSize: 20, lineHeight: 22, marginLeft: 2 },
  sendTextDisabled: { color: '#666' },
  chatImage: {
    width: 200,
    height: 200,
    borderRadius: 12,
    marginBottom: 4,
  },
  voiceBubble: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1c1c2e',
    padding: 12,
    borderRadius: 14,
    gap: 10,
  },
  voicePlayButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: '#B8860B',
    alignItems: 'center',
    justifyContent: 'center',
  },
  voicePlayText: { fontSize: 16 },
  voiceWaveform: { color: '#e0e0e0', fontSize: 16, letterSpacing: 1 },
  voiceDuration: { color: '#8899aa', fontSize: 13 },
});
