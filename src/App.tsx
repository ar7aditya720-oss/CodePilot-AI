import { useEffect, useState } from 'react';
import Editor from '@monaco-editor/react';
import { Play, SearchCode, Bot, Code2, Terminal, CheckCircle2, Send, Wifi, WifiOff, LoaderCircle } from 'lucide-react';

type RunResult = { output?: string; exitCode?: number; timedOut?: boolean; durationMs?: number };
type ProviderStatus = { local?: { configured: boolean; url: string }; remote?: { configured: boolean; model: string } };
type ChatMessage = { role: 'assistant' | 'user'; text: string };
export default function App() {
  const [code, setCode] = useState('public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello, CodePilot AI!");\n    }\n}');
  const [fileName] = useState('Main.java');
  const [provider, setProvider] = useState('local');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState('Ready. Run your Java code or request an AI review.');
  const [review, setReview] = useState('Your review will appear here.');
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([{ role: 'assistant', text: 'Hey! I can explain this Java code, help debug errors, or suggest improvements. What would you like to know?' }]);
  const [providers, setProviders] = useState<ProviderStatus>({});
  const [backendOnline, setBackendOnline] = useState<boolean | null>(null);
  const [chatBusy, setChatBusy] = useState(false);
  const [providerTest, setProviderTest] = useState('Provider has not been tested yet.');
  const [testingProvider, setTestingProvider] = useState(false);

  async function api(path: string, init?: RequestInit) {
    const response = await fetch('/api/' + path, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
    });
    const raw = await response.text();
    let data: any = {};
    try { data = raw ? JSON.parse(raw) : {}; } catch { data = { message: raw }; }
    if (!response.ok) throw new Error(data.message || data.error || data.detail || `Request failed (${response.status})`);
    return data;
  }
  async function refreshStatus() {
    try {
      const [health, status] = await Promise.all([api('health'), api('ai/providers')]);
      setBackendOnline(health.status === 'ok');
      setProviders(status);
    } catch { setBackendOnline(false); }
  }
  useEffect(() => { void refreshStatus(); }, []);

  async function testProvider() {
    setTestingProvider(true);
    setProviderTest('Testing ' + (provider === 'local' ? 'Ollama' : 'remote API') + '…');
    try {
      const data = await api('ai/test?provider=' + provider);
      setProviderTest('Connected: ' + (data.response || 'test prompt succeeded'));
    } catch (e) { setProviderTest('Test failed: ' + (e as Error).message); }
    finally { setTestingProvider(false); void refreshStatus(); }
  }
  async function runCode() {
    setBusy(true);
    try {
      const data: RunResult = await api('run', { method: 'POST', body: JSON.stringify({ code, fileName }) });
      setResult(`${data.output || 'Program finished.'}\n\nExit code: ${data.exitCode ?? 0} · ${data.durationMs ?? 0} ms${data.timedOut ? ' · Timed out' : ''}`);
    } catch (e) { setResult('Run failed: ' + (e as Error).message); }
    finally { setBusy(false); }
  }
  async function reviewCode() {
    setBusy(true);
    try {
      const data = await api('review', { method: 'POST', body: JSON.stringify({ code, fileName, provider }) });
      setReview(data.review || 'The API returned no review text.');
    } catch (e) { setReview('Review failed: ' + (e as Error).message); }
    finally { setBusy(false); void refreshStatus(); }
  }
  async function sendQuestion() {
    const text = question.trim();
    if (!text || chatBusy) return;
    setMessages(prev => [...prev, { role: 'user', text }]);
    setQuestion('');
    setChatBusy(true);
    try {
      const data = await api('chat', { method: 'POST', body: JSON.stringify({ code, fileName, provider, message: text }) });
      setMessages(prev => [...prev, { role: 'assistant', text: data.answer || 'The API returned no answer.' }]);
    } catch (e) {
      setMessages(prev => [...prev, { role: 'assistant', text: 'Connection error: ' + (e as Error).message }]);
    } finally { setChatBusy(false); void refreshStatus(); }
  }

  const selectedConfigured = provider === 'local' ? providers.local?.configured : providers.remote?.configured;
  return <main className="app">
    <header className="topbar">
      <div className="brand"><div className="logo"><Code2 size={21}/></div><div><strong>CodePilot <span>AI</span></strong><small>YOUR AI-POWERED CODING WORKSPACE</small></div></div>
      <div className="top-actions">
        <span className={backendOnline ? 'connection online' : backendOnline === false ? 'connection offline' : 'connection'} title={backendOnline ? 'Backend connected' : backendOnline === false ? 'Backend unavailable' : 'Checking backend'}>{backendOnline ? <Wifi size={14}/> : backendOnline === false ? <WifiOff size={14}/> : <LoaderCircle size={14}/>} {backendOnline ? 'API connected' : backendOnline === false ? 'API offline' : 'Connecting…'}</span>
        <label>AI provider <select value={provider} onChange={e=>setProvider(e.target.value)}><option value="local">Local · Ollama</option><option value="remote">Remote API</option></select></label>
        <button className="primary" disabled={busy || backendOnline === false} onClick={runCode}><Play size={15}/>{busy?'Working…':'Run code'}</button>
        <button onClick={reviewCode} disabled={busy || backendOnline === false}><SearchCode size={16}/> Review</button>
      </div>
    </header>
    <section className="workspace">
      <aside className="sidebar"><div className="section-label">EXPLORER</div><div className="folder">⌄ &nbsp; CODEPILOT-WORKSPACE</div><div className="file active">▣ &nbsp; Main.java</div><div className="side-note"><Bot size={17}/><p>Write code. Find improvements. Understand complexity.</p></div><div className="provider-card"><strong>ACTIVE PROVIDER</strong><p>{provider === 'local' ? 'Ollama · Local model' : 'Remote · Chat Completions'}</p><span className={selectedConfigured ? 'provider-state good' : 'provider-state'}>{selectedConfigured ? (provider === 'local' ? 'URL configured' : 'Key configured') : provider === 'remote' ? 'API key required' : 'URL missing'}</span><button onClick={testProvider} disabled={testingProvider || backendOnline === false}>{testingProvider ? 'Testing provider…' : 'Test selected provider'}</button><p className="provider-test-result">{providerTest}</p></div></aside>
      <section className="editor-area"><div className="tab">☕ &nbsp; {fileName} <span>×</span></div><div className="editor"><Editor height="100%" language="java" theme="vs-dark" value={code} onChange={v=>setCode(v ?? '')} options={{fontSize:14,minimap:{enabled:false},padding:{top:18},automaticLayout:true,scrollBeyondLastLine:false,wordWrap:'on',fontFamily:'JetBrains Mono, Consolas, monospace'}}/></div><div className="bottom"><div className="panel-title"><Terminal size={15}/> TERMINAL / OUTPUT</div><pre>{result}</pre></div></section>
      <aside className="ai-panel"><div className="ai-heading"><Bot size={18}/> <strong>CodePilot Assistant</strong><span className="dot"/></div><p className="muted">Ask about your code or run an AI review. The selected provider is used for both.</p><button className="review-btn" disabled={busy || backendOnline === false} onClick={reviewCode}><SearchCode size={16}/>{busy?'Analyzing…':'Review current file'}</button><div className="review-result"><div className="result-label"><CheckCircle2 size={15}/> CODE REVIEW</div><pre>{review}</pre></div><div className="chat-section"><div className="chat-title"><Bot size={15}/> CHAT ABOUT YOUR CODE</div><div className="messages">{messages.map((m,i)=><div className={'message '+m.role} key={i}><span>{m.role === 'assistant' ? 'AI' : 'YOU'}</span><p>{m.text}</p></div>)}{chatBusy && <div className="message assistant"><span>AI</span><p><LoaderCircle size={13} className="spin"/> Thinking…</p></div>}</div><form className="chat-form" onSubmit={e=>{e.preventDefault();void sendQuestion();}}><textarea value={question} onChange={e=>setQuestion(e.target.value)} placeholder="Ask about this Java code…" rows={2} disabled={chatBusy || backendOnline === false}/><button type="submit" aria-label="Send question" disabled={!question.trim() || chatBusy || backendOnline === false}><Send size={15}/></button></form><small className="chat-foot">Your current editor code is included with the question.</small></div><div className="hint"><strong>Quick tip</strong><p>Use local Ollama for local inference or select Remote API after configuring a key on the backend.</p></div></aside>
    </section>
    <footer><span><i/> {backendOnline ? 'Backend connected' : backendOnline === false ? 'Backend unavailable' : 'Checking connection'}</span><span>Java · React · Spring Boot</span><span>CodePilot AI MVP</span></footer>
  </main>;
}
