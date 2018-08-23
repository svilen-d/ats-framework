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
package com.axway.ats.log.autodb;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import com.axway.ats.core.dbaccess.ConnectionPool;
import com.axway.ats.core.dbaccess.DbConnection;
import com.axway.ats.core.dbaccess.DbUtils;
import com.axway.ats.core.dbaccess.mssql.DbConnSQLServer;
import com.axway.ats.core.dbaccess.postgresql.DbConnPostgreSQL;
import com.axway.ats.core.log.AtsConsoleLogger;
import com.axway.ats.core.threads.ThreadsPerCaller;
import com.axway.ats.core.utils.ExecutorUtils;
import com.axway.ats.core.utils.StringUtils;
import com.axway.ats.log.appenders.ActiveDbAppender;
import com.axway.ats.log.autodb.entities.Run;
import com.axway.ats.log.autodb.entities.Testcase;
import com.axway.ats.log.autodb.events.AddRunMetainfoEvent;
import com.axway.ats.log.autodb.events.AddScenarioMetainfoEvent;
import com.axway.ats.log.autodb.events.CleanupLoadQueueStateEvent;
import com.axway.ats.log.autodb.events.EndCheckpointEvent;
import com.axway.ats.log.autodb.events.EndLoadQueueEvent;
import com.axway.ats.log.autodb.events.EndTestCaseEvent;
import com.axway.ats.log.autodb.events.InsertCheckpointEvent;
import com.axway.ats.log.autodb.events.InsertMessageEvent;
import com.axway.ats.log.autodb.events.InsertSystemStatisticEvent;
import com.axway.ats.log.autodb.events.InsertUserActivityStatisticEvent;
import com.axway.ats.log.autodb.events.JoinTestCaseEvent;
import com.axway.ats.log.autodb.events.RegisterThreadWithLoadQueueEvent;
import com.axway.ats.log.autodb.events.RememberLoadQueueStateEvent;
import com.axway.ats.log.autodb.events.StartCheckpointEvent;
import com.axway.ats.log.autodb.events.StartRunEvent;
import com.axway.ats.log.autodb.events.StartSuiteEvent;
import com.axway.ats.log.autodb.events.StartTestCaseEvent;
import com.axway.ats.log.autodb.events.UpdateRunEvent;
import com.axway.ats.log.autodb.events.UpdateSuiteEvent;
import com.axway.ats.log.autodb.events.UpdateTestcaseEvent;
import com.axway.ats.log.autodb.exceptions.DatabaseAccessException;
import com.axway.ats.log.autodb.exceptions.IncorrectProcessorStateException;
import com.axway.ats.log.autodb.exceptions.LoadQueueAlreadyStartedException;
import com.axway.ats.log.autodb.exceptions.LoggingException;
import com.axway.ats.log.autodb.exceptions.LoggingIsOverException;
import com.axway.ats.log.autodb.exceptions.NoSuchLoadQueueException;
import com.axway.ats.log.autodb.exceptions.ThreadAlreadyRegisteredWithLoadQueueException;
import com.axway.ats.log.autodb.exceptions.ThreadNotRegisteredWithLoadQueue;
import com.axway.ats.log.autodb.model.AbstractLoggingEvent;
import com.axway.ats.log.autodb.model.CacheableEvent;
import com.axway.ats.log.autodb.model.EventRequestProcessor;
import com.axway.ats.log.autodb.model.EventRequestProcessorListener;
import com.axway.ats.log.autodb.model.IDbWriteAccess;
import com.axway.ats.log.autodb.model.LoggingEventType;
import com.axway.ats.log.model.SystemLogLevel;

public class DbEventRequestProcessor implements EventRequestProcessor {

    private static final AtsConsoleLogger log                        = new AtsConsoleLogger(DbEventRequestProcessor.class);

    /**
     * The configuration for this appender
     */
    private DbAppenderConfiguration       appenderConfig;

    /**
     * The connection information
     */
    private DbConnection                  dbConnection;

    /**
     * The DB access instance
     */
    private IDbWriteAccess                dbAccess;

    /**
     * The layout according to which to format events
     */
    private Layout                        layout;

    /**
     * The current state of the event processor
     */
    private EventProcessorState           _state;

    /**
     * Listener to be notified when events are processed
     */
    private EventRequestProcessorListener listener;

    /**
     * The name of the machine running this event processor
     */
    private String                        machineName;

    /**
     * We cache the suites opened by this run, so do not have to query the DB
     *
     * key = <suite name>; value = <suite ID>
     */
    private static Map<String, Integer>   suiteIdsCache              = new HashMap<String, Integer>();

    private boolean                       isBatchMode;

    /*
     * When rerunning a testcase, we have to delete the faulty one.
     * The main thread passes here the id of the test to be deleted.
     */
    private int                           testcaseToDelete           = -1;
    /*
     * This is a list with all deleted tests.
     * We use it in order to skip going to the DB as we know the operation will fail.
     */
    private List<Integer>                 deletedTestcases           = new ArrayList<Integer>();

    /*
     * If the current state of the DbEventProcessor could not process UpdateSuiteEvent,
     * preserve this event and fire it right after StartSuiteEvent is received
     * */
    private UpdateSuiteEvent              pendingUpdateSuiteEvent    = null;

    /*
     * The UpdateRunEvent, fired from the user.
     * */
    private UpdateRunEvent                userProvidedUpdateRunEvent = null;

    /*
     * The actual UpdateRunEvent that will be processed and executed
     * */
    private UpdateRunEvent                actualUpdateRunEvent       = null;

    /*
     * While these flag is true, all messages are logged as run messages
     * */
    private boolean                       afterSuiteMode             = false;

    /*
     * While these flag is true, all messages are logged as suite messages
     * */
    private boolean                       afterClassMode             = false;

    /*
     * While these flag is true, all messages, statistics and checkpoints
     * are logged as they have been logged from the testcase, that was ended most recently
     * */
    private boolean                       afterMethodMode            = false;

    /*
     * Keeps the ID of the last ended suite
     * */
    private int                           lastEndedSuiteId           = -1;

    /**
     * Do not use this constructor.
     * It is implemented only to be used, when a dummy db event request processor is needed to be created.
     * Currently the only case that this is needed is when ActiveDbAppender config info is not found in log4j.xml
     **/
    public DbEventRequestProcessor() {

        if (_state == null) {
            _state = new EventProcessorState();
        }

    }

    public DbEventRequestProcessor( DbAppenderConfiguration appenderConfig, Layout layout,
                                    EventRequestProcessorListener listener,
                                    boolean isBatchMode ) throws DatabaseAccessException {

        this.appenderConfig = appenderConfig;
        this.isBatchMode = isBatchMode;

        if (DbUtils.isMSSQLDatabaseAvailable(appenderConfig.getHost(),
                                             Integer.parseInt(appenderConfig.getPort()),
                                             appenderConfig.getDatabase(),
                                             appenderConfig.getUser(),
                                             appenderConfig.getPassword())) {

            this.dbConnection = new DbConnSQLServer(appenderConfig.getHost(),
                                                    Integer.parseInt(appenderConfig.getPort()),
                                                    appenderConfig.getDatabase(),
                                                    appenderConfig.getUser(),
                                                    appenderConfig.getPassword(), null);

            //create the db access layer
            this.dbAccess = new SQLServerDbWriteAccess((DbConnSQLServer) dbConnection, isBatchMode);

        } else if (DbUtils.isPostgreSQLDatabaseAvailable(appenderConfig.getHost(),
                                                         Integer.parseInt(appenderConfig.getPort()),
                                                         appenderConfig.getDatabase(),
                                                         appenderConfig.getUser(),
                                                         appenderConfig.getPassword())) {

            this.dbConnection = new DbConnPostgreSQL(appenderConfig.getHost(),
                                                     Integer.parseInt(appenderConfig.getPort()),
                                                     appenderConfig.getDatabase(),
                                                     appenderConfig.getUser(),
                                                     appenderConfig.getPassword(), null);

            //create the db access layer
            this.dbAccess = new PGDbWriteAccess((DbConnPostgreSQL) dbConnection, isBatchMode);

        } else {
            String errMsg = "Neither MSSQL, nor PostgreSQL server at '" + appenderConfig.getHost() + ":"
                            + (!StringUtils.isNullOrEmpty(appenderConfig.getPort())
                                                                                    ? appenderConfig.getPort()
                                                                                    : "")
                            + "' contains ATS log database with name '" + appenderConfig.getDatabase() + "'.";
            throw new DatabaseAccessException(errMsg);
        }

        this.layout = layout;
        this.listener = listener;

        //get the hostname of the machine
        try {
            InetAddress addr = InetAddress.getLocalHost();
            this.machineName = addr.getHostName();
        } catch (UnknownHostException uhe) {
            this.machineName = "unknown host";
        }
    }

    /**
     * Set the layout for logging messages
     *
     * @param layout the layout
     */
    public void setLayout( Layout layout ) {

        this.layout = layout;
    }

    public int getRunId() {

        return _state.getRunId();
    }

    public int getSuiteId() {

        return _state.getSuiteId();
    }

    public String getRunName() {

        return _state.getRunName();
    }

    public String getRunUserNote() {

        return _state.getRunUserNote();
    }

    public int getTestCaseId() {

        return _state.getTestCaseId();
    }

    public int getLastExecutedTestCaseId() {

        return _state.getLastExecutedTestCaseId();
    }

    public void processEventRequest( LogEventRequest eventRequest ) throws LoggingException {

        if (testcaseToDelete > 0) {
            /* Pause for a moment processing the current event.
             * This is (delete testcase)event is not coming from the FIFO queue, as we want to process it as
             * soon as possible, so any events related to this testcase are directly skipped.
             */
            deleteRequestedTestcase();

            // now resume the processing of the current event that came from the queue
        }

        if (isBatchMode && eventRequest == null) {
            // timeout waiting for next event - flush the current cache
            dbAccess.flushCache();
            return;
        }

        LoggingEvent event = eventRequest.getEvent();
        AbstractLoggingEvent dbAppenderEvent = event instanceof AbstractLoggingEvent
                                                                                     ? (AbstractLoggingEvent) event
                                                                                     : null;

        if (dbAppenderEvent != null && dbAppenderEvent.getEventType() == LoggingEventType.START_RUN) {
            if (ActiveDbAppender.runState.getPreviousRunId() > 0) {
                // we already have started a run
                // join this run with it
                ActiveDbAppender.runState.setRunId(ActiveDbAppender.runState.getPreviousRunId());
                ActiveDbAppender.runState.setLifeCycleState(LifeCycleState.RUN_STARTED);
                //notify the listener that the run started successfully
                if (listener != null) {
                    listener.onRunStarted();
                }
                return;
            }
        }

        if (_state == null) {
            _state = new EventProcessorState();
        }

        if (_state.getRunId() == 0) {
            // this event is sent from a spawn thread and is missing some important data
            _state.injectRunInformation(ActiveDbAppender.runState);
        }

        if (dbAppenderEvent != null) {

            if (dbAppenderEvent instanceof UpdateSuiteEvent) {
                try {
                    dbAppenderEvent.checkIfCanBeProcessed(_state);
                } catch (IncorrectProcessorStateException e) {
                    /* Suite not started yet, 
                     * so save the current event as pending 
                     * and fired it right after StartSuiteEvent is received
                    */
                    pendingUpdateSuiteEvent = (UpdateSuiteEvent) dbAppenderEvent;
                    return;
                }
            } else if (dbAppenderEvent instanceof UpdateRunEvent) {
                try {
                    /* Run not started yet.
                     * We will fire the event after StartRunEvent is received
                    */
                    userProvidedUpdateRunEvent = (UpdateRunEvent) dbAppenderEvent;
                    dbAppenderEvent.checkIfCanBeProcessed(_state);
                } catch (IncorrectProcessorStateException e) {
                    /*
                     * If we get an exception, do no process the event any further.
                     * We will fire it after StartRunEvent is received.
                     * */
                    return;
                }
            } else {
                //first check if we can process the event at all
                dbAppenderEvent.checkIfCanBeProcessed(_state);
            }

            if (isBatchMode && ! (event instanceof CacheableEvent)
                && _state.getLifeCycleState() == LifeCycleState.TEST_CASE_STARTED) {
                // this event can not be cached - flush the current cache
                dbAccess.flushCache();
            }

            switch (dbAppenderEvent.getEventType()) {
                case START_RUN:
                    startRun((StartRunEvent) event, eventRequest.getTimestamp());
                    if (userProvidedUpdateRunEvent != null) {
                        constructUpdateRunEvent();
                        updateRun(actualUpdateRunEvent);
                    }
                    break;
                case END_RUN:
                    endRun(eventRequest.getTimestamp());
                    break;
                case UPDATE_RUN:
                    /*
                     * By using data from the latest UpdateRunEvent and the current run info,
                     * construct a pending UpdateRunEvent
                     * */
                    constructUpdateRunEvent();
                    updateRun(actualUpdateRunEvent);
                    break;
                case START_AFTER_SUITE:
                    afterSuiteMode = true;
                    break;
                case END_AFTER_SUITE:
                    afterSuiteMode = false;
                    break;
                case ADD_RUN_METAINFO:
                    addRunMetainfo((AddRunMetainfoEvent) event);
                    break;
                case START_SUITE:
                    startSuite((StartSuiteEvent) event, eventRequest.getTimestamp());
                    if (pendingUpdateSuiteEvent != null) {
                        updateSuite(pendingUpdateSuiteEvent);
                        pendingUpdateSuiteEvent = null;
                    }
                    break;
                case END_SUITE:

                    endSuite(eventRequest.getTimestamp());
                    break;
                case UPDATE_SUITE:
                    updateSuite((UpdateSuiteEvent) event);
                    break;
                case START_AFTER_CLASS:
                    afterClassMode = true;
                    break;
                case END_AFTER_CLASS:
                    afterClassMode = false;
                    break;
                case CLEAR_SCENARIO_METAINFO:
                    clearScenarioMetainfo();
                    break;
                case ADD_SCENARIO_METAINFO:
                    addScenarioMetainfo((AddScenarioMetainfoEvent) event);
                    break;
                case UPDATE_TEST_CASE:
                    updateTestcase((UpdateTestcaseEvent) event, eventRequest.getTimestamp());
                    break;
                case END_TEST_CASE:
                    endTestCase((EndTestCaseEvent) event, eventRequest.getTimestamp());
                    break;
                case START_TEST_CASE:
                    startTestCase((StartTestCaseEvent) event, eventRequest.getTimestamp());
                    break;
                case JOIN_TEST_CASE:
                    joinTestCase((JoinTestCaseEvent) event);
                    break;
                case LEAVE_TEST_CASE:
                    String threadId = null;
                    threadId = event.getProperty(ExecutorUtils.ATS_THREAD_ID);
                    if (threadId == null) {
                        threadId = getThreadId(event.getThreadName());
                    }
                    leaveTestCase(threadId);
                    break;
                case START_AFTER_METHOD:
                    afterMethodMode = true;
                    break;
                case END_AFTER_METHOD:
                    afterMethodMode = false;
                    break;
                case REMEMBER_LOADQUEUE_STATE:
                    rememberLoadQueueState((RememberLoadQueueStateEvent) event);
                    break;
                case CLEANUP_LOADQUEUE_STATE:
                    cleanupLoadQueueState((CleanupLoadQueueStateEvent) event);
                    break;
                case END_LOADQUEUE:
                    endLoadQueue((EndLoadQueueEvent) event, eventRequest.getTimestamp());
                    break;
                case REGISTER_THREAD_WITH_LOADQUEUE:
                    registerThreadWithLoadQueue((RegisterThreadWithLoadQueueEvent) event);
                    break;
                case START_CHECKPOINT:
                    startCheckpoint((StartCheckpointEvent) event);
                    break;
                case END_CHECKPOINT:
                    endCheckpoint((EndCheckpointEvent) event);
                    break;
                case INSERT_CHECKPOINT:
                    insertCheckpoint((InsertCheckpointEvent) event);
                    break;
                case INSERT_SYSTEM_STAT:
                    insertSystemStatistics((InsertSystemStatisticEvent) event);
                    break;
                case INSERT_USER_ACTIVITY_STAT:
                    insertUserActivityStatistics((InsertUserActivityStatisticEvent) event);
                    break;
                case INSERT_MESSAGE:
                    InsertMessageEvent insertMessageEvent = (InsertMessageEvent) event;
                    insertMessage(eventRequest, insertMessageEvent.isEscapeHtml(),
                                  insertMessageEvent.isRunMessage());
                    break;
                default:
                    throw new LoggingException("Unsupported logging event of type: "
                                               + dbAppenderEvent.getEventType());
            }
        } else {
            insertMessage(eventRequest, false, false);
        }
    }

    private void startRun( StartRunEvent startRunEvent,
                           long timeStamp ) throws DatabaseAccessException {

        // this temporary map must be cleared prior to each run
        suiteIdsCache.clear();

        int previousRunId = _state.getPreviousRunId();

        int newRunId;
        if (previousRunId == 0) {
            // run sanity check first
            dbAccess.runDbSanityCheck();

            // now create a new run for first time in this JVM execution
            newRunId = dbAccess.startRun(startRunEvent.getRunName(), startRunEvent.getOsName(),
                                         startRunEvent.getProductName(), startRunEvent.getVersionName(),
                                         startRunEvent.getBuildName(), timeStamp,
                                         startRunEvent.getHostName(), true);

            //output the run id in the console, so it can be used for results
            log.info(
                     "Started a new RUN in Test Explorer's database with id: "
                     + newRunId);
        } else {
            // we already had a run, now we will join to the previous run
            // we will update the name of the run only
            dbAccess.updateRun(previousRunId, startRunEvent.getRunName(), null, null, null, null, null, null,
                               true);
            newRunId = previousRunId;

            //output the run id in the console, so it can be used for results
            log.info("Joined an existing RUN in Test Explorer's database with id: "
                     + newRunId);
        }

        /*
         * preserve the some run information
         * This is done in order to be able to execute tests in parallel
         */
        ActiveDbAppender.runState.setRunId(newRunId);
        ActiveDbAppender.runState.setRunName(startRunEvent.getRunName());
        ActiveDbAppender.runState.setRunUserNote(null);
        ActiveDbAppender.runState.setLifeCycleState(LifeCycleState.RUN_STARTED);
        
        _state.setRunId(newRunId);
        _state.setRunName(startRunEvent.getRunName());
        _state.setRunUserNote(null);
        _state.setLifeCycleState(LifeCycleState.RUN_STARTED);

        //notify the listener that the run started successfully
        if (listener != null) {
            listener.onRunStarted();
        }
    }

    private void endRun( long timeStamp ) throws DatabaseAccessException {

        int currentRunId = ActiveDbAppender.runState.getRunId();

        dbAccess.endRun(timeStamp, currentRunId, true);

        /*
         * preserve the some run information
         * This is done in order to be able to execute tests in parallel
         */
        ActiveDbAppender.runState.setRunId(0);
        ActiveDbAppender.runState.setPreviousRunId(currentRunId);
        ActiveDbAppender.runState.setLifeCycleState(LifeCycleState.INITIALIZED);
        
        _state.setRunId(0);
        _state.setPreviousRunId(currentRunId);
        _state.setLifeCycleState(LifeCycleState.INITIALIZED);

        if (listener != null) {
            listener.onRunFinished();
        }
    }

    private void updateRun( UpdateRunEvent updateRunEvent ) throws DatabaseAccessException {

        dbAccess.updateRun(_state.getRunId(), updateRunEvent.getRunName(), updateRunEvent.getOsName(),
                           updateRunEvent.getProductName(), updateRunEvent.getVersionName(),
                           updateRunEvent.getBuildName(), updateRunEvent.getUserNote(),
                           updateRunEvent.getHostName(), true);
        if (updateRunEvent.getRunName() != null) {
            _state.setRunName(updateRunEvent.getRunName());
        }
        if (updateRunEvent.getUserNote() != null) {
            _state.setRunUserNote(updateRunEvent.getUserNote());
        }
    }

    private void addRunMetainfo( AddRunMetainfoEvent addRunMetainfoEvent ) throws DatabaseAccessException {

        dbAccess.addRunMetainfo(_state.getRunId(), addRunMetainfoEvent.getMetaKey(),
                                addRunMetainfoEvent.getMetaValue(), true);
    }

    private void startSuite( StartSuiteEvent startSuiteEvent,
                             long timeStamp ) throws DatabaseAccessException {

        String suiteName = startSuiteEvent.getSuiteName();
        String packageName = startSuiteEvent.getPackage();

        int runId = _state.getRunId();

        // If there is already a suite with same name in the same run don't open a new one, but
        // use the existing suite.
        if (suiteIdsCache.containsKey(_state.getRunId() + suiteName)) {
            _state.setSuiteId(suiteIdsCache.get(_state.getRunId() + suiteName));
        } else {
            int suiteId = dbAccess.startSuite(packageName, suiteName, timeStamp, runId, true);

            //set the current suite id
            _state.setSuiteId(suiteId);

            //put the id in the cache
            suiteIdsCache.put(runId + suiteName, suiteId);
        }

        //set the current appender state
        _state.setLifeCycleState(LifeCycleState.SUITE_STARTED);
    }

    private void endSuite( long timeStamp ) throws DatabaseAccessException {

        try {
            dbAccess.endSuite(timeStamp, _state.getSuiteId(), true);

            lastEndedSuiteId = _state.getSuiteId();
        } finally {
            // even when this DB entity could not finish due to error,
            // we want to clear the internal state,
            // so next sub-entities do not go into this one
            _state.setLifeCycleState(LifeCycleState.RUN_STARTED);

            _state.setSuiteId(0);
        }
    }

    private void updateSuite( UpdateSuiteEvent event ) throws DatabaseAccessException {

        try {
            dbAccess.updateSuite(_state.getSuiteId(), event.getSuiteName(), event.getUserNote(), true);
        } finally {
            // Due to change in the suite name, update suiteIdCache, 
            // only if suite name is not null and is not an empty string
            if (!StringUtils.isNullOrEmpty(event.getSuiteName())) {
                suiteIdsCache.put(_state.getRunId() + event.getSuiteName(), _state.getSuiteId());
            }
        }
    }

    private void clearScenarioMetainfo() throws DatabaseAccessException {

        dbAccess.clearScenarioMetainfo(_state.getTestCaseId(), true);
    }

    private void
            addScenarioMetainfo( AddScenarioMetainfoEvent addScenarioMetainfoEvent ) throws DatabaseAccessException {

        dbAccess.addScenarioMetainfo(_state.getTestCaseId(), addScenarioMetainfoEvent.getMetaKey(),
                                     addScenarioMetainfoEvent.getMetaValue(), true);
    }

    private void startTestCase( StartTestCaseEvent startTestCaseEvent,
                                long timeStamp ) throws LoggingException {

        int currentSuiteId = _state.getSuiteId();

        String newSuiteName = startTestCaseEvent.getSuiteSimpleName();
        if (!StringUtils.isNullOrEmpty(newSuiteName)) {
            // Due to implementation specifics, it is not possible that a suite with
            // the needed name is not in the list of remembered suites (else clause).
            // This is guaranteed when we start the suite(which is before we start a testcase)

            // we want to make sure we are starting this testcase in a suite with this name
            String newSuiteIdentifier = _state.getRunId() + newSuiteName;

            currentSuiteId = suiteIdsCache.get(newSuiteIdentifier);
        }

        /*
         * This event happens on the Test Executor side.
         */

        int testCaseId = dbAccess.startTestCase(startTestCaseEvent.getSuiteFullName(),
                                                startTestCaseEvent.getScenarioName(),
                                                startTestCaseEvent.getScenarioDescription(),
                                                startTestCaseEvent.getTestcaseName(), timeStamp,
                                                currentSuiteId, true);

        //set the current appender state
        TestCaseState testCaseState = new TestCaseState();
        testCaseState.setTestcaseId(testCaseId);
        _state.setTestCaseState(testCaseState);
        _state.setLifeCycleState(LifeCycleState.TEST_CASE_STARTED);

        //unblock the main thread which is waiting for the completion of this event
        if (listener != null) {
            listener.onTestcaseStarted();
        }
    }

    private void endTestCase( EndTestCaseEvent endTestCaseEvent, long timeStamp ) throws LoggingException {

        try {
            if (!this.deletedTestcases.contains(_state.getTestCaseId())) {
                dbAccess.endTestCase(endTestCaseEvent.getTestCaseResult().toInt(), timeStamp,
                                     _state.getTestCaseId(), true);
            }
        } finally {
            // even when this DB entity could not finish due to error,
            // we want to clear the internal state,
            // so next sub-entities do not go into this one
            _state.setLifeCycleState(LifeCycleState.SUITE_STARTED);

            _state.getTestCaseState().setLastExecutedTestcaseId(_state.getTestCaseId());
            _state.getTestCaseState().clearTestcaseId();
            _state.getLoadQueuesState().clearAll();

            //unblock the main thread which is waiting for the completion of this event
            if (listener != null) {
                listener.onTestcaseFinished();
            }
        }
    }

    private void updateTestcase( UpdateTestcaseEvent updateTestcaseEvent,
                                 long timestamp ) throws LoggingException {

        String suiteFullName = updateTestcaseEvent.getSuiteFullName();
        String scenarioName = updateTestcaseEvent.getScenarioName();
        String scenarioDescription = updateTestcaseEvent.getScenarioDescription();
        String testcaseName = updateTestcaseEvent.getTestcaseName();
        String userNote = updateTestcaseEvent.getUserNote();
        int testcaseResult = updateTestcaseEvent.getTestcaseResult();
        /*
         * It is possible, albeit very unusual, that both testcaseId and lastExecutedTestcaseId are -1
         * Maybe an additional check is needed, before invoking dbAccess.updateTestcase()
         * */
        int testcaseId = (getTestCaseId() == -1)
                                                 ? getLastExecutedTestCaseId()
                                                 : getTestCaseId();

        dbAccess.updateTestcase(suiteFullName, scenarioName, scenarioDescription, testcaseName, userNote,
                                testcaseResult, testcaseId, timestamp, true);

    }

    /**
     * Remember a testcase that is to be deleted
     *
     * @param testcaseId
     */
    public void requestTestcaseDeletion( int testcaseId ) {

        /*
         * This code runs on the Test Executor side
         */

        this.testcaseToDelete = testcaseId;
    }

    private void deleteRequestedTestcase() throws DatabaseAccessException {

        /*
         * This code runs on the Test Executor side
         */

        // delete this testcase
        List<Object> testcasesToDelete = new ArrayList<Object>();
        Testcase tc = new Testcase();
        tc.testcaseId = String.valueOf(testcaseToDelete);
        testcasesToDelete.add(tc);

        dbAccess.deleteTestcase(testcasesToDelete);

        /*
         * Remember this testcase was deleted.
         * From now on all events related to this testcase will be simply skipped without going to the DB
         */
        deletedTestcases.add(testcaseToDelete);

        testcaseToDelete = -1;
    }

    private void joinTestCase( JoinTestCaseEvent joinTestCaseEvent ) throws LoggingException {

        /*
         * This event happens on the Agent side.
         */

        //set test case id
        _state.setTestCaseState(joinTestCaseEvent.getTestCaseState());

        //set the current appender state
        _state.setLifeCycleState(LifeCycleState.TEST_CASE_STARTED);

        //set the run id
        _state.setRunId(joinTestCaseEvent.getTestCaseState().getRunId());

        // set the run id to the run state as well
        _state.setRunId(joinTestCaseEvent.getTestCaseState().getRunId());

        /*
         * Now the Agent can log into this test case
         */
    }

    private void leaveTestCase( String threadId ) {

        /*
         * This event happens on the Agent side.
         */
        _state.getTestCaseState().clearTestcaseId();

        //set the current appender state
        _state.setLifeCycleState(LifeCycleState.INITIALIZED);
        /*
         * The Agent can no longer log into this test case
         */

        if (threadId.equals(getThreadId("main"))) {
            // Tests are running one after another.
            // We leave this logging thread alive for the future test(if there is such).

            // NOTE: It will not hurt if we throw 'thread is over' exception here.
            // In such case the logger thread will exit on every 'test leave' event and we will start
            // a new thread on every 'join test' event
        } else {
            // Test are running in parallel.
            // In order to reduce the number of logger threads, we will exit this thread
            throw new LoggingIsOverException("Logging is over for <" + ThreadsPerCaller.getCaller()
                                             + ">. Loging thread "
                                             + Thread.currentThread().getName()
                                             + " will stop working now.");
        }
    }

    private void
            rememberLoadQueueState( RememberLoadQueueStateEvent startLoadQueueEvent ) throws LoadQueueAlreadyStartedException {

        //first check if this load queue has already been started, just in case
        LoadQueuesState loadQueuesState = _state.getLoadQueuesState();

        String loadQueueName = startLoadQueueEvent.getName();
        if (loadQueuesState.isLoadQueueRunning(loadQueueName)) {
            throw new LoadQueueAlreadyStartedException(loadQueueName);
        }

        //cache the load queue id
        loadQueuesState.addLoadQueue(loadQueueName, startLoadQueueEvent.getLoadQueueId());
    }

    private void
            cleanupLoadQueueState( CleanupLoadQueueStateEvent loadQueueStateEvent ) throws NoSuchLoadQueueException {

        //first check if this load queue is started at all
        LoadQueuesState loadQueuesState = _state.getLoadQueuesState();
        String loadQueueName = loadQueueStateEvent.getName();
        int loadQueueId = loadQueuesState.getLoadQueueId(loadQueueName);
        loadQueuesState.removeLoadQueue(loadQueueName, loadQueueId);
    }

    private void endLoadQueue( EndLoadQueueEvent endLoadQueueEvent,
                               long timestamp ) throws NoSuchLoadQueueException, DatabaseAccessException {

        //first check if this load queue is started at all
        LoadQueuesState loadQueuesState = _state.getLoadQueuesState();
        String loadQueueName = endLoadQueueEvent.getName();

        int loadQueueId = loadQueuesState.getLoadQueueId(loadQueueName);

        if (loadQueueId > 0) {
            try {

                dbAccess.endLoadQueue(endLoadQueueEvent.getResult().toInt(), timestamp, loadQueueId, true);
            } finally {
                // even when this DB entity could not finish due to error,
                // we want to clear the internal state,
                // so next sub-entities do not go into this one
                loadQueuesState.removeLoadQueue(loadQueueName, loadQueueId);
            }
        }
    }

    private void
            registerThreadWithLoadQueue( RegisterThreadWithLoadQueueEvent registerThreadWithLoadQueueEvent ) throws NoSuchLoadQueueException,
                                                                                                             ThreadAlreadyRegisteredWithLoadQueueException,
                                                                                                             ThreadNotRegisteredWithLoadQueue {

        LoadQueuesState loadQueuesState = _state.getLoadQueuesState();
        String loadQueueName = registerThreadWithLoadQueueEvent.getLoadQueueName();

        //register the thread with the load queue - this way when a checkpoint comes in, we will
        //now which load queue it belongs to
        loadQueuesState.registerThreadWithLoadQueue(registerThreadWithLoadQueueEvent.getThreadName(),
                                                    loadQueuesState.getLoadQueueId(loadQueueName));
    }

    private void startCheckpoint( StartCheckpointEvent startCheckpointEvent ) throws LoggingException {

        //check if checkpoints are enabled at all
        if (appenderConfig.getEnableCheckpoints()) {

            LoadQueuesState loadQueuesState = _state.getLoadQueuesState();
            int loadQueueId = loadQueuesState.getLoadQueueIdForThread(startCheckpointEvent.getThread());

            if (loadQueueId > 0) {
                final int testcaseId = _state.getTestCaseId();
                if (!deletedTestcases.contains(testcaseId)) {
                    try {
                        CheckpointInfo startedCheckpointInfo = dbAccess.startCheckpoint(startCheckpointEvent.getName(),
                                                                                        startCheckpointEvent.getThread(),
                                                                                        startCheckpointEvent.getStartTimestamp(),
                                                                                        startCheckpointEvent.getTransferUnit(),
                                                                                        loadQueueId, true);
                        loadQueuesState.startCheckpoint(startedCheckpointInfo,
                                                        startCheckpointEvent.getThread());

                    } catch (LoggingException e) {
                        handleDeletedTestcase(e, testcaseId);
                    }
                }
            }
        }
    }

    private void endCheckpoint( EndCheckpointEvent endCheckpointEvent ) throws LoggingException {

        //check if checkpoints are enabled at all
        if (appenderConfig.getEnableCheckpoints()) {

            LoadQueuesState loadQueuesState = _state.getLoadQueuesState();
            CheckpointInfo runningCheckpointInfo = loadQueuesState.endCheckpoint(endCheckpointEvent.getThread(),
                                                                                 endCheckpointEvent.getName(),
                                                                                 endCheckpointEvent.getEndTimestamp());

            final int testcaseId = _state.getTestCaseId();
            if (!deletedTestcases.contains(testcaseId)) {
                try {
                    dbAccess.endCheckpoint(runningCheckpointInfo, endCheckpointEvent.getEndTimestamp(),
                                           endCheckpointEvent.getTransferSize(),
                                           endCheckpointEvent.getResult().toInt(), true);
                } catch (LoggingException e) {
                    handleDeletedTestcase(e, testcaseId);
                }
            }
        }
    }

    private void insertCheckpoint( InsertCheckpointEvent insertCheckpointEvent ) throws LoggingException {

        //check if checkpoints are enabled at all
        if (appenderConfig.getEnableCheckpoints()) {

            LoadQueuesState loadQueuesState = _state.getLoadQueuesState();
            int loadQueueId = loadQueuesState.getLoadQueueIdForThread(insertCheckpointEvent.getThread());

            final int testcaseId = _state.getTestCaseId();
            if (!deletedTestcases.contains(testcaseId)) {
                try {
                    dbAccess.insertCheckpoint(insertCheckpointEvent.getName(),
                                              insertCheckpointEvent.getStartTimestamp(),
                                              insertCheckpointEvent.getResponseTime(),
                                              insertCheckpointEvent.getTransferSize(),
                                              insertCheckpointEvent.getTransferUnit(),
                                              insertCheckpointEvent.getResult().toInt(), loadQueueId, true);
                } catch (LoggingException e) {
                    handleDeletedTestcase(e, testcaseId);
                }
            }
        }
    }

    private void
            insertSystemStatistics( InsertSystemStatisticEvent insertSystemStatEvent ) throws LoggingException {

        final int testcaseId = _state.getTestCaseId();
        if (!deletedTestcases.contains(testcaseId)) {
            try {
                dbAccess.insertSystemStatistics(_state.getTestCaseId(),
                                                insertSystemStatEvent.getMonitoredMachine(),
                                                insertSystemStatEvent.getStatisticIds(),
                                                insertSystemStatEvent.getStatisticValues(),
                                                insertSystemStatEvent.getTimestamp(), true);
            } catch (LoggingException e) {
                handleDeletedTestcase(e, testcaseId);
            }
        }
    }

    private void
            insertUserActivityStatistics( InsertUserActivityStatisticEvent insertUserActivityStatEvent ) throws LoggingException {

        final int testcaseId = _state.getTestCaseId();
        if (!deletedTestcases.contains(testcaseId)) {
            try {
                dbAccess.insertUserActivityStatistics(_state.getTestCaseId(),
                                                      insertUserActivityStatEvent.getMonitoredMachine(),
                                                      insertUserActivityStatEvent.getStatisticIds(),
                                                      insertUserActivityStatEvent.getStatisticValues(),
                                                      insertUserActivityStatEvent.getTimestamp(), true);
            } catch (LoggingException e) {
                handleDeletedTestcase(e, testcaseId);
            }
        }
    }

    private void insertMessage( LogEventRequest eventRequest, boolean escapeHtml,
                                boolean isRunMessage ) throws LoggingException {

        LoggingEvent event = eventRequest.getEvent();

        // If test case is not open, just return - this is necessary because components which are not aware of this
        // appender may try to log before the client has a chance of opening a test case.
        if ( (_state.getLifeCycleState() == LifeCycleState.RUN_STARTED && !afterClassMode) || isRunMessage
             || afterSuiteMode) {
            if (!ActiveDbAppender.isBeforeAndAfterMessagesLoggingSupported) {
                return;
            }
            int runId = _state.getRunId();
            if (isRunMessage && _state.getTestCaseState().getRunId() != 0) {
                runId = _state.getTestCaseState().getRunId();
            }
            Level level = event.getLevel();
            try {
                dbAccess.insertRunMessage(getLoggingMesage(event), convertMsgLevel(level), escapeHtml,
                                          machineName, eventRequest.getThreadName(),
                                          eventRequest.getTimestamp(), runId, true);
            } catch (LoggingException e) {
                handleDeletedRun(e, runId);
            }
        } else if (_state.getLifeCycleState() == LifeCycleState.TEST_CASE_STARTED || afterMethodMode) {

            final int testcaseId = (afterMethodMode)
                                                     ? getLastExecutedTestCaseId()
                                                     : _state.getTestCaseId();
            if (!deletedTestcases.contains(testcaseId)) {
                Level level = event.getLevel();
                try {
                    dbAccess.insertMessage(getLoggingMesage(event), convertMsgLevel(level), escapeHtml,
                                           machineName, eventRequest.getThreadName(),
                                           eventRequest.getTimestamp(), testcaseId, true);
                } catch (LoggingException e) {
                    handleDeletedTestcase(e, testcaseId);
                }
            }
        } else if (_state.getLifeCycleState() == LifeCycleState.SUITE_STARTED || afterClassMode) {
            if (!ActiveDbAppender.isBeforeAndAfterMessagesLoggingSupported) {
                return;
            }
            final int suiteId = (afterClassMode)
                                                 ? lastEndedSuiteId
                                                 : _state.getSuiteId();
            Level level = event.getLevel();
            try {
                dbAccess.insertSuiteMessage(getLoggingMesage(event), convertMsgLevel(level), escapeHtml,
                                            machineName, eventRequest.getThreadName(),
                                            eventRequest.getTimestamp(), suiteId, true);
            } catch (LoggingException e) {
                handleDeletedSuite(e, suiteId);
            }

        }
    }

    private String getLoggingMesage( LoggingEvent event ) {

        Throwable throwable = null;
        ThrowableInformation throwableInfo = event.getThrowableInformation();
        if (throwableInfo != null && throwableInfo.getThrowable() != null) {
            // logging through methods like error(new Exception);
            throwable = throwableInfo.getThrowable();
        } else if (event.getMessage() instanceof Throwable) {
            // logging through methods like error("some message", new Exception);
            throwable = (Throwable) event.getMessage();
        }

        // first format the message using the layout
        String message = layout.format(event);
        // then append the exception stack trace
        if (throwable != null) {
            message = getExceptionMsg(throwable, message);
        }

        return message;

    }

    private void handleDeletedRun( LoggingException e, int runId ) throws LoggingException {

        Throwable cause = e.getCause();
        if (cause != null && cause instanceof SQLException
            && cause.getMessage()
                    .contains("The INSERT statement conflicted with the FOREIGN KEY constraint")) {

            // Check if this error is caused due to deleted run
            if (!dbAccess.isRunPresent(runId)) {
                // This run seems to be deleted
            }
        } else {
            throw e;
        }
    }

    private void handleDeletedSuite( LoggingException e, int suiteId ) throws LoggingException {

        Throwable cause = e.getCause();
        if (cause != null && cause instanceof SQLException
            && cause.getMessage()
                    .contains("The INSERT statement conflicted with the FOREIGN KEY constraint")) {

            // Check if this error is caused due to deleted suite
            if (!dbAccess.isSuitePresent(suiteId)) {
                // This suite seems to be deleted
            }
        } else {
            throw e;
        }
    }

    private void handleDeletedTestcase( LoggingException e, int testcaseId ) throws LoggingException {

        Throwable cause = e.getCause();
        if (cause != null && cause instanceof SQLException
            && cause.getMessage()
                    .contains("The INSERT statement conflicted with the FOREIGN KEY constraint")) {

            // Check if this error is caused due to deleted testcase
            if (!dbAccess.isTestcasePresent(testcaseId)) {
                // This testcase seems to be deleted, mark it as deleted.
                // On the Agent side, this is the normal way to understand a testcase was deleted.
                deletedTestcases.add(testcaseId);
            }
        } else {
            throw e;
        }
    }

    private int convertMsgLevel( org.apache.log4j.Level level ) {

        switch (level.toInt()) {
            case Level.FATAL_INT:
                return 1;
            case Level.ERROR_INT:
                return 2;
            case Level.WARN_INT:
                return 3;
            case Level.INFO_INT:
                return 4;
            case Level.DEBUG_INT:
                return 5;
            case Level.TRACE_INT:
                return 6;
            case SystemLogLevel.SYSTEM_INT:
                return 7;
            default:
                return 4;
        }
    }

    private String getExceptionMsg( Throwable throwable, String usrMsg ) {

        StringBuffer msg = new StringBuffer();
        if (throwable != null) {
            msg.append("EXCEPTION\n");

            if (usrMsg != null && usrMsg.length() > 0) {
                msg.append("USER message:\n\t");
                msg.append(usrMsg);
                msg.append("\n");
            }

            msg.append("\nCALL STACK:");
            msg.append(getStackTraceInfo(throwable));
        }

        return msg.toString();
    }

    private String getStackTraceInfo( Throwable throwable ) {

        final Writer writer = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(writer);
        throwable.printStackTrace(printWriter);

        return writer.toString();
    }

    private void constructUpdateRunEvent() {

        try {

            if (userProvidedUpdateRunEvent != null) {

                // get current run info from database
                Run run = getLatestRun();

                // replace the missing fields, received by the latest UpdateRunEvent with the one from the DB
                String runName = (userProvidedUpdateRunEvent.getRunName() != null)
                                                                                   ? userProvidedUpdateRunEvent.getRunName()
                                                                                   : run.runName;
                String osName = (userProvidedUpdateRunEvent.getOsName() != null)
                                                                                 ? userProvidedUpdateRunEvent.getOsName()
                                                                                 : run.os;
                String productName = (userProvidedUpdateRunEvent.getProductName() != null)
                                                                                           ? userProvidedUpdateRunEvent.getProductName()
                                                                                           : run.productName;
                String versionName = (userProvidedUpdateRunEvent.getVersionName() != null)
                                                                                           ? userProvidedUpdateRunEvent.getVersionName()
                                                                                           : run.versionName;
                String buildName = (userProvidedUpdateRunEvent.getBuildName() != null)
                                                                                       ? userProvidedUpdateRunEvent.getBuildName()
                                                                                       : run.buildName;
                String userNote = (userProvidedUpdateRunEvent.getUserNote() != null)
                                                                                     ? userProvidedUpdateRunEvent.getUserNote()
                                                                                     : run.userNote;
                String hostName = (userProvidedUpdateRunEvent.getHostName() != null)
                                                                                     ? userProvidedUpdateRunEvent.getHostName()
                                                                                     : run.hostName;

                // construct the new pending UpdateRunEvent
                actualUpdateRunEvent = new UpdateRunEvent(userProvidedUpdateRunEvent.getFQNOfLoggerClass(),
                                                          (Logger) userProvidedUpdateRunEvent.getLogger(),
                                                          runName, osName, productName, versionName,
                                                          buildName, userNote, hostName);

            }

        } catch (SQLException e) {
            /*  
             * Could not obtain run info from database.
             * The exception will be logged to the console only, because the event processor is busy handling the UpdateRunEvent
             * and will not be able to handle the log message event as well
            */
            new AtsConsoleLogger(getClass()).error("Unable to update run with ID '" + _state.getRunId() + "'", e);
        }

    }

    private Run getLatestRun() throws SQLException {

        PreparedStatement stmt = null;

        Run run = new Run();

        Connection tmpConn = null;

        try {

            tmpConn = ConnectionPool.getConnection(dbConnection);
            stmt = tmpConn.prepareStatement("SELECT * FROM tRuns WHERE runId=" + _state.getRunId());
            if (dbConnection instanceof DbConnPostgreSQL) {
                stmt = tmpConn.prepareStatement("SELECT * FROM \"tRuns\" WHERE runId=" + _state.getRunId());
            }
            ResultSet rs = stmt.executeQuery();
            rs.next();
            run.runId = rs.getString("runId");
            run.productName = rs.getString("productName");
            run.versionName = rs.getString("versionName");
            run.buildName = rs.getString("buildName");
            run.runName = rs.getString("runName");
            run.os = rs.getString("OS");
            run.hostName = rs.getString("hostName");
            if (run.hostName == null) {
                run.hostName = "";
            }
            run.userNote = rs.getString("userNote");
            if (run.userNote == null) {
                run.userNote = "";
            }

        } finally {
            DbUtils.closeConnection(tmpConn);
            DbUtils.closeStatement(stmt);
        }

        return run;
    }

    public String getThreadId( String threadName ) {

        Thread[] threads = new Thread[Thread.activeCount() * 2];
        Thread.enumerate(threads);
        for (Thread t : threads) {
            if (t != null) {
                if (t.getName().equals(threadName)) {
                    return t.getId() + "";
                }
            }
        }
        return null;
    }
}
