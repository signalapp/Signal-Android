#ifndef crypto_verify_32_H
#define crypto_verify_32_H

#define crypto_verify_32_ref_BYTES 32
#ifdef __cplusplus
#include <string>
extern "C" {
#endif
extern int crypto_verify_32_ref(const unsigned char *,const unsigned char *);
#ifdef __cplusplus
}
#endif

#define crypto_verify_32 crypto_verify_32_ref
#define crypto_verify_32_BYTES crypto_verify_32_ref_BYTES
#define crypto_verify_32_IMPLEMENTATION "crypto_verify/32/ref"
#ifndef crypto_verify_32_ref_VERSION
#define crypto_verify_32_ref_VERSION "-"
#endif
#define crypto_verify_32_VERSION crypto_verify_32_ref_VERSION

#endif
