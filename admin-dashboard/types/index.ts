// API Key Management
export interface RazorpayApiKey {
  id: string;
  keyId: string;
  keySecret?: string; // Only populated during creation/edit
  environment: "sandbox" | "production";
  status: "active" | "inactive" | "expired";
  createdAt: string;
  lastRotatedAt?: string;
  expiresAt?: string;
}

// Transaction Types
export interface Transaction {
  id: string;
  orderId: string;
  paymentId?: string;
  userId: string;
  amount: number; // In paisa
  amountInRupees: number; // In INR
  currency: "INR";
  status: "created" | "authorized" | "captured" | "failed" | "refunded";
  paymentMethod?: string;
  description?: string;
  planTier: "free" | "basic" | "pro" | "premium";
  createdAt: string;
  updatedAt: string;
  metadata?: Record<string, any>;
  refunds?: Refund[];
}

export interface Refund {
  id: string;
  transactionId: string;
  amount: number;
  currency: "INR";
  status: "pending" | "processed" | "failed";
  reason?: string;
  createdAt: string;
  updatedAt: string;
}

// User Activity
export interface UserActivity {
  id: string;
  userId: string;
  email?: string;
  action: "upgrade" | "downgrade" | "refund" | "subscribe" | "cancel";
  previousTier?: string;
  newTier?: string;
  amount?: number;
  transactionId?: string;
  ipAddress?: string;
  userAgent?: string;
  timestamp: string;
}

// Dashboard Analytics
export interface DashboardMetrics {
  totalRevenue: number;
  totalTransactions: number;
  successRate: number;
  averageTransactionValue: number;
  activeSubscriptions: number;
  failedTransactions: number;
  refundedAmount: number;
  periodGrowth: number;
}

export interface ChartData {
  timestamp: string;
  date: string;
  revenue: number;
  transactions: number;
  successCount: number;
  failureCount: number;
}

export interface PaymentMethodDistribution {
  method: string;
  count: number;
  percentage: number;
  totalAmount: number;
}

export interface TierDistribution {
  tier: "free" | "basic" | "pro" | "premium";
  count: number;
  percentage: number;
  monthlyRevenue: number;
}

// Filters and Search
export interface TransactionFilters {
  status?: string[];
  dateFrom?: string;
  dateTo?: string;
  minAmount?: number;
  maxAmount?: number;
  planTier?: string[];
  paymentMethod?: string[];
  sortBy?: "date" | "amount" | "status";
  sortOrder?: "asc" | "desc";
  page?: number;
  limit?: number;
}

export interface ActivityFilters {
  action?: string[];
  dateFrom?: string;
  dateTo?: string;
  userId?: string;
  email?: string;
  sortBy?: "timestamp" | "action";
  sortOrder?: "asc" | "desc";
  page?: number;
  limit?: number;
}

// API Response Types
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
  pagination?: {
    total: number;
    page: number;
    limit: number;
    pages: number;
  };
}

export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  limit: number;
  pages: number;
}

// Authentication
export interface AdminUser {
  id: string;
  email: string;
  name: string;
  role: "admin" | "analyst" | "viewer";
  createdAt: string;
  lastLogin?: string;
}

export interface AuthSession {
  user: AdminUser;
  token: string;
  expiresAt: string;
}

// Razorpay API Response Types
export interface RazorpayPayment {
  id: string;
  entity: string;
  amount: number;
  currency: string;
  status: string;
  method: string;
  description: string;
  amount_refunded: number;
  refund_status: string | null;
  captured: boolean;
  description: string;
  card_id: string | null;
  bank: string | null;
  wallet: string | null;
  vpa: string | null;
  email: string;
  contact: string;
  fee: number;
  tax: number;
  error_code: string | null;
  error_description: string | null;
  error_source: string | null;
  error_reason: string | null;
  error_step: string | null;
  error_field: string | null;
  notes: Record<string, any>;
  created_at: number;
}
