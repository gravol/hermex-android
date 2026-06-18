/**
 * Chat screen — talk to Hermes via the API server (HTTP streaming, port 8650).
 * Full tools, memory, skills, streaming — no bridge needed.
 */
import React, { useEffect, useRef, useState } from 'react';
import {
  View, Text, TextInput, TouchableOpacity, FlatList,
  StyleSheet, KeyboardAvoidingView, Platform, AppState,
} from 'react-native';
import { getHermesAPI, ConnectionStatus } from '../services/api';
import { showLocalNotification } from '../services/notifications';
import CommandShortcuts from '../components/CommandShortcuts';

interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system' | 'error';
  content: string;
  timestamp: number;
}

export default function ChatScreen() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [status, setStatus] = useState<ConnectionStatus>('disconnected');
  const [isTyping, setIsTyping] = useState(false);
  const flatListRef = useRef<FlatList>(null);
  const inputRef = useRef<TextInput>(null);
  const appStateRef = useRef(AppState.currentState);
  const assistantResponseRef = useRef('');
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
          break;
      }
    });

    const unsubStatus = api.onStatus(setStatus);
    return () => {
      unsub();
      unsubStatus();
    };
  }, []);

  const addMessage = (role: Message['role'], content: string) => {
    setMessages(prev => [...prev, { id: Date.now().toString(), role, content, timestamp: Date.now() }]);
  };

  const handleSend = () => {
    const text = input.trim();
    if (!text) return;

    addMessage('user', text);
    assistantResponseRef.current = '';
    setInput('');
    setIsTyping(true);
    api.sendMessage(text);
    inputRef.current?.focus();
  };

  const handleCommandSelect = (cmd: string) => {
    setInput(cmd + ' ');
  };

  const renderMessage = ({ item }: { item: Message }) => {
    const isUser = item.role === 'user';
    const isError = item.role === 'error';
    const isSystem = item.role === 'system';

    return (
      <View style={[
        styles.bubble,
        isUser ? styles.userBubble : styles.assistantBubble,
        isError ? styles.errorBubble : null,
        isSystem ? styles.systemBubble : null,
      ]}>
        {!isUser && !isSystem && (
          <Text style={styles.roleLabel}>{isError ? '⚠️' : '🤖'}</Text>
        )}
        <Text style={[
          styles.bubbleText,
          isUser ? styles.userText : styles.assistantText,
          isError ? styles.errorText : null,
          isSystem ? styles.systemText : null,
        ]}>
          {item.content}
        </Text>
      </View>
    );
  };

  return (
    <KeyboardAvoidingView style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={styles.statusBar}>
        <Text style={[styles.statusDot, {
          color: status === 'connected' ? '#4caf50' : status === 'connecting' ? '#ff9800' : '#f44336'
        }]}>● </Text>
        <Text style={styles.statusText}>
          {status === 'connected' ? 'Connected' : status === 'connecting' ? 'Connecting...' :
           status === 'error' ? 'Error' : 'Disconnected'}
        </Text>
      </View>

      <FlatList ref={flatListRef}
        data={messages} renderItem={renderMessage}
        keyExtractor={item => item.id}
        style={styles.messageList}
        contentContainerStyle={styles.messageContainer}
        onContentSizeChange={() => flatListRef.current?.scrollToEnd()} />

      {isTyping && (
        <View style={styles.typingIndicator}>
          <Text style={styles.typingText}>Hermes is thinking...</Text>
        </View>
      )}

      <CommandShortcuts onSelect={handleCommandSelect} />

      <View style={styles.inputBar}>
        <TextInput ref={inputRef} style={styles.input}
          value={input} onChangeText={setInput}
          placeholder="Message Hermes..." placeholderTextColor="#666"
          multiline maxLength={4000}
          onSubmitEditing={handleSend} blurOnSubmit={false} />
        <TouchableOpacity style={[styles.sendButton, !input.trim() && styles.sendButtonDisabled]}
          onPress={handleSend} disabled={!input.trim()}>
          <Text style={styles.sendText}>Send</Text>
        </TouchableOpacity>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0a0a1a' },
  statusBar: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 6, backgroundColor: '#111128' },
  statusDot: { fontSize: 10, marginRight: 6 },
  statusText: { color: '#8899aa', fontSize: 12 },
  messageList: { flex: 1 },
  messageContainer: { padding: 12, paddingBottom: 4 },
  bubble: { maxWidth: '85%', padding: 12, borderRadius: 16, marginBottom: 8 },
  userBubble: { backgroundColor: '#1a3a5c', alignSelf: 'flex-end', borderBottomRightRadius: 4 },
  assistantBubble: { backgroundColor: '#111128', alignSelf: 'flex-start', borderBottomLeftRadius: 4, borderWidth: 1, borderColor: '#1a1a2e' },
  errorBubble: { backgroundColor: '#2a1010', borderColor: '#5c1a1a' },
  systemBubble: { backgroundColor: '#1a1a2e', alignSelf: 'center', borderColor: '#B8860B', borderWidth: 0.5 },
  roleLabel: { fontSize: 10, marginBottom: 2, color: '#666' },
  bubbleText: { fontSize: 15, lineHeight: 21 },
  userText: { color: '#e0e8f0' },
  assistantText: { color: '#d0d8e0' },
  errorText: { color: '#ff6b6b' },
  systemText: { color: '#e0c070', fontSize: 13 },
  typingIndicator: { paddingHorizontal: 16, paddingVertical: 4 },
  typingText: { color: '#667788', fontSize: 12, fontStyle: 'italic' },
  inputBar: { flexDirection: 'row', alignItems: 'flex-end', paddingHorizontal: 8, paddingVertical: 8, backgroundColor: '#111128', borderTopWidth: 1, borderTopColor: '#1a1a2e' },
  input: { flex: 1, backgroundColor: '#1a1a2e', borderRadius: 20, paddingHorizontal: 16, paddingVertical: 10, color: '#e0e0e0', fontSize: 15, maxHeight: 100 },
  sendButton: { backgroundColor: '#B8860B', borderRadius: 20, paddingHorizontal: 20, paddingVertical: 10, marginLeft: 8 },
  sendButtonDisabled: { backgroundColor: '#333', opacity: 0.5 },
  sendText: { color: 'white', fontWeight: 'bold', fontSize: 15 },
});
