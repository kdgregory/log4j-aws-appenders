// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.util.regex.Pattern;

/**
 *  Holds limits and other constants for CloudWatch Logs.
 */
public class CloudWatchConstants
{

    /**
     *  Maximum number of messages in a single batch.
     */
    final static int AWS_MAX_BATCH_COUNT = 10000;


    /**
     *  Maximum number of bytes in a single batch.
     */
    final static int AWS_MAX_BATCH_BYTES = 1048576;


    /**
     *  Allowed characters for log-stream and log-group names.
     */
    final static Pattern ALLOWED_NAME_REGEX = Pattern.compile("[^A-Za-z0-9-_]");

}
