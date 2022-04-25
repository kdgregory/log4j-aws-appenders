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

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;


public class TestProxyUrl
{
    @Test
    public void testBasicOperation() throws Exception
    {
        ProxyUrl p1 = new ProxyUrl("http://proxy.example.com:4000");
        assertTrue("config 1, isValid",                                 p1.isValid());
        assertEquals("config 1, protocol",      "http",                 p1.getScheme());
        assertEquals("config 1, hostname",      "proxy.example.com",    p1.getHostname());
        assertEquals("config 1, port",          4000,                   p1.getPort());
        assertEquals("config 1, username",      null,                   p1.getUsername());
        assertEquals("config 1, password",      null,                   p1.getPassword());

        ProxyUrl p2 = new ProxyUrl("https://proxy.example.com:4000");
        assertTrue("config 2, isValid",                                 p2.isValid());
        assertEquals("config 2, protocol",      "https",                p2.getScheme());
        assertEquals("config 2, hostname",      "proxy.example.com",    p2.getHostname());
        assertEquals("config 2, port",          4000,                   p2.getPort());
        assertEquals("config 2, username",      null,                   p2.getUsername());
        assertEquals("config 2, password",      null,                   p2.getPassword());

        ProxyUrl p3 = new ProxyUrl("https://myuser@proxy.example.com:4000");
        assertTrue("config 3, isValid",                                 p3.isValid());
        assertEquals("config 3, protocol",      "https",                p3.getScheme());
        assertEquals("config 3, hostname",      "proxy.example.com",    p3.getHostname());
        assertEquals("config 3, port",          4000,                   p3.getPort());
        assertEquals("config 3, username",      "myuser",               p3.getUsername());
        assertEquals("config 3, password",      null,                   p3.getPassword());

        ProxyUrl p4 = new ProxyUrl("https://myuser:mypassword@proxy.example.com:4000");
        assertTrue("config 4, isValid",                                 p4.isValid());
        assertEquals("config 4, protocol",      "https",                p4.getScheme());
        assertEquals("config 4, hostname",      "proxy.example.com",    p4.getHostname());
        assertEquals("config 4, port",          4000,                   p4.getPort());
        assertEquals("config 4, username",      "myuser",               p4.getUsername());
        assertEquals("config 4, password",      "mypassword",           p4.getPassword());
    }


    @Test
    public void testMixedCase() throws Exception
    {
        ProxyUrl pp = new ProxyUrl("hTTpS://Foo:Ba!r@Proxy.example.com:4000");
        assertTrue("isValid",                                           pp.isValid());
        assertEquals("protocol",      "https",                          pp.getScheme());
        assertEquals("hostname",      "proxy.example.com",              pp.getHostname());
        assertEquals("port",          4000,                             pp.getPort());
        assertEquals("username",      "Foo",                            pp.getUsername());
        assertEquals("password",      "Ba!r",                           pp.getPassword());
    }


    @Test
    public void testUrlUnparseable() throws Exception
    {
        assertFalse("passed null",       new ProxyUrl(null).isValid());
        assertFalse("passed empty",      new ProxyUrl("").isValid());
        assertFalse("passed spaces",     new ProxyUrl("   ").isValid());
        assertFalse("passed garbage",    new ProxyUrl("//Proxy.example.com:4000").isValid());
    }


    @Test
    @Ignore("must set environment variable to run this")
    public void testRetrieveFromEnvironment() throws Exception
    {
        // export COM_KDGREGORY_LOGGING_PROXY_URL=http://proxy.example.com:4000
        ProxyUrl pp = new ProxyUrl();
        assertTrue("config 1, isValid",                                 pp.isValid());
        assertEquals("config 1, protocol",      "http",                 pp.getScheme());
        assertEquals("config 1, hostname",      "proxy.example.com",    pp.getHostname());
        assertEquals("config 1, port",          4000,                   pp.getPort());
        assertEquals("config 1, username",      null,                   pp.getUsername());
        assertEquals("config 1, password",      null,                   pp.getPassword());
    }

}
