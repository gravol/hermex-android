/**
 * Terminal screen — run commands on bigred or Clerk.
 * Uses a simple input/output pattern (full xterm.js PTY can be added later).
 */
import React, { useRef, useState, useEffect } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  Platform,
} from 'react-native';
import { getHermesAPI } from '../services/api';

interface TermLine {
  id: string;
  text: string;
  isCommand: boolean;
  isOutput: boolean;
}

export default function TerminalScreen() {
  const [lines, setLines] = useState<TermLine[]>([
    { id: 'welcome', text: '╔══ Hermes Terminal ══╗', isCommand: false, isOutput: false },
    { id: 'welcome2', text: '║ Type commands below  ║', isCommand: false, isOutput: false },
    { id: 'welcome3', text: '╚══════════════════════╝', isCommand: false, isOutput: false },
    { id: 'target', text: 'Target: bigred (tap to switch)', isCommand: false, isOutput: false },
  ]);
  const [command, setCommand] = useState('');
  const [target, setTarget] = useState<'bigred' | 'clerk'>('bigred');
  const [isRunning, setIsRunning] = useState(false);
  const scrollRef = useRef<ScrollView>(null);
  const inputRef = useRef<TextInput>(null);
  const api = getHermesAPI();
  const historyRef = useRef<string[]>([]);
  const historyIdxRef = useRef(-1);

  useEffect(() => {
    const unsub = api.onMessage((data) => {
      if (data.type === 'terminal_output') {
        setIsRunning(false);
        addLine('output', data.content);
      } else if (data.type === 'status' && data.content === 'running') {
        setIsRunning(true);
      }
    });
    return unsub;
  }, []);

  const addLine = (type: 'command' | 'output', text: string) => {
    setLines(prev => [...prev, {
      id: Date.now().toString(),
      text,
      isCommand: type === 'command',
      isOutput: type === 'output',
    }]);
  };

  const handleSend = () => {
    const cmd = command.trim();
    if (!cmd) return;

    addLine('command', `${target}$ ${cmd}`);
    historyRef.current.push(cmd);
    historyIdxRef.current = historyRef.current.length;
    setCommand('');
    setIsRunning(true);
    api.sendMessage(`/terminal ${cmd}`);
    inputRef.current?.focus();
  };

  const handleHistory = (direction: 'up' | 'down') => {
    if (direction === 'up' && historyIdxRef.current > 0) {
      historyIdxRef.current--;
      setCommand(historyRef.current[historyIdxRef.current]);
    } else if (direction === 'down' && historyIdxRef.current < historyRef.current.length - 1) {
      historyIdxRef.current++;
      setCommand(historyRef.current[historyIdxRef.current]);
    } else if (direction === 'down') {
      historyIdxRef.current = historyRef.current.length;
      setCommand('');
    }
  };

  const toggleTarget = () => {
    const newTarget = target === 'bigred' ? 'clerk' : 'bigred';
    setTarget(newTarget);
    api.sendMessage(`/terminal switch ${newTarget}`);
    addLine('output', `→ Switched to ${newTarget}`);
  };

  const clearScreen = () => {
    setLines([]);
  };

  return (
    <View style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={toggleTarget} style={styles.targetButton}>
          <Text style={styles.targetText}>🔷 {target}</Text>
        </TouchableOpacity>
        <TouchableOpacity onPress={clearScreen} style={styles.clearButton}>
          <Text style={styles.clearText}>Clear</Text>
        </TouchableOpacity>
      </View>

      {/* Terminal output */}
      <ScrollView
        ref={scrollRef}
        style={styles.output}
        onContentSizeChange={() => scrollRef.current?.scrollToEnd()}
      >
        {lines.map(line => (
          <Text
            key={line.id}
            style={[
              styles.line,
              line.isCommand && styles.commandLine,
              line.isOutput && styles.outputLine,
            ]}
            selectable
          >
            {line.text}
          </Text>
        ))}
        {isRunning && (
          <Text style={styles.running}>⏳ Running...</Text>
        )}
      </ScrollView>

      {/* Input */}
      <View style={styles.inputBar}>
        <Text style={styles.prompt}>{target}$</Text>
        <TextInput
          ref={inputRef}
          style={styles.input}
          value={command}
          onChangeText={setCommand}
          placeholder="Type a command..."
          placeholderTextColor="#444"
          autoCapitalize="none"
          autoCorrect={false}
          onSubmitEditing={handleSend}
          blurOnSubmit={false}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0a0a0a',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: '#111',
    borderBottomWidth: 1,
    borderBottomColor: '#222',
  },
  targetButton: {
    backgroundColor: '#1a1a2e',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderWidth: 1,
    borderColor: '#B8860B',
  },
  targetText: {
    color: '#e0c070',
    fontSize: 13,
    fontWeight: 'bold',
  },
  clearButton: {
    padding: 6,
  },
  clearText: {
    color: '#667',
    fontSize: 12,
  },
  output: {
    flex: 1,
    padding: 12,
  },
  line: {
    color: '#d0d0d0',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 12,
    lineHeight: 18,
    marginBottom: 2,
  },
  commandLine: {
    color: '#4ecdc4',
    fontWeight: 'bold',
  },
  outputLine: {
    color: '#a0a0a0',
  },
  running: {
    color: '#ff9800',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 12,
    fontStyle: 'italic',
  },
  inputBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 8,
    backgroundColor: '#111',
    borderTopWidth: 1,
    borderTopColor: '#222',
  },
  prompt: {
    color: '#4ecdc4',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 14,
    fontWeight: 'bold',
    marginRight: 6,
  },
  input: {
    flex: 1,
    backgroundColor: '#1a1a1a',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    color: '#e0e0e0',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontSize: 13,
  },
});
