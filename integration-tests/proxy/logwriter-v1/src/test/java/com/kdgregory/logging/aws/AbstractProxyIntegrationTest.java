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

import java.lang.Thread.UncaughtExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.client.builder.AwsSyncClientBuilder;

import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.DefaultThreadFactory;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


/**
 *  Common functionality for the proxy integration tests. Edit this to reflect
 *  the actual proxy location before running tests.
 */
public class AbstractProxyIntegrationTest
{
    protected final static String PROXY_HOST          = "localhost";
    protected final static int    PROXY_PORT          = 3128;

    // this is for logging within the test
    protected Logger localLogger = LoggerFactory.getLogger(getClass());

    // this is to capture logging output from the writer
    protected TestableInternalLogger internalLogger = new TestableInternalLogger();

    // there's nothing test-specific about this
    protected DefaultThreadFactory threadFactory = new DefaultThreadFactory("test")
    {
        @Override
        protected Thread createThread(LogWriter logWriter, UncaughtExceptionHandler exceptionHandler)
        {
            Thread thread = super.createThread(logWriter, exceptionHandler);
            return thread;
        }
    };


    /**
     *  Configures a client-builder with proxy.
     */
    protected static <T extends AwsSyncClientBuilder<?,?>> T configureProxy(T clientBuilder)
    {
        ClientConfiguration clientConfig = new ClientConfiguration()
               .withProxyProtocol(Protocol.HTTP)
               .withProxyHost(PROXY_HOST)
               .withProxyPort(PROXY_PORT)
               .withNonProxyHosts("169.254.169.254");
        clientBuilder.setClientConfiguration(clientConfig);
        return clientBuilder;
    }

}
