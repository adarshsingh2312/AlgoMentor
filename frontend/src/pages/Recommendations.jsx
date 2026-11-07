import React, { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { apiClient } from "../api/client";
import { LogMistakeModal } from "../components/LogMistakeModal";

export function Recommendations() {
  const [recommendations, setRecommendations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [skippingId, setSkippingId] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [activeModalData, setActiveModalData] = useState({ title: "", tags: [] });
  const navigate = useNavigate();

  const loadTodayRecommendations = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiClient("/api/me/recommendations/today");
      setRecommendations(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || "Failed to load today's recommendations");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTodayRecommendations();
  }, [loadTodayRecommendations]);

  const handleSkip = async (id) => {
    setSkippingId(id);
    try {
      const updated = await apiClient(`/api/me/recommendations/${id}/status`, {
        method: "PATCH",
        body: JSON.stringify({ status: "SKIPPED" }),
      });
      setRecommendations((prev) =>
        prev.map((r) => (r.id === id ? { ...r, status: updated.status } : r))
      );
    } catch (err) {
      alert(err.message || "Failed to skip recommendation");
    } finally {
      setSkippingId(null);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case "SOLVED":
        return (
          <span className="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
            SOLVED
          </span>
        );
      case "SKIPPED":
        return (
          <span className="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-slate-700/50 text-slate-400 border border-slate-600/30">
            SKIPPED
          </span>
        );
      default:
        return (
          <span className="px-2.5 py-0.5 text-xs font-semibold rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
            PRACTICE
          </span>
        );
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">
            Daily Curated Practice
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Personalized recommendations prioritized by your weakest topics and target rating window.
          </p>
        </div>
        <div className="flex items-center gap-2 self-start sm:self-auto">
          <button
            onClick={() => {
              setActiveModalData({ title: "", tags: [] });
              setModalOpen(true);
            }}
            className="px-4 py-2 bg-indigo-600/10 hover:bg-indigo-600/20 text-indigo-400 border border-indigo-500/20 text-sm font-semibold rounded-xl transition flex items-center gap-1.5"
          >
            <span>📝</span>
            <span>Log Takeaway</span>
          </button>
          <button
            onClick={loadTodayRecommendations}
            disabled={loading}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-200 text-sm font-medium rounded-xl border border-slate-700 transition"
          >
            Refresh
          </button>
        </div>
      </div>

      {/* Content */}
      {loading ? (
        <div className="flex items-center justify-center p-16">
          <div className="w-8 h-8 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : error ? (
        <div className="p-8 bg-slate-900 border border-slate-800 rounded-2xl text-center text-slate-400">
          <p>{error}</p>
          <button
            onClick={loadTodayRecommendations}
            className="mt-4 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-xl transition"
          >
            Retry
          </button>
        </div>
      ) : recommendations.length === 0 ? (
        <div className="p-12 bg-slate-900 border border-slate-800 rounded-2xl text-center text-slate-400">
          <p className="text-base font-semibold text-white mb-1">
            No recommendations generated yet
          </p>
          <p className="text-sm">
            Sync your Codeforces handle on the Dashboard to calculate your skill profile!
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {(recommendations || []).map((rec) => (
            <div
              key={rec.id}
              className="bg-slate-900 border border-slate-800 rounded-2xl p-6 flex flex-col justify-between hover:border-slate-700 transition shadow-sm"
            >
              <div>
                {/* Top status & rating */}
                <div className="flex items-center justify-between mb-3">
                  {getStatusBadge(rec.status)}
                  {rec.rating ? (
                    <span className="text-xs font-mono font-bold px-2 py-0.5 rounded bg-slate-800 text-amber-300 border border-slate-700">
                      ★ {rec.rating}
                    </span>
                  ) : (
                    <span className="text-xs font-mono text-slate-500">Unrated</span>
                  )}
                </div>

                {/* Problem Name */}
                <h3 className="text-lg font-bold text-white tracking-tight mb-2 line-clamp-2">
                  {rec.problemName}
                </h3>

                {/* Algorithmic Reason */}
                <p className="text-xs text-indigo-300/90 font-medium mb-4 flex items-start gap-1.5">
                  <span className="text-indigo-400 shrink-0">💡</span>
                  <span>{rec.reason}</span>
                </p>

                {/* Tag Chips */}
                <div className="flex flex-wrap gap-1.5 mb-6">
                  {rec.tags?.map((tag) => (
                    <span
                      key={tag}
                      className="px-2 py-0.5 text-[11px] font-medium bg-slate-800/80 text-slate-300 rounded-md border border-slate-700/60"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              </div>

              {/* Action Buttons */}
              <div className="pt-4 border-t border-slate-800/80 flex items-center justify-between gap-2">
                <a
                  href={rec.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-300 hover:text-white px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 transition"
                >
                  <span>Codeforces</span>
                  <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14" />
                  </svg>
                </a>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => {
                      setActiveModalData({
                        title: rec.problemName,
                        tags: rec.tags || [],
                      });
                      setModalOpen(true);
                    }}
                    title="Log your mistake or key takeaway into AI memory"
                    className="p-2 text-xs font-medium text-slate-400 hover:text-indigo-400 hover:bg-slate-800 rounded-lg transition"
                  >
                    📝
                  </button>

                  {rec.status !== "SKIPPED" && rec.status !== "SOLVED" && (
                    <button
                      onClick={() => handleSkip(rec.id)}
                      disabled={skippingId === rec.id}
                      className="px-2.5 py-2 text-xs font-medium text-slate-400 hover:text-rose-400 transition"
                    >
                      {skippingId === rec.id ? "Skipping..." : "Skip"}
                    </button>
                  )}

                  <button
                    onClick={() => navigate(`/mentor?problemId=${rec.problemId || rec.id}`)}
                    className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-500 rounded-lg shadow-sm transition"
                  >
                    <span>Hints</span>
                    <span>&rarr;</span>
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* RAG Memory Log Modal */}
      <LogMistakeModal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        initialProblemTitle={activeModalData.title}
        initialTags={activeModalData.tags}
      />
    </div>
  );
}
