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

package com.kdgregory.aws.logwriters.sns;


/**
 *  Holds limits and other constants for SNS.
 *  <p>
 *  See http://docs.aws.amazon.com/sns/latest/api/API_CreateTopic.html
 *  and http://docs.aws.amazon.com/sns/latest/api/API_Publish.html
 */

public class SNSConstants
{
    /**
     *  Validation regex for a topic name.
     */
    public final static String TOPIC_NAME_REGEX = "[A-Za-z0-9_-]{1,256}";


    /**
     *  Maximum number of bytes in a for a single message.
     */
    public final static int MAX_MESSAGE_BYTES = 256 * 1024;

}
