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
package com.axway.ats.log.autodb.events;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.axway.ats.log.autodb.logqueue.LifeCycleState;
import com.axway.ats.log.autodb.model.AbstractLoggingEvent;
import com.axway.ats.log.autodb.model.LoggingEventType;

@SuppressWarnings( "serial")
public class AddRunMetainfoEvent extends AbstractLoggingEvent {

    private String metaKey;
    private String metaValue;

    public AddRunMetainfoEvent( String loggerFQCN,
                                Logger logger,
                                String metaKey,
                                String metaValue ) {

        super(loggerFQCN,
              logger,
              "Add run meta info '" + metaKey + "=" + metaValue + "'",
              LoggingEventType.ADD_RUN_METAINFO);

        this.metaKey = metaKey;
        this.metaValue = metaValue;
    }

    public String getMetaKey() {

        return metaKey;
    }

    public String getMetaValue() {

        return metaValue;
    }

    @Override
    protected LifeCycleState getExpectedLifeCycleState(
                                                        LifeCycleState state ) {

        return LifeCycleState.ATLEAST_RUN_STARTED;
    }
}
