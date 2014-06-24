#ifndef crypto_hash_sha512_H
#define crypto_hash_sha512_H

#define crypto_hash_sha512_ref_BYTES 64
#ifdef __cplusplus
#include <string>
extern std::string crypto_hash_sha512_ref(const std::string &);
extern "C" {
#endif
extern int crypto_hash_sha512_ref(unsigned char *,const unsigned char *,unsigned long long);
#ifdef __cplusplus
}
#endif

#define crypto_hash_sha512 crypto_hash_sha512_ref
#define crypto_hash_sha512_BYTES crypto_hash_sha512_ref_BYTES
#define crypto_hash_sha512_IMPLEMENTATION "crypto_hash/sha512/ref"
#ifndef crypto_hash_sha512_ref_VERSION
#define crypto_hash_sha512_ref_VERSION "-"
#endif
#define crypto_hash_sha512_VERSION crypto_hash_sha512_ref_VERSION

#endif
