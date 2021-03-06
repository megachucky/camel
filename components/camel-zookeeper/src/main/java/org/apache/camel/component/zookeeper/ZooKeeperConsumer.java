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
package org.apache.camel.component.zookeeper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.String.format;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.zookeeper.operations.AnyOfOperations;
import org.apache.camel.component.zookeeper.operations.ChildrenChangedOperation;
import org.apache.camel.component.zookeeper.operations.DataChangedOperation;
import org.apache.camel.component.zookeeper.operations.ExistenceChangedOperation;
import org.apache.camel.component.zookeeper.operations.ExistsOperation;
import org.apache.camel.component.zookeeper.operations.GetChildrenOperation;
import org.apache.camel.component.zookeeper.operations.GetDataOperation;
import org.apache.camel.component.zookeeper.operations.OperationResult;
import org.apache.camel.component.zookeeper.operations.ZooKeeperOperation;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.zookeeper.ZooKeeper;

/**
 * <code>ZooKeeperConsumer</code> uses various {@link ZooKeeperOperation} to
 * interact and consume data from a ZooKeeper cluster.
 */
@SuppressWarnings("rawtypes")
public class ZooKeeperConsumer extends DefaultConsumer {

    private ZooKeeperConnectionManager connectionManager;

    private ZooKeeper connection;

    private ZooKeeperConfiguration configuration;

    private LinkedBlockingQueue<ZooKeeperOperation> operations = new LinkedBlockingQueue<ZooKeeperOperation>();

    private boolean shuttingDown;

    private ExecutorService executor;

    public ZooKeeperConsumer(ZooKeeperEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.connectionManager = endpoint.getConnectionManager();
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        connection = connectionManager.getConnection();
        if (log.isDebugEnabled()) {
            log.debug(format("Connected to Zookeeper cluster %s", configuration.getConnectString()));
        }

        initializeConsumer();
        executor = getEndpoint().getCamelContext().getExecutorServiceManager().newFixedThreadPool(configuration.getPath(), "Camel-Zookeeper Ops executor", 1);
        OperationsExecutor opsService = new OperationsExecutor();
        executor.execute(opsService);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        shuttingDown = true;
        connection = connectionManager.getConnection();
        if (log.isTraceEnabled()) {
            log.trace(format("Shutting down zookeeper consumer of '%s'", configuration.getPath()));
        }
        executor.shutdown();
        connectionManager.shutdown();
    }

    private void initializeConsumer() {
        String node = configuration.getPath();
        if (configuration.listChildren()) {
            initializeChildListingConsumer(node);
        } else {
            initializeDataConsumer(node);
        }
    }

    private void initializeDataConsumer(String node) {
        if (!shuttingDown) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Initailizing consumption of data on node '%s'", node));
            }
            addBasicDataConsumeSequence(node);
        }
    }

    private void initializeChildListingConsumer(String node) {
        if (!shuttingDown) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Initailizing child listing of node '%s'", node));
            }
            addBasicChildListingSequence(node);
        }
    }

    private Exchange createExchange(String path, OperationResult result) {
        Exchange e = getEndpoint().createExchange();
        ZooKeeperMessage in = new ZooKeeperMessage(path, result.getStatistics());
        e.setIn(in);
        if (result.isOk()) {
            in.setBody(result.getResult());
        } else {
            e.setException(result.getException());
        }
        return e;
    }

    private class OperationsExecutor implements Runnable {

        private ZooKeeperOperation current;

        public void run() {
            while (isRunAllowed()) {

                try {
                    current = operations.take();
                    if (log.isTraceEnabled()) {
                        log.trace(format("Processing '%s' operation", current.getClass().getSimpleName()));
                    }
                } catch (InterruptedException e) {
                    continue;
                }
                String node = current.getNode();
                try {
                    OperationResult result = current.get();
                    if (result != null && current.shouldProduceExchange()) {
                        getProcessor().process(createExchange(node, result));
                    }
                } catch (Exception e) {
                    handleException(e);
                    backoffAndThenRestart();
                } finally {
                    if (configuration.shouldRepeat()) {
                        try {
                            operations.offer(current.createCopy());
                        } catch (Exception e) {
                            e.printStackTrace();
                            backoffAndThenRestart();
                        }
                    }
                }
            }
        }

        private void backoffAndThenRestart() {
            try {
                if (isRunAllowed()) {
                    Thread.sleep(configuration.getBackoff());
                    initializeConsumer();
                }
            } catch (Exception e) {
            }
        }
    }

    private void addBasicDataConsumeSequence(String node) {
        operations.clear();
        operations.add(new AnyOfOperations(node, new ExistsOperation(connection, node), new ExistenceChangedOperation(connection, node)));
        operations.add(new GetDataOperation(connection, node));
        operations.add(new DataChangedOperation(connection, node, false));
    }

    private void addBasicChildListingSequence(String node) {
        operations.clear();
        operations.add(new AnyOfOperations(node, new ExistsOperation(connection, node), new ExistenceChangedOperation(connection, node)));
        operations.add(new GetChildrenOperation(connection, node));
        operations.add(new ChildrenChangedOperation(connection, node, false));
    }
}
