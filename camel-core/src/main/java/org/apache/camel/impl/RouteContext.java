/*
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
package org.apache.camel.impl;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.FromType;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.model.RouteType;
import org.apache.camel.processor.MulticastProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The context used to activate new routing rules
 *
 * @version $Revision: $
 */
public class RouteContext {
    private final RouteType route;
    private final FromType from;
    private final Collection<Route> routes;
    private Endpoint endpoint;
    private List<Processor> eventDrivenProcessors = new ArrayList<Processor>();

    public RouteContext(RouteType route, FromType from, Collection<Route> routes) {
        this.route = route;
        this.from = from;
        this.routes = routes;
    }

    public Endpoint getEndpoint() {
        if (endpoint == null) {
            endpoint = from.resolveEndpoint(this);
        }
        return endpoint;
    }

    public FromType getFrom() {
        return from;
    }

    public RouteType getRoute() {
        return route;
    }

    public CamelContext getCamelContext() {
        return getRoute().getCamelContext();
    }

    public Processor createProcessor(ProcessorType node) throws Exception {
        Processor processor = createProcessor(node.getOutputs());
        return node.wrapProcessor(this, processor);
    }

    public Processor createProcessor(Collection<ProcessorType> outputs) throws Exception {
        List<Processor> list = new ArrayList<Processor>();
        for (ProcessorType output : outputs) {
            Processor processor = output.createProcessor(this);
            list.add(processor);
        }
        return createCompositeProcessor(list);
    }

    protected Processor createCompositeProcessor(List<Processor> list) {
        if (list.size() == 0) {
            return null;
        }
        Processor processor;
        if (list.size() == 1) {
            processor = list.get(0);
        }
        else {
            //processor = new CompositeProcessor(list);
            // TODO move into the node itself
            processor = new MulticastProcessor(list);
        }
        return processor;
    }

    public Endpoint resolveEndpoint(String uri) {
        return route.resolveEndpoint(uri);
    }

    /**
     * Resolves an endpoint from either a URI or a named reference
     */
    public Endpoint resolveEndpoint(String uri, String ref) {
        Endpoint endpoint = null;
        if (uri != null) {
            endpoint = resolveEndpoint(uri);
            if (endpoint == null) {
                throw new NoSuchEndpointException(uri);
            }
        }
        if (ref != null) {
            endpoint = lookup(ref, Endpoint.class);
            if (endpoint == null) {
                throw new NoSuchEndpointException("ref:" + ref);
            }
        }
        if (endpoint == null) {
            throw new IllegalArgumentException("Either 'uri' or 'ref' must be specified on: " + this);
        }
        else {
            return endpoint;
        }
    }

    /**
     * lookup an object by name and type
     */
    public <T> T lookup(String name, Class<T> type) {
        return getCamelContext().getRegistry().lookup(name, type);
    }

    /**
     * Lets complete the route creation, creating a single event driven route for the current from endpoint
     * with any processors required
     */
    public void commit() {
        // now lets turn all of the event driven consumer processors into a single route
        if (!eventDrivenProcessors.isEmpty()) {
            Processor processor = createCompositeProcessor(eventDrivenProcessors);
            routes.add(new EventDrivenConsumerRoute(getEndpoint(), processor));
        }
    }

    public void addEventDrivenProcessor(Processor processor) {
        eventDrivenProcessors.add(processor);
    }
}
