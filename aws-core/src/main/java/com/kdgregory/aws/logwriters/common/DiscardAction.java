// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.aws.logwriters.common;

/**
 *  Controls how messages are discarded once the threshold is reached.
 */
public enum DiscardAction
{
    /**
     *  Never discard; has potential to run out of memory.
     */
    none,

    /**
     *  Discard oldest messages once threshold is reached.
     */
    oldest,

    /**
     *  Discard newest messages once threshold is reached.
     */
    newest;


    public static DiscardAction lookup(String value)
    {
        for (DiscardAction action : values())
        {
            if (action.toString().equals(value))
                return action;
        }
        throw new IllegalArgumentException("invalid discardAction: " + value);
    }
}