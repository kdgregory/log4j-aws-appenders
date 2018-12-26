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

package com.kdgregory.logback.aws.example;

import java.io.File;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.servlet.LogbackServletContextListener;


/**
 *  A context listener that allows configuration of Logback on startup, and
 *  properly shuts it down when the application is undeployed. This is based
 *  on <code>LogbackServletContextListener</code>, which implements shutdown
 *  logic.
 *  <p>
 *  Warning: if you're using Logback for access logging, it's controlled by
 *  the app-server, not the app; you should not use this servlet.
 *  <p>
 *  Note that we use the servlet context to perform all logging in this class:
 *  this avoids a chicken-and-egg problem when the application-level logging
 *  framework hasn't yet been configured (or has been destroyed).
 */
public class ExampleContextListener
extends LogbackServletContextListener
{

    @Override
    public void contextInitialized(ServletContextEvent event)
    {
        ServletContext context = event.getServletContext();

        // if web.xml specifies an alternate location for the Log4J config we'll
        // use it, otherwise we'll fall back to the in-WAR properties file

        String configLocation = context.getInitParameter("logback.config.location");
        if ((configLocation != null) && !configLocation.isEmpty() && new File(configLocation).exists())
        {
            context.log("configuring Logback from " + configLocation);
            try
            {
                LoggerContext loggerContext = (LoggerContext)LoggerFactory.getILoggerFactory();
                loggerContext.reset();
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext(loggerContext);
                configurator.doConfigure(configLocation);
            }
            catch (Exception ex)
            {
                context.log("failed to configure Logback", ex);
            }
        }
    }


    @Override
    public void contextDestroyed(ServletContextEvent event)
    {
        event.getServletContext().log("shutting down Logback");
        super.contextDestroyed(event);
    }

}
