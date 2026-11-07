import React from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { ErrorBoundary } from "./components/ErrorBoundary";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { Navbar } from "./components/Navbar";

import { Login } from "./pages/Login";
import { OAuthCallback } from "./pages/OAuthCallback";
import { Dashboard } from "./pages/Dashboard";
import { Recommendations } from "./pages/Recommendations";
import { DsaRoadmap } from "./pages/DsaRoadmap";
import Notes from "./pages/Notes";
import MentorChat from "./pages/MentorChat";
import Admin from "./pages/Admin";

export default function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <BrowserRouter>
          <div className="min-h-screen bg-zinc-950 text-zinc-100 flex flex-col font-sans selection:bg-indigo-600 selection:text-white">
            <Navbar />
            <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
              <Routes>
                {/* Public Auth Routes */}
                <Route path="/login" element={<Login />} />
                <Route path="/auth/callback" element={<OAuthCallback />} />

                {/* Protected Application Routes */}
                <Route
                  path="/dashboard"
                  element={
                    <ProtectedRoute>
                      <Dashboard />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/recommendations"
                  element={
                    <ProtectedRoute>
                      <Recommendations />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/roadmap"
                  element={
                    <ProtectedRoute>
                      <DsaRoadmap />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/notes"
                  element={
                    <ProtectedRoute>
                      <Notes />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/mentor"
                  element={
                    <ProtectedRoute>
                      <MentorChat />
                    </ProtectedRoute>
                  }
                />

                {/* Admin Only Route */}
                <Route
                  path="/admin"
                  element={
                    <ProtectedRoute requireAdmin={true}>
                      <Admin />
                    </ProtectedRoute>
                  }
                />

                {/* Default redirect */}
                <Route path="/" element={<Navigate to="/dashboard" replace />} />
                <Route path="*" element={<Navigate to="/dashboard" replace />} />
              </Routes>
            </main>
          </div>
        </BrowserRouter>
      </AuthProvider>
    </ErrorBoundary>
  );
}

