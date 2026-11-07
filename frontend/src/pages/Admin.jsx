import React, { useState } from "react";
import { apiClient } from "../api/client";

export default function Admin() {
  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState(null);
  const [syncError, setSyncError] = useState(null);

  const [runningDigest, setRunningDigest] = useState(false);
  const [digestResult, setDigestResult] = useState(null);
  const [digestError, setDigestError] = useState(null);

  const handleSyncProblemset = async () => {
    setSyncing(true);
    setSyncError(null);
    setSyncResult(null);

    try {
      const data = await apiClient("/api/admin/sync/problemset", {
        method: "POST",
      });
      setSyncResult(data);
    } catch (err) {
      setSyncError(err.message || "Failed to trigger problemset sync.");
    } finally {
      setSyncing(false);
    }
  };

  const handleRunDigest = async () => {
    setRunningDigest(true);
    setDigestError(null);
    setDigestResult(null);

    try {
      const data = await apiClient("/api/admin/digest/run", {
        method: "POST",
      });
      setDigestResult(data);
    } catch (err) {
      setDigestError(err.message || "Failed to trigger daily digest run.");
    } finally {
      setRunningDigest(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <div className="inline-block px-2 py-0.5 rounded text-xs font-bold uppercase tracking-wider bg-rose-950 text-rose-400 border border-rose-800 mb-2">
          Admin Portal
        </div>
        <h1 className="text-2xl font-bold text-zinc-100">System Operations</h1>
        <p className="text-zinc-400 text-sm mt-1">
          Trigger system-wide batch tasks: problemset catalog synchronization and daily email digest dispatcher.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Problemset Sync Card */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 shadow-sm flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="text-xl">🔄</span>
              <h2 className="text-base font-semibold text-zinc-100">Codeforces Problemset Sync</h2>
            </div>
            <p className="text-xs text-zinc-400 mb-4 leading-relaxed">
              Fetches the latest catalog of competitive programming problems and problem tags directly from the Codeforces official API and upserts into the local catalog.
            </p>
          </div>

          <div className="space-y-3">
            <button
              onClick={handleSyncProblemset}
              disabled={syncing}
              className="w-full py-2 px-4 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors cursor-pointer"
            >
              {syncing ? "Syncing Problemset..." : "Sync Problemset Now"}
            </button>

            {syncError && (
              <div className="p-3 rounded-lg bg-rose-950/60 border border-rose-800 text-rose-300 text-xs">
                {syncError}
              </div>
            )}

            {syncResult && (
              <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 text-xs space-y-1.5">
                <div className="font-semibold text-emerald-400">Sync Completed Successfully:</div>
                <div className="text-zinc-300 flex justify-between">
                  <span>Problems Inserted:</span>
                  <span className="font-mono font-semibold">{syncResult.problemsInserted}</span>
                </div>
                <div className="text-zinc-300 flex justify-between">
                  <span>Problems Updated:</span>
                  <span className="font-mono font-semibold">{syncResult.problemsUpdated}</span>
                </div>
                <div className="text-zinc-300 flex justify-between">
                  <span>Submissions Inserted:</span>
                  <span className="font-mono font-semibold">{syncResult.submissionsInserted}</span>
                </div>
                <div className="text-zinc-300 flex justify-between">
                  <span>Submissions Skipped:</span>
                  <span className="font-mono font-semibold">{syncResult.submissionsSkipped}</span>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Digest Run Card */}
        <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 shadow-sm flex flex-col justify-between">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="text-xl">📬</span>
              <h2 className="text-base font-semibold text-zinc-100">Trigger Daily Digest Dispatch</h2>
            </div>
            <p className="text-xs text-zinc-400 mb-4 leading-relaxed">
              Generates customized daily problem recommendations and sends morning email digests to all registered users with verified Codeforces handles via SMTP.
            </p>
          </div>

          <div className="space-y-3">
            <button
              onClick={handleRunDigest}
              disabled={runningDigest}
              className="w-full py-2 px-4 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors cursor-pointer"
            >
              {runningDigest ? "Running Digest..." : "Run Digest Batch"}
            </button>

            {digestError && (
              <div className="p-3 rounded-lg bg-rose-950/60 border border-rose-800 text-rose-300 text-xs">
                {digestError}
              </div>
            )}

            {digestResult && (
              <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800 text-xs space-y-1.5">
                <div className="font-semibold text-emerald-400">Digest Run Summary:</div>
                <div className="text-zinc-300 flex justify-between">
                  <span>Emails Sent:</span>
                  <span className="font-mono font-semibold text-emerald-400">{digestResult.sent}</span>
                </div>
                <div className="text-zinc-300 flex justify-between">
                  <span>Users Skipped:</span>
                  <span className="font-mono font-semibold text-zinc-400">{digestResult.skipped}</span>
                </div>
                <div className="text-zinc-300 flex justify-between">
                  <span>Failed Dispatches:</span>
                  <span className="font-mono font-semibold text-rose-400">{digestResult.failed}</span>
                </div>
                <div className="text-zinc-300 flex justify-between">
                  <span>Duration:</span>
                  <span className="font-mono font-semibold text-zinc-400">{digestResult.durationMs} ms</span>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

