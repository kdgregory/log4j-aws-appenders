// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.kinesis;

import java.util.regex.Pattern;


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
    public final static Pattern ALLOWED_NAME_REGEX = Pattern.compile("[^A-Za-z0-9-_]");


    /**
     *  Minimum number of hours for retention period.
     */
    public final static int MINIMUM_RETENTION_PERIOD = 24;


    /**
     *  Maximum number of hours for retention period.
     */
    public final static int MAXIMUM_RETENTION_PERIOD = 168;

}
