import React, { useState, useEffect } from "react";
import { useSearchParams, Link } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import { apiClient } from "../api/client";
import { LogMistakeModal } from "../components/LogMistakeModal";

export default function MentorChat() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialProblemId = searchParams.get("problemId") || "";

  const [recommendations, setRecommendations] = useState([]);
  const [struggles, setStruggles] = useState([]);
  const [selectedProblemId, setSelectedProblemId] = useState(initialProblemId);
  const [loadingRecs, setLoadingRecs] = useState(true);

  const [hints, setHints] = useState([]);
  const [isExhausted, setIsExhausted] = useState(false);
  const [loadingHint, setLoadingHint] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [error, setError] = useState(null);
  const [infoMessage, setInfoMessage] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);

  // Load today's recommendations and struggled problems for problem picker
  useEffect(() => {
    async function loadProblems() {
      setLoadingRecs(true);
      try {
        const [recsData, strugglesData] = await Promise.all([
          apiClient("/api/me/recommendations/today").catch(() => []),
          apiClient("/api/me/mentor/struggles").catch(() => []),
        ]);

        const recList = Array.isArray(recsData) ? recsData : [];
        const strList = Array.isArray(strugglesData) ? strugglesData : [];

        setRecommendations(recList);
        setStruggles(strList);

        // If no problem selected yet, select first available from recommendations or struggles
        if (!initialProblemId) {
          if (recList.length > 0) {
            const first = recList.find((r) => r.problemId || r.id);
            if (first) setSelectedProblemId(first.problemId || first.id);
          } else if (strList.length > 0) {
            setSelectedProblemId(strList[0].problemId);
          }
        }
      } catch (err) {
        setError(err.message || "Failed to load problems.");
      } finally {
        setLoadingRecs(false);
      }
    }
    loadProblems();
  }, [initialProblemId]);

  // When problem selection changes, update query param and clear local hints
  const handleProblemChange = (problemId) => {
    setSelectedProblemId(problemId);
    setSearchParams(problemId ? { problemId } : {});
    setHints([]);
    setIsExhausted(false);
    setError(null);
    setInfoMessage(null);
  };

  // Request next hint
  const handleGetHint = async () => {
    if (!selectedProblemId) return;

    setLoadingHint(true);
    setError(null);
    setInfoMessage(null);

    try {
      const response = await apiClient("/api/me/mentor/hint", {
        method: "POST",
        body: JSON.stringify({ problemId: selectedProblemId }),
      });

      if (response.exhausted && hints.length > 0 && response.level === hints[hints.length - 1].level) {
        setIsExhausted(true);
        setInfoMessage("All hints have been exhausted for this problem. You are at Level 3!");
      } else {
        setHints((prev) => [...prev, response]);
        if (response.exhausted) {
          setIsExhausted(true);
        }
      }
    } catch (err) {
      if (err.status === 502) {
        setError("AI Mentor service is temporarily unavailable. Please try again in a few moments.");
      } else {
        setError(err.message || "Failed to fetch hint.");
      }
    } finally {
      setLoadingHint(false);
    }
  };

  // Reset hints (Start over)
  const handleReset = async () => {
    if (!selectedProblemId) return;
    if (!window.confirm("Are you sure you want to start over? This will reset your hint progress for this problem to Level 1.")) {
      return;
    }

    setResetting(true);
    setError(null);
    setInfoMessage(null);

    try {
      await apiClient(`/api/me/mentor/hint?problemId=${selectedProblemId}`, {
        method: "DELETE",
      });
      setHints([]);
      setIsExhausted(false);
      setInfoMessage("Hint progress reset! You can start over with Level 1.");
    } catch (err) {
      setError(err.message || "Failed to reset hints.");
    } finally {
      setResetting(false);
    }
  };

  const getLevelBadge = (level) => {
    switch (level) {
      case 1:
        return (
          <span className="px-2.5 py-1 rounded-md text-xs font-bold uppercase tracking-wider bg-blue-950 text-blue-400 border border-blue-800">
            Level 1 • Guiding Question
          </span>
        );
      case 2:
        return (
          <span className="px-2.5 py-1 rounded-md text-xs font-bold uppercase tracking-wider bg-amber-950 text-amber-400 border border-amber-800">
            Level 2 • Pattern & Observation
          </span>
        );
      case 3:
        return (
          <span className="px-2.5 py-1 rounded-md text-xs font-bold uppercase tracking-wider bg-purple-950 text-purple-400 border border-purple-800">
            Level 3 • Strategic Approach
          </span>
        );
      default:
        return (
          <span className="px-2.5 py-1 rounded-md text-xs font-bold uppercase tracking-wider bg-zinc-800 text-zinc-300">
            Level {level}
          </span>
        );
    }
  };

  const allAvailableProblems = [
    ...recommendations.map((r) => ({
      id: r.problemId || r.id,
      problemName: r.problemName,
      rating: r.rating,
      url: r.url,
      tags: r.tags || [],
      type: "REC",
    })),
    ...struggles.map((s) => ({
      id: s.problemId,
      problemName: s.problemName,
      rating: s.rating,
      url: s.url,
      tags: s.tags || [],
      failedAttempts: s.failedAttempts,
      type: "STRUGGLE",
    })),
  ];

  const currentProblem = allAvailableProblems.find((p) => p.id === selectedProblemId);

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-zinc-100">Socratic Hint Ladder</h1>
        <p className="text-zinc-400 text-sm mt-1">
          Get calibrated Socratic hints without spoiling the solution or seeing full code. Discuss today's recommendations or revisit problems where you struggled on Codeforces.
        </p>
      </div>

      {/* Problem Picker */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 shadow-sm">
        <label className="block text-xs font-semibold text-zinc-400 uppercase tracking-wider mb-2">
          Select Problem (Daily Picks or Past CF Struggles)
        </label>
        {loadingRecs ? (
          <div className="text-sm text-zinc-500">Loading problems...</div>
        ) : allAvailableProblems.length === 0 ? (
          <div className="text-sm text-zinc-400">
            No problems found. Sync your Codeforces handle on the{" "}
            <Link to="/dashboard" className="text-indigo-400 hover:underline">
              Dashboard
            </Link>{" "}
            or visit{" "}
            <Link to="/recommendations" className="text-indigo-400 hover:underline">
              Recommendations
            </Link>{" "}
            to generate picks.
          </div>
        ) : (
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3">
            <select
              value={selectedProblemId}
              onChange={(e) => handleProblemChange(e.target.value)}
              className="flex-1 bg-zinc-950 border border-zinc-700 rounded-lg px-3 py-2 text-sm text-zinc-200 focus:outline-none focus:border-indigo-500"
            >
              <option value="" disabled>
                -- Choose a problem --
              </option>

              {recommendations.length > 0 && (
                <optgroup label="🎯 Today's Daily Recommendations">
                  {recommendations.map((rec) => {
                    const id = rec.problemId || rec.id;
                    return (
                      <option key={id} value={id}>
                        {rec.problemName} {rec.rating ? `(Rating: ${rec.rating})` : ""}
                      </option>
                    );
                  })}
                </optgroup>
              )}

              {struggles.length > 0 && (
                <optgroup label="⚠️ My Codeforces Struggles (Unsolved with Failed Attempts)">
                  {struggles.map((str) => (
                    <option key={str.problemId} value={str.problemId}>
                      {str.problemName} {str.rating ? `(Rating: ${str.rating})` : ""} — {str.failedAttempts} failed attempts
                    </option>
                  ))}
                </optgroup>
              )}
            </select>

            {currentProblem?.url && (
              <a
                href={currentProblem.url}
                target="_blank"
                rel="noopener noreferrer"
                className="px-3 py-2 text-xs font-semibold bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded-lg border border-zinc-700 text-center transition-colors"
              >
                Open on Codeforces ↗
              </a>
            )}
          </div>
        )}

        {currentProblem?.tags && currentProblem.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mt-3">
            {currentProblem.tags.map((tag) => (
              <span
                key={tag}
                className="px-2 py-0.5 rounded text-xs bg-zinc-800 text-zinc-400 border border-zinc-700/60"
              >
                {tag}
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Messages */}
      {error && (
        <div className="p-3.5 rounded-xl bg-rose-950/60 border border-rose-800 text-rose-300 text-sm">
          {error}
        </div>
      )}

      {infoMessage && (
        <div className="p-3.5 rounded-xl bg-indigo-950/60 border border-indigo-800 text-indigo-300 text-sm">
          {infoMessage}
        </div>
      )}

      {/* Hints List */}
      <div className="space-y-4">
        {hints.length === 0 ? (
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-8 text-center text-zinc-500 text-sm">
            {selectedProblemId
              ? 'No hints requested yet for this problem. Click "Get Hint" below to begin with Level 1.'
              : "Please select a problem above to start receiving Socratic hints."}
          </div>
        ) : (
          hints.map((h, idx) => (
            <div
              key={idx}
              className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 shadow-sm space-y-4"
            >
              <div className="flex items-center justify-between">
                {getLevelBadge(h.level)}
                <span className="text-xs text-zinc-500">Hint #{idx + 1}</span>
              </div>

              {/* Hint Content */}
              <div className="text-zinc-200 text-sm leading-relaxed prose prose-invert max-w-none">
                <ReactMarkdown>{h.hint}</ReactMarkdown>
              </div>

              {/* Follow-up question if any */}
              {h.followUp && (
                <div className="p-3 rounded-lg bg-zinc-950 border border-zinc-800/80">
                  <div className="text-xs font-semibold text-indigo-400 uppercase tracking-wider mb-1">
                    Reflective Question
                  </div>
                  <p className="text-xs text-zinc-300 italic">{h.followUp}</p>
                </div>
              )}

              {/* Citations if any */}
              {h.citations && h.citations.length > 0 && (
                <div className="pt-2 border-t border-zinc-800 text-xs text-zinc-400">
                  <span className="font-semibold text-zinc-400">Referenced from your notes:</span>
                  <div className="mt-1 space-y-1">
                    {h.citations.map((c, cIdx) => (
                      <div key={cIdx} className="text-zinc-400 font-mono text-[11px]">
                        • {c.displayName} (chunk #{c.chunkIndex})
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>

      {/* Exhausted Banner */}
      {isExhausted && (
        <div className="p-4 rounded-xl bg-amber-950/50 border border-amber-800/80 text-amber-200 text-sm flex items-center justify-between">
          <div>
            <div className="font-semibold">Hint Ladder Exhausted</div>
            <p className="text-xs text-amber-300/80 mt-0.5">
              You've received all 3 levels of hints for this problem. Give it your best shot!
            </p>
          </div>
          <button
            onClick={handleReset}
            disabled={resetting}
            className="px-3 py-1.5 bg-amber-900 hover:bg-amber-800 text-amber-100 rounded-lg text-xs font-semibold cursor-pointer"
          >
            {resetting ? "Resetting..." : "Start Over"}
          </button>
        </div>
      )}

      {/* Controls */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 pt-2">
        <div className="flex items-center gap-2">
          <button
            onClick={handleReset}
            disabled={resetting || hints.length === 0 || !selectedProblemId}
            className="px-4 py-2 bg-zinc-800 hover:bg-zinc-700 disabled:opacity-40 text-zinc-300 text-sm font-medium rounded-lg transition-colors cursor-pointer"
          >
            {resetting ? "Resetting..." : "Start over"}
          </button>

          {currentProblem && (
            <button
              onClick={() => setModalOpen(true)}
              className="px-3.5 py-2 bg-indigo-600/10 hover:bg-indigo-600/20 text-indigo-400 border border-indigo-500/20 text-sm font-semibold rounded-lg transition-colors cursor-pointer flex items-center gap-1.5"
            >
              <span>📝</span>
              <span>Record Takeaway</span>
            </button>
          )}
        </div>

        <button
          onClick={handleGetHint}
          disabled={loadingHint || isExhausted || !selectedProblemId}
          className="px-5 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-40 text-white text-sm font-semibold rounded-lg shadow-sm transition-colors cursor-pointer"
        >
          {loadingHint
            ? "Generating Hint..."
            : hints.length === 0
            ? "Get First Hint (Level 1)"
            : "Bigger hint"}
        </button>
      </div>

      {/* RAG Mistake / Takeaway Modal */}
      {currentProblem && (
        <LogMistakeModal
          isOpen={modalOpen}
          onClose={() => setModalOpen(false)}
          initialProblemTitle={currentProblem.problemName || "Practice Problem"}
          initialTags={currentProblem.tags || []}
        />
      )}
    </div>
  );
}

