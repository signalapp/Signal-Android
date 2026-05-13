import { create } from "zustand";
import { devtools, persist } from "zustand/middleware";
import { AdminUser, AuthSession, RazorpayApiKey } from "@/types";

interface AuthState {
  // User
  user: AdminUser | null;
  token: string | null;
  sessionExpiresAt: string | null;

  // API Keys
  activeApiKey: RazorpayApiKey | null;
  apiKeys: RazorpayApiKey[];

  // Session management
  setUser: (user: AdminUser | null) => void;
  setToken: (token: string | null) => void;
  setSession: (session: AuthSession) => void;
  logout: () => void;

  // API Key management
  setActiveApiKey: (key: RazorpayApiKey) => void;
  setApiKeys: (keys: RazorpayApiKey[]) => void;
  addApiKey: (key: RazorpayApiKey) => void;
  removeApiKey: (keyId: string) => void;
  updateApiKey: (key: RazorpayApiKey) => void;

  // Utilities
  isAuthenticated: () => boolean;
  isSessionExpired: () => boolean;
  hasValidApiKey: () => boolean;
  clearAll: () => void;
}

const useAuthStore = create<AuthState>()(
  devtools(
    persist(
      (set, get) => ({
        user: null,
        token: null,
        sessionExpiresAt: null,
        activeApiKey: null,
        apiKeys: [],

        setUser: (user) => set({ user }),

        setToken: (token) => set({ token }),

        setSession: (session) => {
          set({
            user: session.user,
            token: session.token,
            sessionExpiresAt: session.expiresAt,
          });
        },

        logout: () => {
          set({
            user: null,
            token: null,
            sessionExpiresAt: null,
          });
        },

        setActiveApiKey: (key) => {
          set({ activeApiKey: key });
        },

        setApiKeys: (keys) => {
          set({ apiKeys: keys });
        },

        addApiKey: (key) => {
          const { apiKeys } = get();
          const updated = [...apiKeys, key];
          set({
            apiKeys: updated,
            activeApiKey: key, // Set as active when added
          });
        },

        removeApiKey: (keyId) => {
          const { apiKeys, activeApiKey } = get();
          const updated = apiKeys.filter((k) => k.keyId !== keyId);
          const newActiveKey = activeApiKey?.keyId === keyId ? updated[0] || null : activeApiKey;
          set({
            apiKeys: updated,
            activeApiKey: newActiveKey,
          });
        },

        updateApiKey: (key) => {
          const { apiKeys, activeApiKey } = get();
          const updated = apiKeys.map((k) => (k.keyId === key.keyId ? key : k));
          const newActiveKey = activeApiKey?.keyId === key.keyId ? key : activeApiKey;
          set({
            apiKeys: updated,
            activeApiKey: newActiveKey,
          });
        },

        isAuthenticated: () => {
          const { token, user } = get();
          return !!token && !!user && !get().isSessionExpired();
        },

        isSessionExpired: () => {
          const { sessionExpiresAt } = get();
          if (!sessionExpiresAt) return true;
          return new Date(sessionExpiresAt) < new Date();
        },

        hasValidApiKey: () => {
          const { activeApiKey } = get();
          return (
            !!activeApiKey &&
            activeApiKey.status === "active" &&
            (!activeApiKey.expiresAt || new Date(activeApiKey.expiresAt) > new Date())
          );
        },

        clearAll: () => {
          set({
            user: null,
            token: null,
            sessionExpiresAt: null,
            activeApiKey: null,
            apiKeys: [],
          });
        },
      }),
      {
        name: "auth-store",
        partialize: (state) => ({
          user: state.user,
          token: state.token,
          sessionExpiresAt: state.sessionExpiresAt,
          activeApiKey: state.activeApiKey,
          apiKeys: state.apiKeys,
        }),
      }
    )
  )
);

export default useAuthStore;
