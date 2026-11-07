import React from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function Home() {
  const { isAuthenticated } = useAuth();

  return (
    <div className="space-y-24 py-6">
      {/* Hero Section */}
      <section className="relative overflow-hidden pt-8 pb-12 sm:pt-16 sm:pb-20 text-center">
        {/* Glow ambient background effects */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[350px] bg-indigo-600/15 blur-[120px] rounded-full pointer-events-none -z-10" />
        <div className="absolute top-1/4 left-1/3 -translate-x-1/2 -translate-y-1/2 w-[300px] h-[200px] bg-purple-600/10 blur-[90px] rounded-full pointer-events-none -z-10" />

        <div className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 text-xs font-semibold mb-6 animate-pulse">
          <span className="w-2 h-2 rounded-full bg-indigo-400" />
          Powered by Gemini 2.5 Flash & LangGraph Multi-Turn Socratic AI
        </div>

        <h1 className="text-4xl sm:text-6xl font-extrabold text-white tracking-tight leading-[1.15] max-w-4xl mx-auto">
          Crack Coding Interviews with a{" "}
          <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 via-purple-300 to-indigo-200">
            Socratic AI Mentor
          </span>
        </h1>

        <p className="mt-6 text-base sm:text-lg text-slate-400 max-w-2xl mx-auto leading-relaxed">
          AlgoMentor analyzes your real Codeforces submissions, identifies your weak topic tags, curates daily rating-adjusted unsolved problems, and mentors your thought process without spoiling answers.
        </p>

        {/* CTA Buttons */}
        <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-4">
          {isAuthenticated ? (
            <Link
              to="/dashboard"
              className="w-full sm:w-auto px-8 py-3.5 bg-indigo-600 hover:bg-indigo-500 text-white font-semibold rounded-xl transition shadow-lg shadow-indigo-600/25 flex items-center justify-center gap-2"
            >
              <span>Go to Your Dashboard</span>
              <span>&rarr;</span>
            </Link>
          ) : (
            <Link
              to="/login"
              className="w-full sm:w-auto px-8 py-3.5 bg-indigo-600 hover:bg-indigo-500 text-white font-semibold rounded-xl transition shadow-lg shadow-indigo-600/25 flex items-center justify-center gap-2"
            >
              <span>Get Started Free with Google</span>
              <span>&rarr;</span>
            </Link>
          )}

          <a
            href="#features"
            className="w-full sm:w-auto px-7 py-3.5 bg-slate-900/80 hover:bg-slate-800 text-slate-300 hover:text-white font-medium rounded-xl border border-slate-800 transition flex items-center justify-center"
          >
            Explore Features
          </a>
        </div>

        {/* Tech Highlights Pill Bar */}
        <div className="mt-12 pt-8 border-t border-slate-900 max-w-3xl mx-auto flex flex-wrap items-center justify-center gap-6 text-xs text-slate-500">
          <div className="flex items-center gap-1.5">
            <span className="text-emerald-400">✓</span> Real Codeforces Sync
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-emerald-400">✓</span> 100% Unsolved Daily Picks
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-emerald-400">✓</span> Zero Answer Spoilers
          </div>
          <div className="flex items-center gap-1.5">
            <span className="text-emerald-400">✓</span> Automated Morning Digests
          </div>
        </div>
      </section>

      {/* Feature Grid Section */}
      <section id="features" className="space-y-12">
        <div className="text-center space-y-3">
          <h2 className="text-xs uppercase tracking-widest text-indigo-400 font-bold">
            Built for Serious Coders
          </h2>
          <p className="text-2xl sm:text-3xl font-bold text-white tracking-tight">
            Everything you need to level up your rating
          </p>
          <p className="text-sm text-slate-400 max-w-xl mx-auto">
            Traditional practice sheets are static. AlgoMentor continuously adapts to your actual performance.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {/* Card 1: AI Socratic Mentor */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 hover:border-indigo-500/40 transition group">
            <div className="w-12 h-12 rounded-xl bg-indigo-600/10 border border-indigo-500/20 text-indigo-400 flex items-center justify-center font-bold text-xl mb-4 group-hover:scale-105 transition-transform">
              🧠
            </div>
            <h3 className="text-lg font-bold text-white mb-2">
              Socratic AI Mentor
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Stuck on a problem? Ask for hints instead of looking at solutions. The mentor climbs a guided ladder (Conceptual → Invariant → Algorithmic) without revealing direct code.
            </p>
          </div>

          {/* Card 2: 100% Unsolved Daily Picks */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 hover:border-indigo-500/40 transition group">
            <div className="w-12 h-12 rounded-xl bg-purple-600/10 border border-purple-500/20 text-purple-400 flex items-center justify-center font-bold text-xl mb-4 group-hover:scale-105 transition-transform">
              🎯
            </div>
            <h3 className="text-lg font-bold text-white mb-2">
              Personalized Daily Picks
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Every morning, get 3 handpicked problems targeted around your current rating window. Guaranteed to only pick problems you haven't solved yet.
            </p>
          </div>

          {/* Card 3: Deep Submissions Analytics */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 hover:border-indigo-500/40 transition group">
            <div className="w-12 h-12 rounded-xl bg-emerald-600/10 border border-emerald-500/20 text-emerald-400 flex items-center justify-center font-bold text-xl mb-4 group-hover:scale-105 transition-transform">
              📊
            </div>
            <h3 className="text-lg font-bold text-white mb-2">
              Deep Analytics & Weak Tags
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Identify tags where your solve ratio is below 40% (DP, graphs, math). View your solve trends across 7, 30, and 90 days alongside rating accuracy distributions.
            </p>
          </div>

          {/* Card 4: Curated DSA Roadmap */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 hover:border-indigo-500/40 transition group">
            <div className="w-12 h-12 rounded-xl bg-amber-600/10 border border-amber-500/20 text-amber-400 flex items-center justify-center font-bold text-xl mb-4 group-hover:scale-105 transition-transform">
              🗺️
            </div>
            <h3 className="text-lg font-bold text-white mb-2">
              Structured DSA Roadmap
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              40+ interview topics arranged into progressive categories. Direct links to practice problems with optimistic progress tracking (TODO, In Progress, Solved).
            </p>
          </div>

          {/* Card 5: Smart Study Notes & RAG */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 hover:border-indigo-500/40 transition group">
            <div className="w-12 h-12 rounded-xl bg-cyan-600/10 border border-cyan-500/20 text-cyan-400 flex items-center justify-center font-bold text-xl mb-4 group-hover:scale-105 transition-transform">
              📝
            </div>
            <h3 className="text-lg font-bold text-white mb-2">
              Study Notes with Semantic Search
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Upload your own algorithmic cheat-sheets or contest retrospectives. The AI indexes them into a vector database so you can query your own insights anytime.
            </p>
          </div>

          {/* Card 6: Daily Morning Email Digest */}
          <div className="bg-slate-900/80 border border-slate-800 rounded-2xl p-6 hover:border-indigo-500/40 transition group">
            <div className="w-12 h-12 rounded-xl bg-rose-600/10 border border-rose-500/20 text-rose-400 flex items-center justify-center font-bold text-xl mb-4 group-hover:scale-105 transition-transform">
              📬
            </div>
            <h3 className="text-lg font-bold text-white mb-2">
              Automated Email Digest
            </h3>
            <p className="text-sm text-slate-400 leading-relaxed">
              Receive a fresh problem sheet directly in your inbox at 7:00 AM every morning. Build an unbreakable daily habit without needing to open the app first.
            </p>
          </div>
        </div>
      </section>

      {/* How it works Section */}
      <section className="bg-slate-900/50 border border-slate-800/80 rounded-3xl p-8 sm:p-12">
        <div className="text-center space-y-2 mb-12">
          <h2 className="text-xs uppercase tracking-widest text-indigo-400 font-bold">
            Simple 3-Step Routine
          </h2>
          <p className="text-2xl font-bold text-white">How AlgoMentor accelerates your growth</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8 relative">
          <div className="text-center space-y-3">
            <div className="w-10 h-10 rounded-full bg-indigo-600 text-white font-bold flex items-center justify-center mx-auto text-sm">
              1
            </div>
            <h4 className="font-bold text-white text-base">Connect Your Handle</h4>
            <p className="text-xs text-slate-400 leading-relaxed">
              Link your Codeforces handle in 1 click. AlgoMentor automatically syncs all historical ACs and failed attempts.
            </p>
          </div>

          <div className="text-center space-y-3">
            <div className="w-10 h-10 rounded-full bg-indigo-600 text-white font-bold flex items-center justify-center mx-auto text-sm">
              2
            </div>
            <h4 className="font-bold text-white text-base">Get Targeted Daily Picks</h4>
            <p className="text-xs text-slate-400 leading-relaxed">
              Wake up to 3 problems specifically targeting topics where your solve ratio is weakest, scaled to your current rating.
            </p>
          </div>

          <div className="text-center space-y-3">
            <div className="w-10 h-10 rounded-full bg-indigo-600 text-white font-bold flex items-center justify-center mx-auto text-sm">
              3
            </div>
            <h4 className="font-bold text-white text-base">Consult Mentor & Improve</h4>
            <p className="text-xs text-slate-400 leading-relaxed">
              Whenever you're stuck, talk with your Socratic AI mentor to uncover invariants and master the underlying patterns.
            </p>
          </div>
        </div>
      </section>

      {/* Bottom CTA Banner */}
      <section className="text-center bg-gradient-to-b from-slate-900 to-indigo-950/40 border border-indigo-500/20 rounded-3xl p-8 sm:p-14 space-y-6">
        <h2 className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight">
          Ready to raise your competitive rating?
        </h2>
        <p className="text-sm sm:text-base text-slate-400 max-w-xl mx-auto">
          Join AlgoMentor today and transform passive problem-solving into guided, deliberate mastery.
        </p>
        <div>
          {isAuthenticated ? (
            <Link
              to="/dashboard"
              className="inline-flex items-center gap-2 px-8 py-3.5 bg-indigo-600 hover:bg-indigo-500 text-white font-semibold rounded-xl transition shadow-lg shadow-indigo-600/25"
            >
              <span>Jump to Dashboard</span>
              <span>&rarr;</span>
            </Link>
          ) : (
            <Link
              to="/login"
              className="inline-flex items-center gap-2 px-8 py-3.5 bg-indigo-600 hover:bg-indigo-500 text-white font-semibold rounded-xl transition shadow-lg shadow-indigo-600/25"
            >
              <span>Get Started with Google</span>
              <span>&rarr;</span>
            </Link>
          )}
        </div>
      </section>
    </div>
  );
}

