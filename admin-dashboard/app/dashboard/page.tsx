'use client';

import { BarChart3, CreditCard, TrendingUp, AlertCircle, DollarSign, Users } from 'lucide-react';

export default function DashboardPage() {
  // KPI Cards data
  const kpis = [
    {
      title: 'Total Revenue',
      value: '₹45,231',
      change: '+12.5%',
      icon: DollarSign,
      color: 'bg-blue-500',
    },
    {
      title: 'Total Transactions',
      value: '1,234',
      change: '+8.2%',
      icon: CreditCard,
      color: 'bg-green-500',
    },
    {
      title: 'Success Rate',
      value: '98.5%',
      change: '+2.1%',
      icon: TrendingUp,
      color: 'bg-purple-500',
    },
    {
      title: 'Active Users',
      value: '456',
      change: '+15.3%',
      icon: Users,
      color: 'bg-orange-500',
    },
  ];

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div>
        <h1 className="text-3xl font-bold text-foreground">Dashboard</h1>
        <p className="text-muted-foreground mt-2">Welcome back! Here's what's happening with your payments today.</p>
      </div>

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {kpis.map((kpi) => {
          const Icon = kpi.icon;
          return (
            <div key={kpi.title} className="bg-card border border-border rounded-lg p-6 hover:shadow-lg transition">
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-sm font-medium text-muted-foreground">{kpi.title}</h3>
                <div className={`${kpi.color} p-2 rounded-lg`}>
                  <Icon className="w-4 h-4 text-white" />
                </div>
              </div>
              <div className="space-y-2">
                <p className="text-2xl font-bold text-foreground">{kpi.value}</p>
                <p className="text-xs text-green-600 font-medium">{kpi.change} from last month</p>
              </div>
            </div>
          );
        })}
      </div>

      {/* Quick Stats Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Transactions */}
        <div className="bg-card border border-border rounded-lg p-6">
          <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
            <CreditCard className="w-5 h-5 text-primary" />
            Recent Transactions
          </h2>
          <div className="space-y-3">
            <div className="flex justify-between items-center pb-3 border-b border-border">
              <div>
                <p className="font-medium text-foreground">Order #ORD-001</p>
                <p className="text-xs text-muted-foreground">2 hours ago</p>
              </div>
              <span className="text-sm font-bold text-green-600">₹2,499</span>
            </div>
            <div className="flex justify-between items-center pb-3 border-b border-border">
              <div>
                <p className="font-medium text-foreground">Order #ORD-002</p>
                <p className="text-xs text-muted-foreground">4 hours ago</p>
              </div>
              <span className="text-sm font-bold text-green-600">₹1,999</span>
            </div>
            <div className="flex justify-between items-center pb-3 border-b border-border">
              <div>
                <p className="font-medium text-foreground">Order #ORD-003</p>
                <p className="text-xs text-muted-foreground">6 hours ago</p>
              </div>
              <span className="text-sm font-bold text-red-600">Failed</span>
            </div>
          </div>
          <button className="w-full mt-4 text-primary hover:text-primary/80 text-sm font-medium transition">
            View all transactions →
          </button>
        </div>

        {/* Alerts */}
        <div className="bg-card border border-border rounded-lg p-6">
          <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
            <AlertCircle className="w-5 h-5 text-orange-500" />
            Alerts & Notifications
          </h2>
          <div className="space-y-3">
            <div className="bg-orange-50 dark:bg-orange-950/20 border border-orange-200 dark:border-orange-800 rounded-lg p-3">
              <p className="text-sm font-medium text-orange-900 dark:text-orange-200">
                API Key expiring soon
              </p>
              <p className="text-xs text-orange-700 dark:text-orange-300 mt-1">
                Your production API key will expire in 7 days
              </p>
            </div>
            <div className="bg-green-50 dark:bg-green-950/20 border border-green-200 dark:border-green-800 rounded-lg p-3">
              <p className="text-sm font-medium text-green-900 dark:text-green-200">
                System Status: Operational
              </p>
              <p className="text-xs text-green-700 dark:text-green-300 mt-1">
                All systems running normally
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Actions */}
      <div className="bg-gradient-to-r from-primary/10 to-primary/5 border border-primary/20 rounded-lg p-6">
        <h2 className="text-lg font-bold text-foreground mb-2">Quick Actions</h2>
        <p className="text-muted-foreground text-sm mb-4">
          Manage your payments and settings
        </p>
        <div className="flex gap-3 flex-wrap">
          <button className="px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition">
            View Transactions
          </button>
          <button className="px-4 py-2 border border-primary text-primary rounded-lg text-sm font-medium hover:bg-primary/10 transition">
            Manage API Keys
          </button>
          <button className="px-4 py-2 border border-border text-foreground rounded-lg text-sm font-medium hover:bg-muted/50 transition">
            View Analytics
          </button>
        </div>
      </div>
    </div>
  );
}
