/**
 * Command shortcuts — dropdown of common Hermes slash commands.
 */
import React, { useState } from 'react';
import { View, TouchableOpacity, Text, ScrollView, StyleSheet } from 'react-native';

const COMMANDS = [
  '/new', '/stop', '/clear', '/queue',
  '/retry', '/undo', '/compress',
  '/model', '/skills', '/memory',
  '/help', '/usage', '/status',
];

interface Props {
  onSelect: (command: string) => void;
}

export default function CommandShortcuts({ onSelect }: Props) {
  const [expanded, setExpanded] = useState(false);

  return (
    <View style={styles.container}>
      <TouchableOpacity
        style={styles.toggle}
        onPress={() => setExpanded(!expanded)}
      >
        <Text style={styles.toggleText}>
          {expanded ? '▲ Slash Commands' : '▼ Slash Commands'}
        </Text>
      </TouchableOpacity>
      {expanded && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.scroll}
        >
          {COMMANDS.map(cmd => (
            <TouchableOpacity
              key={cmd}
              style={styles.chip}
              onPress={() => {
                onSelect(cmd);
                setExpanded(false);
              }}
            >
              <Text style={styles.chipText}>{cmd}</Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 4,
  },
  toggle: {
    paddingVertical: 4,
    paddingHorizontal: 12,
  },
  toggleText: {
    fontSize: 12,
    color: '#B8860B',
  },
  scroll: {
    maxHeight: 40,
    paddingHorizontal: 8,
  },
  chip: {
    backgroundColor: '#1a1a2e',
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 6,
    marginRight: 8,
    borderWidth: 1,
    borderColor: '#B8860B',
  },
  chipText: {
    color: '#e0c070',
    fontSize: 13,
    fontWeight: '500',
  },
});
