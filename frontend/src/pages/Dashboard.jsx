import React, { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from "recharts";
import { useAuth } from "../context/AuthContext";
import { apiClient } from "../api/client";

export function Dashboard() {
  const { user, refreshUser } = useAuth();

  // Handle & Sync states
  const [handleInput, setHandleInput] = useState(user?.cfHandle || "");
  const [handleSaving, setHandleSaving] = useState(false);
  const [handleMsg, setHandleMsg] = useState(null); // { type: 'success' | 'error', text }

  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState(null);
  const [syncError, setSyncError] = useState(null);

  // Analytics states
  const [weakTags, setWeakTags] = useState([]);
  const [ratingAccuracy, setRatingAccuracy] = useState([]);
  const [trendDays, setTrendDays] = useState(30);
  const [trendData, setTrendData] = useState([]);

  const [loadingAnalytics, setLoadingAnalytics] = useState(true);
  const [analyticsError, setAnalyticsError] = useState(null);

  // Load analytics
  const loadAnalytics = useCallback(async () => {
    setLoadingAnalytics(true);
    setAnalyticsError(null);
    try {
      const [tags, accuracy, trend] = await Promise.all([
        apiClient("/api/me/analytics/weak-tags?limit=5"),
        apiClient("/api/me/analytics/rating-accuracy"),
        apiClient(`/api/me/analytics/trend?days=${trendDays}`),
      ]);
      setWeakTags(Array.isArray(tags) ? tags : []);
      setRatingAccuracy(Array.isArray(accuracy) ? accuracy : []);
      setTrendData(Array.isArray(trend) ? trend : []);
    } catch (err) {
      setAnalyticsError(err.message || "Failed to load analytics");
    } finally {
      setLoadingAnalytics(false);
    }
  }, [trendDays]);

  useEffect(() => {
    loadAnalytics();
  }, [loadAnalytics]);

  // Update handle
  const handleSaveHandle = async (e) => {
    e.preventDefault();
    if (!handleInput.trim()) return;

    setHandleSaving(true);
    setHandleMsg(null);
    try {
      await apiClient("/api/me/cf-handle", {
        method: "PUT",
        body: JSON.stringify({ handle: handleInput.trim() }),
      });
      await refreshUser();
      setHandleMsg({
        type: "success",
        text: `Handle @${handleInput.trim()} linked successfully!`,
      });
    } catch (err) {
      if (err.status === 400) {
        setHandleMsg({
          type: "error",
          text: "Invalid Codeforces handle. Please check your spelling on codeforces.com.",
        });
      } else if (err.status === 502) {
        setHandleMsg({
          type: "error",
          text: "Codeforces API is currently unavailable or timing out. Please try again later.",
        });
      } else {
        setHandleMsg({
          type: "error",
          text: err.message || "Failed to update handle",
        });
      }
    } finally {
      setHandleSaving(false);
    }
  };

  // Sync submissions
  const handleSync = async () => {
    if (syncing) return; // Double-click protection
    setSyncing(true);
    setSyncResult(null);
    setSyncError(null);

    try {
      const res = await apiClient("/api/me/sync", { method: "POST" });
      setSyncResult(res);
      await refreshUser();
      await loadAnalytics();
    } catch (err) {
      setSyncError(
        err.status === 502
          ? "Codeforces sync timed out or service is down. Please try again."
          : err.message || "Sync failed"
      );
    } finally {
      setSyncing(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Top Header & Codeforces Sync Banner */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 sm:p-8 shadow-sm">
        <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-6">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">
              Welcome back, {user?.name || "Candidate"}!
            </h1>
            <p className="text-slate-400 text-sm mt-1">
              Track your weaknesses, practice daily curated problems, and consult your Socratic AI mentor.
            </p>
          </div>

          {/* Sync & Handle Controls */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
            {user?.cfHandle ? (
              <button
                onClick={handleSync}
                disabled={syncing}
                className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 disabled:bg-indigo-900/60 disabled:cursor-not-allowed text-white font-medium text-sm rounded-xl transition shadow-sm"
              >
                {syncing ? (
                  <>
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    <span>Syncing... (first sync takes ~20s)</span>
                  </>
                ) : (
                  <>
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                    </svg>
                    <span>Sync Submissions Now</span>
                  </>
                )}
              </button>
            ) : null}

            <Link
              to="/recommendations"
              className="inline-flex items-center justify-center gap-2 px-5 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-medium rounded-xl border border-slate-700 transition"
            >
              <span>Today's Problems</span>
              <span>&rarr;</span>
            </Link>
          </div>
        </div>

        {/* Sync notification message */}
        {syncResult && (
          <div className="mt-4 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-sm flex items-center justify-between">
            <span>
              Synced in {syncResult.elapsedMs}ms! New: {syncResult.submissionsInserted} | Skipped: {syncResult.submissionsSkipped}
            </span>
            <button onClick={() => setSyncResult(null)} className="text-emerald-400/80 hover:text-emerald-300">
              &times;
            </button>
          </div>
        )}

        {syncError && (
          <div className="mt-4 p-3 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 text-sm flex items-center justify-between">
            <span>{syncError}</span>
            <button onClick={() => setSyncError(null)} className="text-rose-400/80 hover:text-rose-300">
              &times;
            </button>
          </div>
        )}

        {/* Link Handle Form */}
        <form onSubmit={handleSaveHandle} className="mt-6 pt-6 border-t border-slate-800/80 flex flex-col sm:flex-row items-start sm:items-center gap-3">
          <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Codeforces Handle:
          </label>
          <div className="flex items-center gap-2 w-full sm:w-auto">
            <input
              type="text"
              value={handleInput}
              onChange={(e) => setHandleInput(e.target.value)}
              placeholder="e.g. tourist"
              className="bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 w-full sm:w-56"
            />
            <button
              type="submit"
              disabled={handleSaving || !handleInput.trim()}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-white rounded-lg text-sm font-medium border border-slate-700 transition"
            >
              {handleSaving ? "Saving..." : "Save"}
            </button>
          </div>
          {handleMsg && (
            <span
              className={`text-xs ${
                handleMsg.type === "success" ? "text-emerald-400" : "text-rose-400"
              }`}
            >
              {handleMsg.text}
            </span>
          )}
        </form>
      </div>

      {/* Analytics Section */}
      {loadingAnalytics ? (
        <div className="flex items-center justify-center p-12">
          <div className="w-8 h-8 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : analyticsError ? (
        <div className="p-6 bg-slate-900 border border-slate-800 rounded-2xl text-center text-slate-400">
          <p>{analyticsError}</p>
          <button onClick={loadAnalytics} className="mt-3 text-sm text-indigo-400 hover:underline">
            Retry loading analytics
          </button>
        </div>
      ) : (
        <>
          {/* Top Weak Tags Section */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-white flex items-center gap-2">
                <span>Identified Weak Tags</span>
                <span className="text-xs font-normal text-slate-400">
                  (&ge; 5 distinct problem attempts, lowest solve ratio first)
                </span>
              </h2>
            </div>

            {weakTags.length === 0 ? (
              <div className="p-8 bg-slate-900 border border-slate-800 rounded-2xl text-center text-slate-400 text-sm">
                No weak tags identified yet. Link your Codeforces handle and sync your submissions to discover topics requiring practice!
              </div>
            ) : (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
                {(weakTags || []).map((tag) => {
                  const accuracyPct = Math.round((tag.solved / (tag.attempted || 1)) * 100);
                  return (
                    <div
                      key={tag.tag}
                      className="bg-slate-900 border border-slate-800 p-4 rounded-xl flex flex-col justify-between hover:border-slate-700 transition"
                    >
                      <div>
                        <div className="flex items-center justify-between mb-2">
                          <span className="font-semibold text-white capitalize">{tag.tag}</span>
                          <span
                            className={`text-xs font-bold px-2 py-0.5 rounded ${
                              accuracyPct < 40
                                ? "bg-rose-500/10 text-rose-400 border border-rose-500/20"
                                : "bg-amber-500/10 text-amber-400 border border-amber-500/20"
                            }`}
                          >
                            {accuracyPct}% AC
                          </span>
                        </div>
                        <div className="text-xs text-slate-400 space-y-1">
                          <p>Solved: {tag.solved} / {tag.attempted} attempted</p>
                        </div>
                      </div>
                      <div className="w-full bg-slate-800 rounded-full h-1.5 mt-4 overflow-hidden">
                        <div
                          className={`h-1.5 rounded-full ${
                            accuracyPct < 40 ? "bg-rose-500" : "bg-amber-500"
                          }`}
                          style={{ width: `${Math.min(100, Math.max(5, accuracyPct))}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Charts Row: Solve Trend + Rating Accuracy */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
            {/* Solve Trend Chart */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 flex flex-col justify-between">
              <div className="flex items-center justify-between mb-6">
                <div>
                  <h3 className="text-base font-bold text-white">Solve Trend</h3>
                  <p className="text-xs text-slate-400">Distinct problems first solved per day</p>
                </div>
                <div className="flex items-center gap-1 bg-slate-950 p-1 rounded-lg border border-slate-800">
                  {[7, 30, 90].map((days) => (
                    <button
                      key={days}
                      onClick={() => setTrendDays(days)}
                      className={`px-2.5 py-1 text-xs font-medium rounded-md transition ${
                        trendDays === days
                          ? "bg-indigo-600 text-white"
                          : "text-slate-400 hover:text-white"
                      }`}
                    >
                      {days}d
                    </button>
                  ))}
                </div>
              </div>

              {trendData.length === 0 ? (
                <div className="h-64 flex items-center justify-center text-slate-500 text-sm">
                  No solve data in this period
                </div>
              ) : (
                <div className="h-64 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={trendData}>
                      <defs>
                        <linearGradient id="solveGradient" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#6366f1" stopOpacity={0.4} />
                          <stop offset="95%" stopColor="#6366f1" stopOpacity={0.0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                      <XAxis dataKey="date" stroke="#64748b" tick={{ fontSize: 10 }} />
                      <YAxis stroke="#64748b" allowDecimals={false} tick={{ fontSize: 10 }} />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#0f172a",
                          borderColor: "#334155",
                          borderRadius: "8px",
                          color: "#f8fafc",
                        }}
                      />
                      <Area
                        type="monotone"
                        dataKey="count"
                        stroke="#818cf8"
                        fillOpacity={1}
                        fill="url(#solveGradient)"
                        strokeWidth={2}
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              )}
            </div>

            {/* Rating Accuracy Chart */}
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 flex flex-col justify-between">
              <div className="mb-6">
                <h3 className="text-base font-bold text-white">Rating Accuracy Distribution</h3>
                <p className="text-xs text-slate-400">Solved vs. Attempted grouped in 100-rating increments</p>
              </div>

              {ratingAccuracy.length === 0 ? (
                <div className="h-64 flex items-center justify-center text-slate-500 text-sm">
                  No rated problem data available
                </div>
              ) : (
                <div className="h-64 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={ratingAccuracy}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                      <XAxis dataKey="bucket" stroke="#64748b" tick={{ fontSize: 10 }} />
                      <YAxis stroke="#64748b" allowDecimals={false} tick={{ fontSize: 10 }} />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#0f172a",
                          borderColor: "#334155",
                          borderRadius: "8px",
                          color: "#f8fafc",
                        }}
                      />
                      <Bar dataKey="attempted" name="Attempted" fill="#334155" radius={[4, 4, 0, 0]} />
                      <Bar dataKey="solved" name="Solved" fill="#10b981" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}

