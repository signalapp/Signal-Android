'use client';

import { CreditCard, Download, Filter, Search } from 'lucide-react';

export default function TransactionsPage() {
  const transactions = [
    { id: 'ORD-001', orderId: 'RAZ-1001', amount: '₹2,499', status: 'captured', date: '2024-01-15', user: 'user@example.com' },
    { id: 'ORD-002', orderId: 'RAZ-1002', amount: '₹1,999', status: 'captured', date: '2024-01-14', user: 'john@example.com' },
    { id: 'ORD-003', orderId: 'RAZ-1003', amount: '₹999', status: 'failed', date: '2024-01-14', user: 'jane@example.com' },
    { id: 'ORD-004', orderId: 'RAZ-1004', amount: '₹3,499', status: 'captured', date: '2024-01-13', user: 'bob@example.com' },
    { id: 'ORD-005', orderId: 'RAZ-1005', amount: '₹2,199', status: 'refunded', date: '2024-01-13', user: 'alice@example.com' },
  ];

  const getStatusBadge = (status: string) => {
    const statusStyles = {
      captured: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
      failed: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
      refunded: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300',
      created: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    };
    return (
      <span className={`px-3 py-1 rounded-full text-xs font-medium capitalize ${statusStyles[status as keyof typeof statusStyles] || statusStyles.created}`}>
        {status}
      </span>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-foreground flex items-center gap-2">
          <CreditCard className="w-8 h-8" />
          Transactions
        </h1>
        <p className="text-muted-foreground mt-2">View and manage all payment transactions</p>
      </div>

      {/* Controls */}
      <div className="flex gap-3 flex-wrap">
        <div className="flex-1 min-w-xs bg-card border border-border rounded-lg flex items-center px-4 gap-2">
          <Search className="w-4 h-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search by order ID or email..."
            className="flex-1 py-2 bg-transparent text-foreground placeholder-muted-foreground outline-none"
          />
        </div>
        <button className="px-4 py-2 bg-card border border-border rounded-lg text-foreground hover:bg-muted/50 transition flex items-center gap-2">
          <Filter className="w-4 h-4" />
          Filter
        </button>
        <button className="px-4 py-2 bg-card border border-border rounded-lg text-foreground hover:bg-muted/50 transition flex items-center gap-2">
          <Download className="w-4 h-4" />
          Export
        </button>
      </div>

      {/* Table */}
      <div className="bg-card border border-border rounded-lg overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="bg-muted/50 border-b border-border">
                <th className="px-6 py-3 text-left text-sm font-semibold text-foreground">Order ID</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-foreground">Razorpay ID</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-foreground">Amount</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-foreground">Status</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-foreground">Date</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-foreground">User</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-foreground">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {transactions.map((tx) => (
                <tr key={tx.id} className="hover:bg-muted/30 transition">
                  <td className="px-6 py-3 text-sm font-medium text-foreground">{tx.id}</td>
                  <td className="px-6 py-3 text-sm text-muted-foreground font-mono">{tx.orderId}</td>
                  <td className="px-6 py-3 text-sm font-semibold text-foreground">{tx.amount}</td>
                  <td className="px-6 py-3 text-sm">{getStatusBadge(tx.status)}</td>
                  <td className="px-6 py-3 text-sm text-muted-foreground">{tx.date}</td>
                  <td className="px-6 py-3 text-sm text-muted-foreground">{tx.user}</td>
                  <td className="px-6 py-3 text-sm">
                    <button className="text-primary hover:text-primary/80 transition font-medium">
                      View
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">Showing 1 to 5 of 156 transactions</p>
        <div className="flex gap-2">
          <button className="px-3 py-2 border border-border rounded-lg text-foreground hover:bg-muted/50 transition disabled:opacity-50" disabled>
            Previous
          </button>
          <button className="px-3 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition">
            1
          </button>
          <button className="px-3 py-2 border border-border rounded-lg text-foreground hover:bg-muted/50 transition">
            2
          </button>
          <button className="px-3 py-2 border border-border rounded-lg text-foreground hover:bg-muted/50 transition">
            Next
          </button>
        </div>
      </div>
    </div>
  );
}
