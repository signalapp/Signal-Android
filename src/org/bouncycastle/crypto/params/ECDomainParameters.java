package org.bouncycastle.crypto.params;

import java.math.BigInteger;

import org.bouncycastle.math.ec.ECConstants;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;

public class ECDomainParameters
    implements ECConstants
{
    ECCurve     curve;
    byte[]      seed;
    ECPoint     G;
    BigInteger  n;
    BigInteger  h;

    public ECDomainParameters(
        ECCurve     curve,
        ECPoint     G,
        BigInteger  n)
    {
        this.curve = curve;
        this.G = G;
        this.n = n;
        this.h = ONE;
        this.seed = null;
    }

    public ECDomainParameters(
        ECCurve     curve,
        ECPoint     G,
        BigInteger  n,
        BigInteger  h)
    {
        this.curve = curve;
        this.G = G;
        this.n = n;
        this.h = h;
        this.seed = null;
    }

    public ECDomainParameters(
        ECCurve     curve,
        ECPoint     G,
        BigInteger  n,
        BigInteger  h,
        byte[]      seed)
    {
        this.curve = curve;
        this.G = G;
        this.n = n;
        this.h = h;
        this.seed = seed;
    }

    public ECCurve getCurve()
    {
        return curve;
    }

    public ECPoint getG()
    {
        return G;
    }

    public BigInteger getN()
    {
        return n;
    }

    public BigInteger getH()
    {
        return h;
    }

    public byte[] getSeed()
    {
        return seed;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (!(o instanceof ECDomainParameters))
            return false;

        ECDomainParameters otherParams = (ECDomainParameters)o;

        //In the case where either seed is null, default to true
        boolean checkSeed = true;
        if (this.getSeed() != null && otherParams.getSeed() != null)
            checkSeed = this.getSeed().equals(otherParams.getSeed());

        return this.getCurve().equals(otherParams.getCurve()) &&
                this.getG().equals(otherParams.getG()) &&
                this.getN().equals(otherParams.getN()) &&
                this.getH().equals(otherParams.getH()) &&
                checkSeed;
    }
}
