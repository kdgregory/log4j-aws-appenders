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


/**
 *  This exception is thown by {@link CloudWatchFacade} for any situation that
 *  requires intervention by the caller (yes, it's a checked exception). Each
 *  instance has a reason code; where relevant, it may wrap an underlying cause.
 */
public class CloudWatchFacadeException
extends Exception
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
     *  Constructs an instance; cause may be null.
     */
    public CloudWatchFacadeException(String message, ReasonCode reasonCode, Throwable cause)
    {
        super(message, cause);
        this.reasonCode = reasonCode;
    }


    /**
     *  Returns a code that can be used by the application to dispatch exception handling.
     */
    public ReasonCode getReason()
    {
        return reasonCode;
    }


    /**
     *  Returns <code>true</code> if the caller should retry the operation, <code>false</code>
     *  if it should look more closely at the reason (or throw).
     */
    public boolean isRetryable()
    {
        switch (reasonCode)
        {
            case ABORTED:
            case THROTTLING:
            case INVALID_SEQUENCE_TOKEN:
                return true;
            default:
                return false;
        }
    }
}
