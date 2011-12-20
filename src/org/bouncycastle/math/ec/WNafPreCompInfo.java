package org.bouncycastle.math.ec;

/**
 * Class holding precomputation data for the WNAF (Window Non-Adjacent Form)
 * algorithm.
 */
class WNafPreCompInfo implements PreCompInfo
{
    /**
     * Array holding the precomputed <code>ECPoint</code>s used for the Window
     * NAF multiplication in <code>
     * {@link org.bouncycastle.math.ec.multiplier.WNafMultiplier.multiply()
     * WNafMultiplier.multiply()}</code>.
     */
    private ECPoint[] preComp = null;

    /**
     * Holds an <code>ECPoint</code> representing twice(this). Used for the
     * Window NAF multiplication in <code>
     * {@link org.bouncycastle.math.ec.multiplier.WNafMultiplier.multiply()
     * WNafMultiplier.multiply()}</code>.
     */
    private ECPoint twiceP = null;

    protected ECPoint[] getPreComp()
    {
        return preComp;
    }

    protected void setPreComp(ECPoint[] preComp)
    {
        this.preComp = preComp;
    }

    protected ECPoint getTwiceP()
    {
        return twiceP;
    }

    protected void setTwiceP(ECPoint twiceThis)
    {
        this.twiceP = twiceThis;
    }
}
