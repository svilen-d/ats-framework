/*
 * Copyright 2017-2019 Axway Software
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * 
 */
package com.axway.ats.log.autodb.events;

import org.apache.logging.log4j.Logger;

import com.axway.ats.log.autodb.logqueue.LifeCycleState;
import com.axway.ats.log.autodb.model.AbstractLoggingEvent;
import com.axway.ats.log.autodb.model.LoggingEventType;

@SuppressWarnings( "serial")
public class StartRunEvent extends AbstractLoggingEvent {

    private final String runName;
    private final String osName;
    private final String productName;
    private final String versionName;
    private final String buildName;
    private final String hostName;

    public StartRunEvent( String loggerFQCN,
                          Logger logger,
                          String runName,
                          String osName,
                          String productName,
                          String versionName,
                          String buildName,
                          String hostName ) {

        super(loggerFQCN, logger, "Start run '" + runName + "'", LoggingEventType.START_RUN);

        this.runName = runName;
        this.osName = osName;
        this.productName = productName;
        this.versionName = versionName;
        this.buildName = buildName;
        this.hostName = hostName;
    }

    public StartRunEvent( String loggerFQCN,
                          Logger logger,
                          String message,
                          String runName,
                          String osName,
                          String productName,
                          String versionName,
                          String buildName,
                          String hostName,
                          LoggingEventType eventType ) {

        super(loggerFQCN, logger, message, eventType);

        this.runName = runName;
        this.osName = osName;
        this.productName = productName;
        this.versionName = versionName;
        this.buildName = buildName;
        this.hostName = hostName;
    }

    public String getRunName() {

        return this.runName;
    }

    public String getOsName() {

        return osName;
    }

    public String getProductName() {

        return productName;
    }

    public String getVersionName() {

        return versionName;
    }

    public String getBuildName() {

        return buildName;
    }

    public String getHostName() {

        return hostName;
    }

    @Override
    protected LifeCycleState getExpectedLifeCycleState(
                                                        LifeCycleState state ) {

        return LifeCycleState.INITIALIZED;
    }
}
