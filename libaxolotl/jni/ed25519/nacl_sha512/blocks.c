#include <stdint.h>
typedef uint64_t uint64;

static uint64 load_bigendian(const unsigned char *x)
{
  return
      (uint64) (x[7]) \
  | (((uint64) (x[6])) << 8) \
  | (((uint64) (x[5])) << 16) \
  | (((uint64) (x[4])) << 24) \
  | (((uint64) (x[3])) << 32) \
  | (((uint64) (x[2])) << 40) \
  | (((uint64) (x[1])) << 48) \
  | (((uint64) (x[0])) << 56)
  ;
}

static void store_bigendian(unsigned char *x,uint64 u)
{
  x[7] = u; u >>= 8;
  x[6] = u; u >>= 8;
  x[5] = u; u >>= 8;
  x[4] = u; u >>= 8;
  x[3] = u; u >>= 8;
  x[2] = u; u >>= 8;
  x[1] = u; u >>= 8;
  x[0] = u;
}

#define SHR(x,c) ((x) >> (c))
#define ROTR(x,c) (((x) >> (c)) | ((x) << (64 - (c))))

#define Ch(x,y,z) ((x & y) ^ (~x & z))
#define Maj(x,y,z) ((x & y) ^ (x & z) ^ (y & z))
#define Sigma0(x) (ROTR(x,28) ^ ROTR(x,34) ^ ROTR(x,39))
#define Sigma1(x) (ROTR(x,14) ^ ROTR(x,18) ^ ROTR(x,41))
#define sigma0(x) (ROTR(x, 1) ^ ROTR(x, 8) ^ SHR(x,7))
#define sigma1(x) (ROTR(x,19) ^ ROTR(x,61) ^ SHR(x,6))

#define M(w0,w14,w9,w1) w0 = sigma1(w14) + w9 + sigma0(w1) + w0;

#define EXPAND \
  M(w0 ,w14,w9 ,w1 ) \
  M(w1 ,w15,w10,w2 ) \
  M(w2 ,w0 ,w11,w3 ) \
  M(w3 ,w1 ,w12,w4 ) \
  M(w4 ,w2 ,w13,w5 ) \
  M(w5 ,w3 ,w14,w6 ) \
  M(w6 ,w4 ,w15,w7 ) \
  M(w7 ,w5 ,w0 ,w8 ) \
  M(w8 ,w6 ,w1 ,w9 ) \
  M(w9 ,w7 ,w2 ,w10) \
  M(w10,w8 ,w3 ,w11) \
  M(w11,w9 ,w4 ,w12) \
  M(w12,w10,w5 ,w13) \
  M(w13,w11,w6 ,w14) \
  M(w14,w12,w7 ,w15) \
  M(w15,w13,w8 ,w0 )

#define F(w,k) \
  T1 = h + Sigma1(e) + Ch(e,f,g) + k + w; \
  T2 = Sigma0(a) + Maj(a,b,c); \
  h = g; \
  g = f; \
  f = e; \
  e = d + T1; \
  d = c; \
  c = b; \
  b = a; \
  a = T1 + T2;

int crypto_hashblocks_sha512(unsigned char *statebytes,const unsigned char *in,unsigned long long inlen)
{
  uint64 state[8];
  uint64 a;
  uint64 b;
  uint64 c;
  uint64 d;
  uint64 e;
  uint64 f;
  uint64 g;
  uint64 h;
  uint64 T1;
  uint64 T2;

  a = load_bigendian(statebytes +  0); state[0] = a;
  b = load_bigendian(statebytes +  8); state[1] = b;
  c = load_bigendian(statebytes + 16); state[2] = c;
  d = load_bigendian(statebytes + 24); state[3] = d;
  e = load_bigendian(statebytes + 32); state[4] = e;
  f = load_bigendian(statebytes + 40); state[5] = f;
  g = load_bigendian(statebytes + 48); state[6] = g;
  h = load_bigendian(statebytes + 56); state[7] = h;

  while (inlen >= 128) {
    uint64 w0  = load_bigendian(in +   0);
    uint64 w1  = load_bigendian(in +   8);
    uint64 w2  = load_bigendian(in +  16);
    uint64 w3  = load_bigendian(in +  24);
    uint64 w4  = load_bigendian(in +  32);
    uint64 w5  = load_bigendian(in +  40);
    uint64 w6  = load_bigendian(in +  48);
    uint64 w7  = load_bigendian(in +  56);
    uint64 w8  = load_bigendian(in +  64);
    uint64 w9  = load_bigendian(in +  72);
    uint64 w10 = load_bigendian(in +  80);
    uint64 w11 = load_bigendian(in +  88);
    uint64 w12 = load_bigendian(in +  96);
    uint64 w13 = load_bigendian(in + 104);
    uint64 w14 = load_bigendian(in + 112);
    uint64 w15 = load_bigendian(in + 120);

    F(w0 ,0x428a2f98d728ae22ULL)
    F(w1 ,0x7137449123ef65cdULL)
    F(w2 ,0xb5c0fbcfec4d3b2fULL)
    F(w3 ,0xe9b5dba58189dbbcULL)
    F(w4 ,0x3956c25bf348b538ULL)
    F(w5 ,0x59f111f1b605d019ULL)
    F(w6 ,0x923f82a4af194f9bULL)
    F(w7 ,0xab1c5ed5da6d8118ULL)
    F(w8 ,0xd807aa98a3030242ULL)
    F(w9 ,0x12835b0145706fbeULL)
    F(w10,0x243185be4ee4b28cULL)
    F(w11,0x550c7dc3d5ffb4e2ULL)
    F(w12,0x72be5d74f27b896fULL)
    F(w13,0x80deb1fe3b1696b1ULL)
    F(w14,0x9bdc06a725c71235ULL)
    F(w15,0xc19bf174cf692694ULL)

    EXPAND

    F(w0 ,0xe49b69c19ef14ad2ULL)
    F(w1 ,0xefbe4786384f25e3ULL)
    F(w2 ,0x0fc19dc68b8cd5b5ULL)
    F(w3 ,0x240ca1cc77ac9c65ULL)
    F(w4 ,0x2de92c6f592b0275ULL)
    F(w5 ,0x4a7484aa6ea6e483ULL)
    F(w6 ,0x5cb0a9dcbd41fbd4ULL)
    F(w7 ,0x76f988da831153b5ULL)
    F(w8 ,0x983e5152ee66dfabULL)
    F(w9 ,0xa831c66d2db43210ULL)
    F(w10,0xb00327c898fb213fULL)
    F(w11,0xbf597fc7beef0ee4ULL)
    F(w12,0xc6e00bf33da88fc2ULL)
    F(w13,0xd5a79147930aa725ULL)
    F(w14,0x06ca6351e003826fULL)
    F(w15,0x142929670a0e6e70ULL)

    EXPAND

    F(w0 ,0x27b70a8546d22ffcULL)
    F(w1 ,0x2e1b21385c26c926ULL)
    F(w2 ,0x4d2c6dfc5ac42aedULL)
    F(w3 ,0x53380d139d95b3dfULL)
    F(w4 ,0x650a73548baf63deULL)
    F(w5 ,0x766a0abb3c77b2a8ULL)
    F(w6 ,0x81c2c92e47edaee6ULL)
    F(w7 ,0x92722c851482353bULL)
    F(w8 ,0xa2bfe8a14cf10364ULL)
    F(w9 ,0xa81a664bbc423001ULL)
    F(w10,0xc24b8b70d0f89791ULL)
    F(w11,0xc76c51a30654be30ULL)
    F(w12,0xd192e819d6ef5218ULL)
    F(w13,0xd69906245565a910ULL)
    F(w14,0xf40e35855771202aULL)
    F(w15,0x106aa07032bbd1b8ULL)

    EXPAND

    F(w0 ,0x19a4c116b8d2d0c8ULL)
    F(w1 ,0x1e376c085141ab53ULL)
    F(w2 ,0x2748774cdf8eeb99ULL)
    F(w3 ,0x34b0bcb5e19b48a8ULL)
    F(w4 ,0x391c0cb3c5c95a63ULL)
    F(w5 ,0x4ed8aa4ae3418acbULL)
    F(w6 ,0x5b9cca4f7763e373ULL)
    F(w7 ,0x682e6ff3d6b2b8a3ULL)
    F(w8 ,0x748f82ee5defb2fcULL)
    F(w9 ,0x78a5636f43172f60ULL)
    F(w10,0x84c87814a1f0ab72ULL)
    F(w11,0x8cc702081a6439ecULL)
    F(w12,0x90befffa23631e28ULL)
    F(w13,0xa4506cebde82bde9ULL)
    F(w14,0xbef9a3f7b2c67915ULL)
    F(w15,0xc67178f2e372532bULL)

    EXPAND

    F(w0 ,0xca273eceea26619cULL)
    F(w1 ,0xd186b8c721c0c207ULL)
    F(w2 ,0xeada7dd6cde0eb1eULL)
    F(w3 ,0xf57d4f7fee6ed178ULL)
    F(w4 ,0x06f067aa72176fbaULL)
    F(w5 ,0x0a637dc5a2c898a6ULL)
    F(w6 ,0x113f9804bef90daeULL)
    F(w7 ,0x1b710b35131c471bULL)
    F(w8 ,0x28db77f523047d84ULL)
    F(w9 ,0x32caab7b40c72493ULL)
    F(w10,0x3c9ebe0a15c9bebcULL)
    F(w11,0x431d67c49c100d4cULL)
    F(w12,0x4cc5d4becb3e42b6ULL)
    F(w13,0x597f299cfc657e2aULL)
    F(w14,0x5fcb6fab3ad6faecULL)
    F(w15,0x6c44198c4a475817ULL)

    a += state[0];
    b += state[1];
    c += state[2];
    d += state[3];
    e += state[4];
    f += state[5];
    g += state[6];
    h += state[7];
  
    state[0] = a;
    state[1] = b;
    state[2] = c;
    state[3] = d;
    state[4] = e;
    state[5] = f;
    state[6] = g;
    state[7] = h;

    in += 128;
    inlen -= 128;
  }

  store_bigendian(statebytes +  0,state[0]);
  store_bigendian(statebytes +  8,state[1]);
  store_bigendian(statebytes + 16,state[2]);
  store_bigendian(statebytes + 24,state[3]);
  store_bigendian(statebytes + 32,state[4]);
  store_bigendian(statebytes + 40,state[5]);
  store_bigendian(statebytes + 48,state[6]);
  store_bigendian(statebytes + 56,state[7]);

  return 0;
}
