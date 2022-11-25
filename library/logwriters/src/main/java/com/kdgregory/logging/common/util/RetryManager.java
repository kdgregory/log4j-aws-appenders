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

package com.kdgregory.logging.common.util;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;


/**
 *  Invokes a function, retrying with a delay, optionally with exponential backoff.
 *  Instances are reusable and thread-safe, so can be created once to define the
 *  "standard" retry logic for a given operation.
 *  <p>
 *  The function must be a Java8 <code>Supplier</code>, which returns a value. Use
 *  a lambda to wrap calls that don't return a value (to retry operations that may
 *  throw a non-fatal exception).
 *  
 *  @deprecated use {@linkRetryManager2}
 */
@Deprecated()
public class RetryManager
{
    private long initialDuration;
    private long timeout;
    private boolean isExponential;

    // this is used by the variant of invoke that propagates exceptions
    // testing showed that simply passing a Lambda would result in arbitrary timing
    private Consumer<RuntimeException> uncaughtHandler = new Consumer<RuntimeException>()
    {
        @Override
        public void accept(RuntimeException ex)
        {
            throw ex;
        }
    };


    /**
     *  Constructor for those who like to specify times in milliseconds.
     *
     *  @param  initialDuration     The initial time, in milliseconds, to wait between calls.
     *  @param  timeout             The maximum time, in milliseconds, to attempt calls. The
     *                              number of calls attempted will depend on initialDuration
     *                              and the wait strategy.
     *  @param  isExponential       If true, then the sleep duration is doubled for every attempt.
     *                              If false, the sleeps are of equal length.
     */
    public RetryManager(long initialDuration, long timeout, boolean isExponential)
    {
        this.initialDuration = initialDuration;
        this.timeout = timeout;
        this.isExponential = isExponential;
    }


    /**
     *  Constructor for those preferring Java8 durations.
     *
     *  @param  initialDuration     The initial time to wait between calls.
     *  @param  timeout             The maximum time to attempt calls. The number of
     *                              calls attempted will depend on initialDuration
     *                              and the wait strategy.
     *  @param  isExponential       If true, then the sleep duration is doubled for
     *                              every attempt.
     */
    public RetryManager(Duration initialDuration, Duration timeout, boolean isExponential)
    {
        this(initialDuration.toMillis(), timeout.toMillis(), isExponential);
    }


    /**
     *  Invokes the passed function. If it returns a non-null result, that result is
     *  returned to the caller. If it returns null, it is retried after sleep. After
     *  the timeout expires, this method returns null. All exceptions are propagated.
     *  <p>
     *  If the thread is interrupted, this method terminates and returns null.
     */
    public <T> T invoke(Supplier<T> supplier)
    {
        return invoke(supplier, uncaughtHandler);
    }


    /**
     *  Invokes the passed function. If it returns a non-null result, that result is
     *  returned to the caller. If it returns null, it is retried after sleep. After
     *  the timeout expires, this method returns null.
     *
     *  Exceptions are caught and passed to the handler, which can either return (to
     *  sleep and retry) or throw. Note that the handler only accepts subclasses of
     *  <code>RuntimeException</code>, since <code>Supplier</code> is not permitted
     *  to throw arbitrary exceptions.
     *  <p>
     *  If the thread is interrupted, this method terminates and returns null.
     */
    public <T> T invoke(Supplier<T> supplier, Consumer<RuntimeException> exceptionHandler)
    {
        long runUntil = System.currentTimeMillis() + timeout;
        long currentSleep = initialDuration;
        while (System.currentTimeMillis() < runUntil)
        {
            try
            {
                T result = supplier.get();
                if (result != null)
                    return result;
            }
            catch (RuntimeException ex)
            {
                exceptionHandler.accept(ex);
            }
            sleepQuietly(currentSleep);
            if (isExponential)
                currentSleep *= 2;
        }
        return null;
    }


    /**
     *  Sleeps for the specified duration (in milliseconds). Returns <code>true</code> if
     *  the sleep completes normally, <code>false</code> if the thread is interrupted.
     */
    public static boolean sleepQuietly(long duration)
    {
        try
        {
            Thread.sleep(duration);
            return true;
        }
        catch (InterruptedException ex)
        {
            return false;
        }
    }
}
