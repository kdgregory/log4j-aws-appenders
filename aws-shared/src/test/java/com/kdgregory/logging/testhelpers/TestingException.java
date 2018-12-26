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

package com.kdgregory.logging.testhelpers;


/**
 *  A test-specific exception class: we can assert on class and be confident that
 *  we're not getting some unexpected exception.
 */
public class TestingException
extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    public TestingException(String message)
    {
        super(message);
    }
}
