import { create } from "zustand";
import { devtools } from "zustand/middleware";
import { Transaction, UserActivity, DashboardMetrics, ChartData } from "@/types";

interface DataState {
  // Transactions
  transactions: Transaction[];
  selectedTransaction: Transaction | null;
  transactionFilters: Record<string, any>;
  isLoadingTransactions: boolean;

  // User Activity
  activities: UserActivity[];
  activityFilters: Record<string, any>;
  isLoadingActivities: boolean;

  // Analytics
  metrics: DashboardMetrics | null;
  chartData: ChartData[];
  isLoadingMetrics: boolean;

  // Pagination
  transactionPage: number;
  transactionPageSize: number;
  activityPage: number;
  activityPageSize: number;

  // Transaction operations
  setTransactions: (transactions: Transaction[]) => void;
  selectTransaction: (transaction: Transaction | null) => void;
  setTransactionFilters: (filters: Record<string, any>) => void;
  setIsLoadingTransactions: (loading: boolean) => void;
  setTransactionPage: (page: number) => void;
  updateTransaction: (transaction: Transaction) => void;

  // Activity operations
  setActivities: (activities: UserActivity[]) => void;
  setActivityFilters: (filters: Record<string, any>) => void;
  setIsLoadingActivities: (loading: boolean) => void;
  setActivityPage: (page: number) => void;

  // Analytics operations
  setMetrics: (metrics: DashboardMetrics) => void;
  setChartData: (data: ChartData[]) => void;
  setIsLoadingMetrics: (loading: boolean) => void;

  // Utilities
  getTransactionCount: () => number;
  getActivityCount: () => number;
  clearAllData: () => void;
}

const useDataStore = create<DataState>()(
  devtools((set, get) => ({
    transactions: [],
    selectedTransaction: null,
    transactionFilters: {},
    isLoadingTransactions: false,

    activities: [],
    activityFilters: {},
    isLoadingActivities: false,

    metrics: null,
    chartData: [],
    isLoadingMetrics: false,

    transactionPage: 1,
    transactionPageSize: 50,
    activityPage: 1,
    activityPageSize: 50,

    setTransactions: (transactions) => {
      set({ transactions });
    },

    selectTransaction: (transaction) => {
      set({ selectedTransaction: transaction });
    },

    setTransactionFilters: (filters) => {
      set({ transactionFilters: filters, transactionPage: 1 });
    },

    setIsLoadingTransactions: (loading) => {
      set({ isLoadingTransactions: loading });
    },

    setTransactionPage: (page) => {
      set({ transactionPage: page });
    },

    updateTransaction: (transaction) => {
      const { transactions, selectedTransaction } = get();
      const updated = transactions.map((t) => (t.id === transaction.id ? transaction : t));
      set({
        transactions: updated,
        selectedTransaction: selectedTransaction?.id === transaction.id ? transaction : selectedTransaction,
      });
    },

    setActivities: (activities) => {
      set({ activities });
    },

    setActivityFilters: (filters) => {
      set({ activityFilters: filters, activityPage: 1 });
    },

    setIsLoadingActivities: (loading) => {
      set({ isLoadingActivities: loading });
    },

    setActivityPage: (page) => {
      set({ activityPage: page });
    },

    setMetrics: (metrics) => {
      set({ metrics });
    },

    setChartData: (data) => {
      set({ chartData: data });
    },

    setIsLoadingMetrics: (loading) => {
      set({ isLoadingMetrics: loading });
    },

    getTransactionCount: () => {
      return get().transactions.length;
    },

    getActivityCount: () => {
      return get().activities.length;
    },

    clearAllData: () => {
      set({
        transactions: [],
        selectedTransaction: null,
        transactionFilters: {},
        isLoadingTransactions: false,
        activities: [],
        activityFilters: {},
        isLoadingActivities: false,
        metrics: null,
        chartData: [],
        isLoadingMetrics: false,
        transactionPage: 1,
        activityPage: 1,
      });
    },
  }))
);

export default useDataStore;
