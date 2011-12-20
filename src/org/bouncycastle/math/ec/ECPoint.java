package org.bouncycastle.math.ec;

import java.math.BigInteger;

import org.thoughtcrime.bouncycastle.asn1.x9.X9IntegerConverter;

/**
 * base class for points on elliptic curves.
 */
public abstract class ECPoint
{
    ECCurve        curve;
    ECFieldElement x;
    ECFieldElement y;

    protected boolean withCompression;

    protected ECMultiplier multiplier = null;

    protected PreCompInfo preCompInfo = null;

    private static X9IntegerConverter converter = new X9IntegerConverter();

    protected ECPoint(ECCurve curve, ECFieldElement x, ECFieldElement y)
    {
        this.curve = curve;
        this.x = x;
        this.y = y;
    }
    
    public ECCurve getCurve()
    {
        return curve;
    }
    
    public ECFieldElement getX()
    {
        return x;
    }

    public ECFieldElement getY()
    {
        return y;
    }

    public boolean isInfinity()
    {
        return x == null && y == null;
    }

    public boolean isCompressed()
    {
        return withCompression;
    }

    public boolean equals(
        Object  other)
    {
        if (other == this)
        {
            return true;
        }

        if (!(other instanceof ECPoint))
        {
            return false;
        }

        ECPoint o = (ECPoint)other;

        if (this.isInfinity())
        {
            return o.isInfinity();
        }

        return x.equals(o.x) && y.equals(o.y);
    }

    public int hashCode()
    {
        if (this.isInfinity())
        {
            return 0;
        }
        
        return x.hashCode() ^ y.hashCode();
    }

//    /**
//     * Mainly for testing. Explicitly set the <code>ECMultiplier</code>.
//     * @param multiplier The <code>ECMultiplier</code> to be used to multiply
//     * this <code>ECPoint</code>.
//     */
//    public void setECMultiplier(ECMultiplier multiplier)
//    {
//        this.multiplier = multiplier;
//    }

    /**
     * Sets the <code>PreCompInfo</code>. Used by <code>ECMultiplier</code>s
     * to save the precomputation for this <code>ECPoint</code> to store the
     * precomputation result for use by subsequent multiplication.
     * @param preCompInfo The values precomputed by the
     * <code>ECMultiplier</code>.
     */
    void setPreCompInfo(PreCompInfo preCompInfo)
    {
        this.preCompInfo = preCompInfo;
    }

    public abstract byte[] getEncoded();

    public abstract ECPoint add(ECPoint b);
    public abstract ECPoint subtract(ECPoint b);
    public abstract ECPoint negate();
    public abstract ECPoint twice();

    /**
     * Sets the default <code>ECMultiplier</code>, unless already set. 
     */
    synchronized void assertECMultiplier()
    {
        if (this.multiplier == null)
        {
            this.multiplier = new FpNafMultiplier();
        }
    }

    /**
     * Multiplies this <code>ECPoint</code> by the given number.
     * @param k The multiplicator.
     * @return <code>k * this</code>.
     */
    public ECPoint multiply(BigInteger k)
    {
        if (this.isInfinity())
        {
            return this;
        }

        if (k.signum() == 0)
        {
            return this.curve.getInfinity();
        }

        assertECMultiplier();
        return this.multiplier.multiply(this, k, preCompInfo);
    }

    /**
     * Elliptic curve points over Fp
     */
    public static class Fp extends ECPoint
    {
        
        /**
         * Create a point which encodes with point compression.
         * 
         * @param curve the curve to use
         * @param x affine x co-ordinate
         * @param y affine y co-ordinate
         */
        public Fp(ECCurve curve, ECFieldElement x, ECFieldElement y)
        {
            this(curve, x, y, false);
        }

        /**
         * Create a point that encodes with or without point compresion.
         * 
         * @param curve the curve to use
         * @param x affine x co-ordinate
         * @param y affine y co-ordinate
         * @param withCompression if true encode with point compression
         */
        public Fp(ECCurve curve, ECFieldElement x, ECFieldElement y, boolean withCompression)
        {
            super(curve, x, y);

            if ((x != null && y == null) || (x == null && y != null))
            {
                throw new IllegalArgumentException("Exactly one of the field elements is null");
            }

            this.withCompression = withCompression;
        }
         
        /**
         * return the field element encoded with point compression. (S 4.3.6)
         */
        public byte[] getEncoded()
        {
            if (this.isInfinity()) 
            {
                return new byte[1];
            }

            int qLength = converter.getByteLength(x);
            
            if (withCompression)
            {
                byte    PC;
    
                if (this.getY().toBigInteger().testBit(0))
                {
                    PC = 0x03;
                }
                else
                {
                    PC = 0x02;
                }
    
                byte[]  X = converter.integerToBytes(this.getX().toBigInteger(), qLength);
                byte[]  PO = new byte[X.length + 1];
    
                PO[0] = PC;
                System.arraycopy(X, 0, PO, 1, X.length);
    
                return PO;
            }
            else
            {
                byte[]  X = converter.integerToBytes(this.getX().toBigInteger(), qLength);
                byte[]  Y = converter.integerToBytes(this.getY().toBigInteger(), qLength);
                byte[]  PO = new byte[X.length + Y.length + 1];
                
                PO[0] = 0x04;
                System.arraycopy(X, 0, PO, 1, X.length);
                System.arraycopy(Y, 0, PO, X.length + 1, Y.length);

                return PO;
            }
        }

        // B.3 pg 62
        public ECPoint add(ECPoint b)
        {
            if (this.isInfinity())
            {
                return b;
            }

            if (b.isInfinity())
            {
                return this;
            }

            // Check if b = this or b = -this
            if (this.x.equals(b.x))
            {
                if (this.y.equals(b.y))
                {
                    // this = b, i.e. this must be doubled
                    return this.twice();
                }

                // this = -b, i.e. the result is the point at infinity
                return this.curve.getInfinity();
            }

            ECFieldElement gamma = b.y.subtract(this.y).divide(b.x.subtract(this.x));

            ECFieldElement x3 = gamma.square().subtract(this.x).subtract(b.x);
            ECFieldElement y3 = gamma.multiply(this.x.subtract(x3)).subtract(this.y);

            return new ECPoint.Fp(curve, x3, y3);
        }

        // B.3 pg 62
        public ECPoint twice()
        {
            if (this.isInfinity())
            {
                // Twice identity element (point at infinity) is identity
                return this;
            }

            if (this.y.toBigInteger().signum() == 0) 
            {
                // if y1 == 0, then (x1, y1) == (x1, -y1)
                // and hence this = -this and thus 2(x1, y1) == infinity
                return this.curve.getInfinity();
            }

            ECFieldElement TWO = this.curve.fromBigInteger(BigInteger.valueOf(2));
            ECFieldElement THREE = this.curve.fromBigInteger(BigInteger.valueOf(3));
            ECFieldElement gamma = this.x.square().multiply(THREE).add(curve.a).divide(y.multiply(TWO));

            ECFieldElement x3 = gamma.square().subtract(this.x.multiply(TWO));
            ECFieldElement y3 = gamma.multiply(this.x.subtract(x3)).subtract(this.y);
                
            return new ECPoint.Fp(curve, x3, y3, this.withCompression);
        }

        // D.3.2 pg 102 (see Note:)
        public ECPoint subtract(ECPoint b)
        {
            if (b.isInfinity())
            {
                return this;
            }

            // Add -b
            return add(b.negate());
        }

        public ECPoint negate()
        {
            return new ECPoint.Fp(curve, this.x, this.y.negate(), this.withCompression);
        }

        // TODO Uncomment this to enable WNAF algorithm for Fp point multiplication
//        /**
//         * Sets the default <code>ECMultiplier</code>, unless already set. 
//         */
//        synchronized void assertECMultiplier()
//        {
//            if (this.multiplier == null)
//            {
//                this.multiplier = new WNafMultiplier();
//            }
//        }
    }

    /**
     * Elliptic curve points over F2m
     */
    public static class F2m extends ECPoint
    {
        /**
         * @param curve base curve
         * @param x x point
         * @param y y point
         */
        public F2m(ECCurve curve, ECFieldElement x, ECFieldElement y)
        {
            this(curve, x, y, false);
        }
        
        /**
         * @param curve base curve
         * @param x x point
         * @param y y point
         * @param withCompression true if encode with point compression.
         */
        public F2m(ECCurve curve, ECFieldElement x, ECFieldElement y, boolean withCompression)
        {
            super(curve, x, y);

            if ((x != null && y == null) || (x == null && y != null))
            {
                throw new IllegalArgumentException("Exactly one of the field elements is null");
            }
            
            if (x != null)
            {
                // Check if x and y are elements of the same field
                ECFieldElement.F2m.checkFieldElements(this.x, this.y);
    
                // Check if x and a are elements of the same field
                if (curve != null)
                {
                    ECFieldElement.F2m.checkFieldElements(this.x, this.curve.getA());
                }
            }
            
            this.withCompression = withCompression;
        }

        /**
         * @deprecated use ECCurve.getInfinity()
         * Constructor for point at infinity
         */
        public F2m(ECCurve curve)
        {
            super(curve, null, null);
        }

        /* (non-Javadoc)
         * @see org.bouncycastle.math.ec.ECPoint#getEncoded()
         */
        public byte[] getEncoded()
        {
            if (this.isInfinity()) 
            {
                return new byte[1];
            }

            int byteCount = converter.getByteLength(this.x);
            byte[] X = converter.integerToBytes(this.getX().toBigInteger(), byteCount);
            byte[] PO;

            if (withCompression)
            {
                // See X9.62 4.3.6 and 4.2.2
                PO = new byte[byteCount + 1];

                PO[0] = 0x02;
                // X9.62 4.2.2 and 4.3.6:
                // if x = 0 then ypTilde := 0, else ypTilde is the rightmost
                // bit of y * x^(-1)
                // if ypTilde = 0, then PC := 02, else PC := 03
                // Note: PC === PO[0]
                if (!(this.getX().toBigInteger().equals(ECConstants.ZERO)))
                {
                    if (this.getY().multiply(this.getX().invert())
                            .toBigInteger().testBit(0))
                    {
                        // ypTilde = 1, hence PC = 03
                        PO[0] = 0x03;
                    }
                }

                System.arraycopy(X, 0, PO, 1, byteCount);
            }
            else
            {
                byte[] Y = converter.integerToBytes(this.getY().toBigInteger(), byteCount);
    
                PO = new byte[byteCount + byteCount + 1];
    
                PO[0] = 0x04;
                System.arraycopy(X, 0, PO, 1, byteCount);
                System.arraycopy(Y, 0, PO, byteCount + 1, byteCount);    
            }

            return PO;
        }

        /**
         * Check, if two <code>ECPoint</code>s can be added or subtracted.
         * @param a The first <code>ECPoint</code> to check.
         * @param b The second <code>ECPoint</code> to check.
         * @throws IllegalArgumentException if <code>a</code> and <code>b</code>
         * cannot be added.
         */
        private static void checkPoints(ECPoint a, ECPoint b)
        {
            // Check, if points are on the same curve
            if (!(a.curve.equals(b.curve)))
            {
                throw new IllegalArgumentException("Only points on the same "
                        + "curve can be added or subtracted");
            }

//            ECFieldElement.F2m.checkFieldElements(a.x, b.x);
        }

        /* (non-Javadoc)
         * @see org.bouncycastle.math.ec.ECPoint#add(org.bouncycastle.math.ec.ECPoint)
         */
        public ECPoint add(ECPoint b)
        {
            checkPoints(this, b);
            return addSimple((ECPoint.F2m)b);
        }

        /**
         * Adds another <code>ECPoints.F2m</code> to <code>this</code> without
         * checking if both points are on the same curve. Used by multiplication
         * algorithms, because there all points are a multiple of the same point
         * and hence the checks can be omitted.
         * @param b The other <code>ECPoints.F2m</code> to add to
         * <code>this</code>.
         * @return <code>this + b</code>
         */
        public ECPoint.F2m addSimple(ECPoint.F2m b)
        {
            ECPoint.F2m other = b;
            if (this.isInfinity())
            {
                return other;
            }

            if (other.isInfinity())
            {
                return this;
            }

            ECFieldElement.F2m x2 = (ECFieldElement.F2m)other.getX();
            ECFieldElement.F2m y2 = (ECFieldElement.F2m)other.getY();

            // Check if other = this or other = -this
            if (this.x.equals(x2))
            {
                if (this.y.equals(y2))
                {
                    // this = other, i.e. this must be doubled
                    return (ECPoint.F2m)this.twice();
                }

                // this = -other, i.e. the result is the point at infinity
                return (ECPoint.F2m)this.curve.getInfinity();
            }

            ECFieldElement.F2m lambda
                = (ECFieldElement.F2m)(this.y.add(y2)).divide(this.x.add(x2));

            ECFieldElement.F2m x3
                = (ECFieldElement.F2m)lambda.square().add(lambda).add(this.x).add(x2).add(this.curve.getA());

            ECFieldElement.F2m y3
                = (ECFieldElement.F2m)lambda.multiply(this.x.add(x3)).add(x3).add(this.y);

            return new ECPoint.F2m(curve, x3, y3, withCompression);
        }

        /* (non-Javadoc)
         * @see org.bouncycastle.math.ec.ECPoint#subtract(org.bouncycastle.math.ec.ECPoint)
         */
        public ECPoint subtract(ECPoint b)
        {
            checkPoints(this, b);
            return subtractSimple((ECPoint.F2m)b);
        }

        /**
         * Subtracts another <code>ECPoints.F2m</code> from <code>this</code>
         * without checking if both points are on the same curve. Used by
         * multiplication algorithms, because there all points are a multiple
         * of the same point and hence the checks can be omitted.
         * @param b The other <code>ECPoints.F2m</code> to subtract from
         * <code>this</code>.
         * @return <code>this - b</code>
         */
        public ECPoint.F2m subtractSimple(ECPoint.F2m b)
        {
            if (b.isInfinity())
            {
                return this;
            }

            // Add -b
            return addSimple((ECPoint.F2m)b.negate());
        }

        /* (non-Javadoc)
         * @see org.bouncycastle.math.ec.ECPoint#twice()
         */
        public ECPoint twice()
        {
            if (this.isInfinity()) 
            {
                // Twice identity element (point at infinity) is identity
                return this;
            }

            if (this.x.toBigInteger().signum() == 0) 
            {
                // if x1 == 0, then (x1, y1) == (x1, x1 + y1)
                // and hence this = -this and thus 2(x1, y1) == infinity
                return this.curve.getInfinity();
            }

            ECFieldElement.F2m lambda
                = (ECFieldElement.F2m)this.x.add(this.y.divide(this.x));

            ECFieldElement.F2m x3
                = (ECFieldElement.F2m)lambda.square().add(lambda).
                    add(this.curve.getA());

            ECFieldElement ONE = this.curve.fromBigInteger(ECConstants.ONE);
            ECFieldElement.F2m y3
                = (ECFieldElement.F2m)this.x.square().add(
                    x3.multiply(lambda.add(ONE)));

            return new ECPoint.F2m(this.curve, x3, y3, withCompression);
        }

        public ECPoint negate()
        {
            return new ECPoint.F2m(curve, this.getX(), this.getY().add(this.getX()), withCompression);
        }

        // TODO Uncomment this to enable WNAF/WTNAF F2m point multiplication
//        /**
//         * Sets the appropriate <code>ECMultiplier</code>, unless already set. 
//         */
//        synchronized void assertECMultiplier()
//        {
//            if (this.multiplier == null)
//            {
//                if (((ECCurve.F2m)(this.curve)).isKoblitz())
//                {
//                    this.multiplier = new WTauNafMultiplier();
//                }
//                else
//                {
//                    this.multiplier = new WNafMultiplier();
//                }
//            }
//        }
    }
}
