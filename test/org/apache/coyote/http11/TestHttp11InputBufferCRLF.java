/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.coyote.http11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import org.apache.catalina.Context;
import org.apache.catalina.startup.SimpleHttpClient;
import org.apache.catalina.startup.TesterServlet;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.TomcatBaseTest;

@RunWith(Parameterized.class)
public class TestHttp11InputBufferCRLF extends TomcatBaseTest {

    private static final String CR = "\r";
    private static final String LF = "\n";
    private static final String CRLF = CR + LF;

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        List<Object[]> parameterSets = new ArrayList<Object[]>();

        // Requests to simulate package boundaries
        // Standard HTTP/1.1 request
        addRequestWithSplits("GET http://localhost:8080/test HTTP/1.1" + CRLF +
                "Host: localhost:8080" + CRLF +
                "Connection: close" + CRLF +
                CRLF,
                parameterSets);

        return parameterSets;
    }


    private static void addRequestWithSplits(String request, List<Object[]> parameterSets) {
        // Add as a single String
        parameterSets.add(new Object[] { new String[] { request } } );

        // Add with all CRLF split between the CR and LF
        List<String> parts = new ArrayList<String>();
        int lastPos = 0;
        int pos = request.indexOf("\n");
        while (pos > -1) {
            parts.add(request.substring(lastPos, pos));
            lastPos = pos;
            pos = request.indexOf("\n", lastPos + 1);
        }
        parts.add(request.substring(lastPos));
        parameterSets.add(new Object[] { parts.toArray(new String[0]) });

        // Add with a split between each character
        List<String> chars = new ArrayList<String>();
        for (char c : request.toCharArray()) {
            chars.add(Character.toString(c));
        }
        parameterSets.add(new Object[] { chars.toArray(new String[0]) });
    }

    @Parameter(0)
    public String[] request;

    @Test
    public void testBug54947() {

        Client client = new Client(request);

        client.doRequest();
        Assert.assertTrue(client.isResponseBodyOK());
    }


    private class Client extends SimpleHttpClient {

        public Client(String[] request) {
            setRequest(request);
        }

        private Exception doRequest() {

            Tomcat tomcat = getTomcatInstance();

            Context root = tomcat.addContext("", TEMP_DIR);
            Tomcat.addServlet(root, "TesterServlet", new TesterServlet());
            root.addServletMapping("/test", "TesterServlet");

            try {
                tomcat.start();
                setPort(tomcat.getConnector().getLocalPort());
                setRequestPause(20);

                // Open connection
                connect();

                setRequest(request);
                processRequest(); // blocks until response has been read

                // Close the connection
                disconnect();
            } catch (Exception e) {
                return e;
            }
            return null;
        }

        @Override
        public boolean isResponseBodyOK() {
            if (getResponseBody() == null) {
                return false;
            }
            if (!getResponseBody().contains("OK")) {
                return false;
            }
            return true;
        }
    }
}
