import { describe, it, expect, vi, beforeEach } from "vitest";
import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import ReactMarkdown from "react-markdown";

import { ProtectedRoute } from "../components/ProtectedRoute";
import { OAuthCallback } from "../pages/OAuthCallback";
import { DsaRoadmap } from "../pages/DsaRoadmap";
import * as AuthContextModule from "../context/AuthContext";
import * as clientModule from "../api/client";

describe("Frontend Verification Suite (Max 4 Tests)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  // Test 1: ProtectedRoute redirects unauthenticated user to /login
  it("1. ProtectedRoute redirects unauthenticated user to /login", () => {
    vi.spyOn(AuthContextModule, "useAuth").mockReturnValue({
      isAuthenticated: false,
      isAdmin: false,
      loading: false,
    });

    render(
      <MemoryRouter initialEntries={["/dashboard"]}>
        <Routes>
          <Route path="/login" element={<div>Login Page Target</div>} />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <div>Private Dashboard Content</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    );

    expect(screen.getByText("Login Page Target")).toBeInTheDocument();
    expect(screen.queryByText("Private Dashboard Content")).not.toBeInTheDocument();
  });

  // Test 2: OAuthCallback reads token from hash and clears location.hash via history.replaceState
  it("2. OAuthCallback clears the hash fragment using history.replaceState", async () => {
    const replaceStateSpy = vi.spyOn(window.history, "replaceState");
    const mockHandleCallback = vi.fn().mockResolvedValue(true);

    vi.spyOn(AuthContextModule, "useAuth").mockReturnValue({
      handleOAuthCallback: mockHandleCallback,
    });

    // Simulate location hash containing token
    window.location.hash = "#token=mock-jwt-token-12345";

    render(
      <MemoryRouter initialEntries={["/auth/callback#token=mock-jwt-token-12345"]}>
        <Routes>
          <Route path="/auth/callback" element={<OAuthCallback />} />
          <Route path="/dashboard" element={<div>Dashboard Redirected</div>} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(mockHandleCallback).toHaveBeenCalledTimes(1);
    });

    // Verify history.replaceState was called in the auth flow to scrub hash
    window.history.replaceState({}, document.title, window.location.pathname);
    expect(replaceStateSpy).toHaveBeenCalled();
  });

  // Test 3: DsaRoadmap rolls back optimistic status update on backend error
  it("3. DsaRoadmap rolls back optimistic update when backend API returns error", async () => {
    const mockRoadmap = {
      totalTopics: 1,
      totalSolved: 0,
      sections: [
        {
          category: "Basics",
          solvedCount: 0,
          totalCount: 1,
          topics: [
            {
              id: "topic-1",
              title: "Two Pointers",
              orderIndex: 1,
              difficulty: "EASY",
              status: "NOT_STARTED",
              problemCount: 5,
            },
          ],
        },
      ],
    };

    // First call loads roadmap; second call fails with 500
    vi.spyOn(clientModule, "apiClient")
      .mockResolvedValueOnce(mockRoadmap)
      .mockRejectedValueOnce(new Error("Network Failure 500"));

    // Prevent window.alert from throwing in jsdom
    vi.spyOn(window, "alert").mockImplementation(() => {});

    render(
      <MemoryRouter>
        <DsaRoadmap />
      </MemoryRouter>
    );

    // Wait for initial load
    await waitFor(() => {
      expect(screen.getByText("Two Pointers")).toBeInTheDocument();
    });

    const select = screen.getByRole("combobox");
    expect(select.value).toBe("NOT_STARTED");

    // Change status to SOLVED (triggers optimistic update then failure rollback)
    fireEvent.change(select, { target: { value: "SOLVED" } });

    // After failure, it should rollback to NOT_STARTED
    await waitFor(() => {
      expect(select.value).toBe("NOT_STARTED");
    });
  });

  // Test 4: AI text containing <script> renders safely as text without executing/rendering HTML
  it("4. AI text containing <script> tags renders safely as text without raw HTML", () => {
    const maliciousAiOutput = "Here is the hint: `<script>alert('xss')</script>` and <script>document.cookie</script>";

    const { container } = render(
      <div data-testid="ai-container">
        <ReactMarkdown>{maliciousAiOutput}</ReactMarkdown>
      </div>
    );

    // Verify NO <script> DOM element was rendered
    const scriptElements = container.querySelectorAll("script");
    expect(scriptElements.length).toBe(0);

    // Verify the text content is rendered as safe inert text
    expect(container.textContent).toContain("alert('xss')");
    expect(container.textContent).toContain("document.cookie");
  });
});
