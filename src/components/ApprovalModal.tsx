/**
 * Approval modal — shows when Hermes needs sudo/command approval.
 */
import React, { useState } from 'react';
import {
  View,
  Text,
  Modal,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  ScrollView,
} from 'react-native';

interface Props {
  visible: boolean;
  command?: string;
  requiresPassword?: boolean;
  onApprove: (password?: string) => void;
  onDeny: () => void;
}

export default function ApprovalModal({
  visible,
  command,
  requiresPassword,
  onApprove,
  onDeny,
}: Props) {
  const [password, setPassword] = useState('');

  const handleApprove = () => {
    onApprove(requiresPassword ? password : undefined);
    setPassword('');
  };

  const handleDeny = () => {
    onDeny();
    setPassword('');
  };

  return (
    <Modal visible={visible} transparent animationType="fade">
      <View style={styles.overlay}>
        <View style={styles.modal}>
          <Text style={styles.title}>⚠️ Approve Command</Text>
          
          <Text style={styles.label}>Hermes wants to run:</Text>
          <ScrollView style={styles.commandBox}>
            <Text style={styles.commandText}>{command}</Text>
          </ScrollView>

          {requiresPassword && (
            <>
              <Text style={styles.label}>sudo password:</Text>
              <TextInput
                style={styles.passwordInput}
                secureTextEntry
                value={password}
                onChangeText={setPassword}
                placeholder="Enter password..."
                placeholderTextColor="#666"
                autoFocus
              />
            </>
          )}

          <View style={styles.buttons}>
            <TouchableOpacity
              style={[styles.button, styles.denyButton]}
              onPress={handleDeny}
            >
              <Text style={styles.buttonText}>❌ Deny</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.button, styles.approveButton]}
              onPress={handleApprove}
            >
              <Text style={styles.buttonText}>✅ Approve</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.7)',
    justifyContent: 'center',
    padding: 20,
  },
  modal: {
    backgroundColor: '#0d1b2a',
    borderRadius: 16,
    padding: 24,
    borderWidth: 1,
    borderColor: '#B8860B',
  },
  title: {
    color: '#e0c070',
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 16,
    textAlign: 'center',
  },
  label: {
    color: '#8899aa',
    fontSize: 13,
    marginBottom: 6,
    marginTop: 8,
  },
  commandBox: {
    backgroundColor: '#1a1a2e',
    borderRadius: 8,
    padding: 12,
    maxHeight: 150,
    borderWidth: 1,
    borderColor: '#333',
  },
  commandText: {
    color: '#e0e0e0',
    fontFamily: 'monospace',
    fontSize: 13,
  },
  passwordInput: {
    backgroundColor: '#1a1a2e',
    borderRadius: 8,
    padding: 12,
    color: '#e0e0e0',
    fontSize: 16,
    borderWidth: 1,
    borderColor: '#B8860B',
    marginBottom: 4,
  },
  buttons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 20,
    gap: 12,
  },
  button: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 12,
    alignItems: 'center',
  },
  approveButton: {
    backgroundColor: '#1b5e20',
  },
  denyButton: {
    backgroundColor: '#b71c1c',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
});
