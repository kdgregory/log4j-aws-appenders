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

package com.kdgregory.log4j.aws;

import java.util.Date;

import com.kdgregory.log4j.aws.internal.shared.AbstractAppender;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchAppenderStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchAppenderStatisticsMXBean;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchConstants;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.aws.common.DefaultThreadFactory;
import com.kdgregory.logging.aws.common.LogMessage;
import com.kdgregory.logging.aws.common.Substitutions;


/**
 *  Appender that writes to a CloudWatch log stream.
 */
public class CloudWatchAppender
extends AbstractAppender<CloudWatchWriterConfig,CloudWatchAppenderStatistics,CloudWatchAppenderStatisticsMXBean>
{
    // these are the only configuration vars specific to this appender

    private String  logGroup;
    private String  logStream;

    // these variables hold the post-substitution log-group and log-stream names
    // (mostly useful for testing)

    private String  actualLogGroup;
    private String  actualLogStream;


    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public CloudWatchAppender()
    {
        super(new DefaultThreadFactory(),
              new CloudWatchWriterFactory(),
              new CloudWatchAppenderStatistics(),
              CloudWatchAppenderStatisticsMXBean.class);

        logStream = "{startupTimestamp}";
    }

//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    /**
     *  Sets the CloudWatch Log Group associated with this appender.
     *  <p>
     *  You typically assign a single log group to an application, and then
     *  use multiple log streams for instances of that application.
     *  <p>
     *  There is no default value. If you do not configure the log group, the
     *  appender will be disabled and will report its misconfiguration.
     */
    public void setLogGroup(String value)
    {
        logGroup = value;
    }


    /**
     *  Returns the log group name; see {@link #setLogGroup}. Primarily used
     *  for testing.
     */
    public String getLogGroup()
    {
        return logGroup;
    }


    /**
     *  Sets the CloudWatch Log Stream associated with this appender.
     *  <p>
     *  You typically create a separate log stream for each instance of the
     *  application.
     *  <p>
     *  Default value is <code>{startTimestamp}</code>, the JVM startup timestamp.
     */
    public void setLogStream(String value)
    {
        logStream = value;
    }


    /**
     *  Returns the log stream name; see {@link #setLogStream}. Primarily used
     *  for testing.
     */
    public String getLogStream()
    {
        return logStream;
    }

//----------------------------------------------------------------------------
//  Appender-specific methods
//----------------------------------------------------------------------------

    /**
     *  Rotates the log stream: flushes all outstanding messages to the current
     *  stream, and opens a new stream. This is called internally, and exposed
     *  for testing.
     */
    @Override
    public void rotate()
    {
        super.rotate();
    }

//----------------------------------------------------------------------------
//  AbstractAppender overrides
//----------------------------------------------------------------------------

    @Override
    protected CloudWatchWriterConfig generateWriterConfig()
    {
        Substitutions subs = new Substitutions(new Date(), sequence.get());
        actualLogGroup     = subs.perform(logGroup);
        actualLogStream    = subs.perform(logStream);

        return new CloudWatchWriterConfig(actualLogGroup, actualLogStream, batchDelay, discardThreshold, discardAction, clientFactory, clientEndpoint);
    }


    @Override
    protected boolean isMessageTooLarge(LogMessage message)
    {
        return (message.size() + CloudWatchConstants.MESSAGE_OVERHEAD)  >= CloudWatchConstants.MAX_BATCH_BYTES;
    }
}
