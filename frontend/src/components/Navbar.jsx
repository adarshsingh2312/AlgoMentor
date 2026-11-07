import React from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export function Navbar() {
  const { user, isAuthenticated, isAdmin, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const navLinkClass = ({ isActive }) =>
    `px-3 py-2 text-sm font-medium rounded-md transition-colors ${
      isActive
        ? "bg-slate-800 text-indigo-400"
        : "text-slate-300 hover:bg-slate-800/60 hover:text-white"
    }`;

  return (
    <nav className="bg-slate-900 border-b border-slate-800 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Brand */}
          <div className="flex items-center gap-8">
            <Link to="/" className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center font-bold text-white shadow-indigo-500/20 shadow-md">
                AM
              </div>
              <span className="font-bold text-lg text-white tracking-tight">
                Algo<span className="text-indigo-400">Mentor</span>
              </span>
            </Link>

            {/* Nav items */}
            {isAuthenticated && (
              <div className="hidden md:flex items-center space-x-1">
                <NavLink to="/dashboard" className={navLinkClass}>
                  Dashboard
                </NavLink>
                <NavLink to="/recommendations" className={navLinkClass}>
                  Daily Picks
                </NavLink>
                <NavLink to="/roadmap" className={navLinkClass}>
                  DSA Roadmap
                </NavLink>
                <NavLink to="/notes" className={navLinkClass}>
                  Study Notes
                </NavLink>
                <NavLink to="/mentor" className={navLinkClass}>
                  Mentor Chat
                </NavLink>
                {isAdmin && (
                  <NavLink
                    to="/admin"
                    className={({ isActive }) =>
                      `px-3 py-2 text-sm font-medium rounded-md transition-colors ${
                        isActive
                          ? "bg-amber-950 text-amber-400 border border-amber-800"
                          : "text-amber-400/90 hover:bg-amber-950/40"
                      }`
                    }
                  >
                    Admin
                  </NavLink>
                )}
              </div>
            )}
          </div>

          {/* Right side user menu */}
          <div className="flex items-center gap-3">
            {isAuthenticated ? (
              <div className="flex items-center gap-4">
                <div className="hidden sm:flex flex-col text-right">
                  <span className="text-sm font-medium text-slate-200">
                    {user?.name || user?.email}
                  </span>
                  {user?.cfHandle ? (
                    <span className="text-xs text-indigo-400 font-mono">
                      cf: @{user.cfHandle}
                    </span>
                  ) : (
                    <span className="text-xs text-amber-400/80">No CF handle linked</span>
                  )}
                </div>
                {isAdmin && (
                  <span className="px-2 py-0.5 text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20 rounded">
                    ADMIN
                  </span>
                )}
                <button
                  onClick={handleLogout}
                  className="px-3 py-1.5 text-xs font-medium text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 rounded-md border border-slate-700 transition"
                >
                  Logout
                </button>
              </div>
            ) : (
              <Link
                to="/login"
                className="px-4 py-2 text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-500 rounded-md transition shadow-sm"
              >
                Sign In
              </Link>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}

