'use client';

import { Users, Clock } from 'lucide-react';

export default function ActivityPage() {
  const activities = [
    { id: 1, user: 'john@example.com', action: 'Upgraded to Pro', timestamp: '2 hours ago', amount: '₹299' },
    { id: 2, user: 'jane@example.com', action: 'Payment captured', timestamp: '3 hours ago', amount: '₹499' },
    { id: 3, user: 'bob@example.com', action: 'Downgraded to Basic', timestamp: '5 hours ago', amount: '₹99' },
    { id: 4, user: 'alice@example.com', action: 'Refund processed', timestamp: '6 hours ago', amount: '₹299' },
    { id: 5, user: 'user@example.com', action: 'New subscription', timestamp: '8 hours ago', amount: '₹599' },
  ];

  const getActionColor = (action: string) => {
    if (action.includes('Upgrade')) return 'text-green-600';
    if (action.includes('Downgrade')) return 'text-orange-600';
    if (action.includes('Refund')) return 'text-red-600';
    return 'text-blue-600';
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-foreground flex items-center gap-2">
          <Users className="w-8 h-8" />
          User Activity
        </h1>
        <p className="text-muted-foreground mt-2">Track user actions and subscription changes</p>
      </div>

      {/* Activity Timeline */}
      <div className="bg-card border border-border rounded-lg p-6">
        <h2 className="text-lg font-bold text-foreground mb-6">Recent Activity</h2>
        
        <div className="space-y-4">
          {activities.map((activity, index) => (
            <div key={activity.id} className="flex gap-4 pb-4 border-b border-border last:border-0">
              {/* Timeline dot */}
              <div className="flex flex-col items-center">
                <div className="w-3 h-3 rounded-full bg-primary mt-1.5" />
                {index !== activities.length - 1 && (
                  <div className="w-0.5 h-12 bg-border mt-2" />
                )}
              </div>

              {/* Activity content */}
              <div className="flex-1 min-w-0">
                <div className="flex items-start justify-between mb-2">
                  <div>
                    <p className="font-semibold text-foreground">{activity.user}</p>
                    <p className={`text-sm font-medium ${getActionColor(activity.action)}`}>
                      {activity.action}
                    </p>
                  </div>
                  <span className="text-sm font-bold text-foreground whitespace-nowrap ml-2">
                    {activity.amount}
                  </span>
                </div>
                <p className="text-xs text-muted-foreground flex items-center gap-1">
                  <Clock className="w-3 h-3" />
                  {activity.timestamp}
                </p>
              </div>
            </div>
          ))}
        </div>

        <button className="w-full mt-6 text-primary hover:text-primary/80 text-sm font-medium transition">
          Load more activities →
        </button>
      </div>

      {/* Activity Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-card border border-border rounded-lg p-6">
          <p className="text-sm text-muted-foreground mb-2">Upgrades This Month</p>
          <p className="text-3xl font-bold text-foreground">234</p>
          <p className="text-xs text-green-600 font-medium mt-2">↑ 12% from last month</p>
        </div>
        <div className="bg-card border border-border rounded-lg p-6">
          <p className="text-sm text-muted-foreground mb-2">Downgrades This Month</p>
          <p className="text-3xl font-bold text-foreground">23</p>
          <p className="text-xs text-red-600 font-medium mt-2">↓ 5% from last month</p>
        </div>
        <div className="bg-card border border-border rounded-lg p-6">
          <p className="text-sm text-muted-foreground mb-2">Refunds This Month</p>
          <p className="text-3xl font-bold text-foreground">12</p>
          <p className="text-xs text-orange-600 font-medium mt-2">2.4% refund rate</p>
        </div>
      </div>
    </div>
  );
}
