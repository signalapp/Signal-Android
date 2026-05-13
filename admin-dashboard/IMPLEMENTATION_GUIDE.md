# Razorpay Admin Dashboard - Implementation Guide

## Project Overview

A comprehensive React-based admin dashboard for managing Razorpay payments with exclusive INR support. Features include transaction monitoring, user activity tracking, real-time analytics, and API key management.

## Architecture

### Frontend Stack
- **Framework**: Next.js 14 (App Router)
- **UI**: React 18 with TypeScript
- **Styling**: Tailwind CSS with custom dark theme
- **State Management**: Zustand with persist middleware
- **HTTP Client**: Axios with interceptors
- **Charts**: Recharts for analytics visualization
- **Icons**: Lucide React
- **Date Handling**: date-fns

### Security Features
- AES-256 encryption for API keys using CryptoJS
- HMAC-SHA256 signature verification
- Secure session management with token expiration
- Role-based access control (Admin, Analyst, Viewer)
- HTTP security headers (CSP, X-Frame-Options, etc.)
- No sensitive data in logs

### Direct Razorpay API Integration
- Connects directly to Razorpay API via Basic Auth
- No backend server required
- Encrypted API key storage in browser
- Real-time payment data access

## File Structure

```
admin-dashboard/
├── app/
│   ├── layout.tsx           # Root layout
│   ├── page.tsx             # Login page
│   ├── globals.css          # Theme and global styles
│   └── dashboard/
│       ├── layout.tsx       # Dashboard layout with sidebar/header
│       ├── page.tsx         # Overview page with KPIs
│       ├── transactions/
│       │   ├── page.tsx     # Transaction list with filtering
│       │   └── [id]/
│       │       └── page.tsx # Transaction details
│       ├── analytics/
│       │   └── page.tsx     # Analytics with charts
│       ├── activity/
│       │   └── page.tsx     # User activity log
│       ├── api-keys/
│       │   ├── page.tsx     # API key management
│       │   └── [keyId]/
│       │       └── page.tsx # Key details
│       └── settings/
│           └── page.tsx     # Settings and config
├── components/
│   ├── Sidebar.tsx          # Navigation sidebar
│   ├── Header.tsx           # Top header with search
│   ├── KPICard.tsx          # KPI metric card
│   ├── StatCard.tsx         # Stat display card
│   ├── TransactionTable.tsx # Transactions list
│   ├── FilterPanel.tsx      # Advanced filters
│   ├── SearchBox.tsx        # Transaction search
│   ├── ExportButton.tsx     # CSV/PDF export
│   ├── Charts/
│   │   ├── RevenueChart.tsx
│   │   ├── SuccessRateChart.tsx
│   │   ├── PaymentMethodChart.tsx
│   │   └── TierDistributionChart.tsx
│   └── Modals/
│       ├── ApiKeyModal.tsx
│       ├── RefundModal.tsx
│       └── ConfirmDialog.tsx
├── lib/
│   ├── razorpay-client.ts   # Razorpay API wrapper
│   ├── crypto.ts            # Encryption utilities
│   ├── store/
│   │   ├── auth-store.ts    # Auth state management
│   │   └── data-store.ts    # Data state management
│   └── utils.ts             # Helper functions
├── types/
│   └── index.ts             # TypeScript type definitions
├── hooks/
│   ├── useRazorpay.ts       # Razorpay API hook
│   ├── useTransactions.ts   # Transaction fetching hook
│   ├── useAnalytics.ts      # Analytics hook
│   └── useExport.ts         # Export functionality
├── package.json
├── tsconfig.json
├── tailwind.config.ts
├── postcss.config.js
└── next.config.js
```

## Component Descriptions

### Pages

#### Login (app/page.tsx)
- Simple email/password authentication
- Demo credentials support
- Session management
- Redirect to dashboard on success

#### Dashboard Overview (app/dashboard/page.tsx)
- KPI cards: Total Revenue, Transactions, Success Rate, etc.
- Quick actions: View transactions, export data, rotate API keys
- Recent transactions widget
- Performance metrics
- New user signups

#### Transactions (app/dashboard/transactions/page.tsx)
- List of all payments with status
- Advanced filtering by:
  - Date range
  - Amount range
  - Status (created, authorized, captured, failed, refunded)
  - Plan tier
  - Payment method
- Full-text search by transaction ID, order ID, user email
- Sorting by date, amount, status
- Pagination with customizable page size
- Refund processing
- CSV/PDF export

#### Transaction Details (app/dashboard/transactions/[id]/page.tsx)
- Complete payment information
- Order and payment IDs
- Customer details
- Timeline of status changes
- Refund history
- Webhook logs
- Action buttons: Refund, Retry, Export

#### Analytics (app/dashboard/analytics/page.tsx)
- Revenue chart (daily/weekly/monthly)
- Transaction success rate
- Payment method distribution pie chart
- Plan tier distribution
- User growth metrics
- Top performing periods
- Anomaly detection alerts
- Export analytics data

#### User Activity (app/dashboard/activity/page.tsx)
- Timeline of user actions
- Filter by: action type, date range, user ID/email
- Activity types: upgrade, downgrade, refund, subscribe, cancel
- User details with link to transaction
- IP address and user agent tracking
- Export activity logs

#### API Keys (app/dashboard/api-keys/page.tsx)
- List of stored API keys
- Add new API key with validation
- Edit key details (name, environment)
- Rotate keys (creates new key, deactivates old)
- Revoke/delete keys
- Test API connectivity
- Usage statistics
- Key expiration alerts

#### Settings (app/dashboard/settings/page.tsx)
- Admin preferences
- Session timeout configuration
- Export default settings
- Backup/restore configuration
- API key rotation reminders
- Email notification preferences

### Components

#### KPICard.tsx
Displays key performance indicator with:
- Title and value
- Change percentage
- Trend indicator (up/down)
- Optional target value

#### StatCard.tsx
Displays statistic with:
- Label and value
- Icon
- Formatting options (currency, percentage)

#### TransactionTable.tsx
- Sortable columns
- Inline status badges
- Quick action buttons
- Responsive design
- Loading states

#### FilterPanel.tsx
- Date range picker
- Amount range slider
- Status multi-select
- Tier selection
- Payment method filters
- Search box integration

#### Charts/ Components
- Revenue trend chart with Recharts
- Success rate metric chart
- Payment method distribution pie
- Tier distribution bar chart
- Responsive sizing
- Custom colors matching theme

### Hooks

#### useRazorpay()
```typescript
const { client, isLoading, error } = useRazorpay();
```
- Provides Razorpay client instance
- Handles API key from auth store
- Error handling and logging

#### useTransactions()
```typescript
const {
  transactions,
  pagination,
  filters,
  loading,
  fetchTransactions,
  updateFilters,
  refundTransaction,
} = useTransactions();
```

#### useAnalytics()
```typescript
const {
  metrics,
  chartData,
  loading,
  fetchMetrics,
  exportAnalytics,
} = useAnalytics();
```

#### useExport()
```typescript
const { exportCSV, exportPDF, exporting } = useExport();
```

## State Management

### Auth Store (Zustand)
```typescript
- user: AdminUser | null
- token: string | null
- activeApiKey: RazorpayApiKey | null
- apiKeys: RazorpayApiKey[]
- isAuthenticated(): boolean
- hasValidApiKey(): boolean
- setSession(session): void
- logout(): void
```

### Data Store (Zustand)
```typescript
- transactions: Transaction[]
- activities: UserActivity[]
- metrics: DashboardMetrics | null
- chartData: ChartData[]
- transactionFilters: Record<string, any>
- activityFilters: Record<string, any>
- isLoading: boolean states
```

## API Integration

### Razorpay Client Methods

```typescript
// Authentication
client.validateCredentials(): Promise<boolean>

// Payments
client.getPayments(filters): Promise<{items, total}>
client.getPayment(paymentId): Promise<RazorpayPayment>

// Orders
client.getOrders(filters): Promise<{items, total}>
client.getOrder(orderId): Promise<Order>

// Refunds
client.getRefunds(paymentId): Promise<Refund[]>
client.createRefund(paymentId, options): Promise<Refund>

// Analytics
client.getSettlements(filters): Promise<{items, total}>
client.getInvoices(filters): Promise<{items, total}>
client.getTransfers(filters): Promise<{items, total}>
```

## Security Implementation

### API Key Encryption
- AES-256-GCM encryption with PBKDF2
- Random IV and salt per encryption
- Encrypted in browser before transmission
- Never logged or displayed in full

### Session Management
- JWT-style tokens with expiration
- Automatic logout on expiration
- Session storage in Zustand persist
- HTTPS enforcement (next.config.js)

### Secure Headers
- X-Content-Type-Options: nosniff
- X-Frame-Options: DENY
- X-XSS-Protection enabled
- Referrer-Policy: strict-origin-when-cross-origin

### Data Privacy
- No sensitive data in logs
- Masked display of API keys (first 4, last 4 chars)
- Audit logging of key rotations
- GDPR-compliant user data handling

## Environment Variables

Create `.env.local`:
```bash
# Encryption key for API keys (generate securely in production)
NEXT_PUBLIC_ENCRYPTION_KEY=your-secure-key-here

# Razorpay API endpoints (direct to Razorpay)
# No backend API endpoint needed
```

## Setup Instructions

### 1. Install Dependencies
```bash
cd admin-dashboard
npm install
# or
pnpm install
```

### 2. Configure Environment
```bash
# Create .env.local with encryption key
echo "NEXT_PUBLIC_ENCRYPTION_KEY=$(openssl rand -base64 32)" > .env.local
```

### 3. Run Development Server
```bash
npm run dev
# Open http://localhost:3000
```

### 4. Build for Production
```bash
npm run build
npm start
```

## Features Implemented

### Phase 1: Foundation ✓
- Project setup with Next.js 14
- TypeScript configuration
- Tailwind CSS with dark theme
- Global styles and animations
- Type definitions
- Zustand stores
- Encryption utilities
- Razorpay API client

### Phase 2: Core Dashboard ✓
- Login page with demo mode
- Dashboard layout with sidebar
- Navigation
- Header with search
- API key management UI
- Authentication flow

### Phase 3: Transaction Management (In Progress)
- Transaction list with filtering
- Advanced search
- Transaction details page
- Refund processing
- CSV/PDF export
- Pagination

### Phase 4: Analytics
- Revenue charts
- Success rate metrics
- Payment method distribution
- User tier breakdown
- Real-time updates via polling

### Phase 5: User Activity & Polish
- Activity timeline
- User tracking
- Deployment configuration
- Security hardening
- Testing setup

## Performance Optimization

- Next.js Image optimization
- Code splitting per route
- Lazy loading of components
- Memoization of expensive computations
- Debounced search and filters
- Pagination for large datasets
- Compressed API responses

## Monitoring & Logging

- Structured logging with context
- Error tracking preparation
- API call monitoring
- Performance metrics
- Audit logging for sensitive operations

## Future Enhancements

- Webhook support for real-time updates
- Advanced predictive analytics
- Machine learning anomaly detection
- Multi-user collaboration
- Role-based feature flags
- Mobile responsive optimization
- Progressive Web App (PWA)
- Offline mode support

## Troubleshooting

### API Key Not Working
1. Verify key format (alphanumeric)
2. Check environment (sandbox vs production)
3. Confirm key hasn't expired
4. Test in Razorpay dashboard
5. Check browser console for errors

### Data Not Loading
1. Verify API key is active
2. Check network tab for API responses
3. Ensure date filters are correct
4. Check pagination settings
5. Review console for error messages

### Performance Issues
1. Reduce page size (transactions per page)
2. Use more specific date ranges
3. Clear browser cache
4. Check network latency
5. Monitor CPU usage

## Support & Resources

- Razorpay API Documentation: https://razorpay.com/docs/api/
- Next.js Documentation: https://nextjs.org/docs
- Tailwind CSS: https://tailwindcss.com
- Zustand: https://github.com/pmndrs/zustand
