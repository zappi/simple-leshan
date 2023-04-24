/*******************************************************************************
 * Copyright (c) 2021 Sierra Wireless and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 *
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.client;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;


import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.leshan.client.californium.endpoint.CaliforniumClientEndpointsProvider;
import org.eclipse.leshan.client.engine.DefaultRegistrationEngineFactory;
import org.eclipse.leshan.client.object.*;
import org.eclipse.leshan.core.util.NamedThreadFactory;


import static org.eclipse.leshan.client.object.Security.noSec;

import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.resource.listener.ObjectsListenerAdapter;
import org.eclipse.leshan.core.LwM2mId;
import org.eclipse.leshan.core.model.LwM2mModelRepository;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main {

    public final static String[] modelPaths = new String[]{"0-1_0.xml", "0-1_1.xml", "0.xml", "1-1_0.xml", "1-1_1.xml", "2-1_0.xml", "2.xml", "21.xml", "3-1_0.xml", "3-1_1.xml", "3.xml", "3442.xml", "4-1_0.xml", "4-1_1.xml", "4-1_2.xml", "4.xml", "5-1_0.xml", "5.xml", "6.xml", "7.xml"};

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String CF_CONFIGURATION_FILENAME = "Californium3.client.properties";
    private static final String CF_CONFIGURATION_HEADER = "Leshan Client Jerry Thesis - " + Configuration.DEFAULT_HEADER;

    // Configuration
    private int nbclients = 1;
    private Integer timeToStartAllClientInS;
    // Could be null if communicationPeriodInSeconds is used
    private Integer nbUpdatesByMinutes;
    // Could be null if nbUpdatesByMinutes is used
    private Integer communicationPeriodInSeconds;
    // Could be null if test should never ends
    private Long testDurationInSeconds;
    // TRUE if device should bootstrap, FALSE if it should register without
    // bootstrap
    private boolean bootstrap = false;
    private boolean reconnectOnUpdate = false;
    private boolean resumeOnConnect = true;
    // LWM2M bootstrap server or LWM2M server URL
    private String serverURI;
    private InetSocketAddress graphiteServerAddress;
    private int graphitePollingPeriodInSec;
    private String endpointPattern;
    private String pskKeyPattern;
    private String pskIdPattern;

    // Thread configuration
    private CountDownLatch testEnd = new CountDownLatch(1);
    private ScheduledExecutorService executor = Executors
            .newSingleThreadScheduledExecutor(new NamedThreadFactory("Clients Launcher"));
    private ScheduledExecutorService executorForClients = Executors.newScheduledThreadPool(400,
            new NamedThreadFactory("coap+dtls connector"));

    // Internal state
    private List<LeshanClient> clients;
    private int currentClientIndex = 0;


    private Map<String, String> additionalAttributes;

    private static LwM2mModelRepository createModel() {
        List<ObjectModel> models = ObjectLoader.loadAllDefault();
        return new LwM2mModelRepository(models);
    }

    public LeshanClient createLeshanClient(LwM2mModelRepository repository, String serverURI, int i) {
        String endpoint = String.format(endpointPattern, i);
        final ObjectsInitializer initializer = new ObjectsInitializer(repository.getLwM2mModel());

        initializer.setClassForObject(LwM2mId.OSCORE, Oscore.class);
        initializer.setInstancesForObject(LwM2mId.SECURITY, noSec(serverURI, 123));
        initializer.setInstancesForObject(LwM2mId.SERVER, new Server(123, 300));
        initializer.setInstancesForObject(LwM2mId.DEVICE, new Device("Test phone", "3310", "123456789"));
        initializer.setInstancesForObject(LwM2mId.CONNECTIVITY_MONITORING, new ConnectivityMonitoring());


        List<LwM2mObjectEnabler> objectEnablers = initializer.createAll();

        DefaultRegistrationEngineFactory engineFactory = new DefaultRegistrationEngineFactory();


        CaliforniumClientEndpointsProvider.Builder endpointsBuilder = new CaliforniumClientEndpointsProvider.Builder();
        Configuration clientCoapConfig = endpointsBuilder.createDefaultConfiguration();
        File configFile = new File(CF_CONFIGURATION_FILENAME);
        if (configFile.isFile()) {
            clientCoapConfig.load(configFile);
        } else {
            clientCoapConfig.store(configFile, CF_CONFIGURATION_HEADER);
        }


        endpointsBuilder.setConfiguration(clientCoapConfig);
        endpointsBuilder.setClientAddress(new InetSocketAddress(0).getAddress());

        LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);

        builder.setObjects(objectEnablers);
        builder.setRegistrationEngineFactory(engineFactory);
        builder.setEndpointsProvider(endpointsBuilder.build());

        final LeshanClient client = builder.build();

        client.getObjectTree().addListener(new ObjectsListenerAdapter() {

            @Override
            public void objectRemoved(LwM2mObjectEnabler object) {
                LOG.info("Object {} v{} disabled.", object.getId(), object.getObjectModel().version);
            }

            @Override
            public void objectAdded(LwM2mObjectEnabler object) {
                LOG.info("Object {} v{} enabled.", object.getId(), object.getObjectModel().version);
            }
        });

        return client;
    }

    public void setNbClients(int nbclients) {
        this.nbclients = nbclients;
    }

    public void setTimeToStart(int timeToStartAllClientInSeconds) {
        this.timeToStartAllClientInS = timeToStartAllClientInSeconds;
    }

    public void setNbUpdatesByMinutes(int nbUpdatesByMinutes) {
        this.nbUpdatesByMinutes = nbUpdatesByMinutes;
        this.communicationPeriodInSeconds = null;
    }

    public void setCommunicationPeriod(int communicationPeriodInSeconds) {
        this.communicationPeriodInSeconds = communicationPeriodInSeconds;
        this.nbUpdatesByMinutes = null;
    }

    public void setTestDurationInSeconds(long testDurationInSeconds) {
        this.testDurationInSeconds = testDurationInSeconds;
    }

    public void setReconnectOnUpdate(boolean reconnectOnUpdate) {
        this.reconnectOnUpdate = reconnectOnUpdate;
    }

    public void setResumeOnConnect(boolean resumeOnConnect) {
        this.resumeOnConnect = resumeOnConnect;
    }

    public void setBootstrap(boolean bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void setServerURI(String serverURI) {
        this.serverURI = serverURI;
    }

    public void setGraphiteServerAddress(InetSocketAddress graphiteServerAddress) {
        this.graphiteServerAddress = graphiteServerAddress;
    }

    public void setGraphitePollingPeriod(int graphitePollingPeriodInSec) {
        this.graphitePollingPeriodInSec = graphitePollingPeriodInSec;
    }

    public void setEndpointPattern(String endpointPattern) {
        this.endpointPattern = endpointPattern;
    }

    public void setPskIdPattern(String pskIdPattern) {
        this.pskIdPattern = pskIdPattern;
    }

    public void setPskKeyPattern(String pskKeyPattern) {
        this.pskKeyPattern = pskKeyPattern;
    }

    public void setAdditionalAttributes(Map<String, String> additionalAttributes) {
        this.additionalAttributes = additionalAttributes;
    }

    public LeshanClient createClient(int i) {
        LwM2mModelRepository repository = createModel();
        LeshanClient client = createLeshanClient(repository, serverURI, i);
        return client;
    }

    public void createClients() {
        clients = new ArrayList<>(nbclients);
        for (int i = 1; i <= nbclients; i++) {
            clients.add(createClient(i));
        }
    }

    public void start() {
        clients.get(0).start();
        if (nbclients > 1) {
            executor.submit(new Runnable() {

                @Override
                public void run() {
                    int timeBetweenLaunch = timeToStartAllClientInS / (nbclients - 1);
                    boolean interrupted = false;
                    for (int i = 1; i < nbclients && !interrupted; i++) {
                        try {
                            Thread.sleep(timeBetweenLaunch * 1000);
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                        clients.get(i).start();
                    }
                }
            });
        }
    }

    public void waitToEnd() throws InterruptedException {
        testEnd.await();
    }

    public boolean waitToEnd(long timeoutInSec) throws InterruptedException {
        if (testEnd.await(timeoutInSec, TimeUnit.SECONDS)) {
            for (int i = 0; i < nbclients; i++) {
                clients.get(i).destroy(true);
            }
            return true;
        } else {
            return false;
        }
    }

    public void destroy(boolean deregister) {
        for (LeshanClient client : clients) {
            client.destroy(deregister);
        }
        executorForClients.shutdown();
        executor.shutdown();
    }
}

