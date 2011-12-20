package org.bouncycastle.math.ec;

import java.math.BigInteger;
import java.util.Random;

public abstract class ECFieldElement
    implements ECConstants
{

    public abstract BigInteger     toBigInteger();
    public abstract String         getFieldName();
    public abstract int            getFieldSize();
    public abstract ECFieldElement add(ECFieldElement b);
    public abstract ECFieldElement subtract(ECFieldElement b);
    public abstract ECFieldElement multiply(ECFieldElement b);
    public abstract ECFieldElement divide(ECFieldElement b);
    public abstract ECFieldElement negate();
    public abstract ECFieldElement square();
    public abstract ECFieldElement invert();
    public abstract ECFieldElement sqrt();

    public String toString()
    {
        return this.toBigInteger().toString(2);
    }

    public static class Fp extends ECFieldElement
    {
        BigInteger x;

        BigInteger q;
        
        public Fp(BigInteger q, BigInteger x)
        {
            this.x = x;
            
            if (x.compareTo(q) >= 0)
            {
                throw new IllegalArgumentException("x value too large in field element");
            }

            this.q = q;
        }

        public BigInteger toBigInteger()
        {
            return x;
        }

        /**
         * return the field name for this field.
         *
         * @return the string "Fp".
         */
        public String getFieldName()
        {
            return "Fp";
        }

        public int getFieldSize()
        {
            return q.bitLength();
        }

        public BigInteger getQ()
        {
            return q;
        }
        
        public ECFieldElement add(ECFieldElement b)
        {
            return new Fp(q, x.add(b.toBigInteger()).mod(q));
        }

        public ECFieldElement subtract(ECFieldElement b)
        {
            return new Fp(q, x.subtract(b.toBigInteger()).mod(q));
        }

        public ECFieldElement multiply(ECFieldElement b)
        {
            return new Fp(q, x.multiply(b.toBigInteger()).mod(q));
        }

        public ECFieldElement divide(ECFieldElement b)
        {
            return new Fp(q, x.multiply(b.toBigInteger().modInverse(q)).mod(q));
        }

        public ECFieldElement negate()
        {
            return new Fp(q, x.negate().mod(q));
        }

        public ECFieldElement square()
        {
            return new Fp(q, x.multiply(x).mod(q));
        }

        public ECFieldElement invert()
        {
            return new Fp(q, x.modInverse(q));
        }

        // D.1.4 91
        /**
         * return a sqrt root - the routine verifies that the calculation
         * returns the right value - if none exists it returns null.
         */
        public ECFieldElement sqrt()
        {
            if (!q.testBit(0))
            {
                throw new RuntimeException("not done yet");
            }

            // p mod 4 == 3
            if (q.testBit(1))
            {
                // z = g^(u+1) + p, p = 4u + 3
                ECFieldElement z = new Fp(q, x.modPow(q.shiftRight(2).add(ONE), q));

                return z.square().equals(this) ? z : null;
            }

            // p mod 4 == 1
            BigInteger qMinusOne = q.subtract(ECConstants.ONE);

            BigInteger legendreExponent = qMinusOne.shiftRight(1);
            if (!(x.modPow(legendreExponent, q).equals(ECConstants.ONE)))
            {
                return null;
            }

            BigInteger u = qMinusOne.shiftRight(2);
            BigInteger k = u.shiftLeft(1).add(ECConstants.ONE);

            BigInteger Q = this.x;
            BigInteger fourQ = Q.shiftLeft(2).mod(q);

            BigInteger U, V;
            Random rand = new Random();
            do
            {
                BigInteger P;
                do
                {
                    P = new BigInteger(q.bitLength(), rand);
                }
                while (P.compareTo(q) >= 0
                    || !(P.multiply(P).subtract(fourQ).modPow(legendreExponent, q).equals(qMinusOne)));

                BigInteger[] result = lucasSequence(q, P, Q, k);
                U = result[0];
                V = result[1];

                if (V.multiply(V).mod(q).equals(fourQ))
                {
                    // Integer division by 2, mod q
                    if (V.testBit(0))
                    {
                        V = V.add(q);
                    }

                    V = V.shiftRight(1);

                    //assert V.multiply(V).mod(q).equals(x);

                    return new ECFieldElement.Fp(q, V);
                }
            }
            while (U.equals(ECConstants.ONE) || U.equals(qMinusOne));

            return null;

//            BigInteger qMinusOne = q.subtract(ECConstants.ONE);
//            BigInteger legendreExponent = qMinusOne.shiftRight(1); //divide(ECConstants.TWO);
//            if (!(x.modPow(legendreExponent, q).equals(ECConstants.ONE)))
//            {
//                return null;
//            }
//
//            Random rand = new Random();
//            BigInteger fourX = x.shiftLeft(2);
//
//            BigInteger r;
//            do
//            {
//                r = new BigInteger(q.bitLength(), rand);
//            }
//            while (r.compareTo(q) >= 0
//                || !(r.multiply(r).subtract(fourX).modPow(legendreExponent, q).equals(qMinusOne)));
//
//            BigInteger n1 = qMinusOne.shiftRight(2); //.divide(ECConstants.FOUR);
//            BigInteger n2 = n1.add(ECConstants.ONE); //q.add(ECConstants.THREE).divide(ECConstants.FOUR);
//
//            BigInteger wOne = WOne(r, x, q);
//            BigInteger wSum = W(n1, wOne, q).add(W(n2, wOne, q)).mod(q);
//            BigInteger twoR = r.shiftLeft(1); //ECConstants.TWO.multiply(r);
//
//            BigInteger root = twoR.modPow(q.subtract(ECConstants.TWO), q)
//                .multiply(x).mod(q)
//                .multiply(wSum).mod(q);
//
//            return new Fp(q, root);
        }

//        private static BigInteger W(BigInteger n, BigInteger wOne, BigInteger p)
//        {
//            if (n.equals(ECConstants.ONE))
//            {
//                return wOne;
//            }
//            boolean isEven = !n.testBit(0);
//            n = n.shiftRight(1);//divide(ECConstants.TWO);
//            if (isEven)
//            {
//                BigInteger w = W(n, wOne, p);
//                return w.multiply(w).subtract(ECConstants.TWO).mod(p);
//            }
//            BigInteger w1 = W(n.add(ECConstants.ONE), wOne, p);
//            BigInteger w2 = W(n, wOne, p);
//            return w1.multiply(w2).subtract(wOne).mod(p);
//        }
//
//        private BigInteger WOne(BigInteger r, BigInteger x, BigInteger p)
//        {
//            return r.multiply(r).multiply(x.modPow(q.subtract(ECConstants.TWO), q)).subtract(ECConstants.TWO).mod(p);
//        }

        private static BigInteger[] lucasSequence(
            BigInteger  p,
            BigInteger  P,
            BigInteger  Q,
            BigInteger  k)
        {
            int n = k.bitLength();
            int s = k.getLowestSetBit();

            BigInteger Uh = ECConstants.ONE;
            BigInteger Vl = ECConstants.TWO;
            BigInteger Vh = P;
            BigInteger Ql = ECConstants.ONE;
            BigInteger Qh = ECConstants.ONE;

            for (int j = n - 1; j >= s + 1; --j)
            {
                Ql = Ql.multiply(Qh).mod(p);

                if (k.testBit(j))
                {
                    Qh = Ql.multiply(Q).mod(p);
                    Uh = Uh.multiply(Vh).mod(p);
                    Vl = Vh.multiply(Vl).subtract(P.multiply(Ql)).mod(p);
                    Vh = Vh.multiply(Vh).subtract(Qh.shiftLeft(1)).mod(p);
                }
                else
                {
                    Qh = Ql;
                    Uh = Uh.multiply(Vl).subtract(Ql).mod(p);
                    Vh = Vh.multiply(Vl).subtract(P.multiply(Ql)).mod(p);
                    Vl = Vl.multiply(Vl).subtract(Ql.shiftLeft(1)).mod(p);
                }
            }

            Ql = Ql.multiply(Qh).mod(p);
            Qh = Ql.multiply(Q).mod(p);
            Uh = Uh.multiply(Vl).subtract(Ql).mod(p);
            Vl = Vh.multiply(Vl).subtract(P.multiply(Ql)).mod(p);
            Ql = Ql.multiply(Qh).mod(p);

            for (int j = 1; j <= s; ++j)
            {
                Uh = Uh.multiply(Vl).mod(p);
                Vl = Vl.multiply(Vl).subtract(Ql.shiftLeft(1)).mod(p);
                Ql = Ql.multiply(Ql).mod(p);
            }

            return new BigInteger[]{ Uh, Vl };
        }
        
        public boolean equals(Object other)
        {
            if (other == this)
            {
                return true;
            }

            if (!(other instanceof ECFieldElement.Fp))
            {
                return false;
            }
            
            ECFieldElement.Fp o = (ECFieldElement.Fp)other;
            return q.equals(o.q) && x.equals(o.x);
        }

        public int hashCode()
        {
            return q.hashCode() ^ x.hashCode();
        }
    }

//    /**
//     * Class representing the Elements of the finite field
//     * <code>F<sub>2<sup>m</sup></sub></code> in polynomial basis (PB)
//     * representation. Both trinomial (TPB) and pentanomial (PPB) polynomial
//     * basis representations are supported. Gaussian normal basis (GNB)
//     * representation is not supported.
//     */
//    public static class F2m extends ECFieldElement
//    {
//        BigInteger x;
//
//        /**
//         * Indicates gaussian normal basis representation (GNB). Number chosen
//         * according to X9.62. GNB is not implemented at present.
//         */
//        public static final int GNB = 1;
//
//        /**
//         * Indicates trinomial basis representation (TPB). Number chosen
//         * according to X9.62.
//         */
//        public static final int TPB = 2;
//
//        /**
//         * Indicates pentanomial basis representation (PPB). Number chosen
//         * according to X9.62.
//         */
//        public static final int PPB = 3;
//
//        /**
//         * TPB or PPB.
//         */
//        private int representation;
//
//        /**
//         * The exponent <code>m</code> of <code>F<sub>2<sup>m</sup></sub></code>.
//         */
//        private int m;
//
//        /**
//         * TPB: The integer <code>k</code> where <code>x<sup>m</sup> +
//         * x<sup>k</sup> + 1</code> represents the reduction polynomial
//         * <code>f(z)</code>.<br>
//         * PPB: The integer <code>k1</code> where <code>x<sup>m</sup> +
//         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
//         * represents the reduction polynomial <code>f(z)</code>.<br>
//         */
//        private int k1;
//
//        /**
//         * TPB: Always set to <code>0</code><br>
//         * PPB: The integer <code>k2</code> where <code>x<sup>m</sup> +
//         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
//         * represents the reduction polynomial <code>f(z)</code>.<br>
//         */
//        private int k2;
//
//        /**
//         * TPB: Always set to <code>0</code><br>
//         * PPB: The integer <code>k3</code> where <code>x<sup>m</sup> +
//         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
//         * represents the reduction polynomial <code>f(z)</code>.<br>
//         */
//        private int k3;
//        
//        /**
//         * Constructor for PPB.
//         * @param m  The exponent <code>m</code> of
//         * <code>F<sub>2<sup>m</sup></sub></code>.
//         * @param k1 The integer <code>k1</code> where <code>x<sup>m</sup> +
//         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
//         * represents the reduction polynomial <code>f(z)</code>.
//         * @param k2 The integer <code>k2</code> where <code>x<sup>m</sup> +
//         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
//         * represents the reduction polynomial <code>f(z)</code>.
//         * @param k3 The integer <code>k3</code> where <code>x<sup>m</sup> +
//         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
//         * represents the reduction polynomial <code>f(z)</code>.
//         * @param x The BigInteger representing the value of the field element.
//         */
//        public F2m(
//            int m, 
//            int k1, 
//            int k2, 
//            int k3,
//            BigInteger x)
//        {
////            super(x);
//            this.x = x;
//
//            if ((k2 == 0) && (k3 == 0))
//            {
//                this.representation = TPB;
//            }
//            else
//            {
//                if (k2 >= k3)
//                {
//                    throw new IllegalArgumentException(
//                            "k2 must be smaller than k3");
//                }
//                if (k2 <= 0)
//                {
//                    throw new IllegalArgumentException(
//                            "k2 must be larger than 0");
//                }
//                this.representation = PPB;
//            }
//
//            if (x.signum() < 0)
//            {
//                throw new IllegalArgumentException("x value cannot be negative");
//            }
//
//            this.m = m;
//            this.k1 = k1;
//            this.k2 = k2;
//            this.k3 = k3;
//        }
//
//        /**
//         * Constructor for TPB.
//         * @param m  The exponent <code>m</code> of
//         * <code>F<sub>2<sup>m</sup></sub></code>.
//         * @param k The integer <code>k</code> where <code>x<sup>m</sup> +
//         * x<sup>k</sup> + 1</code> represents the reduction
//         * polynomial <code>f(z)</code>.
//         * @param x The BigInteger representing the value of the field element.
//         */
//        public F2m(int m, int k, BigInteger x)
//        {
//            // Set k1 to k, and set k2 and k3 to 0
//            this(m, k, 0, 0, x);
//        }
//
//        public BigInteger toBigInteger()
//        {
//            return x;
//        }
//
//        public String getFieldName()
//        {
//            return "F2m";
//        }
//
//        public int getFieldSize()
//        {
//            return m;
//        }
//
//        /**
//         * Checks, if the ECFieldElements <code>a</code> and <code>b</code>
//         * are elements of the same field <code>F<sub>2<sup>m</sup></sub></code>
//         * (having the same representation).
//         * @param a field element.
//         * @param b field element to be compared.
//         * @throws IllegalArgumentException if <code>a</code> and <code>b</code>
//         * are not elements of the same field
//         * <code>F<sub>2<sup>m</sup></sub></code> (having the same
//         * representation). 
//         */
//        public static void checkFieldElements(
//            ECFieldElement a,
//            ECFieldElement b)
//        {
//            if ((!(a instanceof F2m)) || (!(b instanceof F2m)))
//            {
//                throw new IllegalArgumentException("Field elements are not "
//                        + "both instances of ECFieldElement.F2m");
//            }
//
//            if ((a.toBigInteger().signum() < 0) || (b.toBigInteger().signum() < 0))
//            {
//                throw new IllegalArgumentException(
//                        "x value may not be negative");
//            }
//
//            ECFieldElement.F2m aF2m = (ECFieldElement.F2m)a;
//            ECFieldElement.F2m bF2m = (ECFieldElement.F2m)b;
//
//            if ((aF2m.m != bF2m.m) || (aF2m.k1 != bF2m.k1)
//                    || (aF2m.k2 != bF2m.k2) || (aF2m.k3 != bF2m.k3))
//            {
//                throw new IllegalArgumentException("Field elements are not "
//                        + "elements of the same field F2m");
//            }
//
//            if (aF2m.representation != bF2m.representation)
//            {
//                // Should never occur
//                throw new IllegalArgumentException(
//                        "One of the field "
//                                + "elements are not elements has incorrect representation");
//            }
//        }
//
//        /**
//         * Computes <code>z * a(z) mod f(z)</code>, where <code>f(z)</code> is
//         * the reduction polynomial of <code>this</code>.
//         * @param a The polynomial <code>a(z)</code> to be multiplied by
//         * <code>z mod f(z)</code>.
//         * @return <code>z * a(z) mod f(z)</code>
//         */
//        private BigInteger multZModF(final BigInteger a)
//        {
//            // Left-shift of a(z)
//            BigInteger az = a.shiftLeft(1);
//            if (az.testBit(this.m)) 
//            {
//                // If the coefficient of z^m in a(z) equals 1, reduction
//                // modulo f(z) is performed: Add f(z) to to a(z):
//                // Step 1: Unset mth coeffient of a(z)
//                az = az.clearBit(this.m);
//
//                // Step 2: Add r(z) to a(z), where r(z) is defined as
//                // f(z) = z^m + r(z), and k1, k2, k3 are the positions of
//                // the non-zero coefficients in r(z)
//                az = az.flipBit(0);
//                az = az.flipBit(this.k1);
//                if (this.representation == PPB) 
//                {
//                    az = az.flipBit(this.k2);
//                    az = az.flipBit(this.k3);
//                }
//            }
//            return az;
//        }
//
//        public ECFieldElement add(final ECFieldElement b)
//        {
//            // No check performed here for performance reasons. Instead the
//            // elements involved are checked in ECPoint.F2m
//            // checkFieldElements(this, b);
//            if (b.toBigInteger().signum() == 0)
//            {
//                return this;
//            }
//
//            return new F2m(this.m, this.k1, this.k2, this.k3, this.x.xor(b.toBigInteger()));
//        }
//
//        public ECFieldElement subtract(final ECFieldElement b)
//        {
//            // Addition and subtraction are the same in F2m
//            return add(b);
//        }
//
//
//        public ECFieldElement multiply(final ECFieldElement b)
//        {
//            // Left-to-right shift-and-add field multiplication in F2m
//            // Input: Binary polynomials a(z) and b(z) of degree at most m-1
//            // Output: c(z) = a(z) * b(z) mod f(z)
//
//            // No check performed here for performance reasons. Instead the
//            // elements involved are checked in ECPoint.F2m
//            // checkFieldElements(this, b);
//            final BigInteger az = this.x;
//            BigInteger bz = b.toBigInteger();
//            BigInteger cz;
//
//            // Compute c(z) = a(z) * b(z) mod f(z)
//            if (az.testBit(0)) 
//            {
//                cz = bz;
//            } 
//            else 
//            {
//                cz = ECConstants.ZERO;
//            }
//
//            for (int i = 1; i < this.m; i++) 
//            {
//                // b(z) := z * b(z) mod f(z)
//                bz = multZModF(bz);
//
//                if (az.testBit(i)) 
//                {
//                    // If the coefficient of x^i in a(z) equals 1, b(z) is added
//                    // to c(z)
//                    cz = cz.xor(bz);
//                }
//            }
//            return new ECFieldElement.F2m(m, this.k1, this.k2, this.k3, cz);
//        }
//
//
//        public ECFieldElement divide(final ECFieldElement b)
//        {
//            // There may be more efficient implementations
//            ECFieldElement bInv = b.invert();
//            return multiply(bInv);
//        }
//
//        public ECFieldElement negate()
//        {
//            // -x == x holds for all x in F2m
//            return this;
//        }
//
//        public ECFieldElement square()
//        {
//            // Naive implementation, can probably be speeded up using modular
//            // reduction
//            return multiply(this);
//        }
//
//        public ECFieldElement invert()
//        {
//            // Inversion in F2m using the extended Euclidean algorithm
//            // Input: A nonzero polynomial a(z) of degree at most m-1
//            // Output: a(z)^(-1) mod f(z)
//
//            // u(z) := a(z)
//            BigInteger uz = this.x;
//            if (uz.signum() <= 0) 
//            {
//                throw new ArithmeticException("x is zero or negative, " +
//                        "inversion is impossible");
//            }
//
//            // v(z) := f(z)
//            BigInteger vz = ECConstants.ZERO.setBit(m);
//            vz = vz.setBit(0);
//            vz = vz.setBit(this.k1);
//            if (this.representation == PPB) 
//            {
//                vz = vz.setBit(this.k2);
//                vz = vz.setBit(this.k3);
//            }
//
//            // g1(z) := 1, g2(z) := 0
//            BigInteger g1z = ECConstants.ONE;
//            BigInteger g2z = ECConstants.ZERO;
//
//            // while u != 1
//            while (!(uz.equals(ECConstants.ZERO))) 
//            {
//                // j := deg(u(z)) - deg(v(z))
//                int j = uz.bitLength() - vz.bitLength();
//
//                // If j < 0 then: u(z) <-> v(z), g1(z) <-> g2(z), j := -j
//                if (j < 0) 
//                {
//                    final BigInteger uzCopy = uz;
//                    uz = vz;
//                    vz = uzCopy;
//
//                    final BigInteger g1zCopy = g1z;
//                    g1z = g2z;
//                    g2z = g1zCopy;
//
//                    j = -j;
//                }
//
//                // u(z) := u(z) + z^j * v(z)
//                // Note, that no reduction modulo f(z) is required, because
//                // deg(u(z) + z^j * v(z)) <= max(deg(u(z)), j + deg(v(z)))
//                // = max(deg(u(z)), deg(u(z)) - deg(v(z)) + deg(v(z))
//                // = deg(u(z))
//                uz = uz.xor(vz.shiftLeft(j));
//
//                // g1(z) := g1(z) + z^j * g2(z)
//                g1z = g1z.xor(g2z.shiftLeft(j));
////                if (g1z.bitLength() > this.m) {
////                    throw new ArithmeticException(
////                            "deg(g1z) >= m, g1z = " + g1z.toString(2));
////                }
//            }
//            return new ECFieldElement.F2m(
//                    this.m, this.k1, this.k2, this.k3, g2z);
//        }
//
//        public ECFieldElement sqrt()
//        {
//            throw new RuntimeException("Not implemented");
//        }
//
//        /**
//         * @return the representation of the field
//         * <code>F<sub>2<sup>m</sup></sub></code>, either of
//         * TPB (trinomial
//         * basis representation) or
//         * PPB (pentanomial
//         * basis representation).
//         */
//        public int getRepresentation()
//        {
//            return this.representation;
//        }
//
//        /**
//         * @return the degree <code>m</code> of the reduction polynomial
//         * <code>f(z)</code>.
//         */
//        public int getM()
//        {
//            return this.m;
//        }
//
//        /**
//         * @return TPB: The integer <code>k</code> where <code>x<sup>m</sup> +
//         * x<sup>k</sup> + 1</code> represents the reduction polynomial
//         * <code>f(z)</code>.<br>
//         * PPB: The integer <code>k1</code> where <code>x<sup>m</sup> +
//         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
//         * represents the reduction polynomial <code>f(z)</code>.<br>
//         */
//        public int getK1()
//        {
//            return this.k1;
//        }
//
//        /**
//         * @return TPB: Always returns <code>0</code><br>
//         * PPB: The integer <code>k2</code> where <code>x<sup>m</sup> +
//         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
//         * represents the reduction polynomial <code>f(z)</code>.<br>
//         */
//        public int getK2()
//        {
//            return this.k2;
//        }
//
//        /**
//         * @return TPB: Always set to <code>0</code><br>
//         * PPB: The integer <code>k3</code> where <code>x<sup>m</sup> +
//         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
//         * represents the reduction polynomial <code>f(z)</code>.<br>
//         */
//        public int getK3()
//        {
//            return this.k3;
//        }
//
//        public boolean equals(Object anObject)
//        {
//            if (anObject == this) 
//            {
//                return true;
//            }
//
//            if (!(anObject instanceof ECFieldElement.F2m)) 
//            {
//                return false;
//            }
//
//            ECFieldElement.F2m b = (ECFieldElement.F2m)anObject;
//            
//            return ((this.m == b.m) && (this.k1 == b.k1) && (this.k2 == b.k2)
//                && (this.k3 == b.k3)
//                && (this.representation == b.representation)
//                && (this.x.equals(b.x)));
//        }
//
//        public int hashCode()
//        {
//            return x.hashCode() ^ m ^ k1 ^ k2 ^ k3;
//        }
//    }

    /**
     * Class representing the Elements of the finite field
     * <code>F<sub>2<sup>m</sup></sub></code> in polynomial basis (PB)
     * representation. Both trinomial (TPB) and pentanomial (PPB) polynomial
     * basis representations are supported. Gaussian normal basis (GNB)
     * representation is not supported.
     */
    public static class F2m extends ECFieldElement
    {
        /**
         * Indicates gaussian normal basis representation (GNB). Number chosen
         * according to X9.62. GNB is not implemented at present.
         */
        public static final int GNB = 1;

        /**
         * Indicates trinomial basis representation (TPB). Number chosen
         * according to X9.62.
         */
        public static final int TPB = 2;

        /**
         * Indicates pentanomial basis representation (PPB). Number chosen
         * according to X9.62.
         */
        public static final int PPB = 3;

        /**
         * TPB or PPB.
         */
        private int representation;

        /**
         * The exponent <code>m</code> of <code>F<sub>2<sup>m</sup></sub></code>.
         */
        private int m;

        /**
         * TPB: The integer <code>k</code> where <code>x<sup>m</sup> +
         * x<sup>k</sup> + 1</code> represents the reduction polynomial
         * <code>f(z)</code>.<br>
         * PPB: The integer <code>k1</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.<br>
         */
        private int k1;

        /**
         * TPB: Always set to <code>0</code><br>
         * PPB: The integer <code>k2</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.<br>
         */
        private int k2;

        /**
         * TPB: Always set to <code>0</code><br>
         * PPB: The integer <code>k3</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.<br>
         */
        private int k3;

        /**
         * The <code>IntArray</code> holding the bits.
         */
        private IntArray x;

        /**
         * The number of <code>int</code>s required to hold <code>m</code> bits.
         */
        private int t;

        /**
         * Constructor for PPB.
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
         * @param x The BigInteger representing the value of the field element.
         */
        public F2m(
            int m, 
            int k1, 
            int k2, 
            int k3,
            BigInteger x)
        {
            // t = m / 32 rounded up to the next integer
            t = (m + 31) >> 5;
            this.x = new IntArray(x, t);

            if ((k2 == 0) && (k3 == 0))
            {
                this.representation = TPB;
            }
            else
            {
                if (k2 >= k3)
                {
                    throw new IllegalArgumentException(
                            "k2 must be smaller than k3");
                }
                if (k2 <= 0)
                {
                    throw new IllegalArgumentException(
                            "k2 must be larger than 0");
                }
                this.representation = PPB;
            }

            if (x.signum() < 0)
            {
                throw new IllegalArgumentException("x value cannot be negative");
            }

            this.m = m;
            this.k1 = k1;
            this.k2 = k2;
            this.k3 = k3;
        }

        /**
         * Constructor for TPB.
         * @param m  The exponent <code>m</code> of
         * <code>F<sub>2<sup>m</sup></sub></code>.
         * @param k The integer <code>k</code> where <code>x<sup>m</sup> +
         * x<sup>k</sup> + 1</code> represents the reduction
         * polynomial <code>f(z)</code>.
         * @param x The BigInteger representing the value of the field element.
         */
        public F2m(int m, int k, BigInteger x)
        {
            // Set k1 to k, and set k2 and k3 to 0
            this(m, k, 0, 0, x);
        }

        private F2m(int m, int k1, int k2, int k3, IntArray x)
        {
            t = (m + 31) >> 5;
            this.x = x;
            this.m = m;
            this.k1 = k1;
            this.k2 = k2;
            this.k3 = k3;

            if ((k2 == 0) && (k3 == 0))
            {
                this.representation = TPB;
            }
            else
            {
                this.representation = PPB;
            }

        }

        public BigInteger toBigInteger()
        {
            return x.toBigInteger();
        }

        public String getFieldName()
        {
            return "F2m";
        }

        public int getFieldSize()
        {
            return m;
        }

        /**
         * Checks, if the ECFieldElements <code>a</code> and <code>b</code>
         * are elements of the same field <code>F<sub>2<sup>m</sup></sub></code>
         * (having the same representation).
         * @param a field element.
         * @param b field element to be compared.
         * @throws IllegalArgumentException if <code>a</code> and <code>b</code>
         * are not elements of the same field
         * <code>F<sub>2<sup>m</sup></sub></code> (having the same
         * representation). 
         */
        public static void checkFieldElements(
            ECFieldElement a,
            ECFieldElement b)
        {
            if ((!(a instanceof F2m)) || (!(b instanceof F2m)))
            {
                throw new IllegalArgumentException("Field elements are not "
                        + "both instances of ECFieldElement.F2m");
            }

            ECFieldElement.F2m aF2m = (ECFieldElement.F2m)a;
            ECFieldElement.F2m bF2m = (ECFieldElement.F2m)b;

            if ((aF2m.m != bF2m.m) || (aF2m.k1 != bF2m.k1)
                    || (aF2m.k2 != bF2m.k2) || (aF2m.k3 != bF2m.k3))
            {
                throw new IllegalArgumentException("Field elements are not "
                        + "elements of the same field F2m");
            }

            if (aF2m.representation != bF2m.representation)
            {
                // Should never occur
                throw new IllegalArgumentException(
                        "One of the field "
                                + "elements are not elements has incorrect representation");
            }
        }

        public ECFieldElement add(final ECFieldElement b)
        {
            // No check performed here for performance reasons. Instead the
            // elements involved are checked in ECPoint.F2m
            // checkFieldElements(this, b);
            IntArray iarrClone = (IntArray)this.x.clone();
            F2m bF2m = (F2m)b;
            iarrClone.addShifted(bF2m.x, 0);
            return new F2m(m, k1, k2, k3, iarrClone);
        }

        public ECFieldElement subtract(final ECFieldElement b)
        {
            // Addition and subtraction are the same in F2m
            return add(b);
        }

        public ECFieldElement multiply(final ECFieldElement b)
        {
            // Right-to-left comb multiplication in the IntArray
            // Input: Binary polynomials a(z) and b(z) of degree at most m-1
            // Output: c(z) = a(z) * b(z) mod f(z)

            // No check performed here for performance reasons. Instead the
            // elements involved are checked in ECPoint.F2m
            // checkFieldElements(this, b);
            F2m bF2m = (F2m)b;
            IntArray mult = x.multiply(bF2m.x, m);
            mult.reduce(m, new int[]{k1, k2, k3});
            return new F2m(m, k1, k2, k3, mult);
        }

        public ECFieldElement divide(final ECFieldElement b)
        {
            // There may be more efficient implementations
            ECFieldElement bInv = b.invert();
            return multiply(bInv);
        }

        public ECFieldElement negate()
        {
            // -x == x holds for all x in F2m
            return this;
        }

        public ECFieldElement square()
        {
            IntArray squared = x.square(m);
            squared.reduce(m, new int[]{k1, k2, k3});
            return new F2m(m, k1, k2, k3, squared);
        }


        public ECFieldElement invert()
        {
            // Inversion in F2m using the extended Euclidean algorithm
            // Input: A nonzero polynomial a(z) of degree at most m-1
            // Output: a(z)^(-1) mod f(z)

            // u(z) := a(z)
            IntArray uz = (IntArray)this.x.clone();

            // v(z) := f(z)
            IntArray vz = new IntArray(t);
            vz.setBit(m);
            vz.setBit(0);
            vz.setBit(this.k1);
            if (this.representation == PPB) 
            {
                vz.setBit(this.k2);
                vz.setBit(this.k3);
            }

            // g1(z) := 1, g2(z) := 0
            IntArray g1z = new IntArray(t);
            g1z.setBit(0);
            IntArray g2z = new IntArray(t);

            // while u != 0
            while (!uz.isZero())
//            while (uz.getUsedLength() > 0)
//            while (uz.bitLength() > 1)
            {
                // j := deg(u(z)) - deg(v(z))
                int j = uz.bitLength() - vz.bitLength();

                // If j < 0 then: u(z) <-> v(z), g1(z) <-> g2(z), j := -j
                if (j < 0) 
                {
                    final IntArray uzCopy = uz;
                    uz = vz;
                    vz = uzCopy;

                    final IntArray g1zCopy = g1z;
                    g1z = g2z;
                    g2z = g1zCopy;

                    j = -j;
                }

                // u(z) := u(z) + z^j * v(z)
                // Note, that no reduction modulo f(z) is required, because
                // deg(u(z) + z^j * v(z)) <= max(deg(u(z)), j + deg(v(z)))
                // = max(deg(u(z)), deg(u(z)) - deg(v(z)) + deg(v(z))
                // = deg(u(z))
                // uz = uz.xor(vz.shiftLeft(j));
                // jInt = n / 32
                int jInt = j >> 5;
                // jInt = n % 32
                int jBit = j & 0x1F;
                IntArray vzShift = vz.shiftLeft(jBit);
                uz.addShifted(vzShift, jInt);

                // g1(z) := g1(z) + z^j * g2(z)
//                g1z = g1z.xor(g2z.shiftLeft(j));
                IntArray g2zShift = g2z.shiftLeft(jBit);
                g1z.addShifted(g2zShift, jInt);
                
            }
            return new ECFieldElement.F2m(
                    this.m, this.k1, this.k2, this.k3, g2z);
        }

        public ECFieldElement sqrt()
        {
            throw new RuntimeException("Not implemented");
        }

        /**
         * @return the representation of the field
         * <code>F<sub>2<sup>m</sup></sub></code>, either of
         * TPB (trinomial
         * basis representation) or
         * PPB (pentanomial
         * basis representation).
         */
        public int getRepresentation()
        {
            return this.representation;
        }

        /**
         * @return the degree <code>m</code> of the reduction polynomial
         * <code>f(z)</code>.
         */
        public int getM()
        {
            return this.m;
        }

        /**
         * @return TPB: The integer <code>k</code> where <code>x<sup>m</sup> +
         * x<sup>k</sup> + 1</code> represents the reduction polynomial
         * <code>f(z)</code>.<br>
         * PPB: The integer <code>k1</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.<br>
         */
        public int getK1()
        {
            return this.k1;
        }

        /**
         * @return TPB: Always returns <code>0</code><br>
         * PPB: The integer <code>k2</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.<br>
         */
        public int getK2()
        {
            return this.k2;
        }

        /**
         * @return TPB: Always set to <code>0</code><br>
         * PPB: The integer <code>k3</code> where <code>x<sup>m</sup> +
         * x<sup>k3</sup> + x<sup>k2</sup> + x<sup>k1</sup> + 1</code>
         * represents the reduction polynomial <code>f(z)</code>.<br>
         */
        public int getK3()
        {
            return this.k3;
        }

        public boolean equals(Object anObject)
        {
            if (anObject == this) 
            {
                return true;
            }

            if (!(anObject instanceof ECFieldElement.F2m)) 
            {
                return false;
            }

            ECFieldElement.F2m b = (ECFieldElement.F2m)anObject;
            
            return ((this.m == b.m) && (this.k1 == b.k1) && (this.k2 == b.k2)
                && (this.k3 == b.k3)
                && (this.representation == b.representation)
                && (this.x.equals(b.x)));
        }

        public int hashCode()
        {
            return x.hashCode() ^ m ^ k1 ^ k2 ^ k3;
        }
    }
}
