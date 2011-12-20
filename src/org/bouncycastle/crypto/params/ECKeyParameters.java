package org.bouncycastle.crypto.params;

public class ECKeyParameters
    extends AsymmetricKeyParameter
{
    ECDomainParameters params;

    protected ECKeyParameters(
        boolean             isPrivate,
        ECDomainParameters  params)
    {
        super(isPrivate);

        this.params = params;
    }

    public ECDomainParameters getParameters()
    {
        return params;
    }
}
