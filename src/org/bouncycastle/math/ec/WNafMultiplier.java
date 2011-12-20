package org.bouncycastle.math.ec;

import java.math.BigInteger;

/**
 * Class implementing the WNAF (Window Non-Adjacent Form) multiplication
 * algorithm.
 */
class WNafMultiplier implements ECMultiplier
{
    /**
     * Computes the Window NAF (non-adjacent Form) of an integer.
     * @param width The width <code>w</code> of the Window NAF. The width is
     * defined as the minimal number <code>w</code>, such that for any
     * <code>w</code> consecutive digits in the resulting representation, at
     * most one is non-zero.
     * @param k The integer of which the Window NAF is computed.
     * @return The Window NAF of the given width, such that the following holds:
     * <code>k = &sum;<sub>i=0</sub><sup>l-1</sup> k<sub>i</sub>2<sup>i</sup>
     * </code>, where the <code>k<sub>i</sub></code> denote the elements of the
     * returned <code>byte[]</code>.
     */
    public byte[] windowNaf(byte width, BigInteger k)
    {
        // The window NAF is at most 1 element longer than the binary
        // representation of the integer k. byte can be used instead of short or
        // int unless the window width is larger than 8. For larger width use
        // short or int. However, a width of more than 8 is not efficient for
        // m = log2(q) smaller than 2305 Bits. Note: Values for m larger than
        // 1000 Bits are currently not used in practice.
        byte[] wnaf = new byte[k.bitLength() + 1];

        // 2^width as short and BigInteger
        short pow2wB = (short)(1 << width);
        BigInteger pow2wBI = BigInteger.valueOf(pow2wB);

        int i = 0;

        // The actual length of the WNAF
        int length = 0;

        // while k >= 1
        while (k.signum() > 0)
        {
            // if k is odd
            if (k.testBit(0))
            {
                // k mod 2^width
                BigInteger remainder = k.mod(pow2wBI);

                // if remainder > 2^(width - 1) - 1
                if (remainder.testBit(width - 1))
                {
                    wnaf[i] = (byte)(remainder.intValue() - pow2wB);
                }
                else
                {
                    wnaf[i] = (byte)remainder.intValue();
                }
                // wnaf[i] is now in [-2^(width-1), 2^(width-1)-1]

                k = k.subtract(BigInteger.valueOf(wnaf[i]));
                length = i;
            }
            else
            {
                wnaf[i] = 0;
            }

            // k = k/2
            k = k.shiftRight(1);
            i++;
        }

        length++;

        // Reduce the WNAF array to its actual length
        byte[] wnafShort = new byte[length];
        System.arraycopy(wnaf, 0, wnafShort, 0, length);
        return wnafShort;
    }

    /**
     * Multiplies <code>this</code> by an integer <code>k</code> using the
     * Window NAF method.
     * @param k The integer by which <code>this</code> is multiplied.
     * @return A new <code>ECPoint</code> which equals <code>this</code>
     * multiplied by <code>k</code>.
     */
    public ECPoint multiply(ECPoint p, BigInteger k, PreCompInfo preCompInfo)
    {
        WNafPreCompInfo wnafPreCompInfo;

        if ((preCompInfo != null) && (preCompInfo instanceof WNafPreCompInfo))
        {
            wnafPreCompInfo = (WNafPreCompInfo)preCompInfo;
        }
        else
        {
            // Ignore empty PreCompInfo or PreCompInfo of incorrect type
            wnafPreCompInfo = new WNafPreCompInfo();
        }

        // floor(log2(k))
        int m = k.bitLength();

        // width of the Window NAF
        byte width;

        // Required length of precomputation array
        int reqPreCompLen;

        // Determine optimal width and corresponding length of precomputation
        // array based on literature values
        if (m < 13)
        {
            width = 2;
            reqPreCompLen = 1;
        }
        else
        {
            if (m < 41)
            {
                width = 3;
                reqPreCompLen = 2;
            }
            else
            {
                if (m < 121)
                {
                    width = 4;
                    reqPreCompLen = 4;
                }
                else
                {
                    if (m < 337)
                    {
                        width = 5;
                        reqPreCompLen = 8;
                    }
                    else
                    {
                        if (m < 897)
                        {
                            width = 6;
                            reqPreCompLen = 16;
                        }
                        else
                        {
                            if (m < 2305)
                            {
                                width = 7;
                                reqPreCompLen = 32;
                            }
                            else
                            {
                                width = 8;
                                reqPreCompLen = 127;
                            }
                        }
                    }
                }
            }
        }

        // The length of the precomputation array
        int preCompLen = 1;

        ECPoint[] preComp = wnafPreCompInfo.getPreComp();
        ECPoint twiceP = wnafPreCompInfo.getTwiceP();

        // Check if the precomputed ECPoints already exist
        if (preComp == null)
        {
            // Precomputation must be performed from scratch, create an empty
            // precomputation array of desired length
            preComp = new ECPoint[]{ p };
        }
        else
        {
            // Take the already precomputed ECPoints to start with
            preCompLen = preComp.length;
        }

        if (twiceP == null)
        {
            // Compute twice(p)
            twiceP = p.twice();
        }

        if (preCompLen < reqPreCompLen)
        {
            // Precomputation array must be made bigger, copy existing preComp
            // array into the larger new preComp array
            ECPoint[] oldPreComp = preComp;
            preComp = new ECPoint[reqPreCompLen];
            System.arraycopy(oldPreComp, 0, preComp, 0, preCompLen);

            for (int i = preCompLen; i < reqPreCompLen; i++)
            {
                // Compute the new ECPoints for the precomputation array.
                // The values 1, 3, 5, ..., 2^(width-1)-1 times p are
                // computed
                preComp[i] = twiceP.add(preComp[i - 1]);
            }            
        }

        // Compute the Window NAF of the desired width
        byte[] wnaf = windowNaf(width, k);
        int l = wnaf.length;

        // Apply the Window NAF to p using the precomputed ECPoint values.
        ECPoint q = p.getCurve().getInfinity();
        for (int i = l - 1; i >= 0; i--)
        {
            q = q.twice();

            if (wnaf[i] != 0)
            {
                if (wnaf[i] > 0)
                {
                    q = q.add(preComp[(wnaf[i] - 1)/2]);
                }
                else
                {
                    // wnaf[i] < 0
                    q = q.subtract(preComp[(-wnaf[i] - 1)/2]);
                }
            }
        }

        // Set PreCompInfo in ECPoint, such that it is available for next
        // multiplication.
        wnafPreCompInfo.setPreComp(preComp);
        wnafPreCompInfo.setTwiceP(twiceP);
        p.setPreCompInfo(wnafPreCompInfo);
        return q;
    }

}
