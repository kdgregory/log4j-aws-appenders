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
import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.DefaultThreadFactory;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;


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
    protected static <T extends AwsClientBuilder<?,?>> T configureProxy(T clientBuilder)
    {
        ProxyConfiguration proxyConfig;
        try
        {
            proxyConfig = ProxyConfiguration.builder()
                                             .endpoint(new URI("http://" + PROXY_HOST + ":" + PROXY_PORT))
                                             .addNonProxyHost("169.254.169.254")
                                             .useSystemPropertyValues(Boolean.FALSE)
                                             .build();
            ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder().proxyConfiguration(proxyConfig);
            ((SdkSyncClientBuilder<?,?>)clientBuilder).httpClientBuilder(httpClientBuilder);
            return clientBuilder;
        }
        catch (URISyntaxException ex)
        {
            throw new RuntimeException("invalid proxy URL (indicates coding error)", ex);
        }
    }

}
