package org.bouncycastle.crypto.params;

import org.bouncycastle.math.ec.ECPoint;

public class ECPublicKeyParameters
    extends ECKeyParameters
{
    ECPoint Q;

    public ECPublicKeyParameters(
        ECPoint             Q,
        ECDomainParameters  params)
    {
        super(false, params);
        this.Q = Q;
    }

    public ECPoint getQ()
    {
        return Q;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (!(o instanceof ECPublicKeyParameters))
            return false;

        ECPublicKeyParameters otherParams = (ECPublicKeyParameters)o;
        return this.getParameters().equals(otherParams.getParameters());
    }
}
