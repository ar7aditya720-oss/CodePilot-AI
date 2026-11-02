import { useEffect, useMemo, useState } from 'react';
import Editor from '@monaco-editor/react';
import { Play, SearchCode, Bot, Code2, Terminal, CheckCircle2, Send, Wifi, WifiOff, LoaderCircle, FilePlus2, FileCode2, Trash2, FolderOpen, ChevronDown } from 'lucide-react';

type RunResult = { output?: string; exitCode?: number; timedOut?: boolean; durationMs?: number };
type ProviderStatus = { local?: { configured: boolean; url: string }; remote?: { configured: boolean; model: string } };
type ChatMessage = { role: 'assistant' | 'user'; text: string };
const starter = 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        System.out.print("1st digit: ");\n        int x = sc.nextInt();\n        System.out.print("2nd digit: ");\n        int y = sc.nextInt();\n        System.out.println("The sum is: " + (x + y));\n    }\n}';

export default function App() {
  const [files, setFiles] = useState<Record<string, string>>({ 'Main.java': starter });
  const [fileName, setFileName] = useState('Main.java');
  const [provider, setProvider] = useState('local');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState('Ready. Enter program input below, then select Run code.');
  const [stdin, setStdin] = useState('5\n7\n');
  const [review, setReview] = useState('Your review will appear here.');
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState<ChatMessage[]>([{ role: 'assistant', text: 'Hey! I can explain this Java code, help debug errors, or suggest improvements. What would you like to know?' }]);
  const [providers, setProviders] = useState<ProviderStatus>({});
  const [backendOnline, setBackendOnline] = useState<boolean | null>(null);
  const [chatBusy, setChatBusy] = useState(false);
  const [providerTest, setProviderTest] = useState('Provider has not been tested yet.');
  const [testingProvider, setTestingProvider] = useState(false);
  const [terminalTab, setTerminalTab] = useState<'output' | 'input'>('output');

  const code = files[fileName] ?? '';
  const fileNames = useMemo(() => Object.keys(files).sort((a,b) => a === 'Main.java' ? -1 : b === 'Main.java' ? 1 : a.localeCompare(b)), [files]);

  async function api(path: string, init?: RequestInit) {
    const response = await fetch('/api/' + path, { ...init, headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) } });
    const raw = await response.text();
    let data: any = {};
    try { data = raw ? JSON.parse(raw) : {}; } catch { data = { message: raw }; }
    if (!response.ok) throw new Error(data.message || data.error || data.detail || `Request failed (${response.status})`);
    return data;
  }
  async function refreshStatus() {
    try {
      const [health, status] = await Promise.all([api('health'), api('ai/providers')]);
      setBackendOnline(health.status === 'ok'); setProviders(status);
    } catch { setBackendOnline(false); }
  }
  useEffect(() => {
    void refreshStatus();
    const timer = window.setInterval(() => { void refreshStatus(); }, 5000);
    return () => window.clearInterval(timer);
  }, []);

  function updateCurrentCode(value: string) {
    setFiles(prev => ({ ...prev, [fileName]: value }));
  }
  function addFile() {
    const requested = window.prompt('New Java filename (example: Helper.java)');
    if (!requested) return;
    const name = requested.trim().endsWith('.java') ? requested.trim() : requested.trim() + '.java';
    if (!/^[A-Za-z0-9_-]+\.java$/.test(name)) { window.alert('Use a simple Java filename, such as Helper.java'); return; }
    if (files[name] !== undefined) { setFileName(name); return; }
    const className = name.slice(0, -5);
    setFiles(prev => ({ ...prev, [name]: `public class ${className} {\n    public static void main(String[] args) {\n        System.out.println("Hello from ${className}");\n    }\n}\n` }));
    setFileName(name);
  }
  function deleteCurrentFile() {
    if (fileNames.length === 1) { window.alert('Keep at least one file in your workspace.'); return; }
    if (!window.confirm(`Delete ${fileName} from this session?`)) return;
    const next = { ...files }; delete next[fileName]; setFiles(next);
    setFileName(Object.keys(next)[0]);
  }

  async function testProvider() {
    setTestingProvider(true); setProviderTest('Testing ' + (provider === 'local' ? 'Ollama' : 'remote API') + '…');
    try { const data = await api('ai/test?provider=' + provider); setProviderTest('Connected: ' + (data.response || 'test prompt succeeded')); }
    catch (e) { setProviderTest('Test failed: ' + (e as Error).message); }
    finally { setTestingProvider(false); void refreshStatus(); }
  }
  async function runCode() {
    setBusy(true); setTerminalTab('output'); setResult('Compiling and running Java…');
    try {
      const data: RunResult = await api('run', { method: 'POST', body: JSON.stringify({ code, fileName, stdin, files, provider }) });
      setResult(`${data.output || 'Program finished.'}\n\nExit code: ${data.exitCode ?? 0} · ${data.durationMs ?? 0} ms${data.timedOut ? ' · Timed out' : ''}`);
    } catch (e) { setResult('Run failed: ' + (e as Error).message); }
    finally { setBusy(false); }
  }
  async function reviewCode() {
    setBusy(true);
    try { const data = await api('review', { method: 'POST', body: JSON.stringify({ code, fileName, provider }) }); setReview(data.review || 'The API returned no review text.'); }
    catch (e) { setReview('Review failed: ' + (e as Error).message); }
    finally { setBusy(false); void refreshStatus(); }
  }
  async function sendQuestion() {
    const text = question.trim(); if (!text || chatBusy) return;
    setMessages(prev => [...prev, { role: 'user', text }]); setQuestion(''); setChatBusy(true);
    try {
      const data = await api('chat', { method: 'POST', body: JSON.stringify({ code, fileName, provider, message: text }) });
      setMessages(prev => [...prev, { role: 'assistant', text: data.answer || 'The API returned no answer.' }]);
    } catch (e) { setMessages(prev => [...prev, { role: 'assistant', text: 'Connection error: ' + (e as Error).message }]); }
    finally { setChatBusy(false); void refreshStatus(); }
  }

  const selectedConfigured = provider === 'local' ? providers.local?.configured : providers.remote?.configured;
  return <main className="app">
    <header className="topbar">
      <div className="brand"><div className="logo"><Code2 size={21}/></div><div><strong>CodePilot <span>AI</span></strong><small>YOUR AI-POWERED CODING WORKSPACE</small></div></div>
      <div className="top-actions">
        <span className={backendOnline ? 'connection online' : backendOnline === false ? 'connection offline' : 'connection'} title={backendOnline ? 'Backend connected' : backendOnline === false ? 'Backend unavailable' : 'Checking backend'}>{backendOnline ? <Wifi size={14}/> : backendOnline === false ? <WifiOff size={14}/> : <LoaderCircle size={14}/>} {backendOnline ? 'API connected' : backendOnline === false ? 'API offline' : 'Connecting…'}</span>
        <label>AI provider <select value={provider} onChange={e=>setProvider(e.target.value)}><option value="local">Local · Ollama</option><option value="remote">Remote API</option></select></label>
        <button className="primary" disabled={busy || backendOnline === false} onClick={runCode}><Play size={15}/>{busy?'Running…':'Run code'}</button>
        <button onClick={reviewCode} disabled={busy || backendOnline === false}><SearchCode size={16}/> Review</button>
      </div>
    </header>
    <section className="workspace">
      <aside className="sidebar">
        <div className="explorer-head"><div className="section-label">EXPLORER</div><div className="explorer-actions"><button title="New Java file" onClick={addFile}><FilePlus2 size={15}/></button><button title="Delete current file" onClick={deleteCurrentFile}><Trash2 size={14}/></button></div></div>
        <div className="folder"><ChevronDown size={13}/> <FolderOpen size={14}/> CODEPILOT-WORKSPACE</div>
        <div className="file-list">{fileNames.map(name=><button key={name} className={'file '+(fileName===name?'active':'')} onClick={()=>setFileName(name)}><FileCode2 size={14}/><span>{name}</span>{name===fileName && <i/>}</button>)}</div>
        <div className="side-note"><Bot size={17}/><p>Write code. Find improvements. Understand complexity.</p></div>
        <div className="provider-card"><strong>ACTIVE PROVIDER</strong><p>{provider === 'local' ? 'Ollama · Local model' : 'Remote · Chat Completions'}</p><span className={selectedConfigured ? 'provider-state good' : 'provider-state'}>{selectedConfigured ? (provider === 'local' ? 'URL configured' : 'Key configured') : provider === 'remote' ? 'API key required' : 'URL missing'}</span><button onClick={testProvider} disabled={testingProvider || backendOnline === false}>{testingProvider ? 'Testing provider…' : 'Test selected provider'}</button><p className="provider-test-result">{providerTest}</p></div>
      </aside>
      <section className="editor-area">
        <div className="tabs">{fileNames.map(name=><button key={name} className={'tab '+(name===fileName?'selected':'')} onClick={()=>setFileName(name)}><FileCode2 size={14}/>{name}<span onClick={e=>{e.stopPropagation();setFileName(name);}}> </span></button>)}<button className="add-tab" title="New file" onClick={addFile}><FilePlus2 size={15}/></button></div>
        <div className="editor"><Editor height="100%" language="java" theme="vs-dark" path={fileName} value={code} onChange={v=>updateCurrentCode(v ?? '')} options={{fontSize:14,minimap:{enabled:true},padding:{top:18},automaticLayout:true,scrollBeyondLastLine:false,wordWrap:'on',fontFamily:'JetBrains Mono, Consolas, monospace',renderWhitespace:'selection',bracketPairColorization:{enabled:true},quickSuggestions:true}}/></div>
        <div className="bottom">
          <div className="terminal-head"><div className="terminal-tabs"><button className={terminalTab==='output'?'chosen':''} onClick={()=>setTerminalTab('output')}><Terminal size={14}/> TERMINAL / OUTPUT</button><button className={terminalTab==='input'?'chosen':''} onClick={()=>setTerminalTab('input')}>PROGRAM INPUT</button></div><button className="run-small" onClick={runCode} disabled={busy || backendOnline===false}><Play size={12}/> Run</button></div>
          {terminalTab==='output' ? <pre className="terminal-output">{result}</pre> : <div className="stdin-panel"><label htmlFor="stdin">Standard input (stdin)</label><textarea id="stdin" value={stdin} onChange={e=>setStdin(e.target.value)} placeholder={'Enter input exactly as you would in a terminal.\nExample:\n5\n7'} /><p>Put each input on its own line. The runner sends this input to Scanner / BufferedReader when you click Run.</p><button className="primary" onClick={runCode} disabled={busy || backendOnline===false}><Play size={13}/> Run with this input</button></div>}
        </div>
      </section>
      <aside className="ai-panel"><div className="ai-heading"><Bot size={18}/> <strong>CodePilot Assistant</strong><span className="dot"/></div><p className="muted">Ask about your code or run an AI review. The selected provider is used for both.</p><button className="review-btn" disabled={busy || backendOnline === false} onClick={reviewCode}><SearchCode size={16}/>{busy?'Analyzing…':'Review current file'}</button><div className="review-result"><div className="result-label"><CheckCircle2 size={15}/> CODE REVIEW</div><pre>{review}</pre></div><div className="chat-section"><div className="chat-title"><Bot size={15}/> CHAT ABOUT YOUR CODE</div><div className="messages">{messages.map((m,i)=><div className={'message '+m.role} key={i}><span>{m.role === 'assistant' ? 'AI' : 'YOU'}</span><p>{m.text}</p></div>)}{chatBusy && <div className="message assistant"><span>AI</span><p><LoaderCircle size={13} className="spin"/> Thinking…</p></div>}</div><form className="chat-form" onSubmit={e=>{e.preventDefault();void sendQuestion();}}><textarea value={question} onChange={e=>setQuestion(e.target.value)} placeholder="Ask about this Java code…" rows={2} disabled={chatBusy || backendOnline === false}/><button type="submit" aria-label="Send question" disabled={!question.trim() || chatBusy || backendOnline === false}><Send size={15}/></button></form><small className="chat-foot">Your current editor code is included with the question.</small></div><div className="hint"><strong>Code review focus</strong><p>Check correctness, edge cases, readability, time complexity and security. AI review requires a configured provider.</p></div></aside>
    </section>
    <footer><span><i/> {backendOnline ? 'Backend connected' : backendOnline === false ? 'Backend unavailable' : 'Checking connection'}</span><span>Java · React · Spring Boot</span><span>CodePilot AI · Multi-file workspace</span></footer>
  </main>;
}
