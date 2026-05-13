"use client";

import { useState } from "react";
import { Bell, User, Settings, Search } from "lucide-react";
import useAuthStore from "@/lib/store/auth-store";

export default function Header() {
  const { user } = useAuthStore();
  const [showNotifications, setShowNotifications] = useState(false);

  return (
    <header className="bg-card border-b border-border sticky top-0 z-10">
      <div className="flex items-center justify-between px-8 py-4">
        {/* Search */}
        <div className="flex-1 max-w-md">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <input
              type="text"
              placeholder="Search transactions..."
              className="w-full pl-10 pr-4 py-2 rounded-lg bg-background border border-border focus:border-primary outline-none transition text-sm"
            />
          </div>
        </div>

        {/* Right side actions */}
        <div className="flex items-center gap-4 ml-8">
          {/* Notifications */}
          <div className="relative">
            <button
              onClick={() => setShowNotifications(!showNotifications)}
              className="p-2 hover:bg-muted rounded-lg transition relative"
            >
              <Bell className="w-5 h-5 text-muted-foreground" />
              <span className="absolute top-0 right-0 w-2 h-2 bg-error rounded-full"></span>
            </button>

            {showNotifications && (
              <div className="absolute right-0 mt-2 w-80 bg-card border border-border rounded-lg shadow-lg p-4">
                <h3 className="font-semibold mb-3">Notifications</h3>
                <div className="space-y-2">
                  <div className="p-2 hover:bg-muted/50 rounded cursor-pointer text-sm">
                    <p className="font-medium">High number of failed transactions</p>
                    <p className="text-xs text-muted-foreground">Just now</p>
                  </div>
                  <div className="p-2 hover:bg-muted/50 rounded cursor-pointer text-sm">
                    <p className="font-medium">API Key expiring soon</p>
                    <p className="text-xs text-muted-foreground">2 hours ago</p>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* User Menu */}
          <div className="flex items-center gap-3 pl-4 border-l border-border">
            <div className="text-right">
              <p className="text-sm font-medium">{user?.name || "Admin"}</p>
              <p className="text-xs text-muted-foreground">{user?.role}</p>
            </div>
            <button className="w-10 h-10 rounded-full bg-primary flex items-center justify-center hover:opacity-90 transition">
              <User className="w-5 h-5 text-primary-foreground" />
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}
