'use client';

import { TrendingUp, PieChart, BarChart3 } from 'lucide-react';

export default function AnalyticsPage() {
  const metrics = [
    { label: 'Total Revenue (INR)', value: '₹45,231', change: '+12.5%' },
    { label: 'Success Rate', value: '98.5%', change: '+2.1%' },
    { label: 'Avg Transaction Value', value: '₹1,847', change: '+5.2%' },
    { label: 'Total Transactions', value: '1,234', change: '+8.2%' },
  ];

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-foreground flex items-center gap-2">
          <TrendingUp className="w-8 h-8" />
          Analytics
        </h1>
        <p className="text-muted-foreground mt-2">Payment metrics and performance analytics</p>
      </div>

      {/* Metrics Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {metrics.map((metric) => (
          <div key={metric.label} className="bg-card border border-border rounded-lg p-6">
            <p className="text-sm text-muted-foreground mb-2">{metric.label}</p>
            <p className="text-2xl font-bold text-foreground">{metric.value}</p>
            <p className="text-xs text-green-600 font-medium mt-2">{metric.change} this month</p>
          </div>
        ))}
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Revenue Chart Placeholder */}
        <div className="bg-card border border-border rounded-lg p-6">
          <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
            <BarChart3 className="w-5 h-5 text-primary" />
            Revenue Trend
          </h2>
          <div className="h-64 flex items-center justify-center bg-muted/30 rounded-lg">
            <div className="text-center">
              <BarChart3 className="w-12 h-12 text-muted-foreground mx-auto mb-2 opacity-50" />
              <p className="text-muted-foreground text-sm">Revenue chart placeholder</p>
              <p className="text-xs text-muted-foreground mt-1">Integrate with Recharts for visualization</p>
            </div>
          </div>
        </div>

        {/* Payment Method Distribution */}
        <div className="bg-card border border-border rounded-lg p-6">
          <h2 className="text-lg font-bold text-foreground mb-4 flex items-center gap-2">
            <PieChart className="w-5 h-5 text-primary" />
            Payment Methods
          </h2>
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3 flex-1">
                <div className="w-3 h-3 rounded-full bg-blue-500"></div>
                <span className="text-sm text-foreground">Credit Card</span>
              </div>
              <span className="text-sm font-semibold text-foreground">45%</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3 flex-1">
                <div className="w-3 h-3 rounded-full bg-green-500"></div>
                <span className="text-sm text-foreground">UPI</span>
              </div>
              <span className="text-sm font-semibold text-foreground">35%</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3 flex-1">
                <div className="w-3 h-3 rounded-full bg-purple-500"></div>
                <span className="text-sm text-foreground">Wallet</span>
              </div>
              <span className="text-sm font-semibold text-foreground">15%</span>
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3 flex-1">
                <div className="w-3 h-3 rounded-full bg-orange-500"></div>
                <span className="text-sm text-foreground">Net Banking</span>
              </div>
              <span className="text-sm font-semibold text-foreground">5%</span>
            </div>
          </div>
        </div>
      </div>

      {/* Plan Tier Distribution */}
      <div className="bg-card border border-border rounded-lg p-6">
        <h2 className="text-lg font-bold text-foreground mb-4">User Distribution by Plan</h2>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="bg-blue-50 dark:bg-blue-950/20 rounded-lg p-4">
            <p className="text-sm text-muted-foreground mb-1">Free</p>
            <p className="text-2xl font-bold text-blue-600 dark:text-blue-400">234</p>
            <p className="text-xs text-muted-foreground mt-2">35% of users</p>
          </div>
          <div className="bg-green-50 dark:bg-green-950/20 rounded-lg p-4">
            <p className="text-sm text-muted-foreground mb-1">Basic (₹99)</p>
            <p className="text-2xl font-bold text-green-600 dark:text-green-400">189</p>
            <p className="text-xs text-muted-foreground mt-2">28% of users</p>
          </div>
          <div className="bg-purple-50 dark:bg-purple-950/20 rounded-lg p-4">
            <p className="text-sm text-muted-foreground mb-1">Pro (₹299)</p>
            <p className="text-2xl font-bold text-purple-600 dark:text-purple-400">156</p>
            <p className="text-xs text-muted-foreground mt-2">23% of users</p>
          </div>
          <div className="bg-orange-50 dark:bg-orange-950/20 rounded-lg p-4">
            <p className="text-sm text-muted-foreground mb-1">Premium (₹599)</p>
            <p className="text-2xl font-bold text-orange-600 dark:text-orange-400">98</p>
            <p className="text-xs text-muted-foreground mt-2">14% of users</p>
          </div>
        </div>
      </div>
    </div>
  );
}
