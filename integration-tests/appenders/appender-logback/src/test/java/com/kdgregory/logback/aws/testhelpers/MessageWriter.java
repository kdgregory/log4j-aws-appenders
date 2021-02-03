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

package com.kdgregory.logback.aws.testhelpers;

import org.slf4j.Logger;


/**
 *  Helper class for writing test messages.
 */
public class MessageWriter
extends com.kdgregory.logging.testhelpers.MessageWriter
{
    private Logger logger;

    public MessageWriter(Logger logger, int numMessages)
    {
        super(numMessages);
        this.logger = logger;
    }

    @Override
    protected void writeLogMessage(String message)
    {
        logger.debug(message);
    }
}