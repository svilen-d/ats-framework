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
package com.axway.ats.agent.webapp.restservice.api.system.monitoring;

import java.util.Map;
import java.util.Set;

import com.axway.ats.agent.webapp.restservice.api.ResourcesManager;
import com.axway.ats.common.performance.monitor.beans.ReadingBean;

public class SystemMonitorsManager {

    public synchronized static long initialize() {

        return ResourcesManager.addResource(new RestSystemMonitor());
    }

    public synchronized static long deinitialize(
                                                  long resourceId ) {

        return ResourcesManager.deinitializeResource(resourceId);

    }

    public synchronized static void initializeMonitoringContext(

                                                                 long resourceId,
                                                                 String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);

        monitor.initializeMonitoringContext(atsAgent);

    }

    public synchronized static void scheduleSystemMonitoring(
                                                              long resourceId,
                                                              String[] systemReadingTypes,
                                                              String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        Set<ReadingBean> readings = monitor.scheduleSystemMonitoring(atsAgent, systemReadingTypes);
        monitor.setScheduledReadingTypes(readings);

    }

    public synchronized static void scheduleMonitoring(
                                                        long resourceId,
                                                        String readingType,
                                                        Map<String, String> readingParameters,
                                                        String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        Set<ReadingBean> readings = monitor.scheduleMonitoring(atsAgent,
                                                               readingType,
                                                               readingParameters);
        monitor.setScheduledReadingTypes(readings);

    }

    public synchronized static void scheduleUserActivity( long resourceId, String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        monitor.scheduleUserActivity(atsAgent);

    }

    public synchronized static void startMonitoring( long resourceId, int pollingInterval,
                                                     long startTimestamp, long maximumRunningTime, String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);

        // calculate the time offset between the agent and the test executor
        long timeOffset = System.currentTimeMillis() - startTimestamp;

        monitor.startMonitoring(atsAgent, startTimestamp, pollingInterval, timeOffset, maximumRunningTime);

    }

    public synchronized static void stopMonitoring( long resourceId, String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);

        monitor.stopMonitoring(atsAgent);

    }

    public synchronized static void scheduleChildProcessMonitoring(
                                                                    long resourceId,
                                                                    String parentProcess,
                                                                    String processPattern,
                                                                    String processAlias,
                                                                    String processUsername,
                                                                    String[] processReadingTypes,
                                                                    String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        Set<ReadingBean> readings = monitor.scheduleChildProcessMonitoring(atsAgent, parentProcess, processPattern,
                                                                           processAlias,
                                                                           processUsername, processReadingTypes);
        monitor.setScheduledReadingTypes(readings);

    }

    public synchronized static void scheduleChildProcessMonitoring(
                                                                    long resourceId,
                                                                    String parentProcess,
                                                                    String processPattern,
                                                                    String processAlias,
                                                                    String[] processReadingTypes,
                                                                    String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        Set<ReadingBean> readings = monitor.scheduleChildProcessMonitoring(atsAgent, parentProcess, processPattern,
                                                                           processAlias,
                                                                           processReadingTypes);
        monitor.setScheduledReadingTypes(readings);

    }

    public synchronized static void scheduleProcessMonitoring(
                                                               long resourceId,
                                                               String processPattern,
                                                               String processAlias,
                                                               String[] processReadingTypes,
                                                               String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        Set<ReadingBean> readings = monitor.scheduleProcessMonitoring(atsAgent, processPattern, processAlias,
                                                                      processReadingTypes);
        monitor.setScheduledReadingTypes(readings);
    }

    public synchronized static void scheduleProcessMonitoring( long resourceId, String processPattern,
                                                               String processAlias, String processUsername,
                                                               String[] processReadingTypes,
                                                               String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        Set<ReadingBean> readings = monitor.scheduleProcessMonitoring(atsAgent, processPattern, processAlias,
                                                                      processUsername, processReadingTypes);
        monitor.setScheduledReadingTypes(readings);
    }

    public synchronized static void scheduleJvmMonitoring( long resourceId, String jvmPort,
                                                           String[] jvmReadingTypes, String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        Set<ReadingBean> readings = monitor.scheduleJvmMonitoring(atsAgent, jvmPort, "", jvmReadingTypes);
        monitor.setScheduledReadingTypes(readings);
    }

    public synchronized static void scheduleJvmMonitoring( long resourceId, String jvmPort,
                                                           String alias,
                                                           String[] jvmReadingTypes, String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        Set<ReadingBean> readings = monitor.scheduleJvmMonitoring(atsAgent, jvmPort, alias, jvmReadingTypes);
        monitor.setScheduledReadingTypes(readings);
    }

    public static void scheduleCustomJvmMonitoring( long resourceId, String jmxPort, String alias,
                                                    String mbeanName, String unit, String[] mbeanAttributes,
                                                    String atsAgent ) {

        RestSystemMonitor monitor = (RestSystemMonitor) ResourcesManager.getResource(resourceId);
        Set<ReadingBean> readings = monitor.scheduleCustomJvmMonitoring(atsAgent, jmxPort, alias, mbeanName, unit,
                                                                        mbeanAttributes);
        monitor.setScheduledReadingTypes(readings);
    }

}