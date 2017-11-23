// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.sns;



/**
 *  Holds limits and other constants for SNS.
 *  <p>
 *  See http://docs.aws.amazon.com/sns/latest/api/API_CreateTopic.html
 *  and http://docs.aws.amazon.com/sns/latest/api/API_Publish.html
 */

public class SNSConstants
{
    /**
     *  Maximum number of bytes in a for a single message.
     */
    public final static int MAX_MESSAGE_BYTES = 256 * 1024;

}
