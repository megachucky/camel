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
package org.apache.camel.management;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStartingEvent;
import org.apache.camel.management.event.CamelContextStoppedEvent;
import org.apache.camel.management.event.CamelContextStoppingEvent;
import org.apache.camel.management.event.ExchangeCompletedEvent;
import org.apache.camel.management.event.ExchangeCreatedEvent;
import org.apache.camel.management.event.ExchangeFailedEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.management.event.RouteStartedEvent;
import org.apache.camel.management.event.RouteStoppedEvent;
import org.apache.camel.support.EventNotifierSupport;

/**
 * @version 
 */
public class MultipleEventNotifierEventsTest extends ContextTestSupport {

    private static List<EventObject> events = new ArrayList<EventObject>();
    private static List<EventObject> events2 = new ArrayList<EventObject>();

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    public void setUp() throws Exception {
        events.clear();
        events2.clear();
        super.setUp();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext(createRegistry());
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(EventObject event) throws Exception {
                events.add(event);
            }

            public boolean isEnabled(EventObject event) {
                return true;
            }

            @Override
            protected void doStart() throws Exception {
            }

            @Override
            protected void doStop() throws Exception {
            }
        });
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            public void notify(EventObject event) throws Exception {
                events2.add(event);
            }

            public boolean isEnabled(EventObject event) {
                return true;
            }

            @Override
            protected void doStart() throws Exception {
                setIgnoreCamelContextEvents(true);
                setIgnoreServiceEvents(true);
                setIgnoreRouteEvents(true);
            }

            @Override
            protected void doStop() throws Exception {
            }
        });
        return context;
    }

    public void testExchangeDone() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        assertEquals(9, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(1));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(2));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(3));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(4));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(5));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(6));
        assertIsInstanceOf(ExchangeCompletedEvent.class, events.get(7));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(8));

        assertEquals(5, events2.size());
        assertIsInstanceOf(ExchangeCreatedEvent.class, events2.get(0));
        assertIsInstanceOf(ExchangeSentEvent.class, events2.get(1));
        assertIsInstanceOf(ExchangeSentEvent.class, events2.get(2));
        assertIsInstanceOf(ExchangeCompletedEvent.class, events2.get(3));
        assertIsInstanceOf(ExchangeSentEvent.class, events2.get(4));

        context.stop();

        assertEquals(13, events.size());
        assertIsInstanceOf(CamelContextStoppingEvent.class, events.get(9));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(10));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(11));
        assertIsInstanceOf(CamelContextStoppedEvent.class, events.get(12));

        assertEquals(5, events2.size());
    }

    public void testExchangeFailed() throws Exception {
        try {
            template.sendBody("direct:fail", "Hello World");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
            assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
        }

        assertEquals(7, events.size());
        assertIsInstanceOf(CamelContextStartingEvent.class, events.get(0));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(1));
        assertIsInstanceOf(RouteStartedEvent.class, events.get(2));
        assertIsInstanceOf(CamelContextStartedEvent.class, events.get(3));
        assertIsInstanceOf(ExchangeCreatedEvent.class, events.get(4));
        assertIsInstanceOf(ExchangeFailedEvent.class, events.get(5));
        assertIsInstanceOf(ExchangeSentEvent.class, events.get(6));

        assertEquals(3, events2.size());

        context.stop();
        assertIsInstanceOf(ExchangeCreatedEvent.class, events2.get(0));
        assertIsInstanceOf(ExchangeFailedEvent.class, events2.get(1));
        assertIsInstanceOf(ExchangeSentEvent.class, events2.get(2));

        assertEquals(11, events.size());
        assertIsInstanceOf(CamelContextStoppingEvent.class, events.get(7));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(8));
        assertIsInstanceOf(RouteStoppedEvent.class, events.get(9));
        assertIsInstanceOf(CamelContextStoppedEvent.class, events.get(10));

        assertEquals(3, events2.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("mock:result");

                from("direct:fail").throwException(new IllegalArgumentException("Damn"));
            }
        };
    }

}