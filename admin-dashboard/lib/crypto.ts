import CryptoJS from "crypto-js";

// Use environment variable for encryption key - MUST be set in production
const ENCRYPTION_KEY = process.env.NEXT_PUBLIC_ENCRYPTION_KEY || "default-insecure-key-change-in-production";

interface EncryptedData {
  ciphertext: string;
  iv: string;
  salt: string;
}

/**
 * Encrypt sensitive data (API keys, tokens)
 * Uses AES-256 encryption with a random IV and salt
 */
export function encryptData(data: string): EncryptedData {
  // Generate random IV and salt
  const iv = CryptoJS.lib.WordArray.random(128 / 8);
  const salt = CryptoJS.lib.WordArray.random(128 / 8);

  // Derive key from password with salt
  const derivedKey = CryptoJS.PBKDF2(ENCRYPTION_KEY, salt, {
    keySize: 256 / 32,
    iterations: 1000,
  });

  // Encrypt data
  const encrypted = CryptoJS.AES.encrypt(data, derivedKey, {
    iv: iv,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7,
  });

  return {
    ciphertext: encrypted.ciphertext.toString(CryptoJS.enc.Base64),
    iv: iv.toString(CryptoJS.enc.Base64),
    salt: salt.toString(CryptoJS.enc.Base64),
  };
}

/**
 * Decrypt encrypted data
 */
export function decryptData(encryptedData: EncryptedData): string {
  try {
    // Reconstruct IV and salt from Base64
    const iv = CryptoJS.enc.Base64.parse(encryptedData.iv);
    const salt = CryptoJS.enc.Base64.parse(encryptedData.salt);
    const ciphertext = CryptoJS.enc.Base64.parse(encryptedData.ciphertext);

    // Derive key with same salt and iterations
    const derivedKey = CryptoJS.PBKDF2(ENCRYPTION_KEY, salt, {
      keySize: 256 / 32,
      iterations: 1000,
    });

    // Create encrypted object for decryption
    const encrypted = CryptoJS.lib.CipherParams.create({
      ciphertext: ciphertext,
    });

    // Decrypt data
    const decrypted = CryptoJS.AES.decrypt(encrypted, derivedKey, {
      iv: iv,
      mode: CryptoJS.mode.CBC,
      padding: CryptoJS.pad.Pkcs7,
    });

    return decrypted.toString(CryptoJS.enc.Utf8);
  } catch (error) {
    console.error("[Crypto] Decryption failed:", error);
    throw new Error("Failed to decrypt data. Key may be corrupted or encryption key changed.");
  }
}

/**
 * Generate SHA256 hash of data
 */
export function hashData(data: string): string {
  return CryptoJS.SHA256(data).toString();
}

/**
 * Generate HMAC-SHA256 signature for webhook verification
 */
export function generateSignature(data: string, secret: string): string {
  return CryptoJS.HmacSHA256(data, secret).toString();
}

/**
 * Verify webhook signature
 */
export function verifyWebhookSignature(data: string, signature: string, secret: string): boolean {
  const expectedSignature = generateSignature(data, secret);
  return signature === expectedSignature;
}

/**
 * Hash sensitive data for logging (one-way)
 */
export function hashForLogging(data: string): string {
  return CryptoJS.SHA256(data).toString().substring(0, 16) + "...";
}

/**
 * Generate a secure random string
 */
export function generateRandomString(length: number = 32): string {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  const values = new Uint8Array(length);
  const crypto = typeof window !== "undefined" ? window.crypto : require("crypto");
  crypto.getRandomValues(values);

  let result = "";
  for (let i = 0; i < length; i++) {
    result += chars[values[i] % chars.length];
  }
  return result;
}

/**
 * Mask sensitive string for display (show first 4 and last 4 chars)
 */
export function maskSensitiveData(data: string, visibleChars: number = 4): string {
  if (data.length <= visibleChars * 2) {
    return "*".repeat(Math.max(0, data.length - 1)) + data[data.length - 1];
  }

  const start = data.substring(0, visibleChars);
  const end = data.substring(data.length - visibleChars);
  const masked = "*".repeat(Math.max(0, data.length - visibleChars * 2));

  return `${start}${masked}${end}`;
}
