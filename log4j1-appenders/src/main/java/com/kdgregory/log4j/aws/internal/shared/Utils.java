// Copyright (c) Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.log4j.aws.internal.shared;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

import com.kdgregory.aws.logging.common.LogMessage;

/**
 *  Utility classes for working with Log4J. These methods may get absorbed elsewhere.
 */
public class Utils
{
    /**
     *  Transforms a Log4J loggingEvent into a LogMessage.
     *
     *  @throws RuntimeException if any error occurred during formatting or conversion.
     *          Will include any root cause other than UnsupportedEncodingException.
     */
    public static LogMessage convertToLogMessage(LoggingEvent event, Layout layout)
    {
        try
        {
            StringWriter out = new StringWriter(1024);
            out.write(layout.format(event));
            if ((event.getThrowableInformation() != null) && layout.ignoresThrowable())
            {
                for (String traceline : event.getThrowableStrRep())
                {
                    out.write(traceline);
                    out.write(Layout.LINE_SEP);
                }
            }
            out.close();

            return new LogMessage(event.getTimeStamp(), out.toString());
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UnsupportedEncodingException when converting to UTF-8");
        }
        catch (Exception ex)
        {
            throw new RuntimeException("error creating LogMessage", ex);
        }
    }
}
