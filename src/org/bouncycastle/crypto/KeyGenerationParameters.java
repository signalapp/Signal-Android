package org.bouncycastle.crypto;

import java.security.SecureRandom;

/**
 * The base class for parameters to key generators.
 */
public class KeyGenerationParameters
{
    private SecureRandom    random;
    private int             strength;

    /**
     * initialise the generator with a source of randomness
     * and a strength (in bits).
     *
     * @param random the random byte source.
     * @param strength the size, in bits, of the keys we want to produce.
     */
    public KeyGenerationParameters(
        SecureRandom    random,
        int             strength)
    {
        this.random = random;
        this.strength = strength;
    }

    /**
     * return the random source associated with this
     * generator.
     *
     * @return the generators random source.
     */
    public SecureRandom getRandom()
    {
        return random;
    }

    /**
     * return the bit strength for keys produced by this generator,
     *
     * @return the strength of the keys this generator produces (in bits).
     */
    public int getStrength()
    {
        return strength;
    }
}
