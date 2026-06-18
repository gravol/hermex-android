/**
 * Chat backup helpers.
 *
 * The mobile app cannot write directly to bigred's filesystem. Until Hermes exposes
 * a dedicated export endpoint, this uses the Hermes API itself: it loads the best
 * available session transcript, then sends a hidden /save request asking Hermes to
 * persist the JSON under the user's Obsidian chat backup directory.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { HermesAPI, SessionMessage } from './api';

export const LAST_BACKUP_STORAGE_KEY = 'hermes_last_backup_timestamp';
export const BACKUP_DIRECTORY = '~/Documents/ObsidianVault/Chat-Backups/';

export interface ChatBackupResult {
  timestamp: string;
  path: string;
  sessionId: string | null;
  response: string;
  messages: SessionMessage[];
}

function backupTimestamp(date = new Date()): string {
  return date.toISOString().replace(/[:.]/g, '-');
}

export async function getLastBackupTimestamp(): Promise<string | null> {
  return AsyncStorage.getItem(LAST_BACKUP_STORAGE_KEY);
}

async function resolveExportSessionId(api: HermesAPI): Promise<string | null> {
  const currentApiSessionId = api.getCurrentApiSessionId();
  if (currentApiSessionId) return currentApiSessionId;

  const sessions = await api.listSessions();
  const latestApiSession = sessions
    .filter(session => session.source === 'api_server')
    .sort((a, b) => (b.last_active || 0) - (a.last_active || 0))[0] || sessions[0];

  return latestApiSession?.id || null;
}

export async function exportCurrentChat(api: HermesAPI): Promise<ChatBackupResult> {
  const sessionId = await resolveExportSessionId(api);
  const messages = sessionId ? await api.loadSessionMessages(sessionId) : [];
  const timestamp = backupTimestamp();
  const path = `${BACKUP_DIRECTORY}hermes-chat-backup-${timestamp}.json`;

  const exportPayload = {
    exported_at: new Date().toISOString(),
    response_session_id: api.getLastResponseId(),
    api_session_id: sessionId,
    message_count: messages.length,
    messages,
  };

  const saveCommand = [
    `/save this conversation to ${path}`,
    '',
    'Save the following JSON exactly as the file contents:',
    '```json',
    JSON.stringify(exportPayload, null, 2),
    '```',
  ].join('\n');

  const response = await api.sendHiddenMessage(saveCommand);
  await AsyncStorage.setItem(LAST_BACKUP_STORAGE_KEY, timestamp);

  return {
    timestamp,
    path,
    sessionId,
    response,
    messages,
  };
}
