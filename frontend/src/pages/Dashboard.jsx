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

// Official Codeforces rating ranges:
//  Newbie               ≤ 1199
//  Pupil            1200–1399
//  Specialist       1400–1599
//  Expert           1600–1899
//  Candidate Master 1900–2099
//  Master           2100–2299
//  International Master 2300–2399
//  Grandmaster      2400–2599
//  International Grandmaster 2600–2999
//  Legendary Grandmaster    ≥ 3000
function getCfRatingStyle(rating) {
  if (rating == null) {
    return {
      text: "text-slate-400",
      badge: "bg-slate-800/70 border-slate-700 text-slate-400",
      label: "Unrated",
    };
  }
  if (rating <= 1199) {
    return {
      text: "text-slate-300",
      badge: "bg-slate-500/15 border-slate-500/30 text-slate-300",
      label: "Newbie",
    };
  }
  if (rating <= 1399) {
    return {
      text: "text-emerald-400",
      badge: "bg-emerald-500/15 border-emerald-500/30 text-emerald-400",
      label: "Pupil",
    };
  }
  if (rating <= 1599) {
    return {
      text: "text-cyan-400",
      badge: "bg-cyan-500/15 border-cyan-500/30 text-cyan-400",
      label: "Specialist",
    };
  }
  if (rating <= 1899) {
    return {
      text: "text-blue-400",
      badge: "bg-blue-500/15 border-blue-500/30 text-blue-400",
      label: "Expert",
    };
  }
  if (rating <= 2099) {
    return {
      text: "text-purple-400",
      badge: "bg-purple-500/15 border-purple-500/30 text-purple-400",
      label: "Candidate Master",
    };
  }
  if (rating <= 2299) {
    return {
      text: "text-orange-400",
      badge: "bg-orange-500/15 border-orange-500/30 text-orange-400",
      label: "Master",
    };
  }
  if (rating <= 2399) {
    return {
      text: "text-amber-400",
      badge: "bg-amber-500/15 border-amber-500/30 text-amber-400",
      label: "International Master",
    };
  }
  if (rating <= 2599) {
    return {
      text: "text-red-400",
      badge: "bg-red-500/15 border-red-500/30 text-red-400",
      label: "Grandmaster",
    };
  }
  if (rating <= 2999) {
    return {
      text: "text-rose-400",
      badge: "bg-rose-500/15 border-rose-500/30 text-rose-400",
      label: "International Grandmaster",
    };
  }
  // ≥ 3000
  return {
    text: "text-red-500",
    badge: "bg-red-500/20 border-red-500/40 text-red-500",
    label: "Legendary Grandmaster",
  };
}

export function Dashboard() {
  const { user, refreshUser } = useAuth();

  // Handle & Sync states
  const [handleInput, setHandleInput] = useState(user?.cfHandle || "");
  const [handleSaving, setHandleSaving] = useState(false);
  const [handleMsg, setHandleMsg] = useState(null); // { type: 'success' | 'error', text }
  const [isChangingHandle, setIsChangingHandle] = useState(false);
  const [disconnecting, setDisconnecting] = useState(false);

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
    const newHandle = handleInput.trim();
    if (!newHandle) return;

    if (user?.cfHandle && user.cfHandle.toLowerCase() !== newHandle.toLowerCase()) {
      const confirmChange = window.confirm(
        `Are you sure you want to change your handle from @${user.cfHandle} to @${newHandle}?\n\n` +
        `This will CLEAR all previously synced submissions, recommendations, and analytics, and sync fresh data for @${newHandle}.`
      );
      if (!confirmChange) return;
    }

    setHandleSaving(true);
    setHandleMsg(null);
    try {
      await apiClient("/api/me/cf-handle", {
        method: "PUT",
        body: JSON.stringify({ handle: newHandle }),
      });
      await refreshUser();
      setIsChangingHandle(false);
      setHandleMsg({
        type: "success",
        text: `Handle @${newHandle} linked & synced successfully!`,
      });
      await loadAnalytics();
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

  // Disconnect handle
  const handleDisconnectHandle = async () => {
    const confirmDisconnect = window.confirm(
      `Are you sure you want to disconnect @${user?.cfHandle}?\n\n` +
      `This will remove all synced submissions, analytics, and recommendations from your account.`
    );
    if (!confirmDisconnect) return;

    setDisconnecting(true);
    setHandleMsg(null);
    try {
      await apiClient("/api/me/cf-handle", { method: "DELETE" });
      await refreshUser();
      setHandleInput("");
      setIsChangingHandle(false);
      setWeakTags([]);
      setRatingAccuracy([]);
      setTrendData([]);
      setHandleMsg({
        type: "success",
        text: "Codeforces handle disconnected and data cleared.",
      });
    } catch (err) {
      setHandleMsg({
        type: "error",
        text: err.message || "Failed to disconnect handle",
      });
    } finally {
      setDisconnecting(false);
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

        {/* Codeforces Handle Management */}
        <div className="mt-6 pt-6 border-t border-slate-800/80">
          {user?.cfHandle && !isChangingHandle ? (
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              {(() => {
                // If cfRating is null but cfRank is known (e.g. CF doesn't return rating for 0-rating Newbies),
                // derive a representative rating so the style/color still matches the actual rank.
                // Fallback ratings: midpoint of each official CF band
                // Used when CF API omits the rating field (e.g. some 0-contest Newbie accounts)
                const RANK_FALLBACK_RATING = {
                  newbie: 800,                   // ≤ 1199
                  pupil: 1300,                   // 1200–1399
                  specialist: 1500,              // 1400–1599
                  expert: 1750,                  // 1600–1899
                  "candidate master": 2000,      // 1900–2099
                  master: 2200,                  // 2100–2299
                  "international master": 2350,  // 2300–2399
                  grandmaster: 2500,             // 2400–2599
                  "international grandmaster": 2800, // 2600–2999
                  "legendary grandmaster": 3200, // ≥ 3000
                };
                const ratingForStyle = user.cfRating != null
                  ? user.cfRating
                  : (user.cfRank ? RANK_FALLBACK_RATING[user.cfRank.toLowerCase()] ?? null : null);
                const cfStyle = getCfRatingStyle(ratingForStyle);
                const rankLabel = user.cfRank
                  ? user.cfRank.replace(/\b\w/g, (c) => c.toUpperCase())
                  : cfStyle.label;

                // isKnownRank: true if we have a rank from CF even when rating is absent
                const isKnownRank = user.cfRank != null && user.cfRank.trim() !== "";
                const showRating = user.cfRating != null;

                return (
                  <div className="flex flex-wrap items-center gap-2.5">
                    <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                      Linked Codeforces:
                    </span>
                    <a
                      href={`https://codeforces.com/profile/${user.cfHandle}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-lg border text-sm font-mono font-bold hover:opacity-85 transition ${cfStyle.badge}`}
                    >
                      <span>@{user.cfHandle}</span>
                      <span className="text-xs">↗</span>
                    </a>
                    <span
                      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg border text-xs font-semibold ${cfStyle.badge}`}
                    >
                      {showRating ? (
                        <>
                          <span className="font-mono font-bold">{user.cfRating}</span>
                          <span className="opacity-60">·</span>
                          <span>{rankLabel}</span>
                        </>
                      ) : isKnownRank ? (
                        // Has a rank (e.g. "newbie") but CF didn't return a rating value
                        <span>{rankLabel}</span>
                      ) : (
                        <span>Unrated</span>
                      )}
                    </span>
                  </div>
                );
              })()}

              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => {
                    setHandleInput(user.cfHandle);
                    setIsChangingHandle(true);
                    setHandleMsg(null);
                  }}
                  className="px-3 py-1.5 text-xs font-medium text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-lg border border-slate-700 transition"
                >
                  Change Handle
                </button>
                <button
                  type="button"
                  onClick={handleDisconnectHandle}
                  disabled={disconnecting}
                  className="px-3 py-1.5 text-xs font-medium text-rose-400 hover:text-rose-300 bg-rose-500/10 hover:bg-rose-500/20 rounded-lg border border-rose-500/20 transition disabled:opacity-50"
                >
                  {disconnecting ? "Disconnecting..." : "Disconnect"}
                </button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleSaveHandle} className="space-y-3">
              {isChangingHandle && (
                <div className="p-2.5 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-300 text-xs flex items-center gap-2">
                  <span>⚠️</span>
                  <span>
                    Warning: Switching handle from <strong>@{user?.cfHandle}</strong> will clear all past submission records and calculate fresh analytics for the new handle.
                  </span>
                </div>
              )}

              <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
                <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                  {isChangingHandle ? "New Handle:" : "Codeforces Handle:"}
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
                    className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white rounded-lg text-sm font-semibold shadow-sm transition"
                  >
                    {handleSaving ? "Saving & Syncing..." : "Save & Sync"}
                  </button>
                  {isChangingHandle && (
                    <button
                      type="button"
                      onClick={() => {
                        setIsChangingHandle(false);
                        setHandleInput(user?.cfHandle || "");
                        setHandleMsg(null);
                      }}
                      className="px-3 py-1.5 text-xs font-medium text-slate-400 hover:text-white bg-slate-800 rounded-lg transition"
                    >
                      Cancel
                    </button>
                  )}
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
              </div>
            </form>
          )}

          {handleMsg && !isChangingHandle && user?.cfHandle && (
            <div className="mt-2 text-xs text-emerald-400">
              {handleMsg.text}
            </div>
          )}
        </div>
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

