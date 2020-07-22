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

package com.kdgregory.logging.aws.cloudwatch;


/**
 *  Holds limits and other constants for CloudWatch Logs.
 *  <p>
 *  See http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html
 */
public class CloudWatchConstants
{
    /**
     *  The maximum number of bytes in a single message, after conversion to UTF-8.
     */
    public final static int MAX_MESSAGE_SIZE = 262118;


    /**
     *  Overhead added to each message in a batch.
     */
    public final static int MESSAGE_OVERHEAD = 26;


    /**
     *  Maximum number of messages in a single batch.
     */
    public final static int MAX_BATCH_COUNT = 10000;


    /**
     *  Maximum number of bytes in a single batch. Note that this includes the
     *  message bytes as well as overhead.
     */
    public final static int MAX_BATCH_BYTES = 1048576;


    /**
     *  Used to validate log stream names.
     */
    public final static String ALLOWED_GROUP_NAME_REGEX = "[A-Za-z0-9_/.-]{1,512}";


    /**
     *  Used to validate log group names.
     */
    public final static String ALLOWED_STREAM_NAME_REGEX = "[^:*]{1,512}";


    /**
     *  Validates proposed retention period, throwing if invalid.
     */
    public static Integer validateRetentionPeriod(Integer value)
    {
        // null means no retention period
        if (value == null)
            return value;

        // values per https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutRetentionPolicy.html
        // as-of 2019-05-04
        switch (value.intValue())
        {
            case 1 :
            case 3 :
            case 5 :
            case 7 :
            case 14 :
            case 30 :
            case 60 :
            case 90 :
            case 120 :
            case 150 :
            case 180 :
            case 365 :
            case 400 :
            case 545 :
            case 731 :
            case 1827 :
            case 3653 :
                return value;
            default :
                throw new IllegalArgumentException("invalid retention period: " + value + "; see AWS API for allowed values");
        }
    }
}
