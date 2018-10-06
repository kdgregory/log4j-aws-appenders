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
     *  Maximum number of messages in a single batch.
     */
    public final static int MAX_BATCH_COUNT = 10000;


    /**
     *  Maximum number of bytes in a single batch. Note that this includes the
     *  message bytes as well as overhead.
     */
    public final static int MAX_BATCH_BYTES = 1048576;


    /**
     *  Overhead added to each message.
     */
    public final static int MESSAGE_OVERHEAD = 26;


    /**
     *  Used to validate log stream names.
     */
    public final static String ALLOWED_GROUP_NAME_REGEX = "[A-Za-z0-9_/.-]{1,512}";


    /**
     *  Used to validate log group names.
     */
    public final static String ALLOWED_STREAM_NAME_REGEX = "[^:*]{1,512}";

}
