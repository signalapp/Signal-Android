'use client';

import { Settings, Bell, Lock, Eye } from 'lucide-react';

export default function SettingsPage() {
  return (
    <div className="space-y-6 max-w-2xl">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-foreground flex items-center gap-2">
          <Settings className="w-8 h-8" />
          Settings
        </h1>
        <p className="text-muted-foreground mt-2">Manage your dashboard preferences and configuration</p>
      </div>

      {/* General Settings */}
      <div className="bg-card border border-border rounded-lg p-6">
        <h2 className="text-lg font-bold text-foreground mb-4">General Settings</h2>
        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium text-foreground mb-2 block">Dashboard Name</label>
            <input
              type="text"
              defaultValue="Razorpay Payment Dashboard"
              className="w-full px-3 py-2 bg-muted border border-border rounded-lg text-foreground placeholder-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="text-sm font-medium text-foreground mb-2 block">Default Currency</label>
            <select className="w-full px-3 py-2 bg-muted border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary">
              <option>INR (Indian Rupee)</option>
            </select>
          </div>
        </div>
      </div>

      {/* Security Settings */}
      <div className="bg-card border border-border rounded-lg p-6">
        <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
          <Lock className="w-5 h-5 text-primary" />
          Security Settings
        </h2>
        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium text-foreground mb-2 block">Session Timeout (minutes)</label>
            <input
              type="number"
              defaultValue="30"
              className="w-full px-3 py-2 bg-muted border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium text-foreground">Require password change every 90 days</label>
            <input type="checkbox" defaultChecked className="w-4 h-4 cursor-pointer" />
          </div>
          <div className="flex items-center justify-between">
            <label className="text-sm font-medium text-foreground">Enable two-factor authentication</label>
            <input type="checkbox" className="w-4 h-4 cursor-pointer" />
          </div>
        </div>
      </div>

      {/* Notification Settings */}
      <div className="bg-card border border-border rounded-lg p-6">
        <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
          <Bell className="w-5 h-5 text-primary" />
          Notification Settings
        </h2>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-foreground">Payment alerts</p>
              <p className="text-xs text-muted-foreground">Get notified on failed payments</p>
            </div>
            <input type="checkbox" defaultChecked className="w-4 h-4 cursor-pointer" />
          </div>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-foreground">API key expiration alerts</p>
              <p className="text-xs text-muted-foreground">Receive alerts 7 days before key expires</p>
            </div>
            <input type="checkbox" defaultChecked className="w-4 h-4 cursor-pointer" />
          </div>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-foreground">Daily digest</p>
              <p className="text-xs text-muted-foreground">Receive daily payment summary email</p>
            </div>
            <input type="checkbox" className="w-4 h-4 cursor-pointer" />
          </div>
        </div>
      </div>

      {/* Display Settings */}
      <div className="bg-card border border-border rounded-lg p-6">
        <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
          <Eye className="w-5 h-5 text-primary" />
          Display Settings
        </h2>
        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium text-foreground mb-2 block">Theme</label>
            <select className="w-full px-3 py-2 bg-muted border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary">
              <option>Dark (Default)</option>
              <option>Light</option>
              <option>System</option>
            </select>
          </div>
          <div>
            <label className="text-sm font-medium text-foreground mb-2 block">Transactions per page</label>
            <select className="w-full px-3 py-2 bg-muted border border-border rounded-lg text-foreground focus:outline-none focus:ring-2 focus:ring-primary">
              <option>10</option>
              <option>25</option>
              <option>50</option>
              <option>100</option>
            </select>
          </div>
        </div>
      </div>

      {/* Save Button */}
      <div className="flex gap-3">
        <button className="px-6 py-2 bg-primary text-primary-foreground rounded-lg font-medium hover:bg-primary/90 transition">
          Save Changes
        </button>
        <button className="px-6 py-2 border border-border text-foreground rounded-lg font-medium hover:bg-muted/50 transition">
          Reset to Defaults
        </button>
      </div>
    </div>
  );
}
