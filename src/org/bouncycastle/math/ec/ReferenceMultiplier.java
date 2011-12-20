package org.bouncycastle.math.ec;

import java.math.BigInteger;

class ReferenceMultiplier implements ECMultiplier
{
    /**
     * Simple shift-and-add multiplication. Serves as reference implementation
     * to verify (possibly faster) implementations in
     * {@link org.bouncycastle.math.ec.ECPoint ECPoint}.
     * 
     * @param p The point to multiply.
     * @param k The factor by which to multiply.
     * @return The result of the point multiplication <code>k * p</code>.
     */
    public ECPoint multiply(ECPoint p, BigInteger k, PreCompInfo preCompInfo)
    {
        ECPoint q = p.getCurve().getInfinity();
        int t = k.bitLength();
        for (int i = 0; i < t; i++)
        {
            if (k.testBit(i))
            {
                q = q.add(p);
            }
            p = p.twice();
        }
        return q;
    }
}
