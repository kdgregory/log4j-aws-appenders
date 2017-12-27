// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.kinesis;


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

}
