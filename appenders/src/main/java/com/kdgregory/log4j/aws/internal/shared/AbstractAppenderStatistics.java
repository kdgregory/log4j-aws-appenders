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

package com.kdgregory.log4j.aws.internal.shared;


/**
 *  Base class for writer statistics, providing fields that are used by all
 *  writer implementations. Concrete appender implementations hold/expose a
 *  subclass.
 *  <p>
 *  Statistics are limited to primitives and strings so that they can be read
 *  by JMX. Statistics will be read and written by different threads, so must
 *  be marked as volatile. A given statistic will only be written by appender
 *  or writer, not both, so there's no need for synchronization unless there's
 *  the possibility of multiple writers. In that case, implement an override
 *  in the subclass and synchronize there.
 *  <p>
 *  Note: the MXBean interface implemented by subclasses must explicitly expose
 *  any desired getters (we can't use a superinterface because JMX introspection
 *  only looks at declared methods).
 */
public abstract class AbstractAppenderStatistics
{
    private volatile int messagesSent;


    public int getMessagesSent()
    {
        return messagesSent;
    }

    public synchronized void updateMessagesSent(int count)
    {
        messagesSent += count;
    }
}
