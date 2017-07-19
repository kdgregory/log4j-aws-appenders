// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.util.regex.Pattern;

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
     *  Allowed characters for log-stream and log-group names.
     */
    public final static Pattern ALLOWED_NAME_REGEX = Pattern.compile("[^A-Za-z0-9-_]");

}
