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

package com.kdgregory.logging.aws;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.slf4j.MDC;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.LogWriterMessageWriter;
import com.kdgregory.logging.testhelpers.RoleTestHelper;


/**
 *  Tests the CloudWatch log-writer, as well as assumed roles, using a proxy
 *  for AWS connections.
 *
 *  This is intended to be run on an EC2 instance in a private subnet, with a
 *  proxy  server running in a public subnet.
 *  <p>
 *  You must set the COM_KDGREGORY_LOGGING_PROXY_URL  environment variable, and
 *  edit the static variables in {@link AbstractProxyIntegrationTest} to match.
 */
public class CloudWatchLogWriterProxyIntegrationTest
extends AbstractProxyIntegrationTest
{
    private final static String BASE_LOGGROUP_NAME  = "CloudWatchLogWriterV1ProxyIntegrationTest";

    // the "helper" clients are shared between tests
    private static AWSLogs helperClient;
    private static AmazonIdentityManagement iamClient;
    private static AWSSecurityTokenService stsClient;

    // these are created by init()
    CloudWatchTestHelper testHelper;
    CloudWatchWriterStatistics stats;
    CloudWatchWriterFactory writerFactory;
    private CloudWatchLogWriter writer;

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Called at the beginning of the CloudWatch tests. Creates helper objects
     *  and returns a default configuration.
     */
    private CloudWatchWriterConfig init(String testName, AWSLogs client)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new CloudWatchTestHelper(client, BASE_LOGGROUP_NAME, testName);
        testHelper.deleteLogGroupIfExists();

        stats = new CloudWatchWriterStatistics();
        writerFactory = new CloudWatchWriterFactory();

        return new CloudWatchWriterConfig()
               .setLogGroupName(testHelper.getLogGroupName())
               .setLogStreamName(testName)
               .setBatchDelay(250)
               .setDiscardThreshold(10000)
               .setDiscardAction(DiscardAction.oldest);
    }


    /**
     *  Creates the log writer and its execution thread, and starts it running.
     */
    private void createWriter(CloudWatchWriterConfig config)
    throws Exception
    {
        writer = (CloudWatchLogWriter)writerFactory.newLogWriter(config, stats, internalLogger);
        threadFactory.startWriterThread(writer, null);
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        helperClient = configureProxy(AWSLogsClientBuilder.standard()).build();
        iamClient = configureProxy(AmazonIdentityManagementClientBuilder.standard()).build();
        stsClient = configureProxy(AWSSecurityTokenServiceClientBuilder.standard()).build();
    }


    @Before
    public void setUp()
    {
        // tests will set themselves up via the init() functions
    }


    @After
    public void tearDown()
    {
        if (writer != null)
        {
            writer.stop();
        }

        localLogger.info("finished");
        MDC.clear();
    }


    @AfterClass
    public static void afterClass()
    {
        helperClient.shutdown();
        iamClient.shutdown();
        stsClient.shutdown();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testProxy() throws Exception
    {
        final int numMessages = 1001;

        CloudWatchWriterConfig config = init("testProxy", helperClient);
        createWriter(config);

        new LogWriterMessageWriter(writer, numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
        testHelper.assertMessages(config.getLogStreamName(), numMessages);

        testHelper.deleteLogGroupIfExists();
    }


    // as noted in the main integration test for assumed roles, there's no easy way to discover
    // that a role was assumed during the test; I assume that the assertions I make there are
    // correct, and that this test will actually assume the role -- or not, if the proxy isn't
    // in place
    @Test
    public void testProxyWithAssumedRole() throws Exception
    {
        final int numMessages = 1001;
        final String roleName = "V1ProxyIntegrationTest-" + System.currentTimeMillis();

        RoleTestHelper roleHelper = new RoleTestHelper(iamClient, stsClient);
        try {
            Role role = roleHelper.createRole(roleName, "arn:aws:iam::aws:policy/CloudWatchLogsFullAccess",
                                                        "arn:aws:iam::aws:policy/AmazonKinesisFullAccess",
                                                        "arn:aws:iam::aws:policy/AmazonSNSFullAccess");
            roleHelper.waitUntilRoleAssumable(role.getArn(), 60);

            CloudWatchWriterConfig config = init("testProxyWithAssumedRole", helperClient);
            config.setAssumedRole(role.getArn());
            createWriter(config);

            new LogWriterMessageWriter(writer, numMessages).run();

            CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
            testHelper.assertMessages(config.getLogStreamName(), numMessages);
        }
        finally {
            roleHelper.deleteRole(roleName);
            testHelper.deleteLogGroupIfExists();
        }
    }
}
