/* opensslconf.h */
/* WARNING: Generated automatically from opensslconf.h.in by Configure. */

/* OpenSSL was configured with the following options: */
#ifndef OPENSSL_DOING_MAKEDEPEND


#ifndef OPENSSL_NO_CAMELLIA
# define OPENSSL_NO_CAMELLIA
#endif
#ifndef OPENSSL_NO_CAPIENG
# define OPENSSL_NO_CAPIENG
#endif
#ifndef OPENSSL_NO_CAST
# define OPENSSL_NO_CAST
#endif
#ifndef OPENSSL_NO_CMS
# define OPENSSL_NO_CMS
#endif
#ifndef OPENSSL_NO_COMP
# define OPENSSL_NO_COMP
#endif
#ifndef OPENSSL_NO_CONF
# define OPENSSL_NO_CONF
#endif
#ifndef OPENSSL_NO_DES
# define OPENSSL_NO_DES
#endif
#ifndef OPENSSL_NO_DTLS1
# define OPENSSL_NO_DTLS1
#endif
#ifndef OPENSSL_NO_EC_NISTP_64_GCC_128
# define OPENSSL_NO_EC_NISTP_64_GCC_128
#endif
#ifndef OPENSSL_NO_GMP
# define OPENSSL_NO_GMP
#endif
#ifndef OPENSSL_NO_GOST
# define OPENSSL_NO_GOST
#endif
#ifndef OPENSSL_NO_HEARTBEATS
# define OPENSSL_NO_HEARTBEATS
#endif
#ifndef OPENSSL_NO_IDEA
# define OPENSSL_NO_IDEA
#endif
#ifndef OPENSSL_NO_JPAKE
# define OPENSSL_NO_JPAKE
#endif
#ifndef OPENSSL_NO_KRB5
# define OPENSSL_NO_KRB5
#endif
#ifndef OPENSSL_NO_LOCKING
# define OPENSSL_NO_LOCKING
#endif
#ifndef OPENSSL_NO_MD2
# define OPENSSL_NO_MD2
#endif
#ifndef OPENSSL_NO_MD4
# define OPENSSL_NO_MD4
#endif
#ifndef OPENSSL_NO_MD5
# define OPENSSL_NO_MD5
#endif
#ifndef OPENSSL_NO_MDC2
# define OPENSSL_NO_MDC2
#endif
#ifndef OPENSSL_NO_OCSP
# define OPENSSL_NO_OCSP
#endif
#ifndef OPENSSL_NO_PEM
# define OPENSSL_NO_PEM
#endif
#ifndef OPENSSL_NO_PKCS12
# define OPENSSL_NO_PKCS12
#endif
#ifndef OPENSSL_NO_PQUEUE
# define OPENSSL_NO_PQUEUE
#endif
#ifndef OPENSSL_NO_RC2
# define OPENSSL_NO_RC2
#endif
#ifndef OPENSSL_NO_RC4
# define OPENSSL_NO_RC4
#endif
#ifndef OPENSSL_NO_RC5
# define OPENSSL_NO_RC5
#endif
#ifndef OPENSSL_NO_RDRAND
# define OPENSSL_NO_RDRAND
#endif
#ifndef OPENSSL_NO_RFC3779
# define OPENSSL_NO_RFC3779
#endif
#ifndef OPENSSL_NO_RIPEMD
# define OPENSSL_NO_RIPEMD
#endif
#ifndef OPENSSL_NO_RSAX
# define OPENSSL_NO_RSAX
#endif
#ifndef OPENSSL_NO_SCTP
# define OPENSSL_NO_SCTP
#endif
#ifndef OPENSSL_NO_SEED
# define OPENSSL_NO_SEED
#endif
#ifndef OPENSSL_NO_SHA0
# define OPENSSL_NO_SHA0
#endif
#ifndef OPENSSL_NO_SRP
# define OPENSSL_NO_SRP
#endif
#ifndef OPENSSL_NO_SSL2
# define OPENSSL_NO_SSL2
#endif
#ifndef OPENSSL_NO_SSL3
# define OPENSSL_NO_SSL3
#endif
#ifndef OPENSSL_NO_STATIC_ENGINE
# define OPENSSL_NO_STATIC_ENGINE
#endif
#ifndef OPENSSL_NO_STORE
# define OPENSSL_NO_STORE
#endif
#ifndef OPENSSL_NO_TLS1
# define OPENSSL_NO_TLS1
#endif
#ifndef OPENSSL_NO_TLSEXT
# define OPENSSL_NO_TLSEXT
#endif
#ifndef OPENSSL_NO_TS
# define OPENSSL_NO_TS
#endif
#ifndef OPENSSL_NO_TXT_DB
# define OPENSSL_NO_TXT_DB
#endif
#ifndef OPENSSL_NO_UI
# define OPENSSL_NO_UI
#endif
#ifndef OPENSSL_NO_WHIRLPOOL
# define OPENSSL_NO_WHIRLPOOL
#endif

#endif /* OPENSSL_DOING_MAKEDEPEND */

#ifndef OPENSSL_NO_ERR
# define OPENSSL_NO_ERR
#endif
#ifndef OPENSSL_NO_DYNAMIC_ENGINE
# define OPENSSL_NO_DYNAMIC_ENGINE
#endif

/* The OPENSSL_NO_* macros are also defined as NO_* if the application
   asks for it.  This is a transient feature that is provided for those
   who haven't had the time to do the appropriate changes in their
   applications.  */
#ifdef OPENSSL_ALGORITHM_DEFINES
# if defined(OPENSSL_NO_CAMELLIA) && !defined(NO_CAMELLIA)
#  define NO_CAMELLIA
# endif
# if defined(OPENSSL_NO_CAPIENG) && !defined(NO_CAPIENG)
#  define NO_CAPIENG
# endif
# if defined(OPENSSL_NO_CAST) && !defined(NO_CAST)
#  define NO_CAST
# endif
# if defined(OPENSSL_NO_CMS) && !defined(NO_CMS)
#  define NO_CMS
# endif
# if defined(OPENSSL_NO_COMP) && !defined(NO_COMP)
#  define NO_COMP
# endif
# if defined(OPENSSL_NO_CONF) && !defined(NO_CONF)
#  define NO_CONF
# endif
# if defined(OPENSSL_NO_DES) && !defined(NO_DES)
#  define NO_DES
# endif
# if defined(OPENSSL_NO_DTLS1) && !defined(NO_DTLS1)
#  define NO_DTLS1
# endif
# if defined(OPENSSL_NO_EC_NISTP_64_GCC_128) && !defined(NO_EC_NISTP_64_GCC_128)
#  define NO_EC_NISTP_64_GCC_128
# endif
# if defined(OPENSSL_NO_GMP) && !defined(NO_GMP)
#  define NO_GMP
# endif
# if defined(OPENSSL_NO_GOST) && !defined(NO_GOST)
#  define NO_GOST
# endif
# if defined(OPENSSL_NO_HEARTBEATS) && !defined(NO_HEARTBEATS)
#  define NO_HEARTBEATS
# endif
# if defined(OPENSSL_NO_IDEA) && !defined(NO_IDEA)
#  define NO_IDEA
# endif
# if defined(OPENSSL_NO_JPAKE) && !defined(NO_JPAKE)
#  define NO_JPAKE
# endif
# if defined(OPENSSL_NO_KRB5) && !defined(NO_KRB5)
#  define NO_KRB5
# endif
# if defined(OPENSSL_NO_LOCKING) && !defined(NO_LOCKING)
#  define NO_LOCKING
# endif
# if defined(OPENSSL_NO_MD2) && !defined(NO_MD2)
#  define NO_MD2
# endif
# if defined(OPENSSL_NO_MD4) && !defined(NO_MD4)
#  define NO_MD4
# endif
# if defined(OPENSSL_NO_MD5) && !defined(NO_MD5)
#  define NO_MD5
# endif
# if defined(OPENSSL_NO_MDC2) && !defined(NO_MDC2)
#  define NO_MDC2
# endif
# if defined(OPENSSL_NO_OCSP) && !defined(NO_OCSP)
#  define NO_OCSP
# endif
# if defined(OPENSSL_NO_PEM) && !defined(NO_PEM)
#  define NO_PEM
# endif
# if defined(OPENSSL_NO_PKCS12) && !defined(NO_PKCS12)
#  define NO_PKCS12
# endif
# if defined(OPENSSL_NO_PQUEUE) && !defined(NO_PQUEUE)
#  define NO_PQUEUE
# endif
# if defined(OPENSSL_NO_RC2) && !defined(NO_RC2)
#  define NO_RC2
# endif
# if defined(OPENSSL_NO_RC4) && !defined(NO_RC4)
#  define NO_RC4
# endif
# if defined(OPENSSL_NO_RC5) && !defined(NO_RC5)
#  define NO_RC5
# endif
# if defined(OPENSSL_NO_RDRAND) && !defined(NO_RDRAND)
#  define NO_RDRAND
# endif
# if defined(OPENSSL_NO_RFC3779) && !defined(NO_RFC3779)
#  define NO_RFC3779
# endif
# if defined(OPENSSL_NO_RIPEMD) && !defined(NO_RIPEMD)
#  define NO_RIPEMD
# endif
# if defined(OPENSSL_NO_RSAX) && !defined(NO_RSAX)
#  define NO_RSAX
# endif
# if defined(OPENSSL_NO_SCTP) && !defined(NO_SCTP)
#  define NO_SCTP
# endif
# if defined(OPENSSL_NO_SEED) && !defined(NO_SEED)
#  define NO_SEED
# endif
# if defined(OPENSSL_NO_SHA0) && !defined(NO_SHA0)
#  define NO_SHA0
# endif
# if defined(OPENSSL_NO_SRP) && !defined(NO_SRP)
#  define NO_SRP
# endif
# if defined(OPENSSL_NO_SSL2) && !defined(NO_SSL2)
#  define NO_SSL2
# endif
# if defined(OPENSSL_NO_SSL3) && !defined(NO_SSL3)
#  define NO_SSL3
# endif
# if defined(OPENSSL_NO_STATIC_ENGINE) && !defined(NO_STATIC_ENGINE)
#  define NO_STATIC_ENGINE
# endif
# if defined(OPENSSL_NO_STORE) && !defined(NO_STORE)
#  define NO_STORE
# endif
# if defined(OPENSSL_NO_TLS1) && !defined(NO_TLS1)
#  define NO_TLS1
# endif
# if defined(OPENSSL_NO_TLSEXT) && !defined(NO_TLSEXT)
#  define NO_TLSEXT
# endif
# if defined(OPENSSL_NO_TS) && !defined(NO_TS)
#  define NO_TS
# endif
# if defined(OPENSSL_NO_TXT_DB) && !defined(NO_TXT_DB)
#  define NO_TXT_DB
# endif
# if defined(OPENSSL_NO_UI) && !defined(NO_UI)
#  define NO_UI
# endif
# if defined(OPENSSL_NO_WHIRLPOOL) && !defined(NO_WHIRLPOOL)
#  define NO_WHIRLPOOL
# endif
#endif

/* crypto/opensslconf.h.in */

/* Generate 80386 code? */
#undef I386_ONLY

#if !(defined(VMS) || defined(__VMS)) /* VMS uses logical names instead */
#if defined(HEADER_CRYPTLIB_H) && !defined(OPENSSLDIR)
#define ENGINESDIR "/usr/local/ssl/lib/engines"
#define OPENSSLDIR "/usr/local/ssl"
#endif
#endif

#undef OPENSSL_UNISTD
#define OPENSSL_UNISTD <trusty_std.h>

#undef OPENSSL_EXPORT_VAR_AS_FUNCTION

#if defined(HEADER_IDEA_H) && !defined(IDEA_INT)
#define IDEA_INT unsigned int
#endif

#if defined(HEADER_MD2_H) && !defined(MD2_INT)
#define MD2_INT unsigned int
#endif

#if defined(HEADER_RC2_H) && !defined(RC2_INT)
/* I need to put in a mod for the alpha - eay */
#define RC2_INT unsigned int
#endif

#if defined(HEADER_RC4_H)
#if !defined(RC4_INT)
/* using int types make the structure larger but make the code faster
 * on most boxes I have tested - up to %20 faster. */
/*
 * I don't know what does "most" mean, but declaring "int" is a must on:
 * - Intel P6 because partial register stalls are very expensive;
 * - elder Alpha because it lacks byte load/store instructions;
 */
#define RC4_INT unsigned int
#endif
#if !defined(RC4_CHUNK)
/*
 * This enables code handling data aligned at natural CPU word
 * boundary. See crypto/rc4/rc4_enc.c for further details.
 */
#undef RC4_CHUNK
#endif
#endif

#if (defined(HEADER_NEW_DES_H) || defined(HEADER_DES_H)) && !defined(DES_LONG)
/* If this is set to 'unsigned int' on a DEC Alpha, this gives about a
 * %20 speed up (longs are 8 bytes, int's are 4). */
#ifndef DES_LONG
#define DES_LONG unsigned long
#endif
#endif

#if defined(HEADER_BN_H) && !defined(CONFIG_HEADER_BN_H)
#define CONFIG_HEADER_BN_H
#undef BN_LLONG

/* Should we define BN_DIV2W here? */

/* Only one for the following should be defined */
#undef SIXTY_FOUR_BIT_LONG
#undef SIXTY_FOUR_BIT
#define THIRTY_TWO_BIT
#endif

#if defined(HEADER_RC4_LOCL_H) && !defined(CONFIG_HEADER_RC4_LOCL_H)
#define CONFIG_HEADER_RC4_LOCL_H
/* if this is defined data[i] is used instead of *data, this is a %20
 * speedup on x86 */
#undef RC4_INDEX
#endif

#if defined(HEADER_BF_LOCL_H) && !defined(CONFIG_HEADER_BF_LOCL_H)
#define CONFIG_HEADER_BF_LOCL_H
#undef BF_PTR
#endif /* HEADER_BF_LOCL_H */

#if defined(HEADER_DES_LOCL_H) && !defined(CONFIG_HEADER_DES_LOCL_H)
#define CONFIG_HEADER_DES_LOCL_H
#ifndef DES_DEFAULT_OPTIONS
/* the following is tweaked from a config script, that is why it is a
 * protected undef/define */
#ifndef DES_PTR
#undef DES_PTR
#endif

/* This helps C compiler generate the correct code for multiple functional
 * units.  It reduces register dependancies at the expense of 2 more
 * registers */
#ifndef DES_RISC1
#undef DES_RISC1
#endif

#ifndef DES_RISC2
#undef DES_RISC2
#endif

#if defined(DES_RISC1) && defined(DES_RISC2)
YOU SHOULD NOT HAVE BOTH DES_RISC1 AND DES_RISC2 DEFINED!!!!!
#endif

/* Unroll the inner loop, this sometimes helps, sometimes hinders.
 * Very mucy CPU dependant */
#ifndef DES_UNROLL
#undef DES_UNROLL
#endif

/* These default values were supplied by
 * Peter Gutman <pgut001@cs.auckland.ac.nz>
 * They are only used if nothing else has been defined */
#if !defined(DES_PTR) && !defined(DES_RISC1) && !defined(DES_RISC2) && !defined(DES_UNROLL)
/* Special defines which change the way the code is built depending on the
   CPU and OS.  For SGI machines you can use _MIPS_SZLONG (32 or 64) to find
   even newer MIPS CPU's, but at the moment one size fits all for
   optimization options.  Older Sparc's work better with only UNROLL, but
   there's no way to tell at compile time what it is you're running on */
 
#if defined( sun )		/* Newer Sparc's */
#  define DES_PTR
#  define DES_RISC1
#  define DES_UNROLL
#elif defined( __ultrix )	/* Older MIPS */
#  define DES_PTR
#  define DES_RISC2
#  define DES_UNROLL
#elif defined( __osf1__ )	/* Alpha */
#  define DES_PTR
#  define DES_RISC2
#elif defined ( _AIX )		/* RS6000 */
  /* Unknown */
#elif defined( __hpux )		/* HP-PA */
  /* Unknown */
#elif defined( __aux )		/* 68K */
  /* Unknown */
#elif defined( __dgux )		/* 88K (but P6 in latest boxes) */
#  define DES_UNROLL
#elif defined( __sgi )		/* Newer MIPS */
#  define DES_PTR
#  define DES_RISC2
#  define DES_UNROLL
#elif defined(i386) || defined(__i386__)	/* x86 boxes, should be gcc */
#  define DES_PTR
#  define DES_RISC1
#  define DES_UNROLL
#endif /* Systems-specific speed defines */
#endif

#endif /* DES_DEFAULT_OPTIONS */
#endif /* HEADER_DES_LOCL_H */
