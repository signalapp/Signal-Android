# Auto-generated - DO NOT EDIT!
# To regenerate, edit openssl.config, then run:
#     ./import_openssl.sh import /path/to/openssl-1.0.1h.tar.gz
#
# This script will append to the following variables:
#
#    LOCAL_CFLAGS
#    LOCAL_C_INCLUDES
#    LOCAL_SRC_FILES_$(TARGET_ARCH)
#    LOCAL_SRC_FILES_$(TARGET_2ND_ARCH)
#    LOCAL_CFLAGS_$(TARGET_ARCH)
#    LOCAL_CFLAGS_$(TARGET_2ND_ARCH)
#    LOCAL_ADDITIONAL_DEPENDENCIES


LOCAL_ADDITIONAL_DEPENDENCIES += $(LOCAL_PATH)/Crypto-config-trusty.mk

common_cflags := \
  -DGETPID_IS_MEANINGLESS \
  -DNO_WINDOWS_BRAINDEATH \

common_src_files := \
  Crypto-config.mk \
  crypto/aes/aes_cbc.c \
  crypto/aes/aes_misc.c \
  crypto/asn1/a_bitstr.c \
  crypto/asn1/a_d2i_fp.c \
  crypto/asn1/a_int.c \
  crypto/asn1/a_object.c \
  crypto/asn1/a_octet.c \
  crypto/asn1/a_type.c \
  crypto/asn1/ameth_lib.c \
  crypto/asn1/asn1_lib.c \
  crypto/asn1/asn_pack.c \
  crypto/asn1/d2i_pr.c \
  crypto/asn1/f_int.c \
  crypto/asn1/i2d_pr.c \
  crypto/asn1/p8_pkey.c \
  crypto/asn1/t_pkey.c \
  crypto/asn1/t_x509.c \
  crypto/asn1/tasn_dec.c \
  crypto/asn1/tasn_enc.c \
  crypto/asn1/tasn_fre.c \
  crypto/asn1/tasn_new.c \
  crypto/asn1/tasn_typ.c \
  crypto/asn1/tasn_utl.c \
  crypto/asn1/x_algor.c \
  crypto/asn1/x_attrib.c \
  crypto/asn1/x_bignum.c \
  crypto/asn1/x_long.c \
  crypto/asn1/x_pubkey.c \
  crypto/asn1/x_sig.c \
  crypto/bio/b_print.c \
  crypto/bio/bio_lib.c \
  crypto/bio/bss_mem.c \
  crypto/bn/bn_add.c \
  crypto/bn/bn_asm.c \
  crypto/bn/bn_blind.c \
  crypto/bn/bn_ctx.c \
  crypto/bn/bn_div.c \
  crypto/bn/bn_exp.c \
  crypto/bn/bn_exp2.c \
  crypto/bn/bn_gcd.c \
  crypto/bn/bn_gf2m.c \
  crypto/bn/bn_kron.c \
  crypto/bn/bn_lib.c \
  crypto/bn/bn_mod.c \
  crypto/bn/bn_mont.c \
  crypto/bn/bn_mul.c \
  crypto/bn/bn_nist.c \
  crypto/bn/bn_prime.c \
  crypto/bn/bn_print.c \
  crypto/bn/bn_rand.c \
  crypto/bn/bn_recp.c \
  crypto/bn/bn_shift.c \
  crypto/bn/bn_sqr.c \
  crypto/bn/bn_sqrt.c \
  crypto/bn/bn_word.c \
  crypto/buffer/buf_str.c \
  crypto/buffer/buffer.c \
  crypto/cmac/cm_ameth.c \
  crypto/cmac/cm_pmeth.c \
  crypto/cmac/cmac.c \
  crypto/cryptlib.c \
  crypto/dh/dh_ameth.c \
  crypto/dh/dh_asn1.c \
  crypto/dh/dh_check.c \
  crypto/dh/dh_gen.c \
  crypto/dh/dh_key.c \
  crypto/dh/dh_lib.c \
  crypto/dh/dh_pmeth.c \
  crypto/dsa/dsa_ameth.c \
  crypto/dsa/dsa_asn1.c \
  crypto/dsa/dsa_gen.c \
  crypto/dsa/dsa_key.c \
  crypto/dsa/dsa_lib.c \
  crypto/dsa/dsa_ossl.c \
  crypto/dsa/dsa_pmeth.c \
  crypto/dsa/dsa_sign.c \
  crypto/dsa/dsa_vrf.c \
  crypto/ec/ec2_mult.c \
  crypto/ec/ec2_oct.c \
  crypto/ec/ec2_smpl.c \
  crypto/ec/ec_ameth.c \
  crypto/ec/ec_asn1.c \
  crypto/ec/ec_curve.c \
  crypto/ec/ec_cvt.c \
  crypto/ec/ec_key.c \
  crypto/ec/ec_lib.c \
  crypto/ec/ec_mult.c \
  crypto/ec/ec_oct.c \
  crypto/ec/ec_pmeth.c \
  crypto/ec/ec_print.c \
  crypto/ec/eck_prn.c \
  crypto/ec/ecp_mont.c \
  crypto/ec/ecp_nist.c \
  crypto/ec/ecp_oct.c \
  crypto/ec/ecp_smpl.c \
  crypto/ecdh/ech_key.c \
  crypto/ecdh/ech_lib.c \
  crypto/ecdh/ech_ossl.c \
  crypto/ecdsa/ecs_asn1.c \
  crypto/ecdsa/ecs_lib.c \
  crypto/ecdsa/ecs_ossl.c \
  crypto/ecdsa/ecs_sign.c \
  crypto/ecdsa/ecs_vrf.c \
  crypto/engine/eng_init.c \
  crypto/engine/eng_lib.c \
  crypto/engine/eng_table.c \
  crypto/engine/tb_asnmth.c \
  crypto/engine/tb_cipher.c \
  crypto/engine/tb_dh.c \
  crypto/engine/tb_digest.c \
  crypto/engine/tb_dsa.c \
  crypto/engine/tb_ecdh.c \
  crypto/engine/tb_ecdsa.c \
  crypto/engine/tb_pkmeth.c \
  crypto/engine/tb_rand.c \
  crypto/engine/tb_rsa.c \
  crypto/err/err.c \
  crypto/evp/digest.c \
  crypto/evp/e_aes.c \
  crypto/evp/evp_enc.c \
  crypto/evp/evp_lib.c \
  crypto/evp/evp_pkey.c \
  crypto/evp/m_sha1.c \
  crypto/evp/m_sigver.c \
  crypto/evp/names.c \
  crypto/evp/p_lib.c \
  crypto/evp/pmeth_fn.c \
  crypto/evp/pmeth_gn.c \
  crypto/evp/pmeth_lib.c \
  crypto/ex_data.c \
  crypto/hmac/hm_ameth.c \
  crypto/hmac/hm_pmeth.c \
  crypto/hmac/hmac.c \
  crypto/lhash/lhash.c \
  crypto/mem.c \
  crypto/mem_clr.c \
  crypto/mem_dbg.c \
  crypto/modes/cbc128.c \
  crypto/modes/ctr128.c \
  crypto/objects/o_names.c \
  crypto/objects/obj_dat.c \
  crypto/objects/obj_xref.c \
  crypto/pkcs7/pk7_lib.c \
  crypto/rand/md_rand.c \
  crypto/rand/rand_lib.c \
  crypto/rsa/rsa_ameth.c \
  crypto/rsa/rsa_asn1.c \
  crypto/rsa/rsa_chk.c \
  crypto/rsa/rsa_crpt.c \
  crypto/rsa/rsa_eay.c \
  crypto/rsa/rsa_gen.c \
  crypto/rsa/rsa_lib.c \
  crypto/rsa/rsa_none.c \
  crypto/rsa/rsa_oaep.c \
  crypto/rsa/rsa_pk1.c \
  crypto/rsa/rsa_pmeth.c \
  crypto/rsa/rsa_pss.c \
  crypto/rsa/rsa_saos.c \
  crypto/rsa/rsa_sign.c \
  crypto/rsa/rsa_ssl.c \
  crypto/rsa/rsa_x931.c \
  crypto/sha/sha1_one.c \
  crypto/sha/sha1dgst.c \
  crypto/sha/sha256.c \
  crypto/sha/sha512.c \
  crypto/stack/stack.c \
  crypto/x509/x_all.c \
  crypto/x509v3/v3_utl.c \

common_c_includes := \
  external/openssl/. \
  external/openssl/crypto \
  external/openssl/crypto/asn1 \
  external/openssl/crypto/evp \
  external/openssl/crypto/modes \
  external/openssl/include \
  external/openssl/include/openssl \

arm_cflags := \
  -DAES_ASM \
  -DGHASH_ASM \
  -DOPENSSL_BN_ASM_GF2m \
  -DOPENSSL_BN_ASM_MONT \
  -DSHA1_ASM \
  -DSHA256_ASM \
  -DSHA512_ASM \

arm_src_files := \
  crypto/aes/asm/aes-armv4.S \
  crypto/bn/asm/armv4-gf2m.S \
  crypto/bn/asm/armv4-mont.S \
  crypto/sha/asm/sha1-armv4-large.S \
  crypto/sha/asm/sha256-armv4.S \
  crypto/sha/asm/sha512-armv4.S \

arm_exclude_files :=

arm64_cflags :=

arm64_src_files :=

arm64_exclude_files :=

x86_cflags :=

x86_src_files :=

x86_exclude_files :=

x86_64_cflags :=

x86_64_src_files :=

x86_64_exclude_files :=

mips_cflags :=

mips_src_files :=

mips_exclude_files :=


LOCAL_CFLAGS += $(common_cflags)
LOCAL_C_INCLUDES += $(common_c_includes)

LOCAL_SRC_FILES_arm += $(filter-out $(arm_exclude_files),$(common_src_files) $(arm_src_files))
LOCAL_CFLAGS_arm += $(arm_cflags)

LOCAL_SRC_FILES_arm64 += $(filter-out $(arm64_exclude_files),$(common_src_files) $(arm64_src_files))
LOCAL_CFLAGS_arm64 += $(arm64_cflags)

LOCAL_SRC_FILES_x86 += $(filter-out $(x86_exclude_files),$(common_src_files) $(x86_src_files))
LOCAL_CFLAGS_x86 += $(x86_cflags)

LOCAL_SRC_FILES_x86_64 += $(filter-out $(x86_64_exclude_files),$(common_src_files) $(x86_64_src_files))
LOCAL_CFLAGS_x86_64 += $(x86_64_cflags)

LOCAL_SRC_FILES_mips += $(filter-out $(mips_exclude_files),$(common_src_files) $(mips_src_files))
LOCAL_CFLAGS_mips += $(mips_cflags)
