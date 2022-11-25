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
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;


/**
 *  Invokes a function, retrying with a delay until specified time, with optional
 *  exponential (2x) backoff. If the operation succeeds, its result is returned to
 *  the caller. On failure, it will return <code>null</code> or throw
 *  {@link TimeoutException} depending on configuration.
 *  <p>
 *  The function must be a Java8 <code>Supplier</code>, which returns a value. Use
 *  a lambda to wrap calls that don't return a value.
 *  <p>
 *  If the thread is interrupted (and the invoked operation is interruptible) then
 *  the invocation will return <code>null</code>.
 *  <p>
 *  Instances are reusable and thread-safe, so can be created once to define the
 *  "standard" retry logic for a given operation.
 */
public class RetryManager2
{
    private String operationName;
    private Duration initialDuration;
    private boolean isExponential;
    private boolean throwOnTimeout;

    // this is used by the variant of invoke that propagates exceptions
    // testing showed that simply passing a lambda would result in arbitrary timing
    private Consumer<RuntimeException> uncaughtHandler = new Consumer<RuntimeException>()
    {
        @Override
        public void accept(RuntimeException ex)
        {
            throw ex;
        }
    };


    /**
     *  Base constructor.
     *
     *  @param  operationName       The name of the operation, used when throwing for timeout.
     *  @param  initialDuration     The initial sleep duration, in milliseconds.
     *  @param  isExponential       If true, then the sleep duration is doubled for every attempt.
     *                              If false, the sleeps are of equal length.
     *  @param  throwOnTimeout      If true, then timeout causes an exception; if false, timeout
     *                              returns null.
     */
    public RetryManager2(String operationName, Duration initialDuration, boolean isExponential, boolean throwOnTimeout)
    {
        this.operationName = operationName;
        this.initialDuration = initialDuration;
        this.isExponential = isExponential;
        this.throwOnTimeout = throwOnTimeout;
    }


    /**
     *  Convenience constructor: uses exponential backoff and throws on timeout.
     *
     *  @param  operationName       The name of the operation, used when throwing for timeout.
     *  @param  initialDuration     The initial sleep duration, in milliseconds.
     */
    public RetryManager2(String operationName, Duration initialDuration)
    {
        this(operationName, initialDuration, true, true);
    }


    /**
     *  Invokes the passed function, passing any exceptions to the provided handler.
     */
    public <T> T invoke(Instant timeoutAt, Supplier<T> supplier, Consumer<RuntimeException> exceptionHandler)
    {
        long currentSleep = initialDuration.toMillis();
        long timeoutAtMillis = timeoutAt.toEpochMilli();
        while (System.currentTimeMillis() < timeoutAtMillis)
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

        if (throwOnTimeout)
        {
            throw new TimeoutException(operationName, timeoutAt, Instant.now());
        }
        else
        {
            return null;
        }
    }


    /**
     *  Invokes the passed function, propagating exceptions.
     */
    public <T> T invoke(Instant timeoutAt, Supplier<T> supplier)
    {
        return invoke(timeoutAt, supplier, uncaughtHandler);
    }


    /**
     *  Invokes the passed function, passing any exceptions to the provided handler.
     */
    public <T> T invoke(Duration timeout, Supplier<T> supplier, Consumer<RuntimeException> exceptionHandler)
    {
        return invoke(Instant.now().plus(timeout), supplier, exceptionHandler);
    }


    /**
     *  Invokes the passed function, propagating exceptions.
     */
    public <T> T invoke(Duration timeout, Supplier<T> supplier)
    {
        return invoke(Instant.now().plus(timeout), supplier, uncaughtHandler);
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
    
    
    /**
     *  This exception is thrown by the RetryManager on timeout.
     */
    public static class TimeoutException
    extends RuntimeException
    {
        private static final long serialVersionUID = 1L;
        
        private String operation;
        private Instant expectedTimeout;
        private Instant actualTimeout;
    
        public TimeoutException(String operation, Instant expectedTimeout, Instant actualTimeout)
        {
            this.operation = operation;
            this.expectedTimeout = expectedTimeout;
            this.actualTimeout = actualTimeout;
        }
    
        @Override
        public String getMessage()
        {
            return operation + " did not complete by " + expectedTimeout + " (now " + actualTimeout + ")";
        }
    
        
        /**
         *  Returns the name of the operation that timed out. May be null.
         */
        public String getOperation()
        {
            return operation;
        }
    
        
        /**
         *  Returns the instant provided to the RetryManager as a timeout limit.
         */
        public Instant getExpectedTimeout()
        {
            return expectedTimeout;
        }
    
        
        /**
         *  Returns the instant when the RetryManager decided to abort the operation.
         */
        public Instant getActualTimeout()
        {
            return actualTimeout;
        }
    }
}
