# Razorpay Admin Dashboard

A professional, feature-rich admin dashboard for managing Razorpay payments with exclusive Indian Rupee (INR) support. Built with Next.js 14, React 18, TypeScript, and Tailwind CSS.

## Key Features

### Transaction Management
- Complete payment history with advanced filtering
- Status tracking (created, authorized, captured, failed, refunded)
- Full-text search by transaction ID, order ID, email
- Date range and amount filtering
- Batch refund processing
- CSV/PDF export functionality
- Real-time transaction updates

### Analytics & Reporting
- Revenue trends and forecasting
- Payment method distribution analysis
- User tier breakdown and adoption
- Success rate metrics and anomaly detection
- Custom date range reports
- Exportable analytics data

### User Activity Tracking
- Timeline of user actions (upgrades, downgrades, refunds)
- User details and subscription history
- IP address and device tracking
- Audit logs for compliance
- Filter by action type and date range

### API Key Management
- Secure encrypted storage of Razorpay API keys
- Add, edit, rotate, and delete keys
- Test API connectivity
- Key expiration alerts
- Usage statistics per key
- Sandbox and production environments

### Security Features
- AES-256 encryption for sensitive data
- Role-based access control (Admin, Analyst, Viewer)
- Session management with auto-logout
- Secure HTTP headers
- HMAC signature verification
- Audit logging of all operations
- No sensitive data in logs

## Quick Start

### Prerequisites
- Node.js 18+ and npm/pnpm
- Razorpay API keys (sandbox or production)

### Installation

```bash
# Install dependencies
npm install
# or
pnpm install

# Create environment configuration
echo "NEXT_PUBLIC_ENCRYPTION_KEY=$(openssl rand -base64 32)" > .env.local

# Run development server
npm run dev
```

Visit `http://localhost:3000` and login with any email and password (min 6 chars) for demo mode.

### Production Build

```bash
npm run build
npm run start
```

## Configuration

### Environment Variables

Create `.env.local`:

```bash
# Required: Encryption key for API keys
# Generate with: openssl rand -base64 32
NEXT_PUBLIC_ENCRYPTION_KEY=your-secure-32-byte-base64-key

# Optional: API Gateway
NEXT_PUBLIC_API_BASE_URL=https://your-api.com

# Optional: Feature flags
NEXT_PUBLIC_ENABLE_WEBHOOKS=true
NEXT_PUBLIC_ANALYTICS_ENABLED=true
```

## Architecture

### Frontend Stack
- **Next.js 14**: App Router with Server/Client components
- **React 18**: Latest features and optimizations
- **TypeScript**: Full type safety
- **Tailwind CSS**: Utility-first styling with dark theme
- **Zustand**: Lightweight state management
- **Axios**: HTTP client with interceptors
- **Recharts**: Data visualization
- **date-fns**: Date manipulation

### State Management

Two Zustand stores for optimal performance:

**Auth Store**: User session, API keys, authentication state
**Data Store**: Transactions, activities, metrics, filters, pagination

### API Integration

Direct integration with Razorpay API using Basic Auth (no backend server required).

## Project Structure

```
admin-dashboard/
├── app/                    # Next.js App Router
│   ├── dashboard/         # Main dashboard pages
│   ├── layout.tsx         # Root layout
│   ├── page.tsx           # Login page
│   └── globals.css        # Theme variables
├── components/            # Reusable React components
│   ├── Sidebar.tsx        # Navigation
│   ├── Header.tsx         # Top bar
│   ├── Charts/            # Analytics charts
│   └── Modals/            # Dialog components
├── lib/
│   ├── razorpay-client.ts # API wrapper
│   ├── crypto.ts          # Encryption utilities
│   ├── store/             # Zustand stores
│   └── utils.ts           # Helper functions
├── types/                 # TypeScript definitions
├── hooks/                 # Custom React hooks
└── styles/                # CSS files
```

## Pages & Features

### Dashboard Overview (`/dashboard`)
- KPI metrics (revenue, transactions, success rate)
- Quick actions
- Recent transactions
- Performance alerts

### Transactions (`/dashboard/transactions`)
- Complete payment list with filtering
- Advanced search functionality
- Transaction details view
- Refund processing
- Export to CSV/PDF

### Analytics (`/dashboard/analytics`)
- Revenue trends chart
- Success rate metrics
- Payment method distribution
- User tier breakdown
- Period comparison
- Anomaly alerts

### User Activity (`/dashboard/activity`)
- Timeline of all user actions
- Filter by action type and date
- User details integration
- Audit trail

### API Keys (`/dashboard/api-keys`)
- List of API keys
- Add/edit/delete/rotate keys
- Test connectivity
- Expiration alerts
- Usage metrics

### Settings (`/dashboard/settings`)
- Admin preferences
- Session configuration
- Export settings
- Email notifications
- Backup/restore

## API Documentation

### Razorpay Client

```typescript
import { RazorpayClient } from '@/lib/razorpay-client';

const client = new RazorpayClient(keyId, keySecret);

// Fetch payments
const { items, total } = await client.getPayments({
  skip: 0,
  count: 100,
  from: timestamp,
  to: timestamp,
});

// Get payment details
const payment = await client.getPayment(paymentId);

// Create refund
const refund = await client.createRefund(paymentId, {
  amount: 50000, // in paisa
  speed: 'optimum',
});
```

### Custom Hooks

```typescript
// Use Razorpay client
const { client, error } = useRazorpay();

// Fetch transactions
const { transactions, loading, fetchTransactions } = useTransactions();

// Get analytics
const { metrics, chartData } = useAnalytics();

// Export data
const { exportCSV, exportPDF } = useExport();
```

## Security Best Practices

1. **API Key Management**
   - Store keys encrypted using AES-256
   - Rotate keys regularly
   - Use environment-specific keys
   - Monitor key usage

2. **Session Security**
   - Auto-logout after inactivity
   - HTTPS only in production
   - Secure session cookies
   - Token refresh mechanisms

3. **Data Privacy**
   - Mask sensitive information
   - Audit all key operations
   - GDPR compliance
   - No sensitive logs

4. **Access Control**
   - Role-based permissions
   - Admin-only features
   - Analyst read-only analytics
   - Viewer limited dashboard

## Performance Optimization

- Next.js Image optimization
- Code splitting per route
- Lazy loading components
- Memoized expensive computations
- Debounced filters and search
- Pagination for large datasets
- Compressed API responses

## Monitoring & Logging

```typescript
// Structured logging with context
console.log('[Razorpay] API call:', {
  endpoint: '/payments',
  status: 200,
  duration: '150ms'
});

// Error tracking
console.error('[Error] Payment fetch failed:', {
  error: error.message,
  timestamp: new Date().toISOString(),
});
```

## Testing

```bash
# Run tests
npm run test

# Build checks
npm run type-check
npm run lint
```

## Deployment

### Vercel (Recommended)

```bash
# Push to GitHub
git push origin main

# Deploy to Vercel (automatic)
# https://vercel.com/new
```

### Docker

```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY . .
RUN npm install
RUN npm run build
EXPOSE 3000
CMD ["npm", "start"]
```

### Environment Variables (Production)

```bash
NEXT_PUBLIC_ENCRYPTION_KEY=<secure-key>
NEXT_PUBLIC_API_BASE_URL=https://api.yourdomain.com
NODE_ENV=production
```

## Troubleshooting

### API Key Not Working
- Verify key format and environment
- Check if key has expired
- Confirm in Razorpay dashboard
- Check browser console for errors

### Data Not Loading
- Verify API key is active
- Check network tab
- Ensure date filters are correct
- Review console errors

### Performance Issues
- Reduce pagination size
- Use specific date ranges
- Clear browser cache
- Check network latency

## Future Enhancements

- WebSocket support for real-time updates
- Machine learning anomaly detection
- Advanced predictive analytics
- Mobile app (React Native)
- Multi-currency support
- Custom report builder
- Scheduled export emails
- Team collaboration features

## Support

For issues and questions:
1. Check IMPLEMENTATION_GUIDE.md
2. Review Razorpay API docs: https://razorpay.com/docs/api/
3. Check Next.js docs: https://nextjs.org/docs
4. Open GitHub issue

## License

MIT License - See LICENSE file for details

## Contributing

Contributions welcome! Please submit pull requests with:
- Clear description of changes
- Type-safe TypeScript code
- Tests for new features
- Updated documentation

---

**Built with React, Next.js, and ❤️ for secure payment management**
