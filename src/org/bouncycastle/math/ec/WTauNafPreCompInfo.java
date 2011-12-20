package org.bouncycastle.math.ec;

/**
 * Class holding precomputation data for the WTNAF (Window
 * <code>&tau;</code>-adic Non-Adjacent Form) algorithm.
 */
class WTauNafPreCompInfo implements PreCompInfo
{
    /**
     * Array holding the precomputed <code>ECPoint.F2m</code>s used for the
     * WTNAF multiplication in <code>
     * {@link org.bouncycastle.math.ec.multiplier.WTauNafMultiplier.multiply()
     * WTauNafMultiplier.multiply()}</code>.
     */
    private ECPoint.F2m[] preComp = null;

    /**
     * Constructor for <code>WTauNafPreCompInfo</code>
     * @param preComp Array holding the precomputed <code>ECPoint.F2m</code>s
     * used for the WTNAF multiplication in <code>
     * {@link org.bouncycastle.math.ec.multiplier.WTauNafMultiplier.multiply()
     * WTauNafMultiplier.multiply()}</code>.
     */
    WTauNafPreCompInfo(ECPoint.F2m[] preComp)
    {
        this.preComp = preComp;
    }

    /**
     * @return the array holding the precomputed <code>ECPoint.F2m</code>s
     * used for the WTNAF multiplication in <code>
     * {@link org.bouncycastle.math.ec.multiplier.WTauNafMultiplier.multiply()
     * WTauNafMultiplier.multiply()}</code>.
     */
    protected ECPoint.F2m[] getPreComp()
    {
        return preComp;
    }
}
