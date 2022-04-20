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

package com.kdgregory.logging.aws.facade.v1.internal;

import org.junit.Test;
import static org.junit.Assert.*;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;


public class TestClientUtils
{
    @Test
    public void testParseProxyUrlBasicOperation() throws Exception
    {
        ClientConfiguration c1 = ClientUtils.parseProxyUrl("http://proxy.example.com:4000");
        assertEquals("config 1, protocol",      Protocol.HTTP,          c1.getProxyProtocol());
        assertEquals("config 1, hostname",      "proxy.example.com",    c1.getProxyHost());
        assertEquals("config 1, port",          4000,                   c1.getProxyPort());
        assertEquals("config 1, username",      null,                   c1.getProxyUsername());
        assertEquals("config 1, password",      null,                   c1.getProxyPassword());

        ClientConfiguration c2 = ClientUtils.parseProxyUrl("https://proxy.example.com:4000");
        assertEquals("config 2, protocol",      Protocol.HTTPS,         c2.getProxyProtocol());
        assertEquals("config 2, hostname",      "proxy.example.com",    c2.getProxyHost());
        assertEquals("config 2, port",          4000,                   c2.getProxyPort());
        assertEquals("config 2, username",      null,                   c2.getProxyUsername());
        assertEquals("config 2, password",      null,                   c2.getProxyPassword());
    }


    @Test
    public void testParseProxyUrlWithUserAndPassword() throws Exception
    {
        ClientConfiguration c1 = ClientUtils.parseProxyUrl("http://foo@proxy.example.com:4000");
        assertEquals("config 1, protocol",      Protocol.HTTP,          c1.getProxyProtocol());
        assertEquals("config 1, hostname",      "proxy.example.com",    c1.getProxyHost());
        assertEquals("config 1, port",          4000,                   c1.getProxyPort());
        assertEquals("config 1, username",      "foo",                  c1.getProxyUsername());
        assertEquals("config 1, password",      null,                   c1.getProxyPassword());

        ClientConfiguration c2 = ClientUtils.parseProxyUrl("https://foo:bar@proxy.example.com:4000");
        assertEquals("config 2, protocol",      Protocol.HTTPS,         c2.getProxyProtocol());
        assertEquals("config 2, hostname",      "proxy.example.com",    c2.getProxyHost());
        assertEquals("config 2, port",          4000,                   c2.getProxyPort());
        assertEquals("config 2, username",      "foo",                  c2.getProxyUsername());
        assertEquals("config 2, password",      "bar",                  c2.getProxyPassword());
    }


    @Test
    public void testParseProxyUrlMixedCase() throws Exception
    {
        ClientConfiguration cfg = ClientUtils.parseProxyUrl("hTTpS://Foo:Ba!r@Proxy.example.com:4000");
        assertEquals("protocol",    Protocol.HTTPS,         cfg.getProxyProtocol());
        assertEquals("hostname",    "proxy.example.com",    cfg.getProxyHost());
        assertEquals("port",        4000,                   cfg.getProxyPort());
        assertEquals("username",    "Foo",                  cfg.getProxyUsername());
        assertEquals("password",    "Ba!r",                 cfg.getProxyPassword());
    }


    @Test
    public void testParseProxyUrlUnparseable() throws Exception
    {
        assertNull("passed null",       ClientUtils.parseProxyUrl(null));
        assertNull("passed blank",      ClientUtils.parseProxyUrl(""));
        assertNull("passed garbage",    ClientUtils.parseProxyUrl("//Proxy.example.com:4000"));
    }
}
