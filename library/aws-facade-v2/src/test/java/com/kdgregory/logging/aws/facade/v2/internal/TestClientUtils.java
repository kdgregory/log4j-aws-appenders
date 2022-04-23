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

package com.kdgregory.logging.aws.facade.v2.internal;

import org.junit.Test;
import static org.junit.Assert.*;

import software.amazon.awssdk.http.apache.ProxyConfiguration;


public class TestClientUtils
{
    @Test
    public void testParseProxyUrlBasicOperation() throws Exception
    {
        ProxyConfiguration c1 = ClientUtils.parseProxyUrl("http://proxy.example.com:4000");
        assertEquals("config 1, protocol",      "http",                 c1.scheme());
        assertEquals("config 1, hostname",      "proxy.example.com",    c1.host());
        assertEquals("config 1, port",          4000,                   c1.port());
        assertEquals("config 1, username",      null,                   c1.username());
        assertEquals("config 1, password",      null,                   c1.password());

        ProxyConfiguration c2 = ClientUtils.parseProxyUrl("https://proxy.example.com:4000");
        assertEquals("config 2, protocol",      "https",                c2.scheme());
        assertEquals("config 2, hostname",      "proxy.example.com",    c2.host());
        assertEquals("config 2, port",          4000,                   c2.port());
        assertEquals("config 2, username",      null,                   c2.username());
        assertEquals("config 2, password",      null,                   c2.password());
    }


    @Test
    public void testParseProxyUrlWithUserAndPassword() throws Exception
    {
        ProxyConfiguration c1 = ClientUtils.parseProxyUrl("http://foo@proxy.example.com:4000");
        assertEquals("config 1, protocol",      "http",                 c1.scheme());
        assertEquals("config 1, hostname",      "proxy.example.com",    c1.host());
        assertEquals("config 1, port",          4000,                   c1.port());
        assertEquals("config 1, username",      "foo",                  c1.username());
        assertEquals("config 1, password",      null,                   c1.password());

        ProxyConfiguration c2 = ClientUtils.parseProxyUrl("https://foo:bar@proxy.example.com:4000");
        assertEquals("config 2, protocol",      "https",                c2.scheme());
        assertEquals("config 2, hostname",      "proxy.example.com",    c2.host());
        assertEquals("config 2, port",          4000,                   c2.port());
        assertEquals("config 2, username",      "foo",                  c2.username());
        assertEquals("config 2, password",      "bar",                  c2.password());
    }


    @Test
    public void testParseProxyUrlMixedCase() throws Exception
    {
        ProxyConfiguration cfg = ClientUtils.parseProxyUrl("hTTpS://Foo:Ba!r@Proxy.example.com:4000");
        assertEquals("protocol",                "https",                cfg.scheme());
        assertEquals("hostname",                "proxy.example.com",    cfg.host());
        assertEquals("port",                    4000,                   cfg.port());
        assertEquals("username",                "Foo",                  cfg.username());
        assertEquals("password",                "Ba!r",                 cfg.password());
    }


    @Test
    public void testParseProxyUrlUnparseable() throws Exception
    {
        assertNull("passed null",       ClientUtils.parseProxyUrl(null));
        assertNull("passed blank",      ClientUtils.parseProxyUrl(""));
        assertNull("passed garbage",    ClientUtils.parseProxyUrl("//Proxy.example.com:4000"));
    }
}
