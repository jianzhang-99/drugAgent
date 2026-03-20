import React, { useState, useEffect, useRef } from 'react';
import {
Settings,
HelpCircle,
Upload,
MessageSquare,
ChevronRight,
Search,
CheckCircle2,
Clock,
Menu,
Activity,
Loader2,
X,
Sparkles,
LayoutList,
BookOpen,
FileText,
ShieldCheck,
AlertTriangle,
Send,
Filter,
Eye,
Database,
Network,
ToggleRight,
ToggleLeft,
Trash2,
RefreshCw,
Plus,
CheckSquare,
Download,
User,
GitBranch,
Terminal,
AlertOctagon,
File,
Copy,
Bot,
SquarePen,
ChevronLeft,
Info,
Sliders,
Cpu,
Lock,
Bell,
Save,
ShieldAlert
} from 'lucide-react';

// --- 模拟 API 配置与工具函数 ---
const apiKey = "";
const GEMINI_MODEL = "gemini-2.5-flash-preview-09-2025";

const sleep = (ms) => new Promise(resolve => setTimeout(resolve, ms));

async function callGemini(prompt, systemInstruction = "") {
let delay = 1000;
for (let i = 0; i < 5; i++) {
try {
const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${apiKey}`, {
method: 'POST',
headers: { 'Content-Type': 'application/json' },
body: JSON.stringify({
contents: [{ parts: [{ text: prompt }] }],
systemInstruction: { parts: [{ text: systemInstruction }] }
})
});
const data = await response.json();
const text = data.candidates?.[0]?.content?.parts?.[0]?.text;
if (text) return text;
throw new Error("Empty response");
} catch (error) {
if (i === 4) throw error;
await sleep(delay);
delay *= 2;
}
}
}

// --- 主要组件 ---
const App = () => {
// 增加 SETTINGS 到路由状态中
const [activeView, setActiveView] = useState('WORKSPACE');
const [isSidebarOpen, setIsSidebarOpen] = useState(true);

const [tasks, setTasks] = useState([
{ id: 'T-001', name: '年度设备采购标书分析', scene: 'TENDER', progress: 100, status: 'completed', time: '10分钟前', riskLevel: 'High', findings: '发现 87% 语义重合，疑似围标' }
]);
const [isTaskPaneOpen, setIsTaskPaneOpen] = useState(false);

const [sessions, setSessions] = useState([
{
id: 'sess_10293',
title: '年度设备采购标书比对',
dateGroup: '今天',
scene: 'TENDER',
messages: [
{ role: 'user', content: '帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。', attachments: ['样例A_XX医院标书.docx', '样例B_XX药房投标.pdf'], time: '14:20' },
{
role: 'agent',
content: '我已经为您完成了这两份标书文件的深度比对审查。根据系统分析，存在高风险围标嫌疑。',
time: '14:22',
result: { scene: 'TENDER', riskLevel: 'High', summary: '发现 87% 的语义重合度，且排版格式特征存在强关联，高度疑似围标。', evidenceList: ['片段A：设备的额定功率需满足 1500W-1800W 区间，且外壳需采用医用级 ABS 材质。', '片段B：该机器额定功率符合 1500W 至 1800W，外壳材料为医用级 ABS。'], steps: ['文档解析与 OCR', '语义块向量化抽取', '相似度比对与规则过滤', 'LLM 生成审查意见'], traceId: 'TRC-99281-A' }
}
]
},
{
id: 'sess_10290',
title: '骨科耗材供应商协议预审',
dateGroup: '昨天',
scene: 'CONTRACT',
messages: [
{ role: 'user', content: '审查一下这份最新的采购合同框架，按知识库提取风险条款。', attachments: ['骨科耗材采购合同_v3.pdf'], time: '16:05' },
{
role: 'agent',
content: '合同预审完毕。整体结构完整，但提取到几处需要关注的潜在风险条款。',
time: '16:06',
result: { scene: 'CONTRACT', riskLevel: 'Medium', summary: '发现 3 条倾向于供应商的免责声明及付款周期违规条款。', evidenceList: ['条款 4.2: 甲方需在收到发票后 5 个工作日内结清全款 (违背常规 30 天周期)'], steps: ['合同条款结构化切分', '规则引擎强匹配', '风险评级测算'], traceId: 'TRC-99282-B' }
}
]
}
]);

const [activeSessionId, setActiveSessionId] = useState(null);

const addTask = (name, scene) => {
const newTask = { id: `T-${Math.floor(Math.random() * 1000)}`, name: name || '未命名任务', scene: scene || 'UNKNOWN', progress: 0, status: 'running', time: '刚刚', riskLevel: 'Unknown', findings: '正在初始化 Agent 工作流...' };
setTasks(prev => [newTask, ...prev]);
return newTask.id;
};

const updateTaskProgress = (id, progress, status = 'running') => {
setTasks(prev => prev.map(t => t.id === id ? { ...t, progress, status } : t));
};

const handleNewChat = () => {
setActiveSessionId(null);
setActiveView('WORKSPACE');
};

const handleSelectSession = (sessionId) => {
setActiveSessionId(sessionId);
setActiveView('WORKSPACE');
};

const navItems = [
{ id: 'TASKS', label: '全局任务看板', icon: LayoutList, color: 'text-indigo-600' },
{ id: 'KNOWLEDGE', label: '合规知识库', icon: BookOpen, color: 'text-emerald-600' },
];

return (
<div className="flex h-screen bg-slate-50 text-slate-900 font-sans overflow-hidden">
<aside className={`bg-slate-50 border-r border-slate-200 transition-all duration-300 flex flex-col z-20 ${isSidebarOpen ? 'w-72' : 'w-20'}`}>
<div className="p-4 flex items-center justify-between">
  <div className="flex items-center gap-3 cursor-pointer" onClick={() => setActiveView('WORKSPACE')}>
  <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center flex-shrink-0 shadow-sm">
    <Sparkles className="text-white" size={18} />
  </div>
  {isSidebarOpen && <span className="font-bold text-lg tracking-tight text-slate-800 truncate">Drug-Agent</span>}
</div>
{isSidebarOpen && (
<button
    onClick={handleNewChat}
    className="p-1.5 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
    title="新建任务/会话"
>
  <SquarePen size={20} />
</button>
)}
</div>

<div className="px-3 pb-4">
{navItems.map((item) => (
<button
    key={item.id}
    onClick={() => setActiveView(item.id)}
className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all mb-1 ${
activeView === item.id
? 'bg-white shadow-sm border border-slate-200 text-slate-800'
: 'text-slate-600 hover:bg-slate-200/50 border border-transparent'
}`}
>
<item.icon size={18} className={item.color} />
{isSidebarOpen && <span className="font-medium text-sm whitespace-nowrap">{item.label}</span>}
</button>
))}
</div>

<div className="flex-1 overflow-y-auto px-3">
{isSidebarOpen && <p className="text-[10px] font-bold text-slate-400 uppercase tracking-widest px-3 mb-2 pt-2">历史审查会话</p>}

{['今天', '昨天', '过去 7 天'].map(group => {
const groupSessions = sessions.filter(s => s.dateGroup === group);
if (groupSessions.length === 0) return null;
return (
<div key={group} className="mb-4">
  {isSidebarOpen && <div className="text-[10px] font-semibold text-slate-400 px-3 mb-1">{group}</div>}
  <div className="space-y-0.5">
    {groupSessions.map(session => {
    const isActive = activeView === 'WORKSPACE' && activeSessionId === session.id;
    return (
    <button
        key={session.id}
        onClick={() => handleSelectSession(session.id)}
    className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-all text-left ${
    isActive
    ? 'bg-blue-100 text-blue-800 font-medium'
    : 'text-slate-600 hover:bg-slate-200/50'
    }`}
    title={session.title}
    >
    {isSidebarOpen ? (
    <span className="text-sm truncate pr-2">{session.title}</span>
    ) : (
    <MessageSquare size={16} className="mx-auto" />
    )}
    </button>
    )
    })}
  </div>
</div>
)
})}
</div>

<div className="p-3 border-t border-slate-200 flex flex-col gap-1">
{isSidebarOpen && (
<button
    onClick={() => setActiveView('SETTINGS')}
className={`w-full flex items-center gap-3 px-3 py-2 rounded-lg transition-all text-sm ${
activeView === 'SETTINGS' ? 'bg-white shadow-sm border border-slate-200 text-blue-600 font-semibold' : 'text-slate-500 hover:bg-slate-200/50 border border-transparent'
}`}
>
<Settings size={18} /> 偏好与系统配置
</button>
)}
<button
    onClick={() => setIsSidebarOpen(!isSidebarOpen)}
className={`flex items-center gap-3 px-3 py-2 rounded-lg text-slate-400 hover:bg-slate-200/50 transition-all ${!isSidebarOpen && 'justify-center'}`}
>
<Menu size={18} />
{isSidebarOpen && <span className="text-sm">收起侧边栏</span>}
</button>
</div>
</aside>

<main className="flex-1 flex flex-col relative bg-white shadow-[-8px_0_24px_-12px_rgba(0,0,0,0.05)] z-30">
<header className="h-14 border-b border-slate-100 px-6 flex items-center justify-between bg-white/80 backdrop-blur-md sticky top-0 z-10">
  <div className="flex items-center gap-2">
            <span className="text-slate-400 text-sm font-medium">
              {activeView === 'TASKS' ? '任务调度看板' : activeView === 'KNOWLEDGE' ? '知识大脑' : activeView === 'SETTINGS' ? '系统配置' : 'Agent 审查工作台'}
            </span>
    {activeView === 'WORKSPACE' && activeSessionId && (
    <>
    <ChevronRight size={14} className="text-slate-300" />
    <span className="text-sm font-bold text-slate-800">{sessions.find(s => s.id === activeSessionId)?.title}</span>
  </>
  )}
  </div>

  <div className="flex items-center gap-4">
    <div className="relative">
      <button
          onClick={() => setIsTaskPaneOpen(!isTaskPaneOpen)}
      className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold transition-all ${
      tasks.some(t => t.status === 'running')
      ? 'bg-amber-50 text-amber-700 ring-1 ring-amber-200 shadow-sm'
      : 'bg-slate-50 text-slate-600 hover:bg-slate-100'
      }`}
      >
      {tasks.some(t => t.status === 'running') ? <Loader2 size={14} className="animate-spin" /> : <Activity size={14} />}
      <span>{tasks.filter(t => t.status === 'running').length} 活跃任务</span>
      </button>

      {isTaskPaneOpen && (
      <div className="absolute right-0 mt-2 w-80 bg-white rounded-2xl shadow-2xl border border-slate-200 overflow-hidden z-50">
        <div className="p-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
          <h3 className="font-bold text-sm">后台任务中心</h3>
          <button onClick={() => setIsTaskPaneOpen(false)}><X size={16} className="text-slate-400 hover:text-slate-700" /></button>
        </div>
        <div className="max-h-[400px] overflow-y-auto">
          {tasks.map(task => (
          <div key={task.id} className="p-4 border-b border-slate-50 hover:bg-slate-50 transition-all group cursor-pointer" onClick={() => { setIsTaskPaneOpen(false); }}>
          <div className="flex justify-between items-start mb-2">
            <div className="flex flex-col">
              <span className="text-[13px] font-bold text-slate-800 leading-tight">{task.name}</span>
              <span className="text-[10px] text-slate-400 mt-0.5">点击查看执行详情图谱</span>
            </div>
            {task.status === 'completed' ? (
            <CheckCircle2 size={14} className="text-emerald-500" />
            ) : (
            <span className="text-[10px] font-bold text-blue-600">{task.progress}%</span>
            )}
          </div>
          <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden">
            <div
                className={`h-full transition-all duration-500 ${task.status === 'completed' ? 'bg-emerald-500' : 'bg-blue-600 animate-pulse'}`}
            style={{ width: `${task.progress}%` }}
            />
          </div>
        </div>
        ))}
      </div>
    </div>
    )}
  </div>
  <div className="w-7 h-7 bg-gradient-to-tr from-blue-600 to-indigo-600 rounded-full shadow-md flex items-center justify-center text-[10px] font-bold text-white">
    DA
  </div>
  </div>
</header>

<div className="flex-1 overflow-y-auto relative bg-[#FDFDFD]">
  {activeView === 'WORKSPACE' && (
  <WorkspaceContainer
      activeSessionId={activeSessionId}
      sessions={sessions}
      setSessions={setSessions}
      addTask={addTask}
      updateTaskProgress={updateTaskProgress}
  />
  )}
  {activeView === 'TASKS' && <TaskBoardView tasks={tasks} />}
  {activeView === 'KNOWLEDGE' && <KnowledgeBaseView />}
  {activeView === 'SETTINGS' && <SettingsView />}
</div>
</main>
</div>
);
};

// --- Workspace 容器 ---
const WorkspaceContainer = ({ activeSessionId, sessions, setSessions, addTask, updateTaskProgress }) => {
const [input, setInput] = useState("");
const [isRouting, setIsRouting] = useState(false);
const [selectedReport, setSelectedReport] = useState(null);
const messagesEndRef = useRef(null);

const activeSession = activeSessionId ? sessions.find(s => s.id === activeSessionId) : null;

useEffect(() => {
messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
}, [activeSession?.messages, isRouting]);

const quickActions = [
{ icon: FileText, label: "标书审查", prompt: "帮我对比新上传的这几份标书文件，检查是否有雷同或围标嫌疑。", color: "text-indigo-600", bg: "bg-indigo-50" },
{ icon: ShieldCheck, label: "合同预审", prompt: "审查最新版本的采购合同，基于合规知识库提取潜在风险条款。", color: "text-emerald-600", bg: "bg-emerald-50" },
{ icon: AlertTriangle, label: "合规预警", prompt: "分析近3个月的骨科耗材采购数据，生成异常波动预警报告。", color: "text-amber-600", bg: "bg-amber-50" }
];

const getRiskConfig = (level) => {
switch(level) {
case 'High': return { color: 'text-rose-600', bg: 'bg-rose-50', border: 'border-rose-200', label: '高风险', icon: AlertOctagon };
case 'Medium': return { color: 'text-amber-600', bg: 'bg-amber-50', border: 'border-amber-200', label: '中风险', icon: AlertTriangle };
case 'Low': return { color: 'text-emerald-600', bg: 'bg-emerald-50', border: 'border-emerald-200', label: '低风险', icon: CheckCircle2 };
default: return { color: 'text-slate-600', bg: 'bg-slate-50', border: 'border-slate-200', label: '信息', icon: Info };
}
};

const handleSubmit = async (overrideInput = null) => {
const textToProcess = overrideInput || input;
if (!textToProcess.trim()) return;

setInput("");
setIsRouting(true);
setSelectedReport(null);

const isNewSession = !activeSessionId;
const currentSessionId = activeSessionId || `sess_${Date.now()}`;
const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

const mockAttachments = textToProcess.includes('这几份标书') ? ['投标人A_标书.md', '投标人B_标书.md'] : [];
const newUserMsg = { role: 'user', content: textToProcess, time: timestamp, attachments: mockAttachments };

if (isNewSession) {
const newSess = {
id: currentSessionId,
title: textToProcess.substring(0, 15) + '...',
dateGroup: '今天',
scene: 'UNKNOWN',
messages: [newUserMsg]
};
setSessions(prev => [newSess, ...prev]);
} else {
setSessions(prev => prev.map(s => s.id === currentSessionId ? { ...s, messages: [...s.messages, newUserMsg] } : s));
}

try {
const systemPrompt = `你是一个医疗监管Agent。用户输入：${textToProcess}。请分析意图并返回JSON：{"scene": "TENDER", "suggestedTaskName": "..."}`;
const result = await callGemini(textToProcess, systemPrompt);

let cleanJson = '{}';
if (result) {
const ttt = '`' + '`' + '`';
cleanJson = result.replace(new RegExp(ttt + 'json', 'gi'), '').replace(new RegExp(ttt, 'g'), '').trim();
}

let parsed = { scene: 'GENERAL', suggestedTaskName: '常规分析任务' };
try {
parsed = JSON.parse(cleanJson);
} catch (e) {
console.warn("JSON parsing failed, falling back", e);
}

const taskId = addTask(parsed.suggestedTaskName || "智能审查回复", parsed.scene);

setTimeout(() => {
const isLowRisk = Math.random() > 0.3;

const agentReply = {
role: 'agent',
content: isLowRisk
? '我已经完成了对您上传文档的审查。本次共审查了 2 份文档，未发现保留的高风险命中，建议将结果作为低风险基线。详细报告已生成，请查看下方卡片。'
: '审查已完成。系统在多份文件中发现了高度雷同的排版与语义特征，已判定为高风险。请务必查看详细报告并进行人工复核。',
time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
result: {
scene: parsed.scene || 'TENDER_REVIEW',
riskLevel: isLowRisk ? 'Low' : 'High',
score: isLowRisk ? 0 : 87,
docCount: 2,
summary: isLowRisk ? 'No retained high-risk hits after rule scan.' : '发现显著的语义及排版雷同，疑似存在围标行为。',
managementSummary: [
`本次共审查 2 份文档，综合风险等级为 ${isLowRisk ? 'LOW' : 'HIGH'}，融合分值为 ${isLowRisk ? 0 : 87}。`,
isLowRisk ? '当前未保留高风险命中，建议将结果作为低风险基线，并对关键章节进行抽样复核。' : '触发了强制拦截规则，建议立即中止当前流程并启动专项审计调查。'
],
suggestedActions: [
isLowRisk ? '保留当前报告作为初筛结果，并结合业务经验抽样检查重点章节。' : '导出证据链报告，并约谈相关供应商。'
],
steps: ['scene_route', 'structured_load', 'rule_hit', 'false_positive_exemption', 'risk_fusion', 'evidence_assembly', 'report_generation'],
traceId: `TRC-${Date.now()}`
}
};
setSessions(prev => prev.map(s => s.id === currentSessionId ? {
...s,
scene: parsed.scene,
messages: [...s.messages, agentReply]
} : s));
}, 2000);

} catch (e) {
console.error(e);
const errorReply = {
role: 'agent',
content: '解析时发生网络或模型错误，请稍后再试。',
time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
};
setSessions(prev => prev.map(s => s.id === currentSessionId ? { ...s, messages: [...s.messages, errorReply] } : s));
} finally {
setIsRouting(false);
}
};

return (
<div className="flex h-full relative w-full overflow-hidden">
{/* 左侧对话流 */}
<div className={`flex flex-col h-full transition-all duration-300 ${selectedReport ? 'w-2/3 border-r border-slate-200' : 'w-full max-w-5xl mx-auto'}`}>
<div className="flex-1 overflow-y-auto p-4 sm:p-8 space-y-6 pb-32">
  {!activeSession && (
  <div className="flex flex-col items-center justify-center min-h-[60vh] animate-in fade-in slide-in-from-bottom-4">
    <div className="w-16 h-16 bg-gradient-to-br from-blue-500 to-indigo-600 rounded-2xl flex items-center justify-center shadow-xl shadow-blue-200 mb-6">
      <Sparkles className="text-white" size={32} />
    </div>
    <h1 className="text-2xl sm:text-3xl font-bold text-slate-800 mb-2">有什么我可以帮您分析的？</h1>
    <p className="text-slate-500 mb-10 text-sm">直接描述您的监管需求，Agent 将自动分发到对应的工作流</p>

    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 w-full max-w-4xl">
      {quickActions.map((action, idx) => (
      <div key={idx} onClick={() => handleSubmit(action.prompt)} className="bg-white border border-slate-200 p-4 rounded-2xl cursor-pointer hover:border-blue-300 hover:shadow-md transition-all group">
      <div className={`w-10 h-10 ${action.bg} ${action.color} rounded-xl flex items-center justify-center mb-3 group-hover:scale-110 transition-transform`}>
        <action.icon size={20} />
      </div>
      <h4 className="font-bold text-slate-800 text-sm mb-1">{action.label}</h4>
      <p className="text-xs text-slate-500 line-clamp-2">{action.prompt}</p>
    </div>
    ))}
  </div>
</div>
)}

{activeSession && activeSession.messages.map((msg, idx) => {
const isUser = msg.role === 'user';
return (
<div key={idx} className={`flex gap-4 w-full animate-in fade-in slide-in-from-bottom-2 ${isUser ? 'flex-row-reverse' : ''}`}>
<div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 mt-1 shadow-sm ${isUser ? 'bg-slate-200 text-slate-600' : 'bg-gradient-to-br from-blue-500 to-indigo-600 text-white'}`}>
{isUser ? <User size={16} /> : <Bot size={16} />}
</div>

<div className={`flex flex-col ${selectedReport ? 'max-w-[95%]' : 'max-w-[85%] sm:max-w-[75%]'} ${isUser ? 'items-end' : 'items-start'}`}>
<span className="text-[10px] text-slate-400 font-semibold mb-1 px-1">
                    {isUser ? '您' : 'Drug-Agent'} · {msg.time}
                  </span>

{isUser && (
<div className="flex flex-col items-end gap-2">
<div className="bg-slate-900 text-white px-5 py-3.5 rounded-2xl rounded-tr-sm text-[14px] leading-relaxed shadow-sm">
  {msg.content}
</div>
{msg.attachments && msg.attachments.length > 0 && (
<div className="flex flex-wrap justify-end gap-2 mt-1">
  {msg.attachments.map((att, i) => (
  <div key={i} className="flex items-center gap-1.5 px-3 py-1.5 bg-white border border-slate-200 rounded-lg shadow-sm text-xs text-slate-600">
    <FileText size={12} className="text-blue-500" /> {att}
  </div>
  ))}
</div>
)}
</div>
)}

{!isUser && (
<div className="space-y-3 w-full">
<div className="bg-transparent text-slate-800 text-[14px] leading-relaxed">
  {msg.content}
</div>

{msg.result && (
<div
    onClick={() => setSelectedReport(msg.result)}
className={`bg-white border rounded-2xl p-4 shadow-sm w-full cursor-pointer transition-all hover:shadow-md ${
selectedReport?.traceId === msg.result.traceId
? 'border-blue-400 ring-2 ring-blue-50'
: 'border-slate-200 hover:border-blue-300'
}`}
>
<div className="flex justify-between items-center mb-3">
  <div className="flex items-center gap-2">
                              <span className="px-2.5 py-1 bg-slate-100 text-slate-600 text-[10px] font-bold uppercase rounded-md tracking-wider">
                                {msg.result.scene}
                              </span>
    <span className="text-[10px] text-slate-400 font-mono">ID: {msg.result.traceId.split('-')[1]}</span>
  </div>
  <div className={`px-2.5 py-1 rounded-md text-[10px] font-bold uppercase border flex items-center gap-1 ${getRiskConfig(msg.result.riskLevel).bg} ${getRiskConfig(msg.result.riskLevel).color} ${getRiskConfig(msg.result.riskLevel).border}`}>
    {React.createElement(getRiskConfig(msg.result.riskLevel).icon, { size: 12 })}
    {getRiskConfig(msg.result.riskLevel).label}
  </div>
</div>

<p className="text-sm text-slate-700 font-medium mb-4 line-clamp-2">
  {msg.result.summary}
</p>

<div className="flex items-center justify-between pt-3 border-t border-slate-100">
  <div className="flex gap-4">
    <div className="flex flex-col">
      <span className="text-[10px] text-slate-400">综合评分</span>
      <span className="text-xs font-bold text-slate-700">{msg.result.score} 分</span>
    </div>
    <div className="flex flex-col">
      <span className="text-[10px] text-slate-400">处理文档</span>
      <span className="text-xs font-bold text-slate-700">{msg.result.docCount || 0} 份</span>
    </div>
  </div>
  <button className="text-xs font-bold text-blue-600 flex items-center gap-1 bg-blue-50 px-3 py-1.5 rounded-lg hover:bg-blue-100 transition-colors">
    查看详细报告 <ChevronRight size={14} />
  </button>
</div>
</div>
)}
</div>
)}
</div>
</div>
);
})}

{isRouting && (
<div className="flex gap-4 animate-in fade-in slide-in-from-bottom-2">
<div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 text-white flex items-center justify-center flex-shrink-0 mt-1 shadow-sm animate-pulse">
  <Bot size={16} />
</div>
<div className="bg-slate-100/50 px-5 py-3.5 rounded-2xl rounded-tl-sm text-slate-400 text-[14px] flex items-center gap-2">
  <Loader2 size={16} className="animate-spin" /> Agent 正在执行深层编排工作流...
</div>
</div>
)}
<div ref={messagesEndRef} />
</div>

{/* 底部输入框 */}
<div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-white via-white to-transparent pt-12 pb-6 px-4 sm:px-8">
<div className="w-full max-w-4xl mx-auto bg-white rounded-3xl shadow-[0_-4px_24px_-8px_rgba(0,0,0,0.08)] border border-slate-200 p-1.5 transition-all focus-within:ring-4 focus-within:ring-blue-100 focus-within:border-blue-400">
            <textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
  onKeyDown={(e) => { if(e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSubmit(); } }}
  rows="2"
  placeholder={activeSession ? "向 Agent 追加要求或提供更多材料..." : "描述您的监管需求，例如：检测这两份标书文件是否雷同..."}
  className="w-full bg-transparent border-none p-4 text-slate-700 focus:ring-0 outline-none resize-none text-[15px] max-h-32"
  />
  <div className="flex items-center justify-between px-3 pb-2 pt-1 border-t border-slate-50">
  <div className="flex gap-1 sm:gap-2">
  <button className="p-2 text-slate-500 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors flex items-center gap-1.5 text-xs font-medium">
  <Upload size={16} /> <span className="hidden sm:inline">上传材料</span>
  </button>
  <button className="p-2 text-slate-500 hover:text-emerald-600 hover:bg-emerald-50 rounded-lg transition-colors flex items-center gap-1.5 text-xs font-medium">
  <BookOpen size={16} /> <span className="hidden sm:inline">引用知识</span>
  </button>
  </div>
  <button
  onClick={() => handleSubmit()}
  disabled={isRouting || !input.trim()}
  className="bg-blue-600 text-white px-5 sm:px-6 py-2 rounded-xl text-sm font-bold shadow-md shadow-blue-200 hover:bg-blue-700 transition-all flex items-center gap-2 disabled:bg-slate-300 disabled:shadow-none"
  >
  {isRouting ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
  <span className="hidden sm:inline">发送任务</span>
  </button>
  </div>
  </div>
  <div className="text-center text-[10px] text-slate-400 mt-3 font-medium">
  AI 生成内容仅供参考，重大决策请人工复核 (Drug-Agent Core v0.3)
  </div>
  </div>
  </div>

  {/* 右侧详情报告抽屉 */}
  {selectedReport && (
  <div className="w-1/3 bg-slate-50 border-l border-slate-200 flex flex-col animate-in slide-in-from-right-8 fade-in duration-300 shadow-[-12px_0_24px_-12px_rgba(0,0,0,0.05)] z-20 relative">
  <div className="h-14 border-b border-slate-200 px-4 flex items-center justify-between bg-white shrink-0 sticky top-0 z-10">
  <div className="flex items-center gap-2 text-slate-800">
  <button onClick={() => setSelectedReport(null)} className="p-1.5 hover:bg-slate-100 rounded-md transition-colors mr-1">
  <ChevronLeft size={18} />
  </button>
  <FileText size={16} className="text-blue-600" />
  <h3 className="font-bold text-sm">结构化分析报告</h3>
  </div>
  <button className="p-1.5 text-slate-400 hover:text-slate-800 transition-colors">
  <Download size={16} />
  </button>
  </div>

  <div className="flex-1 overflow-y-auto p-5 space-y-6">
  <div className="flex items-center justify-between bg-white p-4 rounded-xl border border-slate-200 shadow-sm">
  <div>
  <p className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">风险定级</p>
  <div className={`flex items-center gap-1.5 text-lg font-black ${getRiskConfig(selectedReport.riskLevel).color}`}>
  {React.createElement(getRiskConfig(selectedReport.riskLevel).icon, { size: 20 })}
  {getRiskConfig(selectedReport.riskLevel).label}
  </div>
  </div>
  <div className="text-right">
  <p className="text-[10px] text-slate-400 font-bold uppercase tracking-wider mb-1">综合分值</p>
  <span className="text-xl font-black text-slate-800">{selectedReport.score}</span>
  </div>
  </div>

  <section className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
  <h4 className="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
  <Activity size={14} className="text-blue-600"/> 结果摘要 (Agent Output)
  </h4>
  <p className="text-sm text-slate-600 leading-relaxed font-medium">
  Reviewed {selectedReport.docCount} documents, retained {selectedReport.riskLevel === 'High' ? 'multiple' : '0'} high-risk hits. Overall risk={selectedReport.riskLevel.toUpperCase()}, score={selectedReport.score}.
  Top focus: {selectedReport.summary}
  </p>
  </section>

  <section className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
  <h4 className="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
  <Network size={14} className="text-indigo-600"/> 管理摘要
  </h4>
  <ul className="space-y-2">
  {selectedReport.managementSummary?.map((point, i) => (
  <li key={i} className="flex gap-2 text-sm text-slate-600 leading-relaxed">
  <span className="text-slate-400 mt-1.5 text-[10px] font-black">•</span> {point}
  </li>
  ))}
  </ul>
  </section>

  <section className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm border-l-4 border-l-blue-500">
  <h4 className="text-xs font-bold text-slate-800 mb-3 flex items-center gap-2">
  <CheckSquare size={14} className="text-blue-600"/> 建议动作
  </h4>
  <ul className="space-y-2">
  {selectedReport.suggestedActions?.map((action, i) => (
  <li key={i} className="flex gap-2 text-sm text-slate-700 font-medium">
  <span className="text-blue-400 mt-1.5 text-[10px] font-black">→</span> {action}
  </li>
  ))}
  </ul>
  </section>

  <section className="bg-white p-5 rounded-xl border border-slate-200 shadow-sm">
  <h4 className="text-xs font-bold text-slate-800 mb-4 flex items-center gap-2">
  <Terminal size={14} className="text-slate-600"/> 执行步骤 (Execution Trace)
  </h4>
  <div className="space-y-0 relative before:absolute before:inset-y-0 before:left-[7px] before:w-px before:bg-slate-200 pl-1">
  {selectedReport.steps?.map((step, idx) => (
  <div key={idx} className="relative pl-6 pb-4 last:pb-0">
  <div className="absolute left-0 top-1 w-[14px] h-[14px] rounded-full border-2 border-white bg-slate-300 z-10"></div>
  <span className="text-[13px] font-mono text-slate-600">{step}</span>
  </div>
  ))}
  </div>
  </section>
  </div>
  </div>
  )}
  </div>
  );
  };

  // --- 子场景：任务看板 ---
  const TaskBoardView = ({ tasks }) => {
  return (
  <div className="max-w-6xl mx-auto p-8">
  <h2 className="text-2xl font-bold text-slate-900 mb-6">任务调度看板</h2>
  <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
  {tasks.map(task => (
  <div key={task.id} className="bg-white rounded-3xl border border-slate-200 p-6 hover:shadow-xl transition-all">
  <div className="flex justify-between items-start mb-4">
  <span className="px-2.5 py-1 bg-indigo-50 text-indigo-600 text-[10px] font-bold uppercase rounded-md">
  {task.scene}
  </span>
  <span className="text-[10px] font-bold text-slate-400">{task.status}</span>
  </div>
  <h3 className="text-base font-bold text-slate-800 mb-1 leading-tight">{task.name}</h3>
  <p className="text-[11px] text-slate-400 font-mono mb-4">{task.id}</p>

  {task.status === 'running' && (
  <div className="w-full bg-slate-100 h-1.5 rounded-full overflow-hidden mt-4">
  <div className="h-full bg-blue-600 animate-pulse transition-all duration-500" style={{ width: `${task.progress}%` }} />
  </div>
  )}
  </div>
  ))}
  </div>
  </div>
  );
  };

  // --- 子场景：知识库 ---
  const KnowledgeBaseView = () => {
  return (
  <div className="max-w-6xl mx-auto p-8 text-center flex flex-col items-center justify-center h-full text-slate-400">
  <Database size={48} className="mb-4 opacity-20" />
  <h2 className="text-xl font-bold text-slate-700 mb-2">合规知识大脑</h2>
  <p>管理 Agent 的长期记忆与审查准则，包括 RAG 向量切片库和规则引擎字典。</p>
  </div>
  );
  };

  // --- [新增核心组件]：偏好设置与系统配置视图 ---
  const SettingsView = () => {
  const [activeTab, setActiveTab] = useState('ENGINE');

  // 用于模拟滑块和开关的状态
  const [creativity, setCreativity] = useState(20);
  const [autoClean, setAutoClean] = useState(true);
  const [traceEnabled, setTraceEnabled] = useState(true);
  const [notifyEnabled, setNotifyEnabled] = useState(false);

  const tabs = [
  { id: 'PROFILE', label: '基础资料', icon: User },
  { id: 'ENGINE', label: 'Agent 引擎偏好', icon: Cpu },
  { id: 'COMPLIANCE', label: '数据与合规策略', icon: ShieldAlert },
  { id: 'DISPLAY', label: '通知与展示', icon: Sliders },
  ];

  return (
  <div className="max-w-5xl mx-auto p-6 md:p-10 animate-in fade-in slide-in-from-bottom-4">
  <div className="mb-8">
  <h2 className="text-2xl font-bold text-slate-900 mb-2">偏好与系统配置</h2>
  <p className="text-sm text-slate-500">管理当前账户的基础信息、Agent 底层大模型参数以及文档处理的合规留痕策略。</p>
  </div>

  <div className="flex flex-col md:flex-row gap-8 items-start">
  {/* 左侧垂直 Tab 导航 */}
  <div className="w-full md:w-64 bg-white border border-slate-200 rounded-2xl p-2 shadow-sm shrink-0">
  {tabs.map((tab) => (
  <button
  key={tab.id}
  onClick={() => setActiveTab(tab.id)}
  className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-all mb-1 last:mb-0 ${
  activeTab === tab.id
  ? 'bg-blue-50 text-blue-700'
  : 'text-slate-600 hover:bg-slate-50'
  }`}
  >
  <tab.icon size={18} className={activeTab === tab.id ? 'text-blue-600' : 'text-slate-400'} />
  {tab.label}
  </button>
  ))}
  </div>

  {/* 右侧配置内容区 */}
  <div className="flex-1 w-full space-y-6">

  {/* --- Tab: Agent 引擎偏好 --- */}
  {activeTab === 'ENGINE' && (
  <div className="bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden animate-in fade-in slide-in-from-right-4">
  <div className="p-6 border-b border-slate-100 bg-slate-50/50">
  <h3 className="text-base font-bold text-slate-800 flex items-center gap-2">
  <Cpu size={18} className="text-indigo-600" />
  底层模型与路由规则
  </h3>
  <p className="text-xs text-slate-500 mt-1">调整支撑 Agent 运行的核心大语言模型及推理参数。</p>
  </div>
  <div className="p-6 space-y-8">

  {/* 配置项：默认大模型 */}
  <div className="space-y-3">
  <label className="block text-sm font-bold text-slate-800">基座大模型选择 (Default Model)</label>
  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
  <div className="border-2 border-blue-500 bg-blue-50/50 rounded-xl p-4 cursor-pointer relative">
  <div className="absolute top-3 right-3 w-4 h-4 bg-blue-500 rounded-full flex items-center justify-center">
  <div className="w-1.5 h-1.5 bg-white rounded-full"></div>
  </div>
  <h4 className="font-bold text-blue-900 text-sm mb-1">Gemini 2.5 Flash</h4>
  <p className="text-xs text-blue-700/80 leading-relaxed">响应速度极快，适合日常标书查重与快速文本分发路由。（当前推荐）</p>
  </div>
  <div className="border-2 border-slate-200 hover:border-slate-300 bg-white rounded-xl p-4 cursor-pointer transition-colors">
  <h4 className="font-bold text-slate-700 text-sm mb-1">Gemini 2.5 Pro</h4>
  <p className="text-xs text-slate-500 leading-relaxed">具备强大的逻辑推理能力，适合处理复杂的法律合同交叉比对。</p>
  </div>
  </div>
  </div>

  <div className="h-px bg-slate-100 w-full"></div>

  {/* 配置项：温度值/严谨度 */}
  <div className="space-y-4">
  <div className="flex justify-between items-center">
  <label className="block text-sm font-bold text-slate-800">
  推理严谨度 (Temperature 控制)
  </label>
  <span className="text-xs font-mono bg-slate-100 px-2 py-0.5 rounded text-slate-600">{creativity}% 幻觉容忍</span>
  </div>
  <p className="text-xs text-slate-500 mb-2">
  在医疗合规场景中，建议将此值设定在较低水平（&lt; 30%），以确保模型严格遵循规则引擎的结论，减少开放式幻觉。
  </p>
  <div className="flex items-center gap-4">
  <span className="text-xs text-slate-400 font-medium w-16 text-right">严密事实</span>
  <input
  type="range"
  min="0" max="100"
  value={creativity}
  onChange={(e) => setCreativity(e.target.value)}
  className="flex-1 h-2 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-blue-600"
  />
  <span className="text-xs text-slate-400 font-medium w-16">发散创造</span>
  </div>
  </div>

  </div>
  </div>
  )}

  {/* --- Tab: 数据与合规策略 --- */}
  {activeTab === 'COMPLIANCE' && (
  <div className="bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden animate-in fade-in slide-in-from-right-4">
  <div className="p-6 border-b border-slate-100 bg-slate-50/50">
  <h3 className="text-base font-bold text-slate-800 flex items-center gap-2">
  <ShieldAlert size={18} className="text-emerald-600" />
  数据脱敏与留痕策略
  </h3>
  <p className="text-xs text-slate-500 mt-1">控制上传至 Agent 的敏感文件如何被存储与销毁。</p>
  </div>
  <div className="p-6 space-y-6">

  {/* 开关项 */}
  <div className="flex items-center justify-between p-4 border border-slate-200 rounded-xl bg-white shadow-sm hover:border-blue-300 transition-colors cursor-pointer" onClick={() => setAutoClean(!autoClean)}>
  <div>
  <h4 className="font-bold text-slate-800 text-sm mb-1 flex items-center gap-2">
  <Trash2 size={16} className="text-slate-400" /> 自动清理源文件缓存
  </h4>
  <p className="text-xs text-slate-500 max-w-sm leading-relaxed">
  开启后，审查工作流执行完毕 24 小时内，将自动物理销毁云端存储的上传附件 (.pdf/.docx)，仅保留向量化切片与摘要。
  </p>
  </div>
  <div className={autoClean ? "text-emerald-500" : "text-slate-300"}>
  {autoClean ? <ToggleRight size={36} /> : <ToggleLeft size={36} />}
  </div>
  </div>

  {/* 选择框 */}
  <div className="space-y-3">
  <label className="block text-sm font-bold text-slate-800">审计追踪日志 (Trace Log) 留存期限</label>
  <select className="w-full sm:w-1/2 bg-slate-50 border border-slate-200 text-slate-700 text-sm rounded-xl focus:ring-blue-500 focus:border-blue-500 block p-2.5 outline-none">
  <option>保留 30 天</option>
  <option>保留 90 天 (合规推荐)</option>
  <option>保留 180 天</option>
  <option>永久保留 (消耗较高存储)</option>
  </select>
  </div>
  </div>
  </div>
  )}

  {/* --- Tab: 通知与展示 --- */}
  {activeTab === 'DISPLAY' && (
  <div className="bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden animate-in fade-in slide-in-from-right-4">
  <div className="p-6 border-b border-slate-100 bg-slate-50/50">
  <h3 className="text-base font-bold text-slate-800 flex items-center gap-2">
  <Sliders size={18} className="text-amber-600" />
  视图展示与系统通知
  </h3>
  </div>
  <div className="p-6 space-y-6">

  <div className="flex items-center justify-between p-4 border border-slate-200 rounded-xl bg-white shadow-sm hover:border-blue-300 transition-colors cursor-pointer" onClick={() => setTraceEnabled(!traceEnabled)}>
  <div>
  <h4 className="font-bold text-slate-800 text-sm mb-1 flex items-center gap-2">
  <Terminal size={16} className="text-slate-400" /> 默认展开 Agent 思考图谱 (Trace)
  </h4>
  <p className="text-xs text-slate-500 max-w-sm leading-relaxed">
  工作台中生成审查报告时，默认展示 Agent 的调用节点及执行日志，适合审计员及高级审核专家。
  </p>
  </div>
  <div className={traceEnabled ? "text-blue-500" : "text-slate-300"}>
  {traceEnabled ? <ToggleRight size={36} /> : <ToggleLeft size={36} />}
  </div>
  </div>

  <div className="flex items-center justify-between p-4 border border-slate-200 rounded-xl bg-white shadow-sm hover:border-blue-300 transition-colors cursor-pointer" onClick={() => setNotifyEnabled(!notifyEnabled)}>
  <div>
  <h4 className="font-bold text-slate-800 text-sm mb-1 flex items-center gap-2">
  <Bell size={16} className="text-slate-400" /> 高风险命中外部告警
  </h4>
  <p className="text-xs text-slate-500 max-w-sm leading-relaxed">
  当任何一个后台任务发现“高风险 (High Risk)” 级别违规时，自动通过邮件或企业微信发送告警通知。
  </p>
  </div>
  <div className={notifyEnabled ? "text-emerald-500" : "text-slate-300"}>
  {notifyEnabled ? <ToggleRight size={36} /> : <ToggleLeft size={36} />}
  </div>
  </div>

  </div>
  </div>
  )}

  {/* --- Tab: 基础资料 --- */}
  {activeTab === 'PROFILE' && (
  <div className="bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden animate-in fade-in slide-in-from-right-4">
  <div className="p-6 border-b border-slate-100 bg-slate-50/50 flex items-center gap-4">
  <div className="w-16 h-16 bg-slate-200 rounded-full flex items-center justify-center text-slate-500">
  <User size={32} />
  </div>
  <div>
  <h3 className="text-lg font-bold text-slate-800">管理员 (Admin)</h3>
  <p className="text-xs text-slate-500 font-mono mt-1">ID: USR-882194-A</p>
  </div>
  </div>
  <div className="p-6">
  <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-start gap-3">
  <Lock className="text-amber-500 shrink-0 mt-0.5" size={18} />
  <div>
  <h4 className="text-sm font-bold text-amber-800 mb-1">系统权限与角色限制</h4>
  <p className="text-xs text-amber-700/80 leading-relaxed">
  当前账户拥有“全场景工作流调用”及“知识库读写”权限。如需修改权限配置，请联系 IT 部门通过 SSO 同步更新。
  </p>
  </div>
  </div>
  </div>
  </div>
  )}

  {/* 全局保存按钮 */}
  <div className="flex justify-end pt-4">
  <button className="bg-slate-900 hover:bg-blue-600 text-white px-8 py-2.5 rounded-xl text-sm font-bold shadow-md transition-colors flex items-center gap-2">
  <Save size={16} /> 保存系统配置
  </button>
  </div>

  </div>
  </div>
  </div>
  );
  };

  export default App;