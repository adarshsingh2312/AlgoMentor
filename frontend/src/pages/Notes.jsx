import React, { useState, useEffect, useRef } from "react";
import ReactMarkdown from "react-markdown";
import { apiClient, ApiError } from "../api/client";

export default function Notes() {
  const [notes, setNotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Upload state
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState(null);
  const [uploadSuccess, setUploadSuccess] = useState(null);
  const fileInputRef = useRef(null);

  // Note viewer state
  const [viewingNote, setViewingNote] = useState(null);
  const [loadingContent, setLoadingContent] = useState(false);

  // Reindexing state
  const [reindexingId, setReindexingId] = useState(null);

  // Ask notes state
  const [question, setQuestion] = useState("");
  const [asking, setAsking] = useState(false);
  const [askError, setAskError] = useState(null);
  const [askResult, setAskResult] = useState(null);
  const askAbortControllerRef = useRef(null);

  const fetchNotes = async () => {
    try {
      setError(null);
      const data = await apiClient("/api/me/notes");
      setNotes(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || "Failed to load notes");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNotes();
  }, []);

  // Upload handler
  const handleUpload = async (e) => {
    e.preventDefault();
    const file = fileInputRef.current?.files?.[0];
    if (!file) return;

    const ext = file.name.split(".").pop().toLowerCase();
    if (ext !== "md" && ext !== "txt") {
      setUploadError("Only .md and .txt files are supported.");
      return;
    }

    // Client size check is UX warning only (e.g. 500KB)
    if (file.size > 500 * 1024) {
      if (!window.confirm("File is larger than 500KB. Indexing might take longer. Proceed?")) {
        return;
      }
    }

    setUploading(true);
    setUploadError(null);
    setUploadSuccess(null);

    const formData = new FormData();
    formData.append("file", file);

    try {
      const savedNote = await apiClient("/api/me/notes", {
        method: "POST",
        body: formData,
      });
      setUploadSuccess(`Uploaded "${savedNote.displayName}" successfully!`);
      if (fileInputRef.current) fileInputRef.current.value = "";
      fetchNotes();
    } catch (err) {
      setUploadError(err.message || "Upload failed");
    } finally {
      setUploading(false);
    }
  };

  // View note detail
  const handleViewNote = async (id) => {
    setLoadingContent(true);
    try {
      const detail = await apiClient(`/api/me/notes/${id}`);
      setViewingNote(detail);
    } catch (err) {
      alert("Failed to load note content: " + (err.message || "Unknown error"));
    } finally {
      setLoadingContent(false);
    }
  };

  // Delete note
  const handleDeleteNote = async (id, name) => {
    if (!window.confirm(`Are you sure you want to delete "${name}"?`)) {
      return;
    }

    try {
      await apiClient(`/api/me/notes/${id}`, { method: "DELETE" });
      setNotes((prev) => prev.filter((n) => n.id !== id));
      if (viewingNote?.id === id) {
        setViewingNote(null);
      }
    } catch (err) {
      alert("Failed to delete note: " + (err.message || "Unknown error"));
    }
  };

  // Reindex note
  const handleReindex = async (id) => {
    setReindexingId(id);
    try {
      await apiClient(`/api/me/notes/${id}/reindex`, { method: "POST" });
      await fetchNotes();
    } catch (err) {
      alert("Reindex failed: " + (err.message || "Unknown error"));
    } finally {
      setReindexingId(null);
    }
  };

  // Ask notes
  const handleAsk = async (e) => {
    e.preventDefault();
    if (!question.trim()) return;

    setAsking(true);
    setAskError(null);
    setAskResult(null);

    // 60-second AbortController timeout
    const controller = new AbortController();
    askAbortControllerRef.current = controller;
    const timeoutId = setTimeout(() => controller.abort(), 60000);

    try {
      const response = await apiClient("/api/me/notes/ask", {
        method: "POST",
        body: JSON.stringify({ question: question.trim() }),
        signal: controller.signal,
      });
      setAskResult(response);
    } catch (err) {
      if (err.status === 502) {
        setAskError("AI service is temporarily unavailable. Please try again later.");
      } else if (err.status === 408 || err.name === "AbortError") {
        setAskError("The request timed out after 60 seconds. Please try a simpler query.");
      } else {
        setAskError(err.message || "Failed to get an answer.");
      }
    } finally {
      clearTimeout(timeoutId);
      setAsking(false);
      askAbortControllerRef.current = null;
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case "INDEXED":
        return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-emerald-950 text-emerald-400 border border-emerald-800">INDEXED</span>;
      case "INDEXING":
        return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-amber-950 text-amber-400 border border-amber-800 animate-pulse">INDEXING</span>;
      case "UPLOADED":
        return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-blue-950 text-blue-400 border border-blue-800">UPLOADED</span>;
      case "FAILED":
        return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-rose-950 text-rose-400 border border-rose-800">FAILED</span>;
      default:
        return <span className="px-2 py-0.5 rounded text-xs font-semibold bg-zinc-800 text-zinc-400">{status}</span>;
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-zinc-100">DSA Notes & RAG</h1>
        <p className="text-zinc-400 text-sm mt-1">
          Upload your Markdown or text notes. They will be chunked, embedded, and indexed for semantic search and AI mentoring.
        </p>
      </div>

      {/* Upload Section */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 shadow-sm">
        <h2 className="text-base font-semibold text-zinc-100 mb-3">Upload New Note</h2>
        <form onSubmit={handleUpload} className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
          <input
            ref={fileInputRef}
            type="file"
            accept=".md,.txt"
            disabled={uploading}
            className="text-sm text-zinc-300 file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-xs file:font-semibold file:bg-zinc-800 file:text-zinc-200 hover:file:bg-zinc-700 cursor-pointer"
          />
          <button
            type="submit"
            disabled={uploading}
            className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-sm font-medium rounded-lg transition-colors cursor-pointer"
          >
            {uploading ? "Uploading & Indexing..." : "Upload Note"}
          </button>
        </form>
        {uploadError && <p className="text-rose-400 text-xs mt-2">{uploadError}</p>}
        {uploadSuccess && <p className="text-emerald-400 text-xs mt-2">{uploadSuccess}</p>}
      </div>

      {/* Ask Notes Section */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 shadow-sm">
        <h2 className="text-base font-semibold text-zinc-100 mb-2">Ask Your Notes (RAG)</h2>
        <p className="text-xs text-zinc-400 mb-4">
          Query your uploaded notes with semantic vector search. Responses cite relevant chunks directly from your notes.
        </p>
        <form onSubmit={handleAsk} className="flex gap-2">
          <input
            type="text"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="e.g. How does binary lifting for LCA work?"
            disabled={asking}
            className="flex-1 bg-zinc-950 border border-zinc-700 rounded-lg px-3 py-2 text-sm text-zinc-200 focus:outline-none focus:border-indigo-500"
          />
          <button
            type="submit"
            disabled={asking || !question.trim()}
            className="px-5 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-sm font-medium rounded-lg transition-colors cursor-pointer"
          >
            {asking ? "Thinking..." : "Ask"}
          </button>
        </form>

        {askError && (
          <div className="mt-4 p-3 rounded-lg bg-rose-950/60 border border-rose-800 text-rose-300 text-sm">
            {askError}
          </div>
        )}

        {askResult && (
          <div className="mt-5 p-4 rounded-xl bg-zinc-950 border border-zinc-800 space-y-4">
            <div>
              <div className="text-xs font-semibold text-indigo-400 uppercase tracking-wider mb-1">
                AI Answer
              </div>
              <div className="prose prose-invert max-w-none text-zinc-200 text-sm leading-relaxed">
                <ReactMarkdown>{askResult.answer}</ReactMarkdown>
              </div>
            </div>

            {askResult.citations && askResult.citations.length > 0 && (
              <div className="border-t border-zinc-800 pt-3">
                <div className="text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
                  Citations ({askResult.citations.length})
                </div>
                <div className="space-y-2">
                  {askResult.citations.map((c, idx) => (
                    <div
                      key={idx}
                      className="p-2.5 rounded bg-zinc-900 border border-zinc-800 text-xs text-zinc-300"
                    >
                      <div className="flex items-center justify-between font-mono text-zinc-400 mb-1">
                        <span>{c.displayName} (chunk #{c.chunkIndex})</span>
                        <span className="text-zinc-500">score: {c.score ? c.score.toFixed(3) : "N/A"}</span>
                      </div>
                      <p className="line-clamp-2 text-zinc-400 italic">"{c.snippet}"</p>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Notes List Section */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 shadow-sm">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-base font-semibold text-zinc-100">Your Documents</h2>
          <button
            onClick={fetchNotes}
            className="text-xs text-zinc-400 hover:text-zinc-200 transition-colors"
          >
            Refresh List
          </button>
        </div>

        {loading ? (
          <div className="text-center py-8 text-zinc-500 text-sm">Loading notes...</div>
        ) : error ? (
          <div className="text-center py-8 text-rose-400 text-sm">{error}</div>
        ) : notes.length === 0 ? (
          <div className="text-center py-8 text-zinc-500 text-sm">
            No notes uploaded yet. Upload your first .md or .txt file above.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-zinc-300">
              <thead className="text-xs text-zinc-500 uppercase border-b border-zinc-800 bg-zinc-950/40">
                <tr>
                  <th className="py-2.5 px-3">Name</th>
                  <th className="py-2.5 px-3">Size</th>
                  <th className="py-2.5 px-3">Status</th>
                  <th className="py-2.5 px-3">Date</th>
                  <th className="py-2.5 px-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {(notes || []).map((note) => (
                  <tr key={note.id} className="hover:bg-zinc-800/40 transition-colors">
                    <td className="py-3 px-3 font-medium text-zinc-200 flex items-center gap-2">
                      <span>📄</span>
                      <button
                        onClick={() => handleViewNote(note.id)}
                        className="hover:text-indigo-400 text-left cursor-pointer underline-offset-2 hover:underline"
                      >
                        {note.displayName}
                      </button>
                    </td>
                    <td className="py-3 px-3 text-zinc-400 text-xs">
                      {(note.sizeBytes / 1024).toFixed(1)} KB
                    </td>
                    <td className="py-3 px-3">{getStatusBadge(note.status)}</td>
                    <td className="py-3 px-3 text-zinc-400 text-xs">
                      {note.createdAt ? new Date(note.createdAt).toLocaleDateString() : "-"}
                    </td>
                    <td className="py-3 px-3 text-right space-x-2">
                      <button
                        onClick={() => handleViewNote(note.id)}
                        className="text-xs text-zinc-400 hover:text-zinc-200 cursor-pointer"
                      >
                        View
                      </button>
                      {note.status === "FAILED" && (
                        <button
                          onClick={() => handleReindex(note.id)}
                          disabled={reindexingId === note.id}
                          className="text-xs text-amber-400 hover:text-amber-300 disabled:opacity-50 cursor-pointer"
                        >
                          {reindexingId === note.id ? "Reindexing..." : "Reindex"}
                        </button>
                      )}
                      <button
                        onClick={() => handleDeleteNote(note.id, note.displayName)}
                        className="text-xs text-rose-400 hover:text-rose-300 cursor-pointer"
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Note Content Viewer Modal */}
      {viewingNote && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
          <div className="bg-zinc-900 border border-zinc-700 rounded-xl max-w-3xl w-full max-h-[85vh] flex flex-col shadow-2xl">
            <div className="p-4 border-b border-zinc-800 flex items-center justify-between">
              <div>
                <h3 className="text-base font-semibold text-zinc-100">{viewingNote.displayName}</h3>
                <div className="flex items-center gap-2 text-xs text-zinc-400 mt-1">
                  <span>{(viewingNote.sizeBytes / 1024).toFixed(1)} KB</span>
                  <span>•</span>
                  {getStatusBadge(viewingNote.status)}
                </div>
              </div>
              <button
                onClick={() => setViewingNote(null)}
                className="text-zinc-400 hover:text-zinc-100 text-lg px-2 py-1 rounded"
              >
                ✕
              </button>
            </div>
            <div className="p-4 overflow-y-auto flex-1 font-mono text-xs bg-zinc-950 text-zinc-300 whitespace-pre-wrap selection:bg-indigo-500 selection:text-white">
              {viewingNote.content}
            </div>
            <div className="p-3 border-t border-zinc-800 text-right">
              <button
                onClick={() => setViewingNote(null)}
                className="px-4 py-1.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 text-sm rounded-lg"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

