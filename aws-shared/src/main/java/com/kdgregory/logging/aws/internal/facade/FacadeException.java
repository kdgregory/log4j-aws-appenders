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

package com.kdgregory.logging.aws.internal.facade;

import java.util.Arrays;
import java.util.stream.Collectors;


/**
 *  A wrapper for any exceptions that happen inside a facade implementation.
 *  Provides information about where the exception occurred, and whether it's
 *  retryable.
 *  <p>
 *  Note that the "major" facades have their own subclasses of this exception,
 *  which provide additional information that can be used to control the caller's
 *  subsequent action.
 */
public class FacadeException
extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private String functionName;
    private boolean isRetryable;

    /**
     *  Base constructor.
     *
     *  @param  message         A base message explaining what happened. The actual
     *                          exception message consists of this, prefixed by the
     *                          function name and arguments.
     *  @param  cause           The underlying exception wrapped by this. May be null.
     *  @param  isRetryable     Indicates whether the caller can retry the operation.
     *  @param  functionName    The name of the function where this exception was thrown.
     *  @param  args            Optional information about the function state (usually
     *                          the arguments that were passed to the function).
     */
    public FacadeException(String message, Throwable cause, boolean isRetryable, String functionName, Object... args)
    {
        super(constructMessage(message, functionName, args), cause);
        this.isRetryable = isRetryable;
        this.functionName = functionName;
    }

//----------------------------------------------------------------------------
//  Accessors
//----------------------------------------------------------------------------

    /**
     *  Returns the function that throw this exception (included for completeness).
     */
    public String getFunctionName()
    {
        return functionName;
    }


    /**
     *  Returns an indication of whether the caller should retry the operation that
     *  threw this exception. Depending on the operation, callers may need to look
     *  at a subclass reason code.
     */
    public boolean isRetryable()
    {
        return isRetryable;
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private static String constructMessage(String message, String functionName, Object... args)
    {
        if ((functionName == null) || functionName.isEmpty())
            return message;

        String functionCall = functionName
                            + Arrays.asList(args).stream().map(String::valueOf)
                              .collect(Collectors.joining(",", "(", "): "));
        return functionCall + message;
    }
}
