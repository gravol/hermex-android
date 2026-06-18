/**
 * Hermes API client — connects directly to the Hermes API server (port 8650).
 * Uses stateful /v1/responses with previous_response_id for continuity.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { API_PORT, API_KEY, TAILSCALE_HOST } from '../config';
import { detectNetwork, NetworkInfo } from '../utils/network';

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

export const HERMES_SESSION_STORAGE_KEY = 'hermes_session_id';
export const HERMES_API_SESSION_STORAGE_KEY = 'hermes_api_session_id';

type MessageHandler = (data: any) => void;
type StatusHandler = (status: ConnectionStatus) => void;

export interface ModelsResponse {
  data?: Array<{ id?: string; [key: string]: any }>;
  [key: string]: any;
}

export interface SessionMessage {
  id?: string | number;
  session_id?: string;
  role: 'system' | 'user' | 'assistant' | 'tool' | 'error';
  content: string;
  timestamp?: number;
  [key: string]: any;
}

export interface SessionListItem {
  id: string;
  source?: string;
  preview?: string;
  last_active?: number;
  message_count?: number;
  [key: string]: any;
}

interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

type ResponsesInput = string | Array<{
  role: 'user' | 'assistant' | 'system';
  content: Array<
    | { type: 'input_text'; text: string }
    | { type: 'input_image'; image_url: string }
    | { type: 'input_audio'; input_audio: { data: string; format: string } }
  >;
}>;

function extractResponseText(response: any): string {
  const content = response?.output?.flatMap((item: any) => item?.content || []) || [];
  return content.map((part: any) => part?.text || '').join('');
}

export class HermesAPI {
  private messageHandlers: MessageHandler[] = [];
  private statusHandlers: StatusHandler[] = [];
  private _status: ConnectionStatus = 'disconnected';
  private history: ChatMessage[] = [];
  private abortController: AbortController | null = null;
  private activeModel = 'Unknown';
  private modelInfo: ModelsResponse | null = null;
  private lastResponseId: string | null = null;
  private currentApiSessionId: string | null = null;
  private _networkInfo: NetworkInfo | null = null;
  private _lastError: string | null = null;

  get networkInfo(): NetworkInfo | null {
    return this._networkInfo;
  }

  get lastError(): string | null {
    return this._lastError;
  }

  get status(): ConnectionStatus {
    return this._status;
  }

  private setStatus(s: ConnectionStatus) {
    this._status = s;
    this.statusHandlers.forEach(h => h(s));
  }

  async connect(host: string) {
    this.setStatus('connecting');
    
    try {
      const [storedResponseId, storedApiSessionId] = await Promise.all([
        AsyncStorage.getItem(HERMES_SESSION_STORAGE_KEY),
        AsyncStorage.getItem(HERMES_API_SESSION_STORAGE_KEY),
      ]);
      this.lastResponseId = storedResponseId;
      this.currentApiSessionId = storedApiSessionId;

      // Store which host we're connecting to
      this._networkInfo = {
        host,
        type: host === TAILSCALE_HOST ? 'tailscale' : host === '192.168.68.105' ? 'local' : 'unknown',
      };

      const resp = await fetch(`http://${host}:${API_PORT}/v1/models`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${API_KEY}`,
        },
        signal: AbortSignal.timeout(8000),
      });
      
      if (resp.ok) {
        const modelInfo: ModelsResponse = await resp.json();
        this.modelInfo = modelInfo;
        this.activeModel = modelInfo.data?.[0]?.id || 'Unknown';
        this.setStatus('connected');
        this.emit({
          type: 'connected',
          content: this.lastResponseId ? 'Connected to Hermes API (session resumed)' : 'Connected to Hermes API',
        });
        this.emitSessionUpdated();

        import('./notifications')
          .then(({ sendPushEndpointToHermes }) => sendPushEndpointToHermes(this))
          .catch(error => console.warn('UnifiedPush bridge registration failed:', error));
      } else {
        throw new Error(`Status ${resp.status}`);
      }
    } catch (e) {
      console.error('Connection failed:', e);
      this._lastError = e instanceof Error ? e.message : String(e);
      this._networkInfo = null;
      this.setStatus('error');
      // Retry after 3 seconds
      setTimeout(() => this.connect(host), 3000);
    }
  }

  getActiveModel(): string {
    return this.activeModel;
  }

  getModelInfo(): ModelsResponse | null {
    return this.modelInfo;
  }

  getLastResponseId(): string | null {
    return this.lastResponseId;
  }

  getCurrentSessionId(): string | null {
    return this.lastResponseId;
  }

  getCurrentApiSessionId(): string | null {
    return this.currentApiSessionId;
  }

  disconnect() {
    if (this.abortController) {
      this.abortController.abort();
      this.abortController = null;
    }
    this.setStatus('disconnected');
  }

  private async persistResponseId(responseId: string | null) {
    this.lastResponseId = responseId;
    if (responseId) {
      await AsyncStorage.setItem(HERMES_SESSION_STORAGE_KEY, responseId);
    } else {
      await AsyncStorage.removeItem(HERMES_SESSION_STORAGE_KEY);
    }
    this.emitSessionUpdated();
  }

  private async persistApiSessionId(sessionId: string | null) {
    this.currentApiSessionId = sessionId;
    if (sessionId) {
      await AsyncStorage.setItem(HERMES_API_SESSION_STORAGE_KEY, sessionId);
    } else {
      await AsyncStorage.removeItem(HERMES_API_SESSION_STORAGE_KEY);
    }
    this.emitSessionUpdated();
  }

  private emitSessionUpdated() {
    this.emit({
      type: 'session_updated',
      sessionId: this.lastResponseId,
      apiSessionId: this.currentApiSessionId,
    });
  }

  private async buildResponsesBody(input: ResponsesInput, stream: boolean) {
    const body: Record<string, any> = {
      model: 'hermes-agent',
      input,
      stream,
    };

    if (this.lastResponseId) {
      body.previous_response_id = this.lastResponseId;
    }

    return body;
  }

  private async refreshCurrentApiSessionId() {
    try {
      const sessions = await this.listSessions();
      const latestApiSession = sessions
        .filter(session => session.source === 'api_server')
        .sort((a, b) => (b.last_active || 0) - (a.last_active || 0))[0] || sessions[0];

      if (latestApiSession?.id) {
        await this.persistApiSessionId(latestApiSession.id);
      }
    } catch {
      // Session metadata is best-effort; previous_response_id is the source of truth for resume.
    }
  }

  private async sendResponsesInput(input: ResponsesInput, historyContent: string) {
    if (this._status !== 'connected') return;
    if (!historyContent.trim()) return;

    // Keep a lightweight local history only for UI/error recovery. Server-side continuity uses previous_response_id.
    this.history.push({ role: 'user', content: historyContent });

    this.abortController?.abort();
    this.abortController = new AbortController();

    const net = await detectNetwork();
    const url = `http://${net.host}:${API_PORT}/v1/responses`;

    this.emit({ type: 'status', content: 'thinking' });

    try {
      const resp = await fetch(url, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${API_KEY}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(await this.buildResponsesBody(input, true)),
        signal: this.abortController.signal,
      });

      if (!resp.ok) {
        const errText = await resp.text();
        this.emit({ type: 'error', content: `API error ${resp.status}: ${errText.slice(0, 200)}` });
        this.history.pop();
        return;
      }

      const reader = resp.body?.getReader();
      if (!reader) {
        this.emit({ type: 'error', content: 'No response body' });
        return;
      }

      const decoder = new TextDecoder();
      let buffer = '';
      let fullText = '';
      let responseId: string | null = null;

      const finishResponse = async () => {
        if (fullText) {
          this.history.push({ role: 'assistant', content: fullText });
        }
        if (responseId) {
          await this.persistResponseId(responseId);
          await this.refreshCurrentApiSessionId();
        }
        this.emit({ type: 'response_end', content: fullText });
      };

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';  // Keep incomplete line in buffer
        
        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed || !trimmed.startsWith('data: ')) continue;
          
          const data = trimmed.slice(6);
          if (data === '[DONE]') {
            await finishResponse();
            return;
          }
          
          try {
            const chunk = JSON.parse(data);

            if (chunk?.response?.id) {
              responseId = chunk.response.id;
            } else if (chunk?.id && typeof chunk.id === 'string') {
              responseId = chunk.id;
            }

            const delta = chunk.type === 'response.output_text.delta'
              ? chunk.delta
              : chunk.choices?.[0]?.delta?.content;

            if (delta) {
              fullText += delta;
              this.emit({ type: 'response_chunk', content: delta });
            }

            if (chunk.type === 'response.completed') {
              const completedId = chunk.response?.id;
              if (completedId) responseId = completedId;
              if (!fullText) fullText = extractResponseText(chunk.response);
            }
          } catch {
            // Skip malformed JSON
          }
        }
      }

      // If stream ended without [DONE]
      await finishResponse();

    } catch (e: any) {
      if (e.name !== 'AbortError') {
        this.emit({ type: 'error', content: `Network error: ${e.message}` });
        this.history.pop();
      }
    }
  }

  async sendMessage(content: string) {
    await this.sendResponsesInput(content, content);
  }

  async sendImage(base64: string, mimeType: string = 'image/jpeg') {
    const sanitizedBase64 = base64.replace(/^data:[^;]+;base64,/, '');
    const imageUrl = `data:${mimeType};base64,${sanitizedBase64}`;
    const input: ResponsesInput = [{
      role: 'user',
      content: [
        { type: 'input_text', text: 'Look at this image' },
        { type: 'input_image', image_url: imageUrl },
      ],
    }];

    await this.sendResponsesInput(input, 'Look at this image');
  }

  async sendAudio(base64Audio: string, mimeType: string = 'audio/mp4', prompt: string = 'Voice note') {
    const sanitizedBase64 = base64Audio.replace(/^data:[^;]+;base64,/, '');
    const format = mimeType.includes('webm') ? 'webm'
      : mimeType.includes('wav') ? 'wav'
      : mimeType.includes('mpeg') || mimeType.includes('mp3') ? 'mp3'
      : 'mp4';
    const input: ResponsesInput = [{
      role: 'user',
      content: [
        { type: 'input_text', text: 'Transcribe and respond to this voice note.' },
        { type: 'input_audio', input_audio: { data: sanitizedBase64, format } },
      ],
    }];

    await this.sendResponsesInput(input, `🎤 ${prompt}`);
  }

  async sendHiddenMessage(content: string): Promise<string> {
    if (!content.trim()) return '';

    const net = await detectNetwork();
    const resp = await fetch(`http://${net.host}:${API_PORT}/v1/responses`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(await this.buildResponsesBody(content, false)),
    });

    if (!resp.ok) {
      const errText = await resp.text();
      throw new Error(`API error ${resp.status}: ${errText.slice(0, 200)}`);
    }

    const response = await resp.json();
    if (response?.id) {
      await this.persistResponseId(response.id);
      await this.refreshCurrentApiSessionId();
    }

    return extractResponseText(response);
  }

  async listSessions(): Promise<SessionListItem[]> {
    const net = await detectNetwork();
    const resp = await fetch(`http://${net.host}:${API_PORT}/api/sessions`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${API_KEY}`,
      },
    });

    if (!resp.ok) {
      const errText = await resp.text();
      throw new Error(`Failed to list sessions (${resp.status}): ${errText.slice(0, 200)}`);
    }

    const json = await resp.json();
    return Array.isArray(json?.data) ? json.data : [];
  }

  async loadSessionMessages(sessionId: string): Promise<SessionMessage[]> {
    const net = await detectNetwork();
    const headers = { 'Authorization': `Bearer ${API_KEY}` };

    const messagesResp = await fetch(`http://${net.host}:${API_PORT}/api/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: 'GET',
      headers,
    });

    if (messagesResp.ok) {
      const json = await messagesResp.json();
      return Array.isArray(json?.data) ? json.data : [];
    }

    // Fallback for servers that expose only the session list/preview.
    const sessions = await this.listSessions();
    const preview = sessions.find(session => session.id === sessionId || session.preview === sessionId);
    if (preview?.preview) {
      return [{
        id: `${preview.id}-preview`,
        session_id: preview.id,
        role: 'user',
        content: preview.preview,
        timestamp: preview.last_active,
      }];
    }

    const errText = await messagesResp.text();
    throw new Error(`Failed to load session messages (${messagesResp.status}): ${errText.slice(0, 200)}`);
  }

  async resetConversation() {
    this.history = [];
    await this.persistResponseId(null);
    await this.persistApiSessionId(null);
    this.emit({ type: 'status', content: 'Conversation reset' });
  }

  onMessage(handler: MessageHandler) {
    this.messageHandlers.push(handler);
    return () => {
      this.messageHandlers = this.messageHandlers.filter(h => h !== handler);
    };
  }

  onStatus(handler: StatusHandler) {
    this.statusHandlers.push(handler);
    return () => {
      this.statusHandlers = this.statusHandlers.filter(h => h !== handler);
    };
  }

  private emit(data: any) {
    this.messageHandlers.forEach(h => h(data));
  }
}

// Singleton
let _instance: HermesAPI | null = null;
export function getHermesAPI(): HermesAPI {
  if (!_instance) {
    _instance = new HermesAPI();
  }
  return _instance;
}
