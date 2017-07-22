// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.testhelpers.log4j;

import org.apache.log4j.PatternLayout;

/**
 *  This class is used to test writing header and footer.
 */
public class HeaderFooterLayout
extends PatternLayout
{
    public final static String HEADER = "test header";
    public final static String FOOTER = "test footer";

    @Override
    public String getHeader()
    {
        return HEADER;
    }

    @Override
    public String getFooter()
    {
        return FOOTER;
    }
}