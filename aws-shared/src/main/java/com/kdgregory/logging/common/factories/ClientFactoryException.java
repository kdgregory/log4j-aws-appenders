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

package com.kdgregory.logging.common.factories;

import java.lang.reflect.InvocationTargetException;


/**
 *  This exception is thrown by ClientFactory.createClient(), because there wasn't
 *  a predefined exception that fit. This is a generic <code>RuntimeException</code>
 *  with one twist: it unpacks <code>InvocationTargetException</code>.
 */
public class ClientFactoryException extends RuntimeException
{
    private static final long serialVersionUID = 1L;


    public ClientFactoryException(String message)
    {
        super(message);
    }


    public ClientFactoryException(String message, Throwable cause)
    {
        super(message,
              cause instanceof InvocationTargetException
                   ? ((InvocationTargetException)cause).getCause()
                   : cause);
    }
}