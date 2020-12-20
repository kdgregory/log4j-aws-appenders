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

package com.kdgregory.logging.aws.kinesis;


/**
 *  Holds limits and other constants for Kinesis Streams.
 *  <p>
 *  See http://docs.aws.amazon.com/kinesis/latest/APIReference/API_CreateStream.html
 *  and http://docs.aws.amazon.com/kinesis/latest/APIReference/API_PutRecords.html
 */
public class KinesisConstants
{

    /**
     *  Maximum number of messages in a single batch.
     */
    public final static int MAX_BATCH_COUNT = 500;


    /**
     *  Maximum number of bytes in a batch. Note that this includes the overhead
     *  as well as the message bytes.
     */
    public final static int MAX_BATCH_BYTES = 5 * 1024 * 1024;


    /**
     *  Maximum number of bytes in a for a single message. Note that this does not
     *  include any overheads.
     */
    public final static int MAX_MESSAGE_BYTES = 1 * 1024 * 1024;


    /**
     *  Allowed characters for stream name.
     */
    public final static String ALLOWED_STREAM_NAME_REGEX = "[a-zA-Z0-9_.-]{1,128}";


    /**
     *  Allowed characters for partition key.
     */
    public final static String ALLOWED_PARITION_KEY_REGEX = "[a-zA-Z0-9_.-]{1,128}";


    /**
     *  Minimum number of hours for retention period.
     */
    public final static int MINIMUM_RETENTION_PERIOD = 24;


    /**
     *  Maximum number of hours for retention period.
     */
    public final static int MAXIMUM_RETENTION_PERIOD = 168;
    
    
    /**
     *  An SDK-independent enumeration of stream status codes as returned by
     *  <code>DescribeStream</code>, with the addition of a status indicating
     *  that the stream does not exist.
     */
    public enum StreamStatus
    {
        DOES_NOT_EXIST, CREATING, DELETING, ACTIVE, UPDATING
    }

}
