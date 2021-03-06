/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mail;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.mail.MailPreparationException;

/**
 * Unit test for no recipients
 */
public class MailNoRecipientsTest extends CamelTestSupport {

    @Test
    public void testMailNoRecipients() throws Exception {
        try {
            template.sendBody("direct:a", "Hello World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            MailPreparationException mpe = assertIsInstanceOf(MailPreparationException.class, e.getCause());
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, mpe.getCause());
            assertEquals("The mail message does not have any recipients set.", iae.getMessage());
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:a").to("smtp://localhost");
            }
        };
    }
}