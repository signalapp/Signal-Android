// DEPRECATED — see proposals/fips-discovery-2026-04-17.md

/**
 * @file openssl_fips_bridge.c
 * @brief C bridge implementation to connect the Rust Cryptographic Abstraction Layer
 * to the OpenSSL 3.x FIPS provider.
 *
 * This library provides a "chunky" C-style API that the Rust FFI can call. Each
 * function encapsulates a complete cryptographic operation and ensures that it is
 * dispatched to the loaded FIPS provider. This version is tailored for the
 * Client-Only architecture.
 *
 * Compilation:
 * This file should be compiled into a shared library (e.g., libopenssl_fips_bridge.so)
 * and linked against the OpenSSL libraries (libcrypto).
 */

#include <stdio.h>
#include <string.h>
#include <openssl/provider.h>
#include <openssl/evp.h>
#include <openssl/ec.h>
#include <openssl/err.h>

/**
 * @brief Initializes the FIPS provider.
 * This function loads the OpenSSL FIPS provider, which implicitly triggers its
 * power-on self-tests (integrity check, known-answer tests).
 *
 * @return 1 on success, 0 on failure.
 */
int FIPS_BRIDGE_initialize() {
    // Load the FIPS provider. OSSL_PROVIDER_load will return NULL on failure,
    // which includes self-test failures.
    OSSL_PROVIDER* fips_provider = OSSL_PROVIDER_load(NULL, "fips");
    if (fips_provider == NULL) {
        fprintf(stderr, "Failed to load FIPS provider. Self-tests may have failed.\n");
        ERR_print_errors_fp(stderr);
        return 0;
    }
    
    // The "default" provider is also loaded to provide necessary non-crypto
    // functions that the FIPS provider may depend on.
    OSSL_PROVIDER_load(NULL, "default");

    printf("FIPS provider loaded successfully.\n");
    return 1;
}

/**
 * @brief Encrypts data using FIPS-validated AES-256-GCM.
 *
 * @param key The 32-byte AES key.
 * @param key_len The length of the key (must be 32).
 * @param iv The 12-byte initialization vector (nonce).
 * @param iv_len The length of the IV (must be 12).
 * @param plaintext The data to encrypt.
 * @param plaintext_len Length of the plaintext.
 * @param ciphertext Output buffer for the encrypted data (must be at least plaintext_len).
 * @param tag Output buffer for the 16-byte authentication tag.
 * @return 1 on success, 0 on failure.
 */
int FIPS_BRIDGE_aes_gcm_encrypt(
    const unsigned char* key, size_t key_len,
    const unsigned char* iv, size_t iv_len,
    const unsigned char* plaintext, size_t plaintext_len,
    unsigned char* ciphertext,
    unsigned char* tag
) {
    EVP_CIPHER_CTX* ctx = NULL;
    int len;
    int ciphertext_len;
    int ret = 0;
    EVP_CIPHER* cipher = NULL;

    if (!(ctx = EVP_CIPHER_CTX_new())) goto cleanup;

    // Fetch the AES-256-GCM implementation specifically from the FIPS provider.
    cipher = EVP_CIPHER_fetch(NULL, "AES-256-GCM", "provider=fips");
    if (cipher == NULL) {
        fprintf(stderr, "Failed to fetch AES-256-GCM from FIPS provider.\n");
        goto cleanup;
    }

    if (1 != EVP_EncryptInit_ex(ctx, cipher, NULL, key, iv)) goto cleanup;
    if (1 != EVP_EncryptUpdate(ctx, ciphertext, &len, plaintext, plaintext_len)) goto cleanup;
    ciphertext_len = len;

    if (1 != EVP_EncryptFinal_ex(ctx, ciphertext + len, &len)) goto cleanup;
    ciphertext_len += len;

    if (1 != EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, 16, tag)) goto cleanup;

    ret = 1; // Success

cleanup:
    if (ctx) EVP_CIPHER_CTX_free(ctx);
    if (cipher) EVP_CIPHER_free(cipher);
    if (ret == 0) ERR_print_errors_fp(stderr);
    return ret;
}

/**
 * @brief Decrypts data using FIPS-validated AES-256-GCM.
 *
 * @param key The 32-byte AES key.
 * @param key_len The length of the key (must be 32).
 * @param iv The 12-byte initialization vector (nonce).
 * @param iv_len The length of the IV (must be 12).
 * @param ciphertext The data to decrypt.
 * @param ciphertext_len Length of the ciphertext.
 * @param tag The 16-byte authentication tag to verify.
 * @param plaintext Output buffer for the decrypted data.
 * @return 1 on success (tag verified), 0 on failure (tag invalid or other error).
 */
int FIPS_BRIDGE_aes_gcm_decrypt(
    const unsigned char* key, size_t key_len,
    const unsigned char* iv, size_t iv_len,
    const unsigned char* ciphertext, size_t ciphertext_len,
    const unsigned char* tag,
    unsigned char* plaintext
) {
    EVP_CIPHER_CTX* ctx = NULL;
    int len;
    int plaintext_len;
    int ret = 0;
    EVP_CIPHER* cipher = NULL;

    if (!(ctx = EVP_CIPHER_CTX_new())) goto cleanup;

    cipher = EVP_CIPHER_fetch(NULL, "AES-256-GCM", "provider=fips");
    if (cipher == NULL) {
        fprintf(stderr, "Failed to fetch AES-256-GCM from FIPS provider.\n");
        goto cleanup;
    }

    if (1 != EVP_DecryptInit_ex(ctx, cipher, NULL, key, iv)) goto cleanup;
    if (1 != EVP_DecryptUpdate(ctx, plaintext, &len, ciphertext, ciphertext_len)) goto cleanup;
    plaintext_len = len;

    if (1 != EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, 16, (void*)tag)) goto cleanup;

    if (1 != EVP_DecryptFinal_ex(ctx, plaintext + len, &len)) {
        fprintf(stderr, "GCM tag verification failed.\n");
        goto cleanup;
    }

    ret = 1; // Success

cleanup:
    if (ctx) EVP_CIPHER_CTX_free(ctx);
    if (cipher) EVP_CIPHER_free(cipher);
    if (ret == 0) ERR_print_errors_fp(stderr);
    return ret;
}

/**
 * @brief Performs a key exchange using FIPS-validated ECDH on the P-256 curve.
 *
 * @param private_key_bytes Raw bytes of our private key.
 * @param public_key_bytes Raw bytes of the peer's public key (uncompressed format).
 * @param shared_secret Output buffer for the derived shared secret.
 * @return 1 on success, 0 on failure.
 */
int FIPS_BRIDGE_ecdh_p256(
    const unsigned char* private_key_bytes,
    const unsigned char* public_key_bytes,
    unsigned char* shared_secret
) {
    EVP_PKEY_CTX* pctx = NULL;
    EVP_PKEY* private_key = NULL;
    EVP_PKEY* peer_public_key = NULL;
    size_t shared_secret_len;
    int ret = 0;

    // NID_X9_62_prime256v1 is the object identifier for the P-256 curve.
    private_key = EVP_PKEY_new_raw_private_key_ex(NULL, "EC", "provider=fips", private_key_bytes, 32);
    if (private_key == NULL) {
        fprintf(stderr, "Failed to create private key from raw bytes.\n");
        goto cleanup;
    }
    
    peer_public_key = EVP_PKEY_new_raw_public_key_ex(NULL, "EC", "provider=fips", public_key_bytes, 65);
    if (peer_public_key == NULL) {
        fprintf(stderr, "Failed to create peer public key from raw bytes.\n");
        goto cleanup;
    }

    pctx = EVP_PKEY_CTX_new_from_pkey(NULL, private_key, "provider=fips");
    if (pctx == NULL) goto cleanup;

    if (1 != EVP_PKEY_derive_init(pctx)) goto cleanup;
    if (1 != EVP_PKEY_derive_set_peer(pctx, peer_public_key)) goto cleanup;
    if (1 != EVP_PKEY_derive(pctx, NULL, &shared_secret_len)) goto cleanup;
    if (1 != EVP_PKEY_derive(pctx, shared_secret, &shared_secret_len)) goto cleanup;

    ret = 1; // Success

cleanup:
    if (pctx) EVP_PKEY_CTX_free(pctx);
    if (private_key) EVP_PKEY_free(private_key);
    if (peer_public_key) EVP_PKEY_free(peer_public_key);
    if (ret == 0) ERR_print_errors_fp(stderr);
    return ret;
}
