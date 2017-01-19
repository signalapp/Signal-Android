.set	mips2
.rdata
.asciiz	"mips3.s, Version 1.2"
.asciiz	"MIPS II/III/IV ISA artwork by Andy Polyakov <appro@fy.chalmers.se>"

.text
.set	noat

.align	5
.globl	bn_mul_add_words
.ent	bn_mul_add_words
bn_mul_add_words:
	.set	noreorder
	bgtz	$6,bn_mul_add_words_internal
	move	$2,$0
	jr	$31
	move	$4,$2
.end	bn_mul_add_words

.align	5
.ent	bn_mul_add_words_internal
bn_mul_add_words_internal:
	.set	reorder
	li	$3,-4
	and	$8,$6,$3
	beqz	$8,.L_bn_mul_add_words_tail

.L_bn_mul_add_words_loop:
	lw	$12,0($5)
	multu	$12,$7
	lw	$13,0($4)
	lw	$14,4($5)
	lw	$15,4($4)
	lw	$8,2*4($5)
	lw	$9,2*4($4)
	addu	$13,$2
	sltu	$2,$13,$2	# All manuals say it "compares 32-bit
				# values", but it seems to work fine
				# even on 64-bit registers.
	mflo	$1
	mfhi	$12
	addu	$13,$1
	addu	$2,$12
	 multu	$14,$7
	sltu	$1,$13,$1
	sw	$13,0($4)
	addu	$2,$1

	lw	$10,3*4($5)
	lw	$11,3*4($4)
	addu	$15,$2
	sltu	$2,$15,$2
	mflo	$1
	mfhi	$14
	addu	$15,$1
	addu	$2,$14
	 multu	$8,$7
	sltu	$1,$15,$1
	sw	$15,4($4)
	addu	$2,$1

	subu	$6,4
	addu $4,4*4
	addu $5,4*4
	addu	$9,$2
	sltu	$2,$9,$2
	mflo	$1
	mfhi	$8
	addu	$9,$1
	addu	$2,$8
	 multu	$10,$7
	sltu	$1,$9,$1
	sw	$9,-2*4($4)
	addu	$2,$1


	and	$8,$6,$3
	addu	$11,$2
	sltu	$2,$11,$2
	mflo	$1
	mfhi	$10
	addu	$11,$1
	addu	$2,$10
	sltu	$1,$11,$1
	sw	$11,-4($4)
	.set	noreorder
	bgtz	$8,.L_bn_mul_add_words_loop
	addu	$2,$1

	beqz	$6,.L_bn_mul_add_words_return
	nop

.L_bn_mul_add_words_tail:
	.set	reorder
	lw	$12,0($5)
	multu	$12,$7
	lw	$13,0($4)
	subu	$6,1
	addu	$13,$2
	sltu	$2,$13,$2
	mflo	$1
	mfhi	$12
	addu	$13,$1
	addu	$2,$12
	sltu	$1,$13,$1
	sw	$13,0($4)
	addu	$2,$1
	beqz	$6,.L_bn_mul_add_words_return

	lw	$12,4($5)
	multu	$12,$7
	lw	$13,4($4)
	subu	$6,1
	addu	$13,$2
	sltu	$2,$13,$2
	mflo	$1
	mfhi	$12
	addu	$13,$1
	addu	$2,$12
	sltu	$1,$13,$1
	sw	$13,4($4)
	addu	$2,$1
	beqz	$6,.L_bn_mul_add_words_return

	lw	$12,2*4($5)
	multu	$12,$7
	lw	$13,2*4($4)
	addu	$13,$2
	sltu	$2,$13,$2
	mflo	$1
	mfhi	$12
	addu	$13,$1
	addu	$2,$12
	sltu	$1,$13,$1
	sw	$13,2*4($4)
	addu	$2,$1

.L_bn_mul_add_words_return:
	.set	noreorder
	jr	$31
	move	$4,$2
.end	bn_mul_add_words_internal

.align	5
.globl	bn_mul_words
.ent	bn_mul_words
bn_mul_words:
	.set	noreorder
	bgtz	$6,bn_mul_words_internal
	move	$2,$0
	jr	$31
	move	$4,$2
.end	bn_mul_words

.align	5
.ent	bn_mul_words_internal
bn_mul_words_internal:
	.set	reorder
	li	$3,-4
	and	$8,$6,$3
	beqz	$8,.L_bn_mul_words_tail

.L_bn_mul_words_loop:
	lw	$12,0($5)
	multu	$12,$7
	lw	$14,4($5)
	lw	$8,2*4($5)
	lw	$10,3*4($5)
	mflo	$1
	mfhi	$12
	addu	$2,$1
	sltu	$13,$2,$1
	 multu	$14,$7
	sw	$2,0($4)
	addu	$2,$13,$12

	subu	$6,4
	addu $4,4*4
	addu $5,4*4
	mflo	$1
	mfhi	$14
	addu	$2,$1
	sltu	$15,$2,$1
	 multu	$8,$7
	sw	$2,-3*4($4)
	addu	$2,$15,$14

	mflo	$1
	mfhi	$8
	addu	$2,$1
	sltu	$9,$2,$1
	 multu	$10,$7
	sw	$2,-2*4($4)
	addu	$2,$9,$8

	and	$8,$6,$3
	mflo	$1
	mfhi	$10
	addu	$2,$1
	sltu	$11,$2,$1
	sw	$2,-4($4)
	.set	noreorder
	bgtz	$8,.L_bn_mul_words_loop
	addu	$2,$11,$10

	beqz	$6,.L_bn_mul_words_return
	nop

.L_bn_mul_words_tail:
	.set	reorder
	lw	$12,0($5)
	multu	$12,$7
	subu	$6,1
	mflo	$1
	mfhi	$12
	addu	$2,$1
	sltu	$13,$2,$1
	sw	$2,0($4)
	addu	$2,$13,$12
	beqz	$6,.L_bn_mul_words_return

	lw	$12,4($5)
	multu	$12,$7
	subu	$6,1
	mflo	$1
	mfhi	$12
	addu	$2,$1
	sltu	$13,$2,$1
	sw	$2,4($4)
	addu	$2,$13,$12
	beqz	$6,.L_bn_mul_words_return

	lw	$12,2*4($5)
	multu	$12,$7
	mflo	$1
	mfhi	$12
	addu	$2,$1
	sltu	$13,$2,$1
	sw	$2,2*4($4)
	addu	$2,$13,$12

.L_bn_mul_words_return:
	.set	noreorder
	jr	$31
	move	$4,$2
.end	bn_mul_words_internal

.align	5
.globl	bn_sqr_words
.ent	bn_sqr_words
bn_sqr_words:
	.set	noreorder
	bgtz	$6,bn_sqr_words_internal
	move	$2,$0
	jr	$31
	move	$4,$2
.end	bn_sqr_words

.align	5
.ent	bn_sqr_words_internal
bn_sqr_words_internal:
	.set	reorder
	li	$3,-4
	and	$8,$6,$3
	beqz	$8,.L_bn_sqr_words_tail

.L_bn_sqr_words_loop:
	lw	$12,0($5)
	multu	$12,$12
	lw	$14,4($5)
	lw	$8,2*4($5)
	lw	$10,3*4($5)
	mflo	$13
	mfhi	$12
	sw	$13,0($4)
	sw	$12,4($4)

	multu	$14,$14
	subu	$6,4
	addu $4,8*4
	addu $5,4*4
	mflo	$15
	mfhi	$14
	sw	$15,-6*4($4)
	sw	$14,-5*4($4)

	multu	$8,$8
	mflo	$9
	mfhi	$8
	sw	$9,-4*4($4)
	sw	$8,-3*4($4)


	multu	$10,$10
	and	$8,$6,$3
	mflo	$11
	mfhi	$10
	sw	$11,-2*4($4)

	.set	noreorder
	bgtz	$8,.L_bn_sqr_words_loop
	sw	$10,-4($4)

	beqz	$6,.L_bn_sqr_words_return
	nop

.L_bn_sqr_words_tail:
	.set	reorder
	lw	$12,0($5)
	multu	$12,$12
	subu	$6,1
	mflo	$13
	mfhi	$12
	sw	$13,0($4)
	sw	$12,4($4)
	beqz	$6,.L_bn_sqr_words_return

	lw	$12,4($5)
	multu	$12,$12
	subu	$6,1
	mflo	$13
	mfhi	$12
	sw	$13,2*4($4)
	sw	$12,3*4($4)
	beqz	$6,.L_bn_sqr_words_return

	lw	$12,2*4($5)
	multu	$12,$12
	mflo	$13
	mfhi	$12
	sw	$13,4*4($4)
	sw	$12,5*4($4)

.L_bn_sqr_words_return:
	.set	noreorder
	jr	$31
	move	$4,$2

.end	bn_sqr_words_internal

.align	5
.globl	bn_add_words
.ent	bn_add_words
bn_add_words:
	.set	noreorder
	bgtz	$7,bn_add_words_internal
	move	$2,$0
	jr	$31
	move	$4,$2
.end	bn_add_words

.align	5
.ent	bn_add_words_internal
bn_add_words_internal:
	.set	reorder
	li	$3,-4
	and	$1,$7,$3
	beqz	$1,.L_bn_add_words_tail

.L_bn_add_words_loop:
	lw	$12,0($5)
	lw	$8,0($6)
	subu	$7,4
	lw	$13,4($5)
	and	$1,$7,$3
	lw	$14,2*4($5)
	addu $6,4*4
	lw	$15,3*4($5)
	addu $4,4*4
	lw	$9,-3*4($6)
	addu $5,4*4
	lw	$10,-2*4($6)
	lw	$11,-4($6)
	addu	$8,$12
	sltu	$24,$8,$12
	addu	$12,$8,$2
	sltu	$2,$12,$8
	sw	$12,-4*4($4)
	addu	$2,$24

	addu	$9,$13
	sltu	$25,$9,$13
	addu	$13,$9,$2
	sltu	$2,$13,$9
	sw	$13,-3*4($4)
	addu	$2,$25

	addu	$10,$14
	sltu	$24,$10,$14
	addu	$14,$10,$2
	sltu	$2,$14,$10
	sw	$14,-2*4($4)
	addu	$2,$24
	
	addu	$11,$15
	sltu	$25,$11,$15
	addu	$15,$11,$2
	sltu	$2,$15,$11
	sw	$15,-4($4)
	
	.set	noreorder
	bgtz	$1,.L_bn_add_words_loop
	addu	$2,$25

	beqz	$7,.L_bn_add_words_return
	nop

.L_bn_add_words_tail:
	.set	reorder
	lw	$12,0($5)
	lw	$8,0($6)
	addu	$8,$12
	subu	$7,1
	sltu	$24,$8,$12
	addu	$12,$8,$2
	sltu	$2,$12,$8
	sw	$12,0($4)
	addu	$2,$24
	beqz	$7,.L_bn_add_words_return

	lw	$13,4($5)
	lw	$9,4($6)
	addu	$9,$13
	subu	$7,1
	sltu	$25,$9,$13
	addu	$13,$9,$2
	sltu	$2,$13,$9
	sw	$13,4($4)
	addu	$2,$25
	beqz	$7,.L_bn_add_words_return

	lw	$14,2*4($5)
	lw	$10,2*4($6)
	addu	$10,$14
	sltu	$24,$10,$14
	addu	$14,$10,$2
	sltu	$2,$14,$10
	sw	$14,2*4($4)
	addu	$2,$24

.L_bn_add_words_return:
	.set	noreorder
	jr	$31
	move	$4,$2

.end	bn_add_words_internal

.align	5
.globl	bn_sub_words
.ent	bn_sub_words
bn_sub_words:
	.set	noreorder
	bgtz	$7,bn_sub_words_internal
	move	$2,$0
	jr	$31
	move	$4,$0
.end	bn_sub_words

.align	5
.ent	bn_sub_words_internal
bn_sub_words_internal:
	.set	reorder
	li	$3,-4
	and	$1,$7,$3
	beqz	$1,.L_bn_sub_words_tail

.L_bn_sub_words_loop:
	lw	$12,0($5)
	lw	$8,0($6)
	subu	$7,4
	lw	$13,4($5)
	and	$1,$7,$3
	lw	$14,2*4($5)
	addu $6,4*4
	lw	$15,3*4($5)
	addu $4,4*4
	lw	$9,-3*4($6)
	addu $5,4*4
	lw	$10,-2*4($6)
	lw	$11,-4($6)
	sltu	$24,$12,$8
	subu	$8,$12,$8
	subu	$12,$8,$2
	sgtu	$2,$12,$8
	sw	$12,-4*4($4)
	addu	$2,$24

	sltu	$25,$13,$9
	subu	$9,$13,$9
	subu	$13,$9,$2
	sgtu	$2,$13,$9
	sw	$13,-3*4($4)
	addu	$2,$25


	sltu	$24,$14,$10
	subu	$10,$14,$10
	subu	$14,$10,$2
	sgtu	$2,$14,$10
	sw	$14,-2*4($4)
	addu	$2,$24

	sltu	$25,$15,$11
	subu	$11,$15,$11
	subu	$15,$11,$2
	sgtu	$2,$15,$11
	sw	$15,-4($4)

	.set	noreorder
	bgtz	$1,.L_bn_sub_words_loop
	addu	$2,$25

	beqz	$7,.L_bn_sub_words_return
	nop

.L_bn_sub_words_tail:
	.set	reorder
	lw	$12,0($5)
	lw	$8,0($6)
	subu	$7,1
	sltu	$24,$12,$8
	subu	$8,$12,$8
	subu	$12,$8,$2
	sgtu	$2,$12,$8
	sw	$12,0($4)
	addu	$2,$24
	beqz	$7,.L_bn_sub_words_return

	lw	$13,4($5)
	subu	$7,1
	lw	$9,4($6)
	sltu	$25,$13,$9
	subu	$9,$13,$9
	subu	$13,$9,$2
	sgtu	$2,$13,$9
	sw	$13,4($4)
	addu	$2,$25
	beqz	$7,.L_bn_sub_words_return

	lw	$14,2*4($5)
	lw	$10,2*4($6)
	sltu	$24,$14,$10
	subu	$10,$14,$10
	subu	$14,$10,$2
	sgtu	$2,$14,$10
	sw	$14,2*4($4)
	addu	$2,$24

.L_bn_sub_words_return:
	.set	noreorder
	jr	$31
	move	$4,$2
.end	bn_sub_words_internal

.align 5
.globl	bn_div_3_words
.ent	bn_div_3_words
bn_div_3_words:
	.set	noreorder
	move	$7,$4		# we know that bn_div_words does not
				# touch $7, $10, $11 and preserves $6
				# so that we can save two arguments
				# and return address in registers
				# instead of stack:-)
				
	lw	$4,($7)
	move	$10,$5
	bne	$4,$6,bn_div_3_words_internal
	lw	$5,-4($7)
	li	$2,-1
	jr	$31
	move	$4,$2
.end	bn_div_3_words

.align	5
.ent	bn_div_3_words_internal
bn_div_3_words_internal:
	.set	reorder
	move	$11,$31
	bal	bn_div_words_internal
	move	$31,$11
	multu	$10,$2
	lw	$14,-2*4($7)
	move	$8,$0
	mfhi	$13
	mflo	$12
	sltu	$24,$13,$5
.L_bn_div_3_words_inner_loop:
	bnez	$24,.L_bn_div_3_words_inner_loop_done
	sgeu	$1,$14,$12
	seq	$25,$13,$5
	and	$1,$25
	sltu	$15,$12,$10
	addu	$5,$6
	subu	$13,$15
	subu	$12,$10
	sltu	$24,$13,$5
	sltu	$8,$5,$6
	or	$24,$8
	.set	noreorder
	beqz	$1,.L_bn_div_3_words_inner_loop
	subu	$2,1
	addu	$2,1
	.set	reorder
.L_bn_div_3_words_inner_loop_done:
	.set	noreorder
	jr	$31
	move	$4,$2
.end	bn_div_3_words_internal

.align	5
.globl	bn_div_words
.ent	bn_div_words
bn_div_words:
	.set	noreorder
	bnez	$6,bn_div_words_internal
	li	$2,-1		# I would rather signal div-by-zero
				# which can be done with 'break 7'
	jr	$31
	move	$4,$2
.end	bn_div_words

.align	5
.ent	bn_div_words_internal
bn_div_words_internal:
	move	$3,$0
	bltz	$6,.L_bn_div_words_body
	move	$25,$3
	sll	$6,1
	bgtz	$6,.-4
	addu	$25,1

	.set	reorder
	negu	$13,$25
	li	$14,-1
	sll	$14,$13
	and	$14,$4
	srl	$1,$5,$13
	.set	noreorder
	beqz	$14,.+12
	nop
	break	6		# signal overflow
	.set	reorder
	sll	$4,$25
	sll	$5,$25
	or	$4,$1
.L_bn_div_words_body:
	srl	$3,$6,4*4	# bits
	sgeu	$1,$4,$6
	.set	noreorder
	beqz	$1,.+12
	nop
	subu	$4,$6
	.set	reorder

	li	$8,-1
	srl	$9,$4,4*4	# bits
	srl	$8,4*4	# q=0xffffffff
	beq	$3,$9,.L_bn_div_words_skip_div1
	divu	$0,$4,$3
	mflo	$8
.L_bn_div_words_skip_div1:
	multu	$6,$8
	sll	$15,$4,4*4	# bits
	srl	$1,$5,4*4	# bits
	or	$15,$1
	mflo	$12
	mfhi	$13
.L_bn_div_words_inner_loop1:
	sltu	$14,$15,$12
	seq	$24,$9,$13
	sltu	$1,$9,$13
	and	$14,$24
	sltu	$2,$12,$6
	or	$1,$14
	.set	noreorder
	beqz	$1,.L_bn_div_words_inner_loop1_done
	subu	$13,$2
	subu	$12,$6
	b	.L_bn_div_words_inner_loop1
	subu	$8,1
	.set	reorder
.L_bn_div_words_inner_loop1_done:

	sll	$5,4*4	# bits
	subu	$4,$15,$12
	sll	$2,$8,4*4	# bits

	li	$8,-1
	srl	$9,$4,4*4	# bits
	srl	$8,4*4	# q=0xffffffff
	beq	$3,$9,.L_bn_div_words_skip_div2
	divu	$0,$4,$3
	mflo	$8
.L_bn_div_words_skip_div2:
	multu	$6,$8
	sll	$15,$4,4*4	# bits
	srl	$1,$5,4*4	# bits
	or	$15,$1
	mflo	$12
	mfhi	$13
.L_bn_div_words_inner_loop2:
	sltu	$14,$15,$12
	seq	$24,$9,$13
	sltu	$1,$9,$13
	and	$14,$24
	sltu	$3,$12,$6
	or	$1,$14
	.set	noreorder
	beqz	$1,.L_bn_div_words_inner_loop2_done
	subu	$13,$3
	subu	$12,$6
	b	.L_bn_div_words_inner_loop2
	subu	$8,1
	.set	reorder
.L_bn_div_words_inner_loop2_done:

	subu	$4,$15,$12
	or	$2,$8
	srl	$3,$4,$25	# $3 contains remainder if anybody wants it
	srl	$6,$25		# restore $6

	.set	noreorder
	move	$5,$3
	jr	$31
	move	$4,$2
.end	bn_div_words_internal

.align	5
.globl	bn_mul_comba8
.ent	bn_mul_comba8
bn_mul_comba8:
	.set	noreorder
	.frame	$29,6*4,$31
	.mask	0x003f0000,-4
	subu $29,6*4
	sw	$21,5*4($29)
	sw	$20,4*4($29)
	sw	$19,3*4($29)
	sw	$18,2*4($29)
	sw	$17,1*4($29)
	sw	$16,0*4($29)

	.set	reorder
	lw	$12,0($5)	# If compiled with -mips3 option on
				# R5000 box assembler barks on this
				# 1ine with "should not have mult/div
				# as last instruction in bb (R10K
				# bug)" warning. If anybody out there
				# has a clue about how to circumvent
				# this do send me a note.
				#		<appro@fy.chalmers.se>

	lw	$8,0($6)
	lw	$13,4($5)
	lw	$14,2*4($5)
	multu	$12,$8		# mul_add_c(a[0],b[0],c1,c2,c3);
	lw	$15,3*4($5)
	lw	$9,4($6)
	lw	$10,2*4($6)
	lw	$11,3*4($6)
	mflo	$2
	mfhi	$3

	lw	$16,4*4($5)
	lw	$18,5*4($5)
	multu	$12,$9		# mul_add_c(a[0],b[1],c2,c3,c1);
	lw	$20,6*4($5)
	lw	$5,7*4($5)
	lw	$17,4*4($6)
	lw	$19,5*4($6)
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$13,$8		# mul_add_c(a[1],b[0],c2,c3,c1);
	addu	$7,$25,$1
	lw	$21,6*4($6)
	lw	$6,7*4($6)
	sw	$2,0($4)	# r[0]=c1;
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	 multu	$14,$8		# mul_add_c(a[2],b[0],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$2,$7,$25
	sw	$3,4($4)	# r[1]=c2;

	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$13,$9		# mul_add_c(a[1],b[1],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$12,$10		# mul_add_c(a[0],b[2],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$3,$2,$25
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	 multu	$12,$11		# mul_add_c(a[0],b[3],c1,c2,c3);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,2*4($4)	# r[2]=c3;

	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$13,$10		# mul_add_c(a[1],b[2],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$7,$3,$25
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$14,$9		# mul_add_c(a[2],b[1],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$15,$8		# mul_add_c(a[3],b[0],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	 multu	$16,$8		# mul_add_c(a[4],b[0],c2,c3,c1);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,3*4($4)	# r[3]=c1;

	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$15,$9		# mul_add_c(a[3],b[1],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$2,$7,$25
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$14,$10		# mul_add_c(a[2],b[2],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$13,$11		# mul_add_c(a[1],b[3],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$12,$17		# mul_add_c(a[0],b[4],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	 multu	$12,$19		# mul_add_c(a[0],b[5],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,4*4($4)	# r[4]=c2;

	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$13,$17		# mul_add_c(a[1],b[4],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$3,$2,$25
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$14,$11		# mul_add_c(a[2],b[3],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$15,$10		# mul_add_c(a[3],b[2],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$16,$9		# mul_add_c(a[4],b[1],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$18,$8		# mul_add_c(a[5],b[0],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	 multu	$20,$8		# mul_add_c(a[6],b[0],c1,c2,c3);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,5*4($4)	# r[5]=c3;

	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$18,$9		# mul_add_c(a[5],b[1],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$7,$3,$25
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$16,$10		# mul_add_c(a[4],b[2],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$15,$11		# mul_add_c(a[3],b[3],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$14,$17		# mul_add_c(a[2],b[4],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$13,$19		# mul_add_c(a[1],b[5],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$12,$21		# mul_add_c(a[0],b[6],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	 multu	$12,$6		# mul_add_c(a[0],b[7],c2,c3,c1);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,6*4($4)	# r[6]=c1;

	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$13,$21		# mul_add_c(a[1],b[6],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$2,$7,$25
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$14,$19		# mul_add_c(a[2],b[5],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$15,$17		# mul_add_c(a[3],b[4],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$16,$11		# mul_add_c(a[4],b[3],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$18,$10		# mul_add_c(a[5],b[2],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$20,$9		# mul_add_c(a[6],b[1],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$5,$8		# mul_add_c(a[7],b[0],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	 multu	$5,$9		# mul_add_c(a[7],b[1],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,7*4($4)	# r[7]=c2;

	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$20,$10		# mul_add_c(a[6],b[2],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$3,$2,$25
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$18,$11		# mul_add_c(a[5],b[3],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$16,$17		# mul_add_c(a[4],b[4],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$15,$19		# mul_add_c(a[3],b[5],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$14,$21		# mul_add_c(a[2],b[6],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$13,$6		# mul_add_c(a[1],b[7],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	 multu	$14,$6		# mul_add_c(a[2],b[7],c1,c2,c3);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,8*4($4)	# r[8]=c3;

	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$15,$21		# mul_add_c(a[3],b[6],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$7,$3,$25
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$16,$19		# mul_add_c(a[4],b[5],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$18,$17		# mul_add_c(a[5],b[4],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$20,$11		# mul_add_c(a[6],b[3],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$5,$10		# mul_add_c(a[7],b[2],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	 multu	$5,$11		# mul_add_c(a[7],b[3],c2,c3,c1);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,9*4($4)	# r[9]=c1;

	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$20,$17		# mul_add_c(a[6],b[4],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$2,$7,$25
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$18,$19		# mul_add_c(a[5],b[5],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$16,$21		# mul_add_c(a[4],b[6],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$15,$6		# mul_add_c(a[3],b[7],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$16,$6		# mul_add_c(a[4],b[7],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,10*4($4)	# r[10]=c2;

	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$18,$21		# mul_add_c(a[5],b[6],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$3,$2,$25
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$20,$19		# mul_add_c(a[6],b[5],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$5,$17		# mul_add_c(a[7],b[4],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	 multu	$5,$19		# mul_add_c(a[7],b[5],c1,c2,c3);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,11*4($4)	# r[11]=c3;

	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$20,$21		# mul_add_c(a[6],b[6],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$7,$3,$25
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$18,$6		# mul_add_c(a[5],b[7],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	 multu	$20,$6		# mul_add_c(a[6],b[7],c2,c3,c1);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,12*4($4)	# r[12]=c1;

	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$5,$21		# mul_add_c(a[7],b[6],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$2,$7,$25
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$5,$6		# mul_add_c(a[7],b[7],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,13*4($4)	# r[13]=c2;

	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sw	$7,14*4($4)	# r[14]=c3;
	sw	$2,15*4($4)	# r[15]=c1;

	.set	noreorder
	lw	$21,5*4($29)
	lw	$20,4*4($29)
	lw	$19,3*4($29)
	lw	$18,2*4($29)
	lw	$17,1*4($29)
	lw	$16,0*4($29)
	jr	$31
	addu $29,6*4
.end	bn_mul_comba8

.align	5
.globl	bn_mul_comba4
.ent	bn_mul_comba4
bn_mul_comba4:
	.set	reorder
	lw	$12,0($5)
	lw	$8,0($6)
	lw	$13,4($5)
	lw	$14,2*4($5)
	multu	$12,$8		# mul_add_c(a[0],b[0],c1,c2,c3);
	lw	$15,3*4($5)
	lw	$9,4($6)
	lw	$10,2*4($6)
	lw	$11,3*4($6)
	mflo	$2
	mfhi	$3
	sw	$2,0($4)

	multu	$12,$9		# mul_add_c(a[0],b[1],c2,c3,c1);
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$13,$8		# mul_add_c(a[1],b[0],c2,c3,c1);
	addu	$7,$25,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	 multu	$14,$8		# mul_add_c(a[2],b[0],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$2,$7,$25
	sw	$3,4($4)

	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$13,$9		# mul_add_c(a[1],b[1],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$12,$10		# mul_add_c(a[0],b[2],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$3,$2,$25
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	 multu	$12,$11		# mul_add_c(a[0],b[3],c1,c2,c3);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,2*4($4)

	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$13,$10		# mul_add_c(a[1],b[2],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$7,$3,$25
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$14,$9		# mul_add_c(a[2],b[1],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	multu	$15,$8		# mul_add_c(a[3],b[0],c1,c2,c3);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	 multu	$15,$9		# mul_add_c(a[3],b[1],c2,c3,c1);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,3*4($4)

	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$14,$10		# mul_add_c(a[2],b[2],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$2,$7,$25
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	multu	$13,$11		# mul_add_c(a[1],b[3],c2,c3,c1);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	 multu	$14,$11		# mul_add_c(a[2],b[3],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,4*4($4)

	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	multu	$15,$10		# mul_add_c(a[3],b[2],c3,c1,c2);
	addu	$25,$1
	addu	$2,$25
	sltu	$3,$2,$25
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	 multu	$15,$11		# mul_add_c(a[3],b[3],c1,c2,c3);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,5*4($4)

	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sw	$2,6*4($4)
	sw	$3,7*4($4)

	.set	noreorder
	jr	$31
	nop
.end	bn_mul_comba4

.align	5
.globl	bn_sqr_comba8
.ent	bn_sqr_comba8
bn_sqr_comba8:
	.set	reorder
	lw	$12,0($5)
	lw	$13,4($5)
	lw	$14,2*4($5)
	lw	$15,3*4($5)

	multu	$12,$12		# mul_add_c(a[0],b[0],c1,c2,c3);
	lw	$8,4*4($5)
	lw	$9,5*4($5)
	lw	$10,6*4($5)
	lw	$11,7*4($5)
	mflo	$2
	mfhi	$3
	sw	$2,0($4)

	multu	$12,$13		# mul_add_c2(a[0],b[1],c2,c3,c1);
	mflo	$24
	mfhi	$25
	slt	$2,$25,$0
	sll	$25,1
	 multu	$14,$12		# mul_add_c2(a[2],b[0],c3,c1,c2);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$7,$25,$1
	sw	$3,4($4)

	mflo	$24
	mfhi	$25
	slt	$3,$25,$0
	sll	$25,1
	multu	$13,$13		# mul_add_c(a[1],b[1],c3,c1,c2);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	 multu	$12,$15		# mul_add_c2(a[0],b[3],c1,c2,c3);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,2*4($4)

	mflo	$24
	mfhi	$25
	slt	$7,$25,$0
	sll	$25,1
	multu	$13,$14		# mul_add_c2(a[1],b[2],c1,c2,c3);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$7,$1
	 multu	$8,$12		# mul_add_c2(a[4],b[0],c2,c3,c1);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,3*4($4)

	mflo	$24
	mfhi	$25
	slt	$2,$25,$0
	sll	$25,1
	multu	$15,$13		# mul_add_c2(a[3],b[1],c2,c3,c1);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$2,$1
	multu	$14,$14		# mul_add_c(a[2],b[2],c2,c3,c1);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	 multu	$12,$9		# mul_add_c2(a[0],b[5],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,4*4($4)

	mflo	$24
	mfhi	$25
	slt	$3,$25,$0
	sll	$25,1
	multu	$13,$8		# mul_add_c2(a[1],b[4],c3,c1,c2);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$3,$1
	multu	$14,$15		# mul_add_c2(a[2],b[3],c3,c1,c2);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	 multu	$10,$12		# mul_add_c2(a[6],b[0],c1,c2,c3);
	addu	$3,$1
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,5*4($4)

	mflo	$24
	mfhi	$25
	slt	$7,$25,$0
	sll	$25,1
	multu	$9,$13		# mul_add_c2(a[5],b[1],c1,c2,c3);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$7,$1
	multu	$8,$14		# mul_add_c2(a[4],b[2],c1,c2,c3);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$7,$1
	multu	$15,$15		# mul_add_c(a[3],b[3],c1,c2,c3);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	 multu	$12,$11		# mul_add_c2(a[0],b[7],c2,c3,c1);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,6*4($4)

	mflo	$24
	mfhi	$25
	slt	$2,$25,$0
	sll	$25,1
	multu	$13,$10		# mul_add_c2(a[1],b[6],c2,c3,c1);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$2,$1
	multu	$14,$9		# mul_add_c2(a[2],b[5],c2,c3,c1);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$2,$1
	multu	$15,$8		# mul_add_c2(a[3],b[4],c2,c3,c1);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$2,$1
	 multu	$11,$13		# mul_add_c2(a[7],b[1],c3,c1,c2);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,7*4($4)

	mflo	$24
	mfhi	$25
	slt	$3,$25,$0
	sll	$25,1
	multu	$10,$14		# mul_add_c2(a[6],b[2],c3,c1,c2);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$3,$1
	multu	$9,$15		# mul_add_c2(a[5],b[3],c3,c1,c2);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$3,$1
	multu	$8,$8		# mul_add_c(a[4],b[4],c3,c1,c2);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	 multu	$14,$11		# mul_add_c2(a[2],b[7],c1,c2,c3);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,8*4($4)

	mflo	$24
	mfhi	$25
	slt	$7,$25,$0
	sll	$25,1
	multu	$15,$10		# mul_add_c2(a[3],b[6],c1,c2,c3);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$7,$1
	multu	$8,$9		# mul_add_c2(a[4],b[5],c1,c2,c3);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$7,$1
	 multu	$11,$15		# mul_add_c2(a[7],b[3],c2,c3,c1);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,9*4($4)

	mflo	$24
	mfhi	$25
	slt	$2,$25,$0
	sll	$25,1
	multu	$10,$8		# mul_add_c2(a[6],b[4],c2,c3,c1);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$2,$1
	multu	$9,$9		# mul_add_c(a[5],b[5],c2,c3,c1);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	 multu	$8,$11		# mul_add_c2(a[4],b[7],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,10*4($4)

	mflo	$24
	mfhi	$25
	slt	$3,$25,$0
	sll	$25,1
	multu	$9,$10		# mul_add_c2(a[5],b[6],c3,c1,c2);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$3,$1
	 multu	$11,$9		# mul_add_c2(a[7],b[5],c1,c2,c3);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,11*4($4)

	mflo	$24
	mfhi	$25
	slt	$7,$25,$0
	sll	$25,1
	multu	$10,$10		# mul_add_c(a[6],b[6],c1,c2,c3);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	 multu	$10,$11		# mul_add_c2(a[6],b[7],c2,c3,c1);
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,12*4($4)

	mflo	$24
	mfhi	$25
	slt	$2,$25,$0
	sll	$25,1
	 multu	$11,$11		# mul_add_c(a[7],b[7],c3,c1,c2);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,13*4($4)

	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sw	$7,14*4($4)
	sw	$2,15*4($4)

	.set	noreorder
	jr	$31
	nop
.end	bn_sqr_comba8

.align	5
.globl	bn_sqr_comba4
.ent	bn_sqr_comba4
bn_sqr_comba4:
	.set	reorder
	lw	$12,0($5)
	lw	$13,4($5)
	multu	$12,$12		# mul_add_c(a[0],b[0],c1,c2,c3);
	lw	$14,2*4($5)
	lw	$15,3*4($5)
	mflo	$2
	mfhi	$3
	sw	$2,0($4)

	multu	$12,$13		# mul_add_c2(a[0],b[1],c2,c3,c1);
	mflo	$24
	mfhi	$25
	slt	$2,$25,$0
	sll	$25,1
	 multu	$14,$12		# mul_add_c2(a[2],b[0],c3,c1,c2);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$7,$25,$1
	sw	$3,4($4)

	mflo	$24
	mfhi	$25
	slt	$3,$25,$0
	sll	$25,1
	multu	$13,$13		# mul_add_c(a[1],b[1],c3,c1,c2);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	mflo	$24
	mfhi	$25
	addu	$7,$24
	sltu	$1,$7,$24
	 multu	$12,$15		# mul_add_c2(a[0],b[3],c1,c2,c3);
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,2*4($4)

	mflo	$24
	mfhi	$25
	slt	$7,$25,$0
	sll	$25,1
	multu	$13,$14		# mul_add_c(a2[1],b[2],c1,c2,c3);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	mflo	$24
	mfhi	$25
	slt	$1,$25,$0
	addu	$7,$1
	 multu	$15,$13		# mul_add_c2(a[3],b[1],c2,c3,c1);
	sll	$25,1
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sltu	$1,$3,$25
	addu	$7,$1
	sw	$2,3*4($4)

	mflo	$24
	mfhi	$25
	slt	$2,$25,$0
	sll	$25,1
	multu	$14,$14		# mul_add_c(a[2],b[2],c2,c3,c1);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$3,$24
	sltu	$1,$3,$24
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	mflo	$24
	mfhi	$25
	addu	$3,$24
	sltu	$1,$3,$24
	 multu	$14,$15		# mul_add_c2(a[2],b[3],c3,c1,c2);
	addu	$25,$1
	addu	$7,$25
	sltu	$1,$7,$25
	addu	$2,$1
	sw	$3,4*4($4)

	mflo	$24
	mfhi	$25
	slt	$3,$25,$0
	sll	$25,1
	 multu	$15,$15		# mul_add_c(a[3],b[3],c1,c2,c3);
	slt	$6,$24,$0
	addu	$25,$6
	sll	$24,1
	addu	$7,$24
	sltu	$1,$7,$24
	addu	$25,$1
	addu	$2,$25
	sltu	$1,$2,$25
	addu	$3,$1
	sw	$7,5*4($4)

	mflo	$24
	mfhi	$25
	addu	$2,$24
	sltu	$1,$2,$24
	addu	$25,$1
	addu	$3,$25
	sw	$2,6*4($4)
	sw	$3,7*4($4)

	.set	noreorder
	jr	$31
	nop
.end	bn_sqr_comba4
