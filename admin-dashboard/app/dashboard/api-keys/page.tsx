'use client';

import { Key, Plus, Edit2, Trash2, Copy } from 'lucide-react';

export default function ApiKeysPage() {
  const apiKeys = [
    {
      id: 1,
      name: 'Production Key',
      environment: 'Production',
      key: 'rzp_live_xxx...xxxxx',
      created: '2024-01-01',
      lastUsed: '2024-01-15',
      status: 'active',
    },
    {
      id: 2,
      name: 'Development Key',
      environment: 'Sandbox',
      key: 'rzp_test_xxx...xxxxx',
      created: '2024-01-05',
      lastUsed: '2024-01-14',
      status: 'active',
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-3xl font-bold text-foreground flex items-center gap-2">
            <Key className="w-8 h-8" />
            API Keys
          </h1>
          <p className="text-muted-foreground mt-2">Manage your Razorpay API credentials</p>
        </div>
        <button className="px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition flex items-center gap-2">
          <Plus className="w-4 h-4" />
          Add Key
        </button>
      </div>

      {/* API Keys List */}
      <div className="space-y-4">
        {apiKeys.map((apiKey) => (
          <div key={apiKey.id} className="bg-card border border-border rounded-lg p-6 hover:shadow-lg transition">
            <div className="flex items-start justify-between mb-4">
              <div>
                <h3 className="text-lg font-bold text-foreground">{apiKey.name}</h3>
                <p className="text-sm text-muted-foreground">{apiKey.environment}</p>
              </div>
              <div className="flex items-center gap-2">
                <span className={`px-3 py-1 rounded-full text-xs font-medium ${
                  apiKey.status === 'active'
                    ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
                    : 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-300'
                }`}>
                  {apiKey.status}
                </span>
              </div>
            </div>

            {/* Key Display */}
            <div className="bg-muted/50 rounded-lg p-3 mb-4 flex items-center justify-between">
              <code className="text-sm text-foreground font-mono">{apiKey.key}</code>
              <button className="p-2 hover:bg-muted/50 rounded transition text-muted-foreground hover:text-foreground">
                <Copy className="w-4 h-4" />
              </button>
            </div>

            {/* Details */}
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mb-4 pb-4 border-b border-border">
              <div>
                <p className="text-xs text-muted-foreground mb-1">Created</p>
                <p className="text-sm font-medium text-foreground">{apiKey.created}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground mb-1">Last Used</p>
                <p className="text-sm font-medium text-foreground">{apiKey.lastUsed}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground mb-1">Expires In</p>
                <p className="text-sm font-medium text-orange-600">90 days</p>
              </div>
            </div>

            {/* Actions */}
            <div className="flex gap-2">
              <button className="px-3 py-2 bg-muted hover:bg-muted/80 text-foreground rounded-lg text-sm transition flex items-center gap-2">
                <Edit2 className="w-4 h-4" />
                Edit
              </button>
              <button className="px-3 py-2 border border-border hover:bg-muted/50 text-foreground rounded-lg text-sm transition flex items-center gap-2">
                Rotate
              </button>
              <button className="px-3 py-2 border border-red-200 dark:border-red-900 hover:bg-red-50 dark:hover:bg-red-950/20 text-red-600 dark:text-red-400 rounded-lg text-sm transition flex items-center gap-2 ml-auto">
                <Trash2 className="w-4 h-4" />
                Revoke
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Key Management Info */}
      <div className="bg-blue-50 dark:bg-blue-950/20 border border-blue-200 dark:border-blue-900 rounded-lg p-6">
        <h3 className="text-sm font-bold text-blue-900 dark:text-blue-200 mb-2">API Key Security</h3>
        <ul className="text-sm text-blue-800 dark:text-blue-300 space-y-1 list-disc list-inside">
          <li>Keep your API keys secure and never share them publicly</li>
          <li>Rotate keys regularly (at least every 90 days)</li>
          <li>Use different keys for production and sandbox environments</li>
          <li>Monitor key usage and set up alerts for unusual activity</li>
        </ul>
      </div>
    </div>
  );
}
