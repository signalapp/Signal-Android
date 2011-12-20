package org.bouncycastle.math.ec;

import java.math.BigInteger;

/**
 * Class representing an element of <code><b>Z</b>[&tau;]</code>. Let
 * <code>&lambda;</code> be an element of <code><b>Z</b>[&tau;]</code>. Then
 * <code>&lambda;</code> is given as <code>&lambda; = u + v&tau;</code>. The
 * components <code>u</code> and <code>v</code> may be used directly, there
 * are no accessor methods.
 * Immutable class.
 */
class ZTauElement
{
    /**
     * The &quot;real&quot; part of <code>&lambda;</code>.
     */
    public final BigInteger u;

    /**
     * The &quot;<code>&tau;</code>-adic&quot; part of <code>&lambda;</code>.
     */
    public final BigInteger v;

    /**
     * Constructor for an element <code>&lambda;</code> of
     * <code><b>Z</b>[&tau;]</code>.
     * @param u The &quot;real&quot; part of <code>&lambda;</code>.
     * @param v The &quot;<code>&tau;</code>-adic&quot; part of
     * <code>&lambda;</code>.
     */
    public ZTauElement(BigInteger u, BigInteger v)
    {
        this.u = u;
        this.v = v;
    }
}
