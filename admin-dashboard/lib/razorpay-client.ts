import axios, { AxiosInstance, AxiosError } from "axios";
import { RazorpayApiKey, RazorpayPayment, ApiResponse } from "@/types";

export class RazorpayClient {
  private axiosInstance: AxiosInstance;
  private keyId: string;
  private keySecret: string;
  private baseURL: string = "https://api.razorpay.com/v1";

  constructor(keyId: string, keySecret: string, environment: "sandbox" | "production" = "production") {
    this.keyId = keyId;
    this.keySecret = keySecret;

    // Create axios instance with basic auth
    this.axiosInstance = axios.create({
      baseURL: this.baseURL,
      auth: {
        username: keyId,
        password: keySecret,
      },
      headers: {
        "Content-Type": "application/json",
      },
      timeout: 10000,
    });

    // Add response interceptor for error handling
    this.axiosInstance.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => {
        console.error("[Razorpay API Error]", {
          status: error.response?.status,
          data: error.response?.data,
        });
        throw error;
      }
    );
  }

  /**
   * Validate API credentials by fetching account details
   */
  async validateCredentials(): Promise<boolean> {
    try {
      const response = await this.axiosInstance.get("/accounts");
      return !!response.data.id;
    } catch (error) {
      console.error("[Razorpay] Credential validation failed:", error);
      return false;
    }
  }

  /**
   * Fetch all payments with optional filters
   */
  async getPayments(filters?: {
    skip?: number;
    count?: number;
    expand?: string[];
    from?: number;
    to?: number;
    status?: string;
  }): Promise<{
    items: RazorpayPayment[];
    total: number;
  }> {
    try {
      const params = {
        skip: filters?.skip || 0,
        count: filters?.count || 100,
        expand: filters?.expand?.join(","),
        from: filters?.from,
        to: filters?.to,
      };

      // Remove undefined values
      Object.keys(params).forEach(
        (key) => params[key as keyof typeof params] === undefined && delete params[key as keyof typeof params]
      );

      const response = await this.axiosInstance.get("/payments", { params });

      return {
        items: response.data.items || [],
        total: response.data.count || 0,
      };
    } catch (error) {
      console.error("[Razorpay] Failed to fetch payments:", error);
      throw error;
    }
  }

  /**
   * Fetch single payment details
   */
  async getPayment(paymentId: string): Promise<RazorpayPayment> {
    try {
      const response = await this.axiosInstance.get(`/payments/${paymentId}`, {
        params: {
          expand: ["refunds", "card", "bank"],
        },
      });
      return response.data;
    } catch (error) {
      console.error(`[Razorpay] Failed to fetch payment ${paymentId}:`, error);
      throw error;
    }
  }

  /**
   * Fetch all orders
   */
  async getOrders(filters?: {
    skip?: number;
    count?: number;
    from?: number;
    to?: number;
    status?: "created" | "paid" | "attempted";
  }): Promise<{
    items: any[];
    total: number;
  }> {
    try {
      const params = {
        skip: filters?.skip || 0,
        count: filters?.count || 100,
        from: filters?.from,
        to: filters?.to,
        status: filters?.status,
      };

      Object.keys(params).forEach(
        (key) => params[key as keyof typeof params] === undefined && delete params[key as keyof typeof params]
      );

      const response = await this.axiosInstance.get("/orders", { params });

      return {
        items: response.data.items || [],
        total: response.data.count || 0,
      };
    } catch (error) {
      console.error("[Razorpay] Failed to fetch orders:", error);
      throw error;
    }
  }

  /**
   * Fetch order details
   */
  async getOrder(orderId: string): Promise<any> {
    try {
      const response = await this.axiosInstance.get(`/orders/${orderId}`, {
        params: {
          expand: ["payments", "refunds"],
        },
      });
      return response.data;
    } catch (error) {
      console.error(`[Razorpay] Failed to fetch order ${orderId}:`, error);
      throw error;
    }
  }

  /**
   * Get refunds for a payment
   */
  async getRefunds(paymentId: string): Promise<any[]> {
    try {
      const response = await this.axiosInstance.get(`/payments/${paymentId}/refunds`);
      return response.data.items || [];
    } catch (error) {
      console.error(`[Razorpay] Failed to fetch refunds for ${paymentId}:`, error);
      throw error;
    }
  }

  /**
   * Create a refund for a payment
   */
  async createRefund(
    paymentId: string,
    options?: {
      amount?: number;
      speed?: "optimum" | "normal";
      notes?: Record<string, any>;
      receipt?: string;
    }
  ): Promise<any> {
    try {
      const response = await this.axiosInstance.post(`/payments/${paymentId}/refunds`, {
        amount: options?.amount,
        speed: options?.speed || "optimum",
        notes: options?.notes,
        receipt: options?.receipt,
      });
      return response.data;
    } catch (error) {
      console.error(`[Razorpay] Failed to create refund for ${paymentId}:`, error);
      throw error;
    }
  }

  /**
   * Get refund details
   */
  async getRefund(paymentId: string, refundId: string): Promise<any> {
    try {
      const response = await this.axiosInstance.get(`/payments/${paymentId}/refunds/${refundId}`);
      return response.data;
    } catch (error) {
      console.error(`[Razorpay] Failed to fetch refund ${refundId}:`, error);
      throw error;
    }
  }

  /**
   * Get customer details
   */
  async getCustomer(customerId: string): Promise<any> {
    try {
      const response = await this.axiosInstance.get(`/customers/${customerId}`);
      return response.data;
    } catch (error) {
      console.error(`[Razorpay] Failed to fetch customer ${customerId}:`, error);
      throw error;
    }
  }

  /**
   * Get settlements
   */
  async getSettlements(filters?: {
    skip?: number;
    count?: number;
    from?: number;
    to?: number;
    status?: "processed" | "failed" | "pending";
  }): Promise<{
    items: any[];
    total: number;
  }> {
    try {
      const params = {
        skip: filters?.skip || 0,
        count: filters?.count || 100,
        from: filters?.from,
        to: filters?.to,
        status: filters?.status,
      };

      Object.keys(params).forEach(
        (key) => params[key as keyof typeof params] === undefined && delete params[key as keyof typeof params]
      );

      const response = await this.axiosInstance.get("/settlements", { params });

      return {
        items: response.data.items || [],
        total: response.data.count || 0,
      };
    } catch (error) {
      console.error("[Razorpay] Failed to fetch settlements:", error);
      throw error;
    }
  }

  /**
   * Get invoices
   */
  async getInvoices(filters?: {
    skip?: number;
    count?: number;
    customer_id?: string;
    status?: string;
  }): Promise<{
    items: any[];
    total: number;
  }> {
    try {
      const params = {
        skip: filters?.skip || 0,
        count: filters?.count || 100,
        customer_id: filters?.customer_id,
        status: filters?.status,
      };

      Object.keys(params).forEach(
        (key) => params[key as keyof typeof params] === undefined && delete params[key as keyof typeof params]
      );

      const response = await this.axiosInstance.get("/invoices", { params });

      return {
        items: response.data.items || [],
        total: response.data.count || 0,
      };
    } catch (error) {
      console.error("[Razorpay] Failed to fetch invoices:", error);
      throw error;
    }
  }

  /**
   * Get transfer details
   */
  async getTransfers(filters?: {
    skip?: number;
    count?: number;
    from?: number;
    to?: number;
  }): Promise<{
    items: any[];
    total: number;
  }> {
    try {
      const params = {
        skip: filters?.skip || 0,
        count: filters?.count || 100,
        from: filters?.from,
        to: filters?.to,
      };

      Object.keys(params).forEach(
        (key) => params[key as keyof typeof params] === undefined && delete params[key as keyof typeof params]
      );

      const response = await this.axiosInstance.get("/transfers", { params });

      return {
        items: response.data.items || [],
        total: response.data.count || 0,
      };
    } catch (error) {
      console.error("[Razorpay] Failed to fetch transfers:", error);
      throw error;
    }
  }
}

/**
 * Get or create Razorpay client from stored API keys
 */
export function getRazorpayClient(apiKey: RazorpayApiKey): RazorpayClient {
  if (!apiKey.keySecret) {
    throw new Error("API Key secret not available");
  }
  return new RazorpayClient(apiKey.keyId, apiKey.keySecret, apiKey.environment);
}
