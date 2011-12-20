package org.bouncycastle.crypto.params;

import org.bouncycastle.crypto.CipherParameters;

import java.security.SecureRandom;

public class ParametersWithRandom
    implements CipherParameters
{
    private SecureRandom        random;
    private CipherParameters    parameters;

    public ParametersWithRandom(
        CipherParameters    parameters,
        SecureRandom        random)
    {
        this.random = random;
        this.parameters = parameters;
    }

    public ParametersWithRandom(
        CipherParameters    parameters)
    {
        this(parameters, new SecureRandom());
    }

    public SecureRandom getRandom()
    {
        return random;
    }

    public CipherParameters getParameters()
    {
        return parameters;
    }
}
