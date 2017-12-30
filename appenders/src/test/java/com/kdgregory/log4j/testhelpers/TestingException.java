// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers;


/**
 *  A test-specific exception class: we can assert on class and be confident that
 *  we're not getting some unexpected exception.
 */
public class TestingException
extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public TestingException(String message)
    {
        super(message);
    }
}
