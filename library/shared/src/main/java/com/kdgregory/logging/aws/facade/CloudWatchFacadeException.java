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

package com.kdgregory.logging.aws.facade;


/**
 *  This exception is thown by {@link CloudWatchFacade} for any situation that
 *  requires intervention by the caller. Each instance has a reason code, and
 *  an indication of whether the condition is retryable. Where relevant, it may
 *  wrap an underlying SDK-specific cause.
 */
public class CloudWatchFacadeException
extends FacadeException
{
    private static final long serialVersionUID = 1L;

    public enum ReasonCode
    {
        /**
         *  An exception that isn't expected to be corrected by the caller (just
         *  throw it on up).
         */
        UNEXPECTED_EXCEPTION,


        /**
         *  An invalid configuration value (these shouldn't happen if you validate
         *  your configuration!). The message will indicate the problem.
         */
        INVALID_CONFIGURATION,


        /**
         *  The log group was not found, in a call where it should have existed.
         *  Caller must create, then retry the failed call.
         */
        MISSING_LOG_GROUP,


        /**
         *  The log stream was not found, in a call where it should have existed.
         *  Caller must create, then retry the failed call.
         */
        MISSING_LOG_STREAM,


        /**
         *  The API call was aborted; according to the Interwebs, this is caused by
         *  thread interruption. Caller should retry.
         */
        ABORTED,


        /**
         *  The API call was throttled; caller should retry.
         */
        THROTTLING,


        /**
         *  Sequence token passed to PutLogEvents is invalid; retrieve another one and
         *  retry.
         */
        INVALID_SEQUENCE_TOKEN,


        /**
         *  CloudWatch already received these events. Unclear how this happens, although
         *  it seems to be tied to sequence number collisions. Can discard messages and
         *  go on with life.
         */
        ALREADY_PROCESSED
    }

//----------------------------------------------------------------------------
//  Implementation
//----------------------------------------------------------------------------

    private ReasonCode reasonCode;


    /**
     *  Base constructor.
     */
    public CloudWatchFacadeException(String message, Throwable cause, ReasonCode reasonCode, boolean isRetryable, String functionName, Object... args)
    {
        super(message, cause, isRetryable, functionName, args);
        this.reasonCode = reasonCode;
    }


    /**
     *  Convenience constructor, for conditions where there is no underlying exception,
     *  or where it's irrelevant.
     */
    public CloudWatchFacadeException(String message, ReasonCode reasonCode, boolean isRetryable, String functionName, Object... args)
    {
        this(message, null, reasonCode, isRetryable, functionName, args);
    }


    /**
     *  Convenience constructor for testing. Do not use in normal code!
     */
    public CloudWatchFacadeException(ReasonCode reasonCode, boolean isRetryable, Throwable cause)
    {
        this("use for testing only", cause, reasonCode, isRetryable, null);
    }

//----------------------------------------------------------------------------
//  Accessors
//----------------------------------------------------------------------------

    /**
     *  Returns a code that can be used by the application to dispatch exception handling.
     */
    public ReasonCode getReason()
    {
        return reasonCode;
    }
}
