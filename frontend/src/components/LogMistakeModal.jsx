import React, { useState } from "react";
import { apiClient } from "../api/client";

export function LogMistakeModal({ isOpen, onClose, initialProblemTitle = "", initialTags = [] }) {
  const [problemTitle, setProblemTitle] = useState(initialProblemTitle);
  const [tags, setTags] = useState(initialTags);
  const [tagInput, setTagInput] = useState("");
  const [mistakeText, setMistakeText] = useState("");
  const [keyTakeaway, setKeyTakeaway] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);

  if (!isOpen) return null;

  const handleAddTag = (e) => {
    if (e.key === "Enter" || e.key === ",") {
      e.preventDefault();
      const val = tagInput.trim().toLowerCase();
      if (val && !tags.includes(val)) {
        setTags([...tags, val]);
        setTagInput("");
      }
    }
  };

  const handleRemoveTag = (t) => {
    setTags(tags.filter((item) => item !== t));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!problemTitle.trim() || !mistakeText.trim()) {
      setError("Please provide both problem title and mistake description.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await apiClient("/api/me/notes/mistake", {
        method: "POST",
        body: JSON.stringify({
          problemTitle: problemTitle.trim(),
          tags,
          mistakeText: mistakeText.trim(),
          keyTakeaway: keyTakeaway.trim(),
        }),
      });

      setSuccess(true);
      setTimeout(() => {
        setSuccess(false);
        setMistakeText("");
        setKeyTakeaway("");
        onClose();
      }, 1600);
    } catch (err) {
      setError(err.message || "Failed to save mistake takeaway.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm animate-fade-in">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-lg p-6 shadow-2xl relative">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-400 hover:text-white text-lg font-bold w-8 h-8 flex items-center justify-center rounded-lg hover:bg-slate-800 transition"
        >
          &times;
        </button>

        <div className="flex items-center gap-3 mb-4">
          <div className="w-10 h-10 rounded-xl bg-indigo-600/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center text-lg">
            🧠
          </div>
          <div>
            <h2 className="text-lg font-bold text-white tracking-tight">
              Log Problem Mistake & Takeaway
            </h2>
            <p className="text-xs text-slate-400">
              Saved directly into your RAG memory for the AI Mentor to recall.
            </p>
          </div>
        </div>

        {success ? (
          <div className="p-8 text-center space-y-3">
            <div className="w-12 h-12 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-full flex items-center justify-center mx-auto text-xl font-bold">
              ✓
            </div>
            <h3 className="text-base font-bold text-white">Saved to AI Memory!</h3>
            <p className="text-xs text-slate-400">
              AlgoMentor will keep this invariant in mind when hinting similar problems in the future.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs">
                {error}
              </div>
            )}

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Problem Title
              </label>
              <input
                type="text"
                value={problemTitle}
                onChange={(e) => setProblemTitle(e.target.value)}
                placeholder="e.g. 1800C - Remove the Bracket"
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Topic Tags (press Enter to add)
              </label>
              <div className="flex flex-wrap gap-1.5 mb-2">
                {tags.map((t) => (
                  <span
                    key={t}
                    className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs bg-indigo-500/10 text-indigo-400 border border-indigo-500/20"
                  >
                    <span>{t}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveTag(t)}
                      className="hover:text-rose-400"
                    >
                      &times;
                    </button>
                  </span>
                ))}
              </div>
              <input
                type="text"
                value={tagInput}
                onChange={(e) => setTagInput(e.target.value)}
                onKeyDown={handleAddTag}
                placeholder="e.g. dp, binary search, trees..."
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                What went wrong? / Why did you struggle? <span className="text-rose-400">*</span>
              </label>
              <textarea
                rows={3}
                value={mistakeText}
                onChange={(e) => setMistakeText(e.target.value)}
                placeholder="e.g. Got WA on test 4 because I forgot that array can have negative elements; greedy failed and needed prefix sums."
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Rule / Takeaway to Remember (Optional)
              </label>
              <input
                type="text"
                value={keyTakeaway}
                onChange={(e) => setKeyTakeaway(e.target.value)}
                placeholder="e.g. When negative values are present, check if prefix sum + hashmap applies."
                className="w-full bg-slate-950 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              />
            </div>

            <div className="pt-2 flex items-center justify-end gap-3">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 text-xs font-medium text-slate-400 hover:text-white bg-slate-800 rounded-lg transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={loading || !problemTitle.trim() || !mistakeText.trim()}
                className="px-5 py-2 text-xs font-bold text-white bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 rounded-lg transition shadow-md shadow-indigo-600/20"
              >
                {loading ? "Indexing into Memory..." : "Save to AI Memory"}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}

