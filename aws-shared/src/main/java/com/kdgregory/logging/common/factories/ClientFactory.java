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

/**
 *  Used to create an AWS service client. {@link DefaultClientFactory} is the
 *  standard implementation, which uses a series of initialization steps. The
 *  mock-client tests uses an alternate implementation.
 */
public interface ClientFactory<ClientType>
{
    /**
     *  Creates a new instance of the desired client type.
     *
     *  @throws RuntimeException if any error happened. Any exceptions trapped
     *          internally are rethrown as <code>ClientFactoryException</code>.
     */
    public ClientType createClient();
}
