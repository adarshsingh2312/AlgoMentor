import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import {
  getAuthToken,
  setAuthToken,
  clearAuthToken,
  apiClient,
  setLogoutHandler,
  API_BASE_URL,
} from "../api/client";

const AuthContext = createContext(null);

function parseJwtExp(token) {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = JSON.parse(atob(parts[1]));
    return payload.exp ? payload.exp * 1000 : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(() => getAuthToken());
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const logout = useCallback(() => {
    clearAuthToken();
    setTokenState(null);
    setUser(null);
  }, []);

  const loadUserProfile = useCallback(async () => {
    try {
      const profile = await apiClient("/api/me");
      setUser(profile);
    } catch (err) {
      console.error("Failed to load user profile:", err);
      logout();
    } finally {
      setLoading(false);
    }
  }, [logout]);

  useEffect(() => {
    setLogoutHandler(logout);
  }, [logout]);

  useEffect(() => {
    const currentToken = getAuthToken();
    if (!currentToken) {
      setLoading(false);
      return;
    }

    // UX expiry check
    const exp = parseJwtExp(currentToken);
    if (exp && Date.now() >= exp) {
      logout();
      setLoading(false);
      return;
    }

    setTokenState(currentToken);
    loadUserProfile();
  }, [loadUserProfile, logout]);

  const loginWithGoogle = () => {
    window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
  };

  const handleOAuthCallback = useCallback(async () => {
    setLoading(true);
    try {
      const hash = window.location.hash || "";
      let jwt = null;

      if (hash) {
        const params = new URLSearchParams(hash.replace(/^#/, ""));
        jwt = params.get("token");
      }

      if (!jwt) {
        const searchParams = new URLSearchParams(window.location.search);
        jwt = searchParams.get("token");
      }

      if (!jwt) {
        jwt = getAuthToken();
      }

      if (!jwt) {
        throw new Error("No token found in callback hash or storage");
      }

      // Security requirement B2: Clear the URL fragment immediately to prevent leakage
      if (window.location.hash || window.location.search) {
        window.history.replaceState({}, document.title, window.location.pathname);
      }

      setAuthToken(jwt);
      setTokenState(jwt);

      const profile = await apiClient("/api/me");
      setUser(profile);
      return true;
    } catch (err) {
      console.error("OAuth callback error:", err);
      logout();
      throw err;
    } finally {
      setLoading(false);
    }
  }, [logout]);

  const refreshUser = async () => {
    try {
      const profile = await apiClient("/api/me");
      setUser(profile);
      return profile;
    } catch (err) {
      console.error("Failed to refresh user:", err);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        token,
        user,
        loading,
        loginWithGoogle,
        logout,
        handleOAuthCallback,
        refreshUser,
        isAuthenticated: !!token && !!user,
        isAdmin: user?.role === "ADMIN",
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

