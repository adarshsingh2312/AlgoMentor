import React, { useState, useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import { Link } from "react-router-dom";
import { apiClient } from "../api/client";
import { useAuth } from "../context/AuthContext";

const STARTER_PROMPTS = [
  {
    title: "Greedy Algorithms",
    subtitle: "Core intuition, proof idea & example",
    prompt: "What is greedy algorithm? Explain with an example and time complexity.",
  },
  {
    title: "Binary Search",
    subtitle: "Search space, invariants & edge cases",
    prompt: "Explain binary search, its time complexity, and common mistakes.",
  },
  {
    title: "My Struggle Topics",
    subtitle: "Insights from your Codeforces history",
    prompt: "Which problems and topics have I struggled on recently?",
  },
  {
    title: "Dynamic Programming",
    subtitle: "State, transitions & memoization",
    prompt: "How should I approach dynamic programming problems step by step?",
  },
];

export default function StudyTutor() {
  const { user } = useAuth();
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [expandedSources, setExpandedSources] = useState({});
  const [copiedId, setCopiedId] = useState(null);

  const chatEndRef = useRef(null);
  const textareaRef = useRef(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, sending]);

  const adjustTextareaHeight = () => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 140)}px`;
  };

  const sendMessage = async (rawText) => {
    const trimmed = rawText.trim();
    if (!trimmed || sending) return;

    const userMsg = {
      id: `u-${Date.now()}`,
      role: "user",
      content: trimmed,
    };

    const historyPayload = messages
      .filter((m) => !m.isError)
      .slice(-10)
      .map((m) => ({ role: m.role, content: m.content }));

    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    if (textareaRef.current) {
      textareaRef.current.style.height = "auto";
    }
    setSending(true);

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 60000);

    try {
      const res = await apiClient("/api/me/notes/ask", {
        method: "POST",
        body: JSON.stringify({
          question: trimmed,
          history: historyPayload,
        }),
        signal: controller.signal,
      });

      setMessages((prev) => [
        ...prev,
        {
          id: `a-${Date.now()}`,
          role: "assistant",
          content: res.answer,
          citations: res.citations || [],
        },
      ]);
    } catch (err) {
      let errText = err.message || "Failed to get a response.";
      if (err.status === 502) {
        errText = "AI service is temporarily unavailable. Please try again in a moment.";
      } else if (err.status === 408 || err.name === "AbortError") {
        errText = "Request timed out after 60s. Try asking a more focused question.";
      }
      setMessages((prev) => [
        ...prev,
        {
          id: `e-${Date.now()}`,
          role: "assistant",
          content: errText,
          isError: true,
        },
      ]);
    } finally {
      clearTimeout(timeoutId);
      setSending(false);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    sendMessage(input);
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage(input);
    }
  };

  const handleCopy = async (id, text) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 1800);
    } catch {
      // Ignore clipboard errors
    }
  };

  const toggleSources = (id) => {
    setExpandedSources((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const userInitial = (user?.name || user?.email || "U").charAt(0).toUpperCase();

  return (
    <div className="flex flex-col h-[calc(100vh-8.5rem)] max-w-4xl mx-auto bg-slate-900/80 border border-slate-800 rounded-2xl shadow-xl overflow-hidden">
      {/* Top Bar */}
      <div className="px-5 py-3.5 bg-slate-900 border-b border-slate-800 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center text-white font-bold text-sm shadow-md shadow-indigo-500/20">
            AI
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-sm sm:text-base font-bold text-white">AI Study Tutor</h1>
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                RAG Active
              </span>
            </div>
            <p className="text-xs text-slate-400">
              Grounded in your notes, Codeforces struggles &amp; topic guides
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Link
            to="/notes"
            className="px-3 py-1.5 text-xs font-medium text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-lg border border-slate-700 transition"
          >
            Manage Notes
          </Link>
          {messages.length > 0 && (
            <button
              type="button"
              onClick={() => {
                setMessages([]);
                setExpandedSources({});
              }}
              disabled={sending}
              className="px-3 py-1.5 text-xs font-medium text-rose-300 hover:text-rose-200 bg-rose-500/10 hover:bg-rose-500/20 rounded-lg border border-rose-500/20 transition disabled:opacity-50 cursor-pointer"
            >
              New Chat
            </button>
          )}
        </div>
      </div>

      {/* Messages Viewport */}
      <div className="flex-1 overflow-y-auto px-4 sm:px-6 py-6 space-y-6 bg-slate-950/50">
        {messages.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center text-center max-w-xl mx-auto py-6">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-indigo-500/20 to-violet-500/20 border border-indigo-500/30 flex items-center justify-center text-2xl mb-4">
              ✨
            </div>
            <h2 className="text-xl font-bold text-white mb-2">
              What would you like to learn today?
            </h2>
            <p className="text-sm text-slate-400 mb-6">
              Ask concepts, time complexities, code examples, or follow-up questions — I remember the conversation context.
            </p>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full">
              {STARTER_PROMPTS.map((item) => (
                <button
                  key={item.title}
                  type="button"
                  onClick={() => sendMessage(item.prompt)}
                  disabled={sending}
                  className="text-left p-3.5 rounded-xl bg-slate-900/90 hover:bg-slate-800/90 border border-slate-800 hover:border-indigo-500/40 transition group cursor-pointer"
                >
                  <div className="text-sm font-semibold text-slate-200 group-hover:text-indigo-300 transition">
                    {item.title}
                  </div>
                  <div className="text-xs text-slate-400 mt-0.5">{item.subtitle}</div>
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex gap-3 ${msg.role === "user" ? "justify-end" : "justify-start"}`}
            >
              {msg.role === "assistant" && (
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center text-white text-xs font-bold shrink-0 mt-0.5 shadow-sm">
                  AI
                </div>
              )}

              <div
                className={`max-w-[82%] rounded-2xl px-4 py-3 text-sm shadow-sm ${
                  msg.role === "user"
                    ? "bg-indigo-600 text-white rounded-br-sm"
                    : msg.isError
                    ? "bg-rose-950/60 border border-rose-800 text-rose-200 rounded-tl-sm"
                    : "bg-slate-900 border border-slate-800 text-slate-200 rounded-tl-sm"
                }`}
              >
                {msg.role === "user" ? (
                  <p className="whitespace-pre-wrap leading-relaxed">{msg.content}</p>
                ) : (
                  <div className="space-y-3">
                    <div className="prose prose-invert prose-sm max-w-none leading-relaxed prose-pre:bg-slate-950 prose-pre:border prose-pre:border-slate-800">
                      <ReactMarkdown>{msg.content}</ReactMarkdown>
                    </div>

                    {!msg.isError && (
                      <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-slate-800/80">
                        <div className="flex items-center gap-2">
                          {msg.citations && msg.citations.length > 0 && (
                            <button
                              type="button"
                              onClick={() => toggleSources(msg.id)}
                              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-slate-800/80 hover:bg-slate-800 text-xs font-medium text-indigo-300 border border-slate-700/70 transition cursor-pointer"
                            >
                              <span>
                                {expandedSources[msg.id] ? "Hide" : "Sources"} ({msg.citations.length})
                              </span>
                              <span className="text-[10px]">{expandedSources[msg.id] ? "▲" : "▼"}</span>
                            </button>
                          )}
                        </div>

                        <button
                          type="button"
                          onClick={() => handleCopy(msg.id, msg.content)}
                          className="text-xs text-slate-400 hover:text-slate-200 transition cursor-pointer"
                        >
                          {copiedId === msg.id ? "Copied ✓" : "Copy"}
                        </button>
                      </div>
                    )}

                    {expandedSources[msg.id] && msg.citations && msg.citations.length > 0 && (
                      <div className="space-y-1.5 pt-1">
                        {msg.citations.map((c, idx) => (
                          <div
                            key={idx}
                            className="p-2.5 rounded-lg bg-slate-950 border border-slate-800 text-xs text-slate-300"
                          >
                            <div className="flex items-center justify-between font-mono text-slate-400 mb-1">
                              <span className="truncate pr-2">
                                [{idx + 1}] {c.displayName} (chunk #{c.chunkIndex})
                              </span>
                              <span className="text-slate-500 shrink-0">
                                {c.score ? `${Math.round(c.score * 100)}% match` : "N/A"}
                              </span>
                            </div>
                            <p className="line-clamp-2 text-slate-400 italic">"{c.snippet}"</p>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {msg.role === "user" && (
                <div className="w-8 h-8 rounded-lg bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-200 text-xs font-semibold shrink-0 mt-0.5">
                  {userInitial}
                </div>
              )}
            </div>
          ))
        )}

        {sending && (
          <div className="flex gap-3 justify-start">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center text-white text-xs font-bold shrink-0">
              AI
            </div>
            <div className="bg-slate-900 border border-slate-800 text-slate-300 rounded-2xl rounded-tl-sm px-4 py-3 text-sm flex items-center gap-2.5">
              <div className="flex gap-1">
                <span className="w-2 h-2 rounded-full bg-indigo-400 animate-bounce [animation-delay:-0.3s]" />
                <span className="w-2 h-2 rounded-full bg-indigo-400 animate-bounce [animation-delay:-0.15s]" />
                <span className="w-2 h-2 rounded-full bg-indigo-400 animate-bounce" />
              </div>
              <span className="text-xs text-slate-400">Searching notes &amp; thinking...</span>
            </div>
          </div>
        )}
        <div ref={chatEndRef} />
      </div>

      {/* Composer */}
      <form
        onSubmit={handleSubmit}
        className="p-3 sm:p-4 bg-slate-900 border-t border-slate-800 shrink-0"
      >
        <div className="flex items-end gap-2 bg-slate-950 border border-slate-700 focus-within:border-indigo-500 rounded-xl p-2 transition">
          <textarea
            ref={textareaRef}
            rows={1}
            value={input}
            onChange={(e) => {
              setInput(e.target.value);
              adjustTextareaHeight();
            }}
            onKeyDown={handleKeyDown}
            placeholder={
              messages.length > 0
                ? "Ask a follow-up question... (Enter to send, Shift+Enter for new line)"
                : "Ask about algorithms, complexity, or your notes..."
            }
            disabled={sending}
            className="flex-1 bg-transparent px-2 py-1.5 text-sm text-slate-100 placeholder-slate-500 focus:outline-none resize-none max-h-36"
          />
          <button
            type="submit"
            disabled={sending || !input.trim()}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 disabled:cursor-not-allowed text-white text-xs sm:text-sm font-semibold rounded-lg transition shrink-0 cursor-pointer"
          >
            {sending ? "..." : "Send ↑"}
          </button>
        </div>
      </form>
    </div>
  );
}
