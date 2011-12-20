package org.bouncycastle.math.ec;

import java.math.BigInteger;

public class ECAlgorithms
{
    public static ECPoint sumOfTwoMultiplies(ECPoint P, BigInteger a,
        ECPoint Q, BigInteger b)
    {
        ECCurve c = P.getCurve();
        if (!c.equals(Q.getCurve()))
        {
            throw new IllegalArgumentException("P and Q must be on same curve");
        }

        // TODO Add special case back in when WTNAF is enabled
//        // Point multiplication for Koblitz curves (using WTNAF) beats Shamir's trick
//        if (c instanceof ECCurve.F2m)
//        {
//            ECCurve.F2m f2mCurve = (ECCurve.F2m) c;
//            if (f2mCurve.isKoblitz())
//            {
//                return P.multiply(a).add(Q.multiply(b));
//            }
//        }

        return implShamirsTrick(P, a, Q, b);
    }

    /*
     * "Shamir's Trick", originally due to E. G. Straus
     * (Addition chains of vectors. American Mathematical Monthly,
     * 71(7):806-808, Aug./Sept. 1964)
     * <pre>
     * Input: The points P, Q, scalar k = (km?, ... , k1, k0)
     * and scalar l = (lm?, ... , l1, l0).
     * Output: R = k * P + l * Q.
     * 1: Z <- P + Q
     * 2: R <- O
     * 3: for i from m-1 down to 0 do
     * 4:        R <- R + R        {point doubling}
     * 5:        if (ki = 1) and (li = 0) then R <- R + P end if
     * 6:        if (ki = 0) and (li = 1) then R <- R + Q end if
     * 7:        if (ki = 1) and (li = 1) then R <- R + Z end if
     * 8: end for
     * 9: return R
     * </pre>
     */
    public static ECPoint shamirsTrick(ECPoint P, BigInteger k,
        ECPoint Q, BigInteger l)
    {
        if (!P.getCurve().equals(Q.getCurve()))
        {
            throw new IllegalArgumentException("P and Q must be on same curve");
        }

        return implShamirsTrick(P, k, Q, l);
    }

    private static ECPoint implShamirsTrick(ECPoint P, BigInteger k,
        ECPoint Q, BigInteger l)
    {
        int m = Math.max(k.bitLength(), l.bitLength());
        ECPoint Z = P.add(Q);
        ECPoint R = P.getCurve().getInfinity();

        for (int i = m - 1; i >= 0; --i)
        {
            R = R.twice();

            if (k.testBit(i))
            {
                if (l.testBit(i))
                {
                    R = R.add(Z);
                }
                else
                {
                    R = R.add(P);
                }
            }
            else
            {
                if (l.testBit(i))
                {
                    R = R.add(Q);
                }
            }
        }

        return R;
    }
}
