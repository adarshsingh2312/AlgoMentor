import React, { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function OAuthCallback() {
  const { handleOAuthCallback } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState(null);
  const processedRef = useRef(false);

  useEffect(() => {
    if (processedRef.current) return;
    processedRef.current = true;

    async function processLogin() {
      try {
        await handleOAuthCallback();
        navigate("/dashboard", { replace: true });
      } catch (err) {
        setError(err.message || "Failed to complete authentication");
      }
    }

    processLogin();
  }, [handleOAuthCallback, navigate]);

  if (error) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center p-4">
        <div className="bg-slate-900 border border-rose-900/50 rounded-xl p-6 max-w-md w-full text-center">
          <h2 className="text-rose-400 font-bold mb-2">Login Failed</h2>
          <p className="text-slate-400 text-sm mb-4">{error}</p>
          <button
            onClick={() => navigate("/login", { replace: true })}
            className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-lg text-sm"
          >
            Back to Login
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-[60vh] flex flex-col items-center justify-center gap-4">
      <div className="w-10 h-10 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
      <p className="text-slate-400 text-sm font-medium">
        Finalizing authentication...
      </p>
    </div>
  );
}

