"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { AlertCircle, CheckCircle } from "lucide-react";
import useAuthStore from "@/lib/store/auth-store";

export default function LoginPage() {
  const router = useRouter();
  const { setUser, setToken, setSession } = useAuthStore();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [showDemoHint, setShowDemoHint] = useState(true);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      // In production, this would call your backend API
      // For demo, use simple validation
      if (email.includes("@") && password.length >= 6) {
        const mockSession = {
          user: {
            id: "admin-001",
            email: email,
            name: email.split("@")[0],
            role: "admin" as const,
            createdAt: new Date().toISOString(),
            lastLogin: new Date().toISOString(),
          },
          token: "mock-token-" + Math.random().toString(36).substr(2, 9),
          expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        };

        setSession(mockSession);
        router.push("/dashboard");
      } else {
        setError("Invalid email or password (min 6 characters)");
      }
    } catch (err) {
      setError("Login failed. Please try again.");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-background flex items-center justify-center">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold text-primary mb-2">Dashboard</h1>
          <p className="text-muted-foreground">Razorpay Payment Management</p>
        </div>

        {showDemoHint && (
          <div className="bg-blue-950 border border-blue-800 rounded-lg p-4 mb-6 flex items-start gap-3">
            <CheckCircle className="w-5 h-5 text-blue-400 flex-shrink-0 mt-0.5" />
            <div className="text-sm text-blue-200">
              <p className="font-semibold mb-1">Demo Credentials</p>
              <p>Use any email and password (min 6 chars)</p>
            </div>
            <button
              onClick={() => setShowDemoHint(false)}
              className="ml-auto text-blue-400 hover:text-blue-300"
            >
              ×
            </button>
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-4">
          {error && (
            <div className="bg-error/10 border border-error/20 rounded-lg p-3 flex items-center gap-2 text-sm text-error">
              <AlertCircle className="w-4 h-4" />
              {error}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium mb-2">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@example.com"
              className="w-full px-4 py-2 rounded-lg bg-card border border-border focus:border-primary outline-none transition"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium mb-2">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full px-4 py-2 rounded-lg bg-card border border-border focus:border-primary outline-none transition"
              required
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-primary hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed text-primary-foreground font-medium py-2 rounded-lg transition"
          >
            {loading ? "Logging in..." : "Login"}
          </button>
        </form>

        <p className="text-center text-muted-foreground text-sm mt-6">
          Razorpay Admin Dashboard v1.0
        </p>
      </div>
    </div>
  );
}
