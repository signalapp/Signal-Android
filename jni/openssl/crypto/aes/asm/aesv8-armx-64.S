#include "arm_arch.h"

#if __ARM_ARCH__>=7
.text
.arch	armv8-a+crypto
.align	5
rcon:
.long	0x01,0x01,0x01,0x01
.long	0x0c0f0e0d,0x0c0f0e0d,0x0c0f0e0d,0x0c0f0e0d	// rotate-n-splat
.long	0x1b,0x1b,0x1b,0x1b

.globl	aes_v8_set_encrypt_key
.type	aes_v8_set_encrypt_key,%function
.align	5
aes_v8_set_encrypt_key:
.Lenc_key:
	stp	x29,x30,[sp,#-16]!
	add	x29,sp,#0
	adr	x3,rcon
	cmp	w1,#192

	eor	v0.16b,v0.16b,v0.16b
	ld1	{v3.16b},[x0],#16
	mov	w1,#8		// reuse w1
	ld1	{v1.4s,v2.4s},[x3],#32

	b.lt	.Loop128
	b.eq	.L192
	b	.L256

.align	4
.Loop128:
	tbl	v6.16b,{v3.16b},v2.16b
	ext	v5.16b,v0.16b,v3.16b,#12
	st1	{v3.4s},[x2],#16
	aese	v6.16b,v0.16b
	subs	w1,w1,#1

	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	 eor	v6.16b,v6.16b,v1.16b
	eor	v3.16b,v3.16b,v5.16b
	shl	v1.16b,v1.16b,#1
	eor	v3.16b,v3.16b,v6.16b
	b.ne	.Loop128

	ld1	{v1.4s},[x3]

	tbl	v6.16b,{v3.16b},v2.16b
	ext	v5.16b,v0.16b,v3.16b,#12
	st1	{v3.4s},[x2],#16
	aese	v6.16b,v0.16b

	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	 eor	v6.16b,v6.16b,v1.16b
	eor	v3.16b,v3.16b,v5.16b
	shl	v1.16b,v1.16b,#1
	eor	v3.16b,v3.16b,v6.16b

	tbl	v6.16b,{v3.16b},v2.16b
	ext	v5.16b,v0.16b,v3.16b,#12
	st1	{v3.4s},[x2],#16
	aese	v6.16b,v0.16b

	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	 eor	v6.16b,v6.16b,v1.16b
	eor	v3.16b,v3.16b,v5.16b
	eor	v3.16b,v3.16b,v6.16b
	st1	{v3.4s},[x2]
	add	x2,x2,#0x50

	mov	w12,#10
	b	.Ldone

.align	4
.L192:
	ld1	{v4.8b},[x0],#8
	movi	v6.16b,#8			// borrow v6.16b
	st1	{v3.4s},[x2],#16
	sub	v2.16b,v2.16b,v6.16b	// adjust the mask

.Loop192:
	tbl	v6.16b,{v4.16b},v2.16b
	ext	v5.16b,v0.16b,v3.16b,#12
	st1	{v4.8b},[x2],#8
	aese	v6.16b,v0.16b
	subs	w1,w1,#1

	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	eor	v3.16b,v3.16b,v5.16b

	dup	v5.4s,v3.s[3]
	eor	v5.16b,v5.16b,v4.16b
	 eor	v6.16b,v6.16b,v1.16b
	ext	v4.16b,v0.16b,v4.16b,#12
	shl	v1.16b,v1.16b,#1
	eor	v4.16b,v4.16b,v5.16b
	eor	v3.16b,v3.16b,v6.16b
	eor	v4.16b,v4.16b,v6.16b
	st1	{v3.4s},[x2],#16
	b.ne	.Loop192

	mov	w12,#12
	add	x2,x2,#0x20
	b	.Ldone

.align	4
.L256:
	ld1	{v4.16b},[x0]
	mov	w1,#7
	mov	w12,#14
	st1	{v3.4s},[x2],#16

.Loop256:
	tbl	v6.16b,{v4.16b},v2.16b
	ext	v5.16b,v0.16b,v3.16b,#12
	st1	{v4.4s},[x2],#16
	aese	v6.16b,v0.16b
	subs	w1,w1,#1

	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	eor	v3.16b,v3.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	 eor	v6.16b,v6.16b,v1.16b
	eor	v3.16b,v3.16b,v5.16b
	shl	v1.16b,v1.16b,#1
	eor	v3.16b,v3.16b,v6.16b
	st1	{v3.4s},[x2],#16
	b.eq	.Ldone

	dup	v6.4s,v3.s[3]		// just splat
	ext	v5.16b,v0.16b,v4.16b,#12
	aese	v6.16b,v0.16b

	eor	v4.16b,v4.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	eor	v4.16b,v4.16b,v5.16b
	ext	v5.16b,v0.16b,v5.16b,#12
	eor	v4.16b,v4.16b,v5.16b

	eor	v4.16b,v4.16b,v6.16b
	b	.Loop256

.Ldone:
	str	w12,[x2]

	eor	x0,x0,x0		// return value
	ldr	x29,[sp],#16
	ret
.size	aes_v8_set_encrypt_key,.-aes_v8_set_encrypt_key

.globl	aes_v8_set_decrypt_key
.type	aes_v8_set_decrypt_key,%function
.align	5
aes_v8_set_decrypt_key:
	stp	x29,x30,[sp,#-16]!
	add	x29,sp,#0
	bl	.Lenc_key

	sub	x2,x2,#240		// restore original x2
	mov	x4,#-16
	add	x0,x2,x12,lsl#4	// end of key schedule

	ld1	{v0.4s},[x2]
	ld1	{v1.4s},[x0]
	st1	{v0.4s},[x0],x4
	st1	{v1.4s},[x2],#16

.Loop_imc:
	ld1	{v0.4s},[x2]
	ld1	{v1.4s},[x0]
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	st1	{v0.4s},[x0],x4
	st1	{v1.4s},[x2],#16
	cmp	x0,x2
	b.hi	.Loop_imc

	ld1	{v0.4s},[x2]
	aesimc	v0.16b,v0.16b
	st1	{v0.4s},[x0]

	eor	x0,x0,x0		// return value
	ldp	x29,x30,[sp],#16
	ret
.size	aes_v8_set_decrypt_key,.-aes_v8_set_decrypt_key
.globl	aes_v8_encrypt
.type	aes_v8_encrypt,%function
.align	5
aes_v8_encrypt:
	ldr	w3,[x2,#240]
	ld1	{v0.4s},[x2],#16
	ld1	{v2.16b},[x0]
	sub	w3,w3,#2
	ld1	{v1.4s},[x2],#16

.Loop_enc:
	aese	v2.16b,v0.16b
	ld1	{v0.4s},[x2],#16
	aesmc	v2.16b,v2.16b
	subs	w3,w3,#2
	aese	v2.16b,v1.16b
	ld1	{v1.4s},[x2],#16
	aesmc	v2.16b,v2.16b
	b.gt	.Loop_enc

	aese	v2.16b,v0.16b
	ld1	{v0.4s},[x2]
	aesmc	v2.16b,v2.16b
	aese	v2.16b,v1.16b
	eor	v2.16b,v2.16b,v0.16b

	st1	{v2.16b},[x1]
	ret
.size	aes_v8_encrypt,.-aes_v8_encrypt
.globl	aes_v8_decrypt
.type	aes_v8_decrypt,%function
.align	5
aes_v8_decrypt:
	ldr	w3,[x2,#240]
	ld1	{v0.4s},[x2],#16
	ld1	{v2.16b},[x0]
	sub	w3,w3,#2
	ld1	{v1.4s},[x2],#16

.Loop_dec:
	aesd	v2.16b,v0.16b
	ld1	{v0.4s},[x2],#16
	aesimc	v2.16b,v2.16b
	subs	w3,w3,#2
	aesd	v2.16b,v1.16b
	ld1	{v1.4s},[x2],#16
	aesimc	v2.16b,v2.16b
	b.gt	.Loop_dec

	aesd	v2.16b,v0.16b
	ld1	{v0.4s},[x2]
	aesimc	v2.16b,v2.16b
	aesd	v2.16b,v1.16b
	eor	v2.16b,v2.16b,v0.16b

	st1	{v2.16b},[x1]
	ret
.size	aes_v8_decrypt,.-aes_v8_decrypt
.globl	aes_v8_cbc_encrypt
.type	aes_v8_cbc_encrypt,%function
.align	5
aes_v8_cbc_encrypt:
	stp	x29,x30,[sp,#-16]!
	add	x29,sp,#0
	subs	x2,x2,#16
	mov	x8,#16
	b.lo	.Lcbc_abort
	csel	x8,xzr,x8,eq

	cmp	w5,#0			// en- or decrypting?
	ldr	w5,[x3,#240]
	and	x2,x2,#-16
	ld1	{v6.16b},[x4]
	ld1	{v0.16b},[x0],x8

	ld1	{v16.4s-v17.4s},[x3]		// load key schedule...
	sub	w5,w5,#6
	add	x7,x3,x5,lsl#4	// pointer to last 7 round keys
	sub	w5,w5,#2
	ld1	{v18.4s-v19.4s},[x7],#32
	ld1	{v20.4s-v21.4s},[x7],#32
	ld1	{v22.4s-v23.4s},[x7],#32
	ld1	{v7.4s},[x7]

	add	x7,x3,#32
	mov	w6,w5
	b.eq	.Lcbc_dec

	cmp	w5,#2
	eor	v0.16b,v0.16b,v6.16b
	eor	v5.16b,v16.16b,v7.16b
	b.eq	.Lcbc_enc128

.Loop_cbc_enc:
	aese	v0.16b,v16.16b
	ld1	{v16.4s},[x7],#16
	aesmc	v0.16b,v0.16b
	subs	w6,w6,#2
	aese	v0.16b,v17.16b
	ld1	{v17.4s},[x7],#16
	aesmc	v0.16b,v0.16b
	b.gt	.Loop_cbc_enc

	aese	v0.16b,v16.16b
	aesmc	v0.16b,v0.16b
	 subs	x2,x2,#16
	aese	v0.16b,v17.16b
	aesmc	v0.16b,v0.16b
	 csel	x8,xzr,x8,eq
	aese	v0.16b,v18.16b
	aesmc	v0.16b,v0.16b
	 add	x7,x3,#16
	aese	v0.16b,v19.16b
	aesmc	v0.16b,v0.16b
	 ld1	{v16.16b},[x0],x8
	aese	v0.16b,v20.16b
	aesmc	v0.16b,v0.16b
	 eor	v16.16b,v16.16b,v5.16b
	aese	v0.16b,v21.16b
	aesmc	v0.16b,v0.16b
	 ld1 {v17.4s},[x7],#16	// re-pre-load rndkey[1]
	aese	v0.16b,v22.16b
	aesmc	v0.16b,v0.16b
	aese	v0.16b,v23.16b

	 mov	w6,w5
	eor	v6.16b,v0.16b,v7.16b
	st1	{v6.16b},[x1],#16
	b.hs	.Loop_cbc_enc

	b	.Lcbc_done

.align	5
.Lcbc_enc128:
	ld1	{v2.4s-v3.4s},[x7]
	aese	v0.16b,v16.16b
	aesmc	v0.16b,v0.16b
	b	.Lenter_cbc_enc128
.Loop_cbc_enc128:
	aese	v0.16b,v16.16b
	aesmc	v0.16b,v0.16b
	 st1	{v6.16b},[x1],#16
.Lenter_cbc_enc128:
	aese	v0.16b,v17.16b
	aesmc	v0.16b,v0.16b
	 subs	x2,x2,#16
	aese	v0.16b,v2.16b
	aesmc	v0.16b,v0.16b
	 csel	x8,xzr,x8,eq
	aese	v0.16b,v3.16b
	aesmc	v0.16b,v0.16b
	aese	v0.16b,v18.16b
	aesmc	v0.16b,v0.16b
	aese	v0.16b,v19.16b
	aesmc	v0.16b,v0.16b
	 ld1	{v16.16b},[x0],x8
	aese	v0.16b,v20.16b
	aesmc	v0.16b,v0.16b
	aese	v0.16b,v21.16b
	aesmc	v0.16b,v0.16b
	aese	v0.16b,v22.16b
	aesmc	v0.16b,v0.16b
	 eor	v16.16b,v16.16b,v5.16b
	aese	v0.16b,v23.16b
	eor	v6.16b,v0.16b,v7.16b
	b.hs	.Loop_cbc_enc128

	st1	{v6.16b},[x1],#16
	b	.Lcbc_done

.align	5
.Lcbc_dec128:
	ld1	{v4.4s-v5.4s},[x7]
	eor	v6.16b,v6.16b,v7.16b
	eor	v2.16b,v0.16b,v7.16b
	mov	x12,x8

.Loop2x_cbc_dec128:
	aesd	v0.16b,v16.16b
	aesd	v1.16b,v16.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	 subs	x2,x2,#32
	aesd	v0.16b,v17.16b
	aesd	v1.16b,v17.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	 csel	x8,xzr,x8,lo
	aesd	v0.16b,v4.16b
	aesd	v1.16b,v4.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	 csel	x12,xzr,x12,ls
	aesd	v0.16b,v5.16b
	aesd	v1.16b,v5.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	aesd	v0.16b,v18.16b
	aesd	v1.16b,v18.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	aesd	v0.16b,v19.16b
	aesd	v1.16b,v19.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	aesd	v0.16b,v20.16b
	aesd	v1.16b,v20.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	aesd	v0.16b,v21.16b
	aesd	v1.16b,v21.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	aesd	v0.16b,v22.16b
	aesd	v1.16b,v22.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	aesd	v0.16b,v23.16b
	aesd	v1.16b,v23.16b

	eor	v6.16b,v6.16b,v0.16b
	ld1	{v0.16b},[x0],x8
	eor	v2.16b,v2.16b,v1.16b
	ld1	{v1.16b},[x0],x12
	st1	{v6.16b},[x1],#16
	eor	v6.16b,v3.16b,v7.16b
	st1	{v2.16b},[x1],#16
	eor	v2.16b,v0.16b,v7.16b
	orr	v3.16b,v1.16b,v1.16b
	b.hs	.Loop2x_cbc_dec128

	adds	x2,x2,#32
	eor	v6.16b,v6.16b,v7.16b
	b.eq	.Lcbc_done
	eor	v2.16b,v2.16b,v7.16b
	b	.Lcbc_dec_tail

.align	5
.Lcbc_dec:
	subs	x2,x2,#16
	orr	v2.16b,v0.16b,v0.16b
	b.lo	.Lcbc_dec_tail

	csel	x8,xzr,x8,eq
	cmp	w5,#2
	ld1	{v1.16b},[x0],x8
	orr	v3.16b,v1.16b,v1.16b
	b.eq	.Lcbc_dec128

.Loop2x_cbc_dec:
	aesd	v0.16b,v16.16b
	aesd	v1.16b,v16.16b
	ld1	{v16.4s},[x7],#16
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	subs	w6,w6,#2
	aesd	v0.16b,v17.16b
	aesd	v1.16b,v17.16b
	ld1	{v17.4s},[x7],#16
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	b.gt	.Loop2x_cbc_dec

	aesd	v0.16b,v16.16b
	aesd	v1.16b,v16.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	 eor	v4.16b,v6.16b,v7.16b
	 eor	v5.16b,v2.16b,v7.16b
	aesd	v0.16b,v17.16b
	aesd	v1.16b,v17.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	 orr	v6.16b,v3.16b,v3.16b
	 subs	x2,x2,#32
	aesd	v0.16b,v18.16b
	aesd	v1.16b,v18.16b
	aesimc	v0.16b,v0.16b
	 csel	x8,xzr,x8,lo
	aesimc	v1.16b,v1.16b
	 mov	x7,x3
	aesd	v0.16b,v19.16b
	aesd	v1.16b,v19.16b
	aesimc	v0.16b,v0.16b
	 ld1	{v2.16b},[x0],x8
	aesimc	v1.16b,v1.16b
	 csel	x8,xzr,x8,ls
	aesd	v0.16b,v20.16b
	aesd	v1.16b,v20.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	 ld1	{v3.16b},[x0],x8
	aesd	v0.16b,v21.16b
	aesd	v1.16b,v21.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	 ld1 {v16.4s},[x7],#16	// re-pre-load rndkey[0]
	aesd	v0.16b,v22.16b
	aesd	v1.16b,v22.16b
	aesimc	v0.16b,v0.16b
	aesimc	v1.16b,v1.16b
	 ld1 {v17.4s},[x7],#16	// re-pre-load rndkey[1]
	aesd	v0.16b,v23.16b
	aesd	v1.16b,v23.16b

	 mov	w6,w5
	eor	v4.16b,v4.16b,v0.16b
	eor	v5.16b,v5.16b,v1.16b
	 orr	v0.16b,v2.16b,v2.16b
	st1	{v4.16b},[x1],#16
	 orr	v1.16b,v3.16b,v3.16b
	st1	{v5.16b},[x1],#16
	b.hs	.Loop2x_cbc_dec

	adds	x2,x2,#32
	b.eq	.Lcbc_done

.Lcbc_dec_tail:
	aesd	v0.16b,v16.16b
	ld1	{v16.4s},[x7],#16
	aesimc	v0.16b,v0.16b
	subs	w6,w6,#2
	aesd	v0.16b,v17.16b
	ld1	{v17.4s},[x7],#16
	aesimc	v0.16b,v0.16b
	b.gt	.Lcbc_dec_tail

	aesd	v0.16b,v16.16b
	aesimc	v0.16b,v0.16b
	aesd	v0.16b,v17.16b
	aesimc	v0.16b,v0.16b
	 eor	v4.16b,v6.16b,v7.16b
	aesd	v0.16b,v18.16b
	aesimc	v0.16b,v0.16b
	 orr	v6.16b,v2.16b,v2.16b
	aesd	v0.16b,v19.16b
	aesimc	v0.16b,v0.16b
	aesd	v0.16b,v20.16b
	aesimc	v0.16b,v0.16b
	aesd	v0.16b,v21.16b
	aesimc	v0.16b,v0.16b
	aesd	v0.16b,v22.16b
	aesimc	v0.16b,v0.16b
	aesd	v0.16b,v23.16b

	eor	v4.16b,v4.16b,v0.16b
	st1	{v4.16b},[x1],#16

.Lcbc_done:
	st1	{v6.16b},[x4]
.Lcbc_abort:
	ldr	x29,[sp],#16
	ret
.size	aes_v8_cbc_encrypt,.-aes_v8_cbc_encrypt
.globl	aes_v8_ctr32_encrypt_blocks
.type	aes_v8_ctr32_encrypt_blocks,%function
.align	5
aes_v8_ctr32_encrypt_blocks:
	stp		x29,x30,[sp,#-16]!
	add		x29,sp,#0
	ldr		w5,[x3,#240]

	ldr		w8, [x4, #12]
	ld1		{v0.4s},[x4]

	ld1		{v16.4s-v17.4s},[x3]		// load key schedule...
	sub		w5,w5,#6
	add		x7,x3,x5,lsl#4	// pointer to last 7 round keys
	sub		w5,w5,#2
	ld1		{v18.4s-v19.4s},[x7],#32
	ld1		{v20.4s-v21.4s},[x7],#32
	ld1		{v22.4s-v23.4s},[x7],#32
	ld1		{v7.4s},[x7]

	add		x7,x3,#32
	mov		w6,w5

	subs		x2,x2,#2
	b.lo		.Lctr32_tail

#ifndef __ARMEB__
	rev		w8, w8
#endif
	orr		v1.16b,v0.16b,v0.16b
	add		w8, w8, #1
	orr		v6.16b,v0.16b,v0.16b
	rev		w10, w8
	cmp		w5,#2
	mov		v1.s[3],w10
	b.eq		.Lctr32_128

.Loop2x_ctr32:
	aese		v0.16b,v16.16b
	aese		v1.16b,v16.16b
	ld1		{v16.4s},[x7],#16
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	subs		w6,w6,#2
	aese		v0.16b,v17.16b
	aese		v1.16b,v17.16b
	ld1		{v17.4s},[x7],#16
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	b.gt		.Loop2x_ctr32

	aese		v0.16b,v16.16b
	aese		v1.16b,v16.16b
	aesmc		v4.16b,v0.16b
	 orr		v0.16b,v6.16b,v6.16b
	aesmc		v5.16b,v1.16b
	 orr		v1.16b,v6.16b,v6.16b
	aese		v4.16b,v17.16b
	aese		v5.16b,v17.16b
	 ld1		{v2.16b},[x0],#16
	aesmc		v4.16b,v4.16b
	 ld1		{v3.16b},[x0],#16
	aesmc		v5.16b,v5.16b
	 add		w8,w8,#1
	aese		v4.16b,v18.16b
	aese		v5.16b,v18.16b
	 rev		w9,w8
	aesmc		v4.16b,v4.16b
	aesmc		v5.16b,v5.16b
	 add		w8,w8,#1
	aese		v4.16b,v19.16b
	aese		v5.16b,v19.16b
	 eor		v2.16b,v2.16b,v7.16b
	 rev		w10,w8
	aesmc		v4.16b,v4.16b
	aesmc		v5.16b,v5.16b
	 eor		v3.16b,v3.16b,v7.16b
	 mov		x7,x3
	aese		v4.16b,v20.16b
	aese		v5.16b,v20.16b
	 subs		x2,x2,#2
	aesmc		v4.16b,v4.16b
	aesmc		v5.16b,v5.16b
	 ld1	 {v16.4s-v17.4s},[x7],#32	// re-pre-load rndkey[0-1]
	aese		v4.16b,v21.16b
	aese		v5.16b,v21.16b
	aesmc		v4.16b,v4.16b
	aesmc		v5.16b,v5.16b
	aese		v4.16b,v22.16b
	aese		v5.16b,v22.16b
	 mov	v0.s[3], w9
	aesmc		v4.16b,v4.16b
	 mov	v1.s[3], w10
	aesmc		v5.16b,v5.16b
	aese		v4.16b,v23.16b
	aese		v5.16b,v23.16b

	 mov		w6,w5
	eor		v2.16b,v2.16b,v4.16b
	eor		v3.16b,v3.16b,v5.16b
	st1		{v2.16b},[x1],#16
	st1		{v3.16b},[x1],#16
	b.hs		.Loop2x_ctr32

	adds		x2,x2,#2
	b.eq		.Lctr32_done
	b		.Lctr32_tail

.Lctr32_128:
	ld1		{v4.4s-v5.4s},[x7]

.Loop2x_ctr32_128:
	aese		v0.16b,v16.16b
	aese		v1.16b,v16.16b
	aesmc		v0.16b,v0.16b
	 ld1		{v2.16b},[x0],#16
	aesmc		v1.16b,v1.16b
	 ld1		{v3.16b},[x0],#16
	aese		v0.16b,v17.16b
	aese		v1.16b,v17.16b
	 add		w8,w8,#1
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	 rev		w9,w8
	aese		v0.16b,v4.16b
	aese		v1.16b,v4.16b
	 add		w8,w8,#1
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	 rev		w10,w8
	aese		v0.16b,v5.16b
	aese		v1.16b,v5.16b
	 subs		x2,x2,#2
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	aese		v0.16b,v18.16b
	aese		v1.16b,v18.16b
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	aese		v0.16b,v19.16b
	aese		v1.16b,v19.16b
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	aese		v0.16b,v20.16b
	aese		v1.16b,v20.16b
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	aese		v0.16b,v21.16b
	aese		v1.16b,v21.16b
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	aese		v0.16b,v22.16b
	aese		v1.16b,v22.16b
	aesmc		v0.16b,v0.16b
	aesmc		v1.16b,v1.16b
	 eor		v2.16b,v2.16b,v7.16b
	aese		v0.16b,v23.16b
	 eor		v3.16b,v3.16b,v7.16b
	aese		v1.16b,v23.16b

	eor		v2.16b,v2.16b,v0.16b
	orr		v0.16b,v6.16b,v6.16b
	eor		v3.16b,v3.16b,v1.16b
	orr		v1.16b,v6.16b,v6.16b
	st1		{v2.16b},[x1],#16
	mov		v0.s[3], w9
	st1		{v3.16b},[x1],#16
	mov		v1.s[3], w10
	b.hs		.Loop2x_ctr32_128

	adds		x2,x2,#2
	b.eq		.Lctr32_done

.Lctr32_tail:
	aese		v0.16b,v16.16b
	ld1		{v16.4s},[x7],#16
	aesmc		v0.16b,v0.16b
	subs		w6,w6,#2
	aese		v0.16b,v17.16b
	ld1		{v17.4s},[x7],#16
	aesmc		v0.16b,v0.16b
	b.gt		.Lctr32_tail

	aese		v0.16b,v16.16b
	aesmc		v0.16b,v0.16b
	aese		v0.16b,v17.16b
	aesmc		v0.16b,v0.16b
	 ld1		{v2.16b},[x0]
	aese		v0.16b,v18.16b
	aesmc		v0.16b,v0.16b
	aese		v0.16b,v19.16b
	aesmc		v0.16b,v0.16b
	aese		v0.16b,v20.16b
	aesmc		v0.16b,v0.16b
	aese		v0.16b,v21.16b
	aesmc		v0.16b,v0.16b
	aese		v0.16b,v22.16b
	aesmc		v0.16b,v0.16b
	 eor		v2.16b,v2.16b,v7.16b
	aese		v0.16b,v23.16b

	eor		v2.16b,v2.16b,v0.16b
	st1		{v2.16b},[x1]

.Lctr32_done:
	ldr		x29,[sp],#16
	ret
.size	aes_v8_ctr32_encrypt_blocks,.-aes_v8_ctr32_encrypt_blocks
#endif
