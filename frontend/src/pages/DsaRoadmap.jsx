import React, { useState, useEffect, useCallback } from "react";
import { apiClient } from "../api/client";

/**
 * Normalize the backend DsaRoadmapDto shape into the shape the UI expects.
 * Backend returns: { sections: [{ section, items, done, total }], totalDone, totalTopics }
 * UI expects:      { sections: [{ category, topics, solvedCount, totalTopics }], totalSolved, totalTopics }
 */
function normalizeRoadmap(data) {
  if (!data) return null;
  const sections = (data.sections || []).map((sec, secIdx) => ({
    category: sec.category || sec.section || `Section ${secIdx + 1}`,
    topics: (sec.topics || sec.items || []).map((t, idx) => ({
      id: t.id,
      title: t.title || t.name || "",
      orderIndex: t.orderIndex != null ? t.orderIndex : idx + 1,
      difficulty: t.difficulty || "MEDIUM",
      status: t.status || "TODO",
      url: t.url || "",
    })),
    solvedCount: sec.solvedCount != null ? sec.solvedCount : (sec.done != null ? sec.done : 0),
    totalTopics: sec.totalTopics != null ? sec.totalTopics : (sec.total != null ? sec.total : 0),
  }));

  return {
    sections,
    totalSolved: data.totalSolved != null ? data.totalSolved : (data.totalDone != null ? data.totalDone : 0),
    totalTopics: data.totalTopics != null ? data.totalTopics : 0,
  };
}

export function DsaRoadmap() {
  const [roadmap, setRoadmap] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [collapsedSections, setCollapsedSections] = useState({});
  const [updatingTopicId, setUpdatingTopicId] = useState(null);

  const loadRoadmap = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiClient("/api/me/dsa");
      setRoadmap(normalizeRoadmap(data));
    } catch (err) {
      setError(err.message || "Failed to load DSA roadmap");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRoadmap();
  }, [loadRoadmap]);

  const toggleSection = (category) => {
    setCollapsedSections((prev) => ({
      ...prev,
      [category]: !prev[category],
    }));
  };

  // Requirement B6: Optimistic update with rollback on error
  const handleStatusChange = async (topicId, newStatus) => {
    if (!roadmap) return;

    // Save previous state for rollback
    const previousRoadmap = JSON.parse(JSON.stringify(roadmap));

    // Apply optimistic update immediately
    const updatedSections = roadmap.sections.map((sec) => {
      let solvedChange = 0;
      const updatedTopics = (sec.topics || []).map((top) => {
        if (top.id === topicId) {
          const oldStatus = top.status;
          if (oldStatus !== "DONE" && newStatus === "DONE") solvedChange = 1;
          else if (oldStatus === "DONE" && newStatus !== "DONE") solvedChange = -1;
          return { ...top, status: newStatus };
        }
        return top;
      });

      return {
        ...sec,
        solvedCount: Math.max(0, (sec.solvedCount || 0) + solvedChange),
        topics: updatedTopics,
      };
    });

    const totalSolvedChange = updatedSections.reduce((acc, s) => acc + (s.solvedCount || 0), 0);

    setRoadmap({
      ...roadmap,
      totalSolved: totalSolvedChange,
      sections: updatedSections,
    });

    setUpdatingTopicId(topicId);

    try {
      await apiClient(`/api/me/dsa/${topicId}/progress`, {
        method: "PUT",
        body: JSON.stringify({ status: newStatus }),
      });
    } catch (err) {
      // Rollback to previous state on failure
      console.error("Failed to update status, rolling back:", err);
      setRoadmap(previousRoadmap);
      alert(`Update failed: ${err.message}. Rolled back changes.`);
    } finally {
      setUpdatingTopicId(null);
    }
  };

  const overallProgress =
    roadmap?.totalTopics > 0
      ? Math.round(((roadmap.totalSolved || 0) / roadmap.totalTopics) * 100)
      : 0;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Header & Overall Progress */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 sm:p-8 flex flex-col md:flex-row md:items-center md:justify-between gap-6 shadow-sm">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">
            DSA Preparation Roadmap
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Systematic progression across 40+ fundamental technical interview topics.
          </p>
        </div>

        {roadmap && (
          <div className="flex items-center gap-4 bg-slate-950 p-4 rounded-xl border border-slate-800 min-w-[240px]">
            <div className="w-12 h-12 rounded-xl bg-indigo-600/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold text-lg">
              {overallProgress}%
            </div>
            <div>
              <div className="text-xs text-slate-400 font-semibold uppercase tracking-wider">
                Overall Solved
              </div>
              <div className="text-sm font-bold text-white">
                {roadmap.totalSolved || 0} / {roadmap.totalTopics || 0} Topics
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Main Roadmap Content */}
      {loading ? (
        <div className="flex items-center justify-center p-16">
          <div className="w-8 h-8 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin" />
        </div>
      ) : error ? (
        <div className="p-8 bg-slate-900 border border-slate-800 rounded-2xl text-center text-slate-400">
          <p>{error}</p>
          <button
            onClick={loadRoadmap}
            className="mt-4 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-medium rounded-xl transition"
          >
            Retry
          </button>
        </div>
      ) : !roadmap?.sections || roadmap.sections.length === 0 ? (
        <div className="p-12 bg-slate-900 border border-slate-800 rounded-2xl text-center text-slate-400">
          No roadmap topics found. Run backend seeder.
        </div>
      ) : (
        <div className="space-y-6">
          {roadmap.sections.map((sec) => {
            const isCollapsed = collapsedSections[sec.category];
            const secTopics = sec.topics || [];
            const sectionProgress =
              (sec.totalTopics || 0) > 0
                ? Math.round(((sec.solvedCount || 0) / sec.totalTopics) * 100)
                : 0;

            return (
              <div
                key={sec.category}
                className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden transition shadow-sm"
              >
                {/* Section Header Toggle */}
                <button
                  type="button"
                  onClick={() => toggleSection(sec.category)}
                  className="w-full px-6 py-4 flex items-center justify-between bg-slate-900/80 hover:bg-slate-800/50 transition cursor-pointer text-left"
                >
                  <div className="flex items-center gap-3">
                    <span className="text-slate-400 text-xs transition-transform duration-200">
                      {isCollapsed ? "▶" : "▼"}
                    </span>
                    <h2 className="text-base font-bold text-white capitalize">
                      {sec.category}
                    </h2>
                    <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-400 border border-slate-700">
                      {sec.solvedCount || 0}/{sec.totalTopics || 0}
                    </span>
                  </div>

                  {/* Section Progress Bar */}
                  <div className="flex items-center gap-3 w-40 sm:w-56">
                    <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">
                      <div
                        className="bg-indigo-500 h-2 rounded-full transition-all duration-300"
                        style={{ width: `${sectionProgress}%` }}
                      />
                    </div>
                    <span className="text-xs font-mono text-slate-400 w-9 text-right">
                      {sectionProgress}%
                    </span>
                  </div>
                </button>

                {/* Topics Table/List */}
                {!isCollapsed && (
                  <div className="border-t border-slate-800/80 divide-y divide-slate-800/40">
                    {secTopics.map((topic, idx) => (
                      <div
                        key={topic.id || idx}
                        className="px-6 py-3 flex items-center justify-between hover:bg-slate-800/20 transition gap-4"
                      >
                        <div className="flex items-center gap-3 min-w-0">
                          <span className="text-xs font-mono text-slate-500 w-6">
                            #{topic.orderIndex || idx + 1}
                          </span>
                          {topic.url ? (
                            <a
                              href={topic.url}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-sm font-medium text-slate-200 hover:text-indigo-400 transition truncate flex items-center gap-1.5 group"
                            >
                              <span className="group-hover:underline underline-offset-2">{topic.title}</span>
                              <span className="text-xs text-slate-500 group-hover:text-indigo-400">↗</span>
                            </a>
                          ) : (
                            <span className="text-sm font-medium text-slate-200 truncate">
                              {topic.title}
                            </span>
                          )}
                        </div>

                        {/* Status Select Control */}
                        <div className="flex items-center gap-3 shrink-0">
                          <select
                            value={topic.status || "TODO"}
                            disabled={updatingTopicId === topic.id}
                            onChange={(e) => handleStatusChange(topic.id, e.target.value)}
                            className={`text-xs font-semibold px-2.5 py-1.5 rounded-lg border transition focus:outline-none focus:ring-1 focus:ring-indigo-500 cursor-pointer ${
                              topic.status === "DONE"
                                ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/30"
                                : topic.status === "IN_PROGRESS"
                                ? "bg-amber-500/10 text-amber-400 border-amber-500/30"
                                : "bg-slate-800 text-slate-400 border-slate-700"
                            }`}
                          >
                            <option value="TODO">Not Started</option>
                            <option value="IN_PROGRESS">In Progress</option>
                            <option value="DONE">Solved</option>
                          </select>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
