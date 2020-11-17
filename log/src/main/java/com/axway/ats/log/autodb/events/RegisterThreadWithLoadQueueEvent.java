/*
 * Copyright 2017 Axway Software
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

import com.axway.ats.log.autodb.model.LoggingEventType;
import com.axway.ats.log.autodb.model.TestCaseLoggingEvent;

@SuppressWarnings( "serial")
public class RegisterThreadWithLoadQueueEvent extends TestCaseLoggingEvent {

    private String threadName;
    private String loadQueueName;

    public RegisterThreadWithLoadQueueEvent( String loggerFQCN, Logger logger, String threadName,
                                             String loadQueueName ) {

        super(loggerFQCN, logger,
              "Registering thread '" + threadName + "' with load queue '" + loadQueueName + "'",
              LoggingEventType.REGISTER_THREAD_WITH_LOADQUEUE);

        this.threadName = threadName;
        this.loadQueueName = loadQueueName;
    }

    public String getThreadName() {

        return threadName;
    }

    public String getLoadQueueName() {

        return loadQueueName;
    }
}
