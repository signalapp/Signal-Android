package org.bouncycastle.crypto.agreement;

import java.math.BigInteger;

import org.bouncycastle.math.ec.ECPoint;

import org.bouncycastle.crypto.BasicAgreement;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;

/**
 * P1363 7.2.1 ECSVDP-DH
 *
 * ECSVDP-DH is Elliptic Curve Secret Value Derivation Primitive,
 * Diffie-Hellman version. It is based on the work of [DH76], [Mil86],
 * and [Kob87]. This primitive derives a shared secret value from one
 * party's private key and another party's public key, where both have
 * the same set of EC domain parameters. If two parties correctly
 * execute this primitive, they will produce the same output. This
 * primitive can be invoked by a scheme to derive a shared secret key;
 * specifically, it may be used with the schemes ECKAS-DH1 and
 * DL/ECKAS-DH2. It assumes that the input keys are valid (see also
 * Section 7.2.2).
 */
public class ECDHBasicAgreement
    implements BasicAgreement
{
    private ECPrivateKeyParameters key;

    public void init(
        CipherParameters key)
    {
        this.key = (ECPrivateKeyParameters)key;
    }

    public BigInteger calculateAgreement(
        CipherParameters pubKey)
    {
        ECPublicKeyParameters pub = (ECPublicKeyParameters)pubKey;
        ECPoint P = pub.getQ().multiply(key.getD());

        // if (p.isInfinity()) throw new RuntimeException("d*Q == infinity");

        return P.getX().toBigInteger();
    }
}
