/**
 * Hermes API client — connects directly to the Hermes API server (port 8650).
 * Uses stateful /v1/runs with SSE event streaming and approval support.
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import { API_PORT, API_KEY, TAILSCALE_HOST } from '../config';
import { detectNetwork, NetworkInfo } from '../utils/network';

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error';
export type ApprovalChoice = 'once' | 'session' | 'always' | 'deny';

export const HERMES_SESSION_STORAGE_KEY = 'hermes_session_id';
export const HERMES_API_SESSION_STORAGE_KEY = 'hermes_api_session_id';

type MessageHandler = (data: any) => void;
type StatusHandler = (status: ConnectionStatus) => void;

export interface ModelsResponse {
  data?: Array<{ id?: string; name?: string; provider?: string; [key: string]: any }>;
  [key: string]: any;
}

export interface ApprovalRequest {
  id?: string;
  run_id?: string;
  command?: string;
  tool?: string;
  description?: string;
  reason?: string;
  requires_password?: boolean;
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

type RunInput = string | Array<{
  role: 'user' | 'assistant' | 'system';
  content: Array<
    | { type: 'input_text'; text: string }
    | { type: 'input_image'; image_url: string }
    | { type: 'input_audio'; input_audio: { data: string; format: string } }
  >;
}>;

function extractResponseText(response: any): string {
  if (typeof response?.content === 'string') return response.content;
  if (typeof response?.text === 'string') return response.text;
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
  private lastRunId: string | null = null;
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

  private getBaseUrl(host: string) {
    return `http://${host}:${API_PORT}`;
  }

  async connect(host: string) {
    this.setStatus('connecting');

    try {
      const [storedRunId, storedApiSessionId] = await Promise.all([
        AsyncStorage.getItem(HERMES_SESSION_STORAGE_KEY),
        AsyncStorage.getItem(HERMES_API_SESSION_STORAGE_KEY),
      ]);
      this.lastRunId = storedRunId;
      this.currentApiSessionId = storedApiSessionId;

      this._networkInfo = {
        host,
        type: host === TAILSCALE_HOST ? 'tailscale' : host === '192.168.68.105' ? 'local' : 'unknown',
      };

      const resp = await fetch(`${this.getBaseUrl(host)}/v1/models`, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${API_KEY}`,
        },
        signal: AbortSignal.timeout(8000),
      });

      if (resp.ok) {
        const modelInfo: ModelsResponse = await resp.json();
        this.modelInfo = modelInfo;
        this.activeModel = modelInfo.data?.[0]?.id || 'hermes-agent';
        this.setStatus('connected');
        this.emit({
          type: 'connected',
          content: this.currentApiSessionId || this.lastRunId ? 'Connected to Hermes API (session resumed)' : 'Connected to Hermes API',
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
      setTimeout(() => this.connect(host), 3000);
    }
  }

  getActiveModel(): string {
    return this.activeModel;
  }

  setActiveModel(model: string) {
    this.activeModel = model;
    this.emit({ type: 'model_updated', model });
  }

  getModelInfo(): ModelsResponse | null {
    return this.modelInfo;
  }

  async fetchModels(): Promise<ModelsResponse> {
    const net = await detectNetwork();
    const resp = await fetch(`${this.getBaseUrl(net.host)}/v1/models`, {
      method: 'GET',
      headers: { 'Authorization': `Bearer ${API_KEY}` },
    });

    if (!resp.ok) {
      const errText = await resp.text();
      throw new Error(`Failed to fetch models (${resp.status}): ${errText.slice(0, 200)}`);
    }

    const modelInfo: ModelsResponse = await resp.json();
    this.modelInfo = modelInfo;
    if (!this.activeModel || this.activeModel === 'Unknown') {
      this.activeModel = modelInfo.data?.[0]?.id || 'hermes-agent';
    }
    return modelInfo;
  }

  getLastResponseId(): string | null {
    return this.lastRunId;
  }

  getCurrentSessionId(): string | null {
    return this.currentApiSessionId || this.lastRunId;
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

  private async persistRunId(runId: string | null) {
    this.lastRunId = runId;
    if (runId) {
      await AsyncStorage.setItem(HERMES_SESSION_STORAGE_KEY, runId);
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
      sessionId: this.lastRunId,
      apiSessionId: this.currentApiSessionId,
    });
  }

  private async buildRunBody(input: RunInput) {
    const body: Record<string, any> = {
      model: this.activeModel && this.activeModel !== 'Unknown' ? this.activeModel : 'hermes-agent',
      input,
    };

    if (this.currentApiSessionId) body.session_id = this.currentApiSessionId;
    if (this.lastRunId) body.previous_run_id = this.lastRunId;

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
      // Session metadata is best-effort.
    }
  }

  private async createRun(input: RunInput): Promise<{ runId: string; sessionId: string | null }> {
    const net = await detectNetwork();
    const resp = await fetch(`${this.getBaseUrl(net.host)}/v1/runs`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(await this.buildRunBody(input)),
      signal: this.abortController?.signal,
    });

    if (!resp.ok) {
      const errText = await resp.text();
      throw new Error(`API error ${resp.status}: ${errText.slice(0, 200)}`);
    }

    const json = await resp.json();
    const runId = json?.id || json?.run_id || json?.run?.id;
    if (!runId) throw new Error('Run creation response did not include a run id');

    const sessionId = json?.session_id || json?.session?.id || json?.run?.session_id || null;
    return { runId, sessionId };
  }

  private parseSseBlock(block: string): { event: string; data: string } | null {
    let event = 'message';
    const dataLines: string[] = [];

    block.split(/\r?\n/).forEach(line => {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart());
    });

    if (!dataLines.length) return null;
    return { event, data: dataLines.join('\n') };
  }

  private extractEventText(eventName: string, chunk: any): string {
    if (typeof chunk?.delta === 'string') return chunk.delta;
    if (typeof chunk?.text === 'string') return chunk.text;
    if (typeof chunk?.data?.delta === 'string') return chunk.data.delta;
    if (typeof chunk?.data?.text === 'string') return chunk.data.text;
    if (typeof chunk?.choices?.[0]?.delta?.content === 'string') return chunk.choices[0].delta.content;
    if (typeof chunk?.content === 'string' && /delta|chunk|text|message/.test(eventName)) return chunk.content;
    if (chunk?.type === 'response.output_text.delta' && typeof chunk?.delta === 'string') return chunk.delta;
    return '';
  }

  private extractApprovalRequest(runId: string, payload: any): ApprovalRequest {
    const request = payload?.approval || payload?.request || payload?.data || payload || {};
    return {
      ...request,
      id: request?.id || payload?.approval_id || payload?.id,
      run_id: request?.run_id || payload?.run_id || runId,
      command: request?.command || request?.tool_input?.command || request?.arguments?.command || request?.cmd,
      tool: request?.tool || request?.tool_name || payload?.tool,
      description: request?.description || request?.message || payload?.message,
      reason: request?.reason || payload?.reason,
      requires_password: Boolean(request?.requires_password || request?.requiresPassword),
    };
  }

  private async streamRunEvents(runId: string) {
    const net = await detectNetwork();
    const resp = await fetch(`${this.getBaseUrl(net.host)}/v1/runs/${encodeURIComponent(runId)}/events`, {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${API_KEY}`,
        'Accept': 'text/event-stream',
      },
      signal: this.abortController?.signal,
    });

    if (!resp.ok) {
      const errText = await resp.text();
      throw new Error(`Run event stream error ${resp.status}: ${errText.slice(0, 200)}`);
    }

    const reader = resp.body?.getReader();
    if (!reader) throw new Error('No event stream body');

    const decoder = new TextDecoder();
    let buffer = '';
    let fullText = '';
    let finished = false;

    const finishResponse = async () => {
      if (finished) return;
      finished = true;
      if (fullText) this.history.push({ role: 'assistant', content: fullText });
      await this.persistRunId(runId);
      await this.refreshCurrentApiSessionId();
      this.emit({ type: 'response_end', content: fullText, runId });
    };

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split(/\r?\n\r?\n/);
      buffer = blocks.pop() || '';

      for (const block of blocks) {
        const parsed = this.parseSseBlock(block);
        if (!parsed) continue;
        if (parsed.data === '[DONE]') {
          await finishResponse();
          return;
        }

        let chunk: any = parsed.data;
        try { chunk = JSON.parse(parsed.data); } catch { /* plain-text SSE data */ }
        const eventName = typeof chunk === 'string' ? parsed.event : (chunk?.type || parsed.event);

        if (typeof chunk !== 'string' && (chunk?.session_id || chunk?.session?.id || chunk?.run?.session_id)) {
          await this.persistApiSessionId(chunk.session_id || chunk.session?.id || chunk.run?.session_id);
        }

        if (eventName === 'approval.request') {
          this.emit({ type: 'approval_request', request: this.extractApprovalRequest(runId, chunk), runId });
          continue;
        }

        const delta = typeof chunk === 'string' ? (parsed.event === 'message' ? chunk : '') : this.extractEventText(eventName, chunk);
        if (delta) {
          fullText += delta;
          this.emit({ type: 'response_chunk', content: delta, runId });
        }

        if (/completed|done|finished|failed|cancelled|canceled/.test(eventName)) {
          if (!fullText && typeof chunk !== 'string') fullText = extractResponseText(chunk.response || chunk);
          await finishResponse();
          return;
        }
      }
    }

    await finishResponse();
  }

  private async sendRunInput(input: RunInput, historyContent: string) {
    if (this._status !== 'connected') return;
    if (!historyContent.trim()) return;

    this.history.push({ role: 'user', content: historyContent });

    this.abortController?.abort();
    this.abortController = new AbortController();

    this.emit({ type: 'status', content: 'thinking' });

    try {
      const { runId, sessionId } = await this.createRun(input);
      await this.persistRunId(runId);
      if (sessionId) await this.persistApiSessionId(sessionId);
      await this.streamRunEvents(runId);
    } catch (e: any) {
      if (e.name !== 'AbortError') {
        this.emit({ type: 'error', content: `Network error: ${e.message}` });
        this.history.pop();
      }
    }
  }

  async sendMessage(content: string) {
    await this.sendRunInput(content, content);
  }

  async sendImage(base64: string, mimeType: string = 'image/jpeg') {
    const sanitizedBase64 = base64.replace(/^data:[^;]+;base64,/, '');
    const imageUrl = `data:${mimeType};base64,${sanitizedBase64}`;
    const input: RunInput = [{
      role: 'user',
      content: [
        { type: 'input_text', text: 'Look at this image' },
        { type: 'input_image', image_url: imageUrl },
      ],
    }];

    await this.sendRunInput(input, 'Look at this image');
  }

  async sendAudio(base64Audio: string, mimeType: string = 'audio/mp4', prompt: string = 'Voice note') {
    const sanitizedBase64 = base64Audio.replace(/^data:[^;]+;base64,/, '');
    const format = mimeType.includes('webm') ? 'webm'
      : mimeType.includes('wav') ? 'wav'
      : mimeType.includes('mpeg') || mimeType.includes('mp3') ? 'mp3'
      : 'mp4';
    const input: RunInput = [{
      role: 'user',
      content: [
        { type: 'input_text', text: 'Transcribe and respond to this voice note.' },
        { type: 'input_audio', input_audio: { data: sanitizedBase64, format } },
      ],
    }];

    await this.sendRunInput(input, `🎤 ${prompt}`);
  }

  async submitApproval(runId: string, choice: ApprovalChoice, approvalId?: string, password?: string) {
    const net = await detectNetwork();
    const resp = await fetch(`${this.getBaseUrl(net.host)}/v1/runs/${encodeURIComponent(runId)}/approval`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ choice, approval_id: approvalId, password }),
    });

    if (!resp.ok) {
      const errText = await resp.text();
      throw new Error(`Approval failed (${resp.status}): ${errText.slice(0, 200)}`);
    }

    this.emit({ type: 'approval_submitted', choice, runId });
  }

  async sendHiddenMessage(content: string): Promise<string> {
    if (!content.trim()) return '';

    this.abortController?.abort();
    this.abortController = new AbortController();

    const { runId, sessionId } = await this.createRun(content);
    await this.persistRunId(runId);
    if (sessionId) await this.persistApiSessionId(sessionId);

    let result = '';
    const unsub = this.onMessage(data => {
      if (data.runId === runId && data.type === 'response_chunk') result += data.content;
    });

    try {
      await this.streamRunEvents(runId);
    } finally {
      unsub();
    }

    return result;
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
    await this.persistRunId(null);
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

let _instance: HermesAPI | null = null;
export function getHermesAPI(): HermesAPI {
  if (!_instance) {
    _instance = new HermesAPI();
  }
  return _instance;
}
