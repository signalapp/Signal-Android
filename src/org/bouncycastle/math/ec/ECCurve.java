package org.bouncycastle.math.ec;

import java.math.BigInteger;
import java.util.Random;

/**
 * base class for an elliptic curve
 */
public abstract class ECCurve
{
    ECFieldElement a, b;

    public abstract int getFieldSize();

    public abstract ECFieldElement fromBigInteger(BigInteger x);

    public abstract ECPoint createPoint(BigInteger x, BigInteger y, boolean withCompression);

    public abstract ECPoint decodePoint(byte[] encoded);

    public abstract ECPoint getInfinity();

    public ECFieldElement getA()
    {
        return a;
    }

    public ECFieldElement getB()
    {
        return b;
    }

    /**
     * Elliptic curve over Fp
     */
    public static class Fp extends ECCurve
    {
        BigInteger q;
        ECPoint.Fp infinity;

        public Fp(BigInteger q, BigInteger a, BigInteger b)
        {
            this.q = q;
            this.a = fromBigInteger(a);
            this.b = fromBigInteger(b);
            this.infinity = new ECPoint.Fp(this, null, null);
        }

        public BigInteger getQ()
        {
            return q;
        }

        public int getFieldSize()
        {
            return q.bitLength();
        }

        public ECFieldElement fromBigInteger(BigInteger x)
        {
            return new ECFieldElement.Fp(this.q, x);
        }

        public ECPoint createPoint(BigInteger x, BigInteger y, boolean withCompression)
        {
            return new ECPoint.Fp(this, fromBigInteger(x), fromBigInteger(y), withCompression);
        }

        /**
         * Decode a point on this curve from its ASN.1 encoding. The different
         * encodings are taken account of, including point compression for
         * <code>F<sub>p</sub></code> (X9.62 s 4.2.1 pg 17).
         * @return The decoded point.
         */
        public ECPoint decodePoint(byte[] encoded)
        {
            ECPoint p = null;

            switch (encoded[0])
            {
                // infinity
            case 0x00:
                p = getInfinity();
                break;
                // compressed
            case 0x02:
            case 0x03:
                int ytilde = encoded[0] & 1;
                byte[]  i = new byte[encoded.length - 1];

                System.arraycopy(encoded, 1, i, 0, i.length);

                ECFieldElement x = new ECFieldElement.Fp(this.q, new BigInteger(1, i));
                ECFieldElement alpha = x.multiply(x.square().add(a)).add(b);
                ECFieldElement beta = alpha.sqrt();

                //
                // if we can't find a sqrt we haven't got a point on the
                // curve - run!
                //
                if (beta == null)
                {
                    throw new RuntimeException("Invalid point compression");
                }

                int bit0 = (beta.toBigInteger().testBit(0) ? 1 : 0);

                if (bit0 == ytilde)
                {
                    p = new ECPoint.Fp(this, x, beta, true);
                }
                else
                {
                    p = new ECPoint.Fp(this, x,
                        new ECFieldElement.Fp(this.q, q.subtract(beta.toBigInteger())), true);
                }
                break;
                // uncompressed
            case 0x04:
                // hybrid
            case 0x06:
            case 0x07:
                byte[]  xEnc = new byte[(encoded.length - 1) / 2];
                byte[]  yEnc = new byte[(encoded.length - 1) / 2];

                System.arraycopy(encoded, 1, xEnc, 0, xEnc.length);
                System.arraycopy(encoded, xEnc.length + 1, yEnc, 0, yEnc.length);

                p = new ECPoint.Fp(this,
                        new ECFieldElement.Fp(this.q, new BigInteger(1, xEnc)),
                        new ECFieldElement.Fp(this.q, new BigInteger(1, yEnc)));
                break;
            default:
                throw new RuntimeException("Invalid point encoding 0x" + Integer.toString(encoded[0], 16));
            }

            return p;
        }

        public ECPoint getInfinity()
        {
            return infinity;
        }

        public boolean equals(
            Object anObject) 
        {
            if (anObject == this) 
            {
                return true;
            }

            if (!(anObject instanceof ECCurve.Fp)) 
            {
                return false;
            }

            ECCurve.Fp other = (ECCurve.Fp) anObject;

            return this.q.equals(other.q) 
                    && a.equals(other.a) && b.equals(other.b);
        }

        public int hashCode() 
        {
            return a.hashCode() ^ b.hashCode() ^ q.hashCode();
        }
    }

    /**
     * Elliptic curves over F2m. The Weierstrass equation is given by
     * <code>y<sup>2</sup> + xy = x<sup>3</sup> + ax<sup>2</sup> + b</code>.
     */
    public static class F2m extends ECCurve
    {
        /**
         * The exponent <code>m</code> of <code>F<sub>2<sup>m</sup></sub></code>.
         */
        private int m;  // can't be final - JDK 1.1

        /**
         * TPB: The integer <code>k</code> where <code>x<sup>m</sup> +
         * x<sup>k</sup> + 1</code> represents the reduction polynomial
         * <code>f(z)</code>.<br>
         * PPB: The integer <code>k1</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.<br>
         */
        private int k1;  // can't be final - JDK 1.1

        /**
         * TPB: Always set to <code>0</code><br>
         * PPB: The integer <code>k2</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.<br>
         */
        private int k2;  // can't be final - JDK 1.1

        /**
         * TPB: Always set to <code>0</code><br>
         * PPB: The integer <code>k3</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.<br>
         */
        private int k3;  // can't be final - JDK 1.1

        /**
         * The order of the base point of the curve.
         */
        private BigInteger n;  // can't be final - JDK 1.1

        /**
         * The cofactor of the curve.
         */
        private BigInteger h;  // can't be final - JDK 1.1
        
         /**
         * The point at infinity on this curve.
         */
        private ECPoint.F2m infinity;  // can't be final - JDK 1.1

        /**
         * The parameter <code>&mu;</code> of the elliptic curve if this is
         * a Koblitz curve.
         */
        private byte mu = 0;

        /**
         * The auxiliary values <code>s<sub>0</sub></code> and
         * <code>s<sub>1</sub></code> used for partial modular reduction for
         * Koblitz curves.
         */
        private BigInteger[] si = null;

        /**
         * Constructor for Trinomial Polynomial Basis (TPB).
         * @param m  The exponent <code>m</code> of
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param k The integer <code>k</code> where <code>x<sup>m</sup> +
         * x<sup>k</sup> + 1</code> represents the reduction
         * polynomial <code>f(z)</code>.
         * @param a The coefficient <code>a</code> in the Weierstrass equation
         * for non-supersingular elliptic curves over
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param b The coefficient <code>b</code> in the Weierstrass equation
         * for non-supersingular elliptic curves over
         * <code>F<sub>2<sup>m</sup></sub></code>.
         */
        public F2m(
            int m,
            int k,
            BigInteger a,
            BigInteger b)
        {
            this(m, k, 0, 0, a, b, null, null);
        }

        /**
         * Constructor for Trinomial Polynomial Basis (TPB).
         * @param m  The exponent <code>m</code> of
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param k The integer <code>k</code> where <code>x<sup>m</sup> +
         * x<sup>k</sup> + 1</code> represents the reduction
         * polynomial <code>f(z)</code>.
         * @param a The coefficient <code>a</code> in the Weierstrass equation
         * for non-supersingular elliptic curves over
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param b The coefficient <code>b</code> in the Weierstrass equation
         * for non-supersingular elliptic curves over
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param n The order of the main subgroup of the elliptic curve.
         * @param h The cofactor of the elliptic curve, i.e.
         * <code>#E<sub>a</sub>(F<sub>2<sup>m</sup></sub>) = h * n</code>.
         */
        public F2m(
            int m, 
            int k, 
            BigInteger a, 
            BigInteger b,
            BigInteger n,
            BigInteger h)
        {
            this(m, k, 0, 0, a, b, n, h);
        }

        /**
         * Constructor for Pentanomial Polynomial Basis (PPB).
         * @param m  The exponent <code>m</code> of
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param k1 The integer <code>k1</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.
         * @param k2 The integer <code>k2</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.
         * @param k3 The integer <code>k3</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.
         * @param a The coefficient <code>a</code> in the Weierstrass equation
         * for non-supersingular elliptic curves over
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param b The coefficient <code>b</code> in the Weierstrass equation
         * for non-supersingular elliptic curves over
         * <code>F<sub>2<sup>m</sup></sub></code>.
         */
        public F2m(
            int m,
            int k1,
            int k2,
            int k3,
            BigInteger a,
            BigInteger b)
        {
            this(m, k1, k2, k3, a, b, null, null);
        }

        /**
         * Constructor for Pentanomial Polynomial Basis (PPB).
         * @param m  The exponent <code>m</code> of
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param k1 The integer <code>k1</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.
         * @param k2 The integer <code>k2</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.
         * @param k3 The integer <code>k3</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.
         * @param a The coefficient <code>a</code> in the Weierstrass equation
         * for non-supersingular elliptic curves over
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param b The coefficient <code>b</code> in the Weierstrass equation
         * for non-supersingular elliptic curves over
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param n The order of the main subgroup of the elliptic curve.
         * @param h The cofactor of the elliptic curve, i.e.
         * <code>#E<sub>a</sub>(F<sub>2<sup>m</sup></sub>) = h * n</code>.
         */
        public F2m(
            int m, 
            int k1, 
            int k2, 
            int k3,
            BigInteger a, 
            BigInteger b,
            BigInteger n,
            BigInteger h)
        {
            this.m = m;
            this.k1 = k1;
            this.k2 = k2;
            this.k3 = k3;
            this.n = n;
            this.h = h;

            if (k1 == 0)
            {
                throw new IllegalArgumentException("k1 must be > 0");
            }

            if (k2 == 0)
            {
                if (k3 != 0)
                {
                    throw new IllegalArgumentException("k3 must be 0 if k2 == 0");
                }
            }
            else
            {
                if (k2 <= k1)
                {
                    throw new IllegalArgumentException("k2 must be > k1");
                }

                if (k3 <= k2)
                {
                    throw new IllegalArgumentException("k3 must be > k2");
                }
            }

            this.a = fromBigInteger(a);
            this.b = fromBigInteger(b);
            this.infinity = new ECPoint.F2m(this, null, null);
        }

        public int getFieldSize()
        {
            return m;
        }

        public ECFieldElement fromBigInteger(BigInteger x)
        {
            return new ECFieldElement.F2m(this.m, this.k1, this.k2, this.k3, x);
        }

        public ECPoint createPoint(BigInteger x, BigInteger y, boolean withCompression)
        {
            return new ECPoint.F2m(this, fromBigInteger(x), fromBigInteger(y), withCompression);
        }

        /* (non-Javadoc)
         * @see org.bouncycastle.math.ec.ECCurve#decodePoint(byte[])
         */
        public ECPoint decodePoint(byte[] encoded)
        {
            ECPoint p = null;

            switch (encoded[0])
            {
                // infinity
            case 0x00:
                p = getInfinity();
                break;
                // compressed
            case 0x02:
            case 0x03:
                byte[] enc = new byte[encoded.length - 1];
                System.arraycopy(encoded, 1, enc, 0, enc.length);
                if (encoded[0] == 0x02) 
                {
                        p = decompressPoint(enc, 0);
                }
                else 
                {
                        p = decompressPoint(enc, 1);
                }
                break;
                // uncompressed
            case 0x04:
                // hybrid
            case 0x06:
            case 0x07:
                byte[] xEnc = new byte[(encoded.length - 1) / 2];
                byte[] yEnc = new byte[(encoded.length - 1) / 2];

                System.arraycopy(encoded, 1, xEnc, 0, xEnc.length);
                System.arraycopy(encoded, xEnc.length + 1, yEnc, 0, yEnc.length);

                p = new ECPoint.F2m(this,
                    new ECFieldElement.F2m(this.m, this.k1, this.k2, this.k3,
                        new BigInteger(1, xEnc)),
                    new ECFieldElement.F2m(this.m, this.k1, this.k2, this.k3,
                        new BigInteger(1, yEnc)), false);
                break;

            default:
                throw new RuntimeException("Invalid point encoding 0x" + Integer.toString(encoded[0], 16));
            }

            return p;
        }

        public ECPoint getInfinity()
        {
            return infinity;
        }

        /**
         * Returns true if this is a Koblitz curve (ABC curve).
         * @return true if this is a Koblitz curve (ABC curve), false otherwise
         */
        public boolean isKoblitz()
        {
            return ((n != null) && (h != null) &&
                    ((a.toBigInteger().equals(ECConstants.ZERO)) ||
                    (a.toBigInteger().equals(ECConstants.ONE))) &&
                    (b.toBigInteger().equals(ECConstants.ONE)));
        }

        /**
         * Returns the parameter <code>&mu;</code> of the elliptic curve.
         * @return <code>&mu;</code> of the elliptic curve.
         * @throws IllegalArgumentException if the given ECCurve is not a
         * Koblitz curve.
         */
        synchronized byte getMu()
        {
            if (mu == 0)
            {
                mu = Tnaf.getMu(this);
            }
            return mu;
        }

        /**
         * @return the auxiliary values <code>s<sub>0</sub></code> and
         * <code>s<sub>1</sub></code> used for partial modular reduction for
         * Koblitz curves.
         */
        synchronized BigInteger[] getSi()
        {
            if (si == null)
            {
                si = Tnaf.getSi(this);
            }
            return si;
        }

        /**
         * Decompresses a compressed point P = (xp, yp) (X9.62 s 4.2.2).
         * 
         * @param xEnc
         *            The encoding of field element xp.
         * @param ypBit
         *            ~yp, an indication bit for the decompression of yp.
         * @return the decompressed point.
         */
        private ECPoint decompressPoint(
            byte[] xEnc, 
            int ypBit)
        {
            ECFieldElement xp = new ECFieldElement.F2m(
                    this.m, this.k1, this.k2, this.k3, new BigInteger(1, xEnc));
            ECFieldElement yp = null;
            if (xp.toBigInteger().equals(ECConstants.ZERO))
            {
                yp = (ECFieldElement.F2m)b;
                for (int i = 0; i < m - 1; i++)
                {
                    yp = yp.square();
                }
            }
            else
            {
                ECFieldElement beta = xp.add(a).add(
                        b.multiply(xp.square().invert()));
                ECFieldElement z = solveQuadradicEquation(beta);
                if (z == null)
                {
                    throw new RuntimeException("Invalid point compression");
                }
                int zBit = 0;
                if (z.toBigInteger().testBit(0))
                {
                    zBit = 1;
                }
                if (zBit != ypBit)
                {
                    z = z.add(new ECFieldElement.F2m(this.m, this.k1, this.k2,
                            this.k3, ECConstants.ONE));
                }
                yp = xp.multiply(z);
            }
            
            return new ECPoint.F2m(this, xp, yp);
        }
        
        /**
         * Solves a quadratic equation <code>z<sup>2</sup> + z = beta</code>(X9.62
         * D.1.6) The other solution is <code>z + 1</code>.
         * 
         * @param beta
         *            The value to solve the qradratic equation for.
         * @return the solution for <code>z<sup>2</sup> + z = beta</code> or
         *         <code>null</code> if no solution exists.
         */
        private ECFieldElement solveQuadradicEquation(ECFieldElement beta)
        {
            ECFieldElement zeroElement = new ECFieldElement.F2m(
                    this.m, this.k1, this.k2, this.k3, ECConstants.ZERO);

            if (beta.toBigInteger().equals(ECConstants.ZERO))
            {
                return zeroElement;
            }

            ECFieldElement z = null;
            ECFieldElement gamma = zeroElement;

            Random rand = new Random();
            do
            {
                ECFieldElement t = new ECFieldElement.F2m(this.m, this.k1,
                        this.k2, this.k3, new BigInteger(m, rand));
                z = zeroElement;
                ECFieldElement w = beta;
                for (int i = 1; i <= m - 1; i++)
                {
                    ECFieldElement w2 = w.square();
                    z = z.square().add(w2.multiply(t));
                    w = w2.add(beta);
                }
                if (!w.toBigInteger().equals(ECConstants.ZERO))
                {
                    return null;
                }
                gamma = z.square().add(z);
            }
            while (gamma.toBigInteger().equals(ECConstants.ZERO));

            return z;
        }
        
        public boolean equals(
            Object anObject)
        {
            if (anObject == this) 
            {
                return true;
            }

            if (!(anObject instanceof ECCurve.F2m)) 
            {
                return false;
            }

            ECCurve.F2m other = (ECCurve.F2m)anObject;
            
            return (this.m == other.m) && (this.k1 == other.k1)
                && (this.k2 == other.k2) && (this.k3 == other.k3)
                && a.equals(other.a) && b.equals(other.b);
        }

        public int hashCode()
        {
            return this.a.hashCode() ^ this.b.hashCode() ^ m ^ k1 ^ k2 ^ k3;
        }

        public int getM()
        {
            return m;
        }

        /**
         * Return true if curve uses a Trinomial basis.
         * 
         * @return true if curve Trinomial, false otherwise.
         */
        public boolean isTrinomial()
        {
            return k2 == 0 && k3 == 0;
        }
        
        public int getK1()
        {
            return k1;
        }

        public int getK2()
        {
            return k2;
        }

        public int getK3()
        {
            return k3;
        }

        public BigInteger getN()
        {
            return n;
        }

        public BigInteger getH()
        {
            return h;
        }
    }
}
