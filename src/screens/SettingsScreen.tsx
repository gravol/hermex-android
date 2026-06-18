/**
 * Settings screen — connection info, Clerk control, network status.
 */
import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  Clipboard,
  Switch,
  Alert,
} from 'react-native';
import { getHermesAPI, ConnectionStatus } from '../services/api';
import { exportCurrentChat, getLastBackupTimestamp } from '../services/backup';
import {
  areNotificationsEnabled,
  getDeviceId,
  getExpoPushToken,
  getUnifiedPushStatus,
  setNotificationsEnabled,
  type UnifiedPushStatus,
} from '../services/notifications';
import { NTFY_SERVER_URL } from '../config';
import { detectNetwork } from '../utils/network';

export default function SettingsScreen() {
  const api = getHermesAPI();
  const [status, setStatus] = useState<ConnectionStatus>(api.status);
  const [activeModel, setActiveModel] = useState('Unknown');
  const [copied, setCopied] = useState(false);
  const [isClerkOnline, setIsClerkOnline] = useState(false);
  const [notificationsEnabled, setNotificationsEnabledState] = useState(false);
  const [pushToken, setPushToken] = useState<string | null>(null);
  const [unifiedPushStatus, setUnifiedPushStatus] = useState<UnifiedPushStatus>('unavailable');
  const [deviceId, setDeviceId] = useState<string | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(api.getCurrentSessionId());
  const [apiSessionId, setApiSessionId] = useState<string | null>(api.getCurrentApiSessionId());
  const [lastBackup, setLastBackup] = useState<string | null>(null);
  const [isExporting, setIsExporting] = useState(false);
  const [networkInfo, setNetworkInfo] = useState(api.networkInfo);
  const [lastError, setLastError] = useState<string | null>(api.lastError);

  useEffect(() => {
    const unsub = api.onStatus((s) => {
      setStatus(s);
      setNetworkInfo(api.networkInfo);
      setLastError(api.lastError);
    });
    return unsub;
  }, []);

  useEffect(() => {
    const unsub = api.onMessage((data) => {
      if (data.type === 'session_updated') {
        setSessionId(data.sessionId || null);
        setApiSessionId(data.apiSessionId || null);
      }
    });

    setSessionId(api.getCurrentSessionId());
    setApiSessionId(api.getCurrentApiSessionId());
    getLastBackupTimestamp().then(setLastBackup);

    return unsub;
  }, []);

  useEffect(() => {
    setActiveModel(status === 'connected' ? api.getActiveModel() : 'Unknown');
  }, [status]);

  useEffect(() => {
    let isMounted = true;

    areNotificationsEnabled().then(enabled => {
      if (isMounted) {
        setNotificationsEnabledState(enabled);
        setPushToken(getExpoPushToken());
        setUnifiedPushStatus(getUnifiedPushStatus());
      }
    });
    getDeviceId().then(id => {
      if (isMounted) {
        setDeviceId(id);
      }
    });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    let isMounted = true;

    fetch('http://100.110.113.97:11434/api/tags', {
      signal: AbortSignal.timeout(3000),
    })
      .then(response => {
        if (isMounted) {
          setIsClerkOnline(response.ok);
        }
      })
      .catch(() => {
        if (isMounted) {
          setIsClerkOnline(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const handleReconnect = () => {
    detectNetwork().then(info => {
      api.connect(info.host);
    });
  };

  const handleCopyIP = (text: string) => {
    // expo-clipboard
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const wakeClerk = () => {
    api.sendMessage('/terminal wakeonlan 30:9c:23:a2:7f:a5');
  };

  const handleNotificationsToggle = async (enabled: boolean) => {
    setNotificationsEnabledState(enabled);
    await setNotificationsEnabled(enabled);
    setPushToken(getExpoPushToken());
    setUnifiedPushStatus(getUnifiedPushStatus());
  };

  const truncatedDeviceId = deviceId
    ? `${deviceId.slice(0, 18)}…${deviceId.slice(-8)}`
    : 'Not generated yet';

  const handleExportChat = async () => {
    setIsExporting(true);
    try {
      const result = await exportCurrentChat(api);
      setLastBackup(result.timestamp);
      Alert.alert(
        'Chat export requested',
        `Hermes was asked to save ${result.messages.length} messages to:\n${result.path}`,
      );
    } catch (e: any) {
      Alert.alert('Export failed', e?.message || 'Unable to export chat.');
    } finally {
      setIsExporting(false);
    }
  };

  const statusColor = status === 'connected' ? '#4caf50' : 
                      status === 'connecting' ? '#ff9800' : '#f44336';
  const statusText = status === 'connected' ? 'Connected' :
                     status === 'connecting' ? 'Connecting...' :
                     status === 'error' ? 'Connection Error' : 'Disconnected';

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Connection</Text>

      <View style={styles.card}>
        <View style={styles.row}>
          <Text style={styles.label}>Status</Text>
          <Text style={[styles.value, { color: statusColor }]}>● {statusText}</Text>
        </View>
        {networkInfo && (
          <View style={styles.row}>
            <Text style={styles.label}>Via</Text>
            <Text style={[styles.value, { color: '#4ecdc4' }]}>{networkInfo.type} ({networkInfo.host})</Text>
          </View>
        )}
        {lastError && (
          <View style={styles.row}>
            <Text style={styles.label}>Error</Text>
            <Text style={[styles.value, { color: '#f44336', fontSize: 12 }]} numberOfLines={2}>{lastError}</Text>
          </View>
        )}
        <TouchableOpacity style={styles.button} onPress={handleReconnect}>
          <Text style={styles.buttonText}>Reconnect</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.sectionTitle}>Session</Text>

      <View style={styles.card}>
        <View style={styles.row}>
          <Text style={styles.label}>Responses session</Text>
          <Text style={styles.sessionValue} numberOfLines={1}>{sessionId || 'New session'}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>API transcript</Text>
          <Text style={styles.sessionValue} numberOfLines={1}>{apiSessionId || 'Not resolved yet'}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>Last backup</Text>
          <Text style={styles.value}>{lastBackup || 'Never'}</Text>
        </View>
        <TouchableOpacity
          style={[styles.button, isExporting && styles.buttonDisabled]}
          onPress={handleExportChat}
          disabled={isExporting}
        >
          <Text style={styles.buttonText}>{isExporting ? 'Exporting...' : 'Export Chat'}</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.sectionTitle}>Notifications</Text>

      <View style={styles.card}>
        <View style={styles.row}>
          <View style={styles.settingText}>
            <Text style={styles.cardTitle}>Hermes replies</Text>
            <Text style={styles.description}>Show Android notifications for replies and errors while the app is in the background.</Text>
          </View>
          <Switch
            value={notificationsEnabled}
            onValueChange={handleNotificationsToggle}
            trackColor={{ false: '#33384a', true: '#6b5408' }}
            thumbColor={notificationsEnabled ? '#B8860B' : '#8899aa'}
          />
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>Expo push token</Text>
          <Text style={styles.value}>{pushToken ? 'Registered' : 'Not registered'}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>UnifiedPush</Text>
          <Text style={styles.value}>{unifiedPushStatus.replace('_', ' ')}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>ntfy server</Text>
          <Text style={styles.mono}>{NTFY_SERVER_URL}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>Device ID</Text>
          <Text style={styles.mono}>{truncatedDeviceId}</Text>
        </View>
      </View>

      <Text style={styles.sectionTitle}>Bridges</Text>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>bigred (host)</Text>
        <View style={styles.row}>
          <Text style={styles.label}>API Server</Text>
          <Text style={styles.mono}>http://:8650</Text>
        </View>
        <TouchableOpacity
          style={styles.copyButton}
          onPress={() => handleCopyIP('http://bigred:8650/v1/responses')}
        >
          <Text style={styles.copyText}>{copied ? '✓ Copied' : 'Copy address'}</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.card}>
        <Text style={styles.cardTitle}>Clerk</Text>
        <View style={styles.row}>
          <Text style={styles.label}>IP</Text>
          <Text style={styles.mono}>100.110.113.97</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>GPU</Text>
          <Text style={styles.value}>RX 9060 XT</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>Status</Text>
          <Text style={[styles.value, { color: isClerkOnline ? '#4caf50' : '#ff9800' }]}>● {isClerkOnline ? 'Online' : 'Sleeping'}</Text>
        </View>
        <TouchableOpacity style={styles.button} onPress={wakeClerk}>
          <Text style={styles.buttonText}>Wake Clerk</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.sectionTitle}>Hermes Info</Text>

      <View style={styles.card}>
        <View style={styles.row}>
          <Text style={styles.label}>Model</Text>
          <Text style={styles.value}>{activeModel || 'Unknown'}</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>Through</Text>
          <Text style={styles.value}>Headroom (port 8787)</Text>
        </View>
        <View style={styles.row}>
          <Text style={styles.label}>Bridge</Text>
          <Text style={styles.value}>Port 8765</Text>
        </View>
      </View>

      <Text style={styles.footer}>
        All traffic stays on your Tailscale/WiFi network. Zero third parties.
      </Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0a0a1a',
    padding: 16,
  },
  title: {
    color: '#e0c070',
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  sectionTitle: {
    color: '#8899aa',
    fontSize: 14,
    fontWeight: '600',
    marginTop: 20,
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  card: {
    backgroundColor: '#111128',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#1a1a2e',
  },
  cardTitle: {
    color: '#e0c070',
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 10,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  settingText: {
    flex: 1,
    paddingRight: 12,
  },
  description: {
    color: '#8899aa',
    fontSize: 13,
    lineHeight: 18,
  },
  label: {
    color: '#667788',
    fontSize: 14,
  },
  value: {
    color: '#e0e0e0',
    fontSize: 14,
  },
  sessionValue: {
    color: '#e0e0e0',
    fontSize: 12,
    flex: 1,
    textAlign: 'right',
    marginLeft: 12,
  },
  mono: {
    color: '#4ecdc4',
    fontSize: 13,
    fontFamily: 'monospace',
  },
  button: {
    backgroundColor: '#B8860B',
    borderRadius: 8,
    paddingVertical: 10,
    alignItems: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: 'white',
    fontWeight: 'bold',
    fontSize: 14,
  },
  copyButton: {
    marginTop: 4,
  },
  copyText: {
    color: '#4ecdc4',
    fontSize: 12,
  },
  footer: {
    color: '#445566',
    fontSize: 12,
    textAlign: 'center',
    marginTop: 24,
    marginBottom: 40,
    lineHeight: 18,
  },
});
