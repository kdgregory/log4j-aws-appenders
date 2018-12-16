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

/**
 *  Controls how messages are discarded once the threshold is reached.
 */
public enum DiscardAction
{
    /**
     *  Never discard; has potential to run out of memory.
     */
    none,

    /**
     *  Discard oldest messages once threshold is reached.
     */
    oldest,

    /**
     *  Discard newest messages once threshold is reached.
     */
    newest;


    public static DiscardAction lookup(String value)
    {
        if (value == null)
            return null;

        try
        {
            return DiscardAction.valueOf(value.toLowerCase());
        }
        catch (IllegalArgumentException ex)
        {
            return null;
        }
    }
}