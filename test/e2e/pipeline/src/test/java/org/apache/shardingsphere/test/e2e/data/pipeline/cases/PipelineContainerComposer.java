/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.test.e2e.data.pipeline.cases;

import com.google.common.base.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.data.pipeline.api.job.JobStatus;
import org.apache.shardingsphere.data.pipeline.core.util.ThreadUtil;
import org.apache.shardingsphere.data.pipeline.spi.job.JobType;
import org.apache.shardingsphere.driver.api.yaml.YamlShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.database.metadata.url.JdbcUrlAppender;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.infra.util.yaml.YamlEngine;
import org.apache.shardingsphere.infra.yaml.config.pojo.YamlRootConfiguration;
import org.apache.shardingsphere.test.e2e.data.pipeline.cases.base.BaseIncrementTask;
import org.apache.shardingsphere.test.e2e.data.pipeline.command.ExtraSQLCommand;
import org.apache.shardingsphere.test.e2e.data.pipeline.env.PipelineE2EEnvironment;
import org.apache.shardingsphere.test.e2e.data.pipeline.env.enums.PipelineEnvTypeEnum;
import org.apache.shardingsphere.test.e2e.data.pipeline.framework.container.compose.BaseContainerComposer;
import org.apache.shardingsphere.test.e2e.data.pipeline.framework.container.compose.DockerContainerComposer;
import org.apache.shardingsphere.test.e2e.data.pipeline.framework.container.compose.NativeContainerComposer;
import org.apache.shardingsphere.test.e2e.data.pipeline.framework.param.PipelineTestParameter;
import org.apache.shardingsphere.test.e2e.env.container.atomic.constants.ProxyContainerConstants;
import org.apache.shardingsphere.test.e2e.env.container.atomic.storage.DockerStorageContainer;
import org.apache.shardingsphere.test.e2e.env.container.atomic.util.DatabaseTypeUtil;
import org.apache.shardingsphere.test.e2e.env.container.atomic.util.StorageContainerUtil;
import org.apache.shardingsphere.test.e2e.env.runtime.DataSourceEnvironment;
import org.apache.shardingsphere.test.util.PropertiesBuilder;
import org.apache.shardingsphere.test.util.PropertiesBuilder.Property;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import javax.sql.DataSource;
import javax.xml.bind.JAXB;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pipeline container composer.
 */
@Getter
@Slf4j
public final class PipelineContainerComposer implements AutoCloseable {
    
    public static final String SCHEMA_NAME = "test";
    
    public static final String PROXY_DATABASE = "sharding_db";
    
    public static final String DS_0 = "pipeline_it_0";
    
    public static final String DS_1 = "pipeline_it_1";
    
    public static final String DS_2 = "pipeline_it_2";
    
    public static final String DS_3 = "pipeline_it_3";
    
    public static final String DS_4 = "pipeline_it_4";
    
    public static final int TABLE_INIT_ROW_COUNT = 3000;
    
    private final BaseContainerComposer containerComposer;
    
    private final ExtraSQLCommand extraSQLCommand;
    
    private final DatabaseType databaseType;
    
    private final String username;
    
    private final String password;
    
    private DataSource sourceDataSource;
    
    private DataSource proxyDataSource;
    
    private Thread increaseTaskThread;
    
    public PipelineContainerComposer(final PipelineTestParameter testParam) {
        databaseType = testParam.getDatabaseType();
        containerComposer = PipelineE2EEnvironment.getInstance().getItEnvType() == PipelineEnvTypeEnum.DOCKER
                ? new DockerContainerComposer(testParam.getDatabaseType(), testParam.getStorageContainerImage(), testParam.getStorageContainerCount())
                : new NativeContainerComposer(testParam.getDatabaseType());
        if (PipelineE2EEnvironment.getInstance().getItEnvType() == PipelineEnvTypeEnum.DOCKER) {
            DockerStorageContainer storageContainer = ((DockerContainerComposer) containerComposer).getStorageContainers().get(0);
            username = storageContainer.getUsername();
            password = storageContainer.getPassword();
        } else {
            username = PipelineE2EEnvironment.getInstance().getActualDataSourceUsername(databaseType);
            password = PipelineE2EEnvironment.getInstance().getActualDataSourcePassword(databaseType);
        }
        extraSQLCommand = JAXB.unmarshal(Objects.requireNonNull(PipelineContainerComposer.class.getClassLoader().getResource(testParam.getScenario())), ExtraSQLCommand.class);
        containerComposer.start();
    }
    
    /**
     * Initialize environment.
     * 
     * @param databaseType database type
     * @param jobType job type
     * @throws SQLException SQL exception
     */
    public void initEnvironment(final DatabaseType databaseType, final JobType jobType) throws SQLException {
        sourceDataSource = StorageContainerUtil.generateDataSource(appendExtraParam(getActualJdbcUrlTemplate(DS_0, false)), username, password);
        proxyDataSource = StorageContainerUtil.generateDataSource(appendExtraParam(containerComposer.getProxyJdbcUrl(PROXY_DATABASE)),
                ProxyContainerConstants.USERNAME, ProxyContainerConstants.PASSWORD);
        String defaultDatabaseName = "";
        if (DatabaseTypeUtil.isPostgreSQL(databaseType) || DatabaseTypeUtil.isOpenGauss(databaseType)) {
            defaultDatabaseName = "postgres";
        }
        String jdbcUrl = containerComposer.getProxyJdbcUrl(defaultDatabaseName);
        try (Connection connection = DriverManager.getConnection(jdbcUrl, ProxyContainerConstants.USERNAME, ProxyContainerConstants.PASSWORD)) {
            cleanUpPipelineJobs(connection, jobType);
            cleanUpProxyDatabase(connection);
            createProxyDatabase(connection);
        }
        cleanUpDataSource();
    }
    
    /**
     * Append extra parameter.
     * 
     * @param jdbcUrl JDBC URL
     * @return appended JDBC URL
     */
    public String appendExtraParam(final String jdbcUrl) {
        String result = jdbcUrl;
        if (DatabaseTypeUtil.isMySQL(getDatabaseType())) {
            result = new JdbcUrlAppender().appendQueryProperties(jdbcUrl, PropertiesBuilder.build(new Property("rewriteBatchedStatements", Boolean.TRUE.toString())));
        }
        if (DatabaseTypeUtil.isPostgreSQL(getDatabaseType()) || DatabaseTypeUtil.isOpenGauss(getDatabaseType())) {
            result = new JdbcUrlAppender().appendQueryProperties(jdbcUrl, PropertiesBuilder.build(new Property("stringtype", "unspecified")));
        }
        return result;
    }
    
    private void cleanUpProxyDatabase(final Connection connection) {
        if (PipelineEnvTypeEnum.NATIVE != PipelineE2EEnvironment.getInstance().getItEnvType()) {
            return;
        }
        try {
            connection.createStatement().execute(String.format("DROP DATABASE IF EXISTS %s", PROXY_DATABASE));
            ThreadUtil.sleep(2, TimeUnit.SECONDS);
        } catch (final SQLException ex) {
            log.warn("Drop proxy database failed, error={}", ex.getMessage());
        }
    }
    
    private void cleanUpPipelineJobs(final Connection connection, final JobType jobType) throws SQLException {
        if (PipelineEnvTypeEnum.NATIVE != PipelineE2EEnvironment.getInstance().getItEnvType()) {
            return;
        }
        String jobTypeName = jobType.getTypeName();
        List<Map<String, Object>> jobList;
        try {
            ResultSet resultSet = connection.createStatement().executeQuery(String.format("SHOW %s LIST", jobTypeName));
            jobList = transformResultSetToList(resultSet);
        } catch (final SQLException ex) {
            log.warn("{} execute failed, message {}", String.format("SHOW %s LIST", jobTypeName), ex.getMessage());
            return;
        }
        if (jobList.isEmpty()) {
            return;
        }
        for (Map<String, Object> each : jobList) {
            String jobId = each.get("id").toString();
            Map<String, Object> jobInfo = queryForListWithLog(String.format("SHOW %s STATUS '%s'", jobTypeName, jobId)).get(0);
            String status = jobInfo.get("status").toString();
            if (JobStatus.FINISHED.name().equals(status)) {
                connection.createStatement().execute(String.format("COMMIT %s '%s'", jobTypeName, jobId));
            } else {
                connection.createStatement().execute(String.format("ROLLBACK %s '%s'", jobTypeName, jobId));
            }
        }
    }
    
    private void cleanUpDataSource() {
        if (PipelineEnvTypeEnum.NATIVE != PipelineE2EEnvironment.getInstance().getItEnvType()) {
            return;
        }
        for (String each : Arrays.asList(DS_0, DS_1, DS_2, DS_3, DS_4)) {
            containerComposer.cleanUpDatabase(each);
        }
    }
    
    private void createProxyDatabase(final Connection connection) throws SQLException {
        String sql = String.format("CREATE DATABASE %s", PROXY_DATABASE);
        log.info("create proxy database {}", PROXY_DATABASE);
        connection.createStatement().execute(sql);
        ThreadUtil.sleep(2, TimeUnit.SECONDS);
    }
    
    /**
     * Register storage unit.
     * 
     * @param storageUnitName storage unit name
     * @throws SQLException SQL exception
     */
    public void registerStorageUnit(final String storageUnitName) throws SQLException {
        String registerStorageUnitTemplate = "REGISTER STORAGE UNIT ${ds} ( URL='${url}', USER='${user}', PASSWORD='${password}')".replace("${ds}", storageUnitName)
                .replace("${user}", getUsername())
                .replace("${password}", getPassword())
                .replace("${url}", appendExtraParam(getActualJdbcUrlTemplate(storageUnitName, true)));
        proxyExecuteWithLog(registerStorageUnitTemplate, 2);
    }
    
    /**
     * Add resource.
     * 
     * @param distSQL dist SQL
     * @throws SQLException SQL exception
     */
    // TODO Use registerStorageUnit instead, and remove the method
    public void addResource(final String distSQL) throws SQLException {
        proxyExecuteWithLog(distSQL, 2);
    }
    
    /**
     * Get actual JDBC URL template.
     * 
     * @param databaseName database name
     * @param isInContainer is in container
     * @param storageContainerIndex storage container index
     * @return actual JDBC URL template
     */
    public String getActualJdbcUrlTemplate(final String databaseName, final boolean isInContainer, final int storageContainerIndex) {
        if (PipelineEnvTypeEnum.DOCKER == PipelineE2EEnvironment.getInstance().getItEnvType()) {
            DockerStorageContainer storageContainer = ((DockerContainerComposer) containerComposer).getStorageContainers().get(storageContainerIndex);
            return isInContainer
                    ? DataSourceEnvironment.getURL(getDatabaseType(), storageContainer.getNetworkAliases().get(0), storageContainer.getExposedPort(), databaseName)
                    : storageContainer.getJdbcUrl(databaseName);
        }
        return DataSourceEnvironment.getURL(getDatabaseType(), "127.0.0.1", PipelineE2EEnvironment.getInstance().getActualDataSourceDefaultPort(databaseType), databaseName);
    }
    
    /**
     * Get actual JDBC URL template.
     * 
     * @param databaseName database name
     * @param isInContainer is in container
     * @return actual JDBC URL template
     */
    public String getActualJdbcUrlTemplate(final String databaseName, final boolean isInContainer) {
        return getActualJdbcUrlTemplate(databaseName, isInContainer, 0);
    }
    
    /**
     * Get target table order name.
     * 
     * @return target table order name
     */
    public String getTargetTableOrderName() {
        return "t_order";
    }
    
    /**
     * Create schema.
     * 
     * @param connection connection
     * @param sleepSeconds sleep seconds
     * @throws SQLException SQL exception
     */
    public void createSchema(final Connection connection, final int sleepSeconds) throws SQLException {
        if (!getDatabaseType().isSchemaAvailable()) {
            return;
        }
        connection.createStatement().execute(String.format("CREATE SCHEMA %s", SCHEMA_NAME));
        if (sleepSeconds > 0) {
            ThreadUtil.sleep(sleepSeconds, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Create source order table.
     * 
     * @param sourceTableOrderName source table order name 
     * @throws SQLException SQL exception
     */
    public void createSourceOrderTable(final String sourceTableOrderName) throws SQLException {
        sourceExecuteWithLog(getExtraSQLCommand().getCreateTableOrder(sourceTableOrderName));
    }
    
    /**
     * Create source table index list.
     * 
     * @param schema schema
     * @param sourceTableOrderName source table order name
     * @throws SQLException SQL exception
     */
    public void createSourceTableIndexList(final String schema, final String sourceTableOrderName) throws SQLException {
        if (DatabaseTypeUtil.isPostgreSQL(getDatabaseType())) {
            sourceExecuteWithLog(String.format("CREATE INDEX IF NOT EXISTS idx_user_id ON %s.%s ( user_id )", schema, sourceTableOrderName));
        } else if (DatabaseTypeUtil.isOpenGauss(getDatabaseType())) {
            sourceExecuteWithLog(String.format("CREATE INDEX idx_user_id ON %s.%s ( user_id )", schema, sourceTableOrderName));
        }
    }
    
    /**
     * Create source comment on list.
     * 
     * @param schema schema
     * @param sourceTableOrderName source table order name
     * @throws SQLException SQL exception
     */
    public void createSourceCommentOnList(final String schema, final String sourceTableOrderName) throws SQLException {
        sourceExecuteWithLog(String.format("COMMENT ON COLUMN %s.%s.user_id IS 'user id'", schema, sourceTableOrderName));
    }
    
    /**
     * Create source order item table.
     * 
     * @throws SQLException SQL exception
     */
    public void createSourceOrderItemTable() throws SQLException {
        sourceExecuteWithLog(extraSQLCommand.getCreateTableOrderItem());
    }
    
    /**
     * Source execute with log.
     * 
     * @param sql SQL
     * @throws SQLException SQL exception
     */
    public void sourceExecuteWithLog(final String sql) throws SQLException {
        log.info("source execute :{}", sql);
        try (Connection connection = sourceDataSource.getConnection()) {
            connection.createStatement().execute(sql);
        }
    }
    
    /**
     * Proxy execute with log.
     * 
     * @param sql SQL
     * @param sleepSeconds sleep seconds
     * @throws SQLException SQL exception
     */
    public void proxyExecuteWithLog(final String sql, final int sleepSeconds) throws SQLException {
        log.info("proxy execute :{}", sql);
        try (Connection connection = proxyDataSource.getConnection()) {
            connection.createStatement().execute(sql);
        }
        ThreadUtil.sleep(Math.max(sleepSeconds, 0), TimeUnit.SECONDS);
    }
    
    /**
     * Wait job prepare success.
     * 
     * @param distSQL dist SQL
     */
    public void waitJobPrepareSuccess(final String distSQL) {
        for (int i = 0; i < 5; i++) {
            List<Map<String, Object>> jobStatus = queryForListWithLog(distSQL);
            Set<String> statusSet = jobStatus.stream().map(each -> String.valueOf(each.get("status"))).collect(Collectors.toSet());
            if (statusSet.contains(JobStatus.PREPARING.name()) || statusSet.contains(JobStatus.RUNNING.name())) {
                ThreadUtil.sleep(2, TimeUnit.SECONDS);
            }
        }
    }
    
    /**
     * Query for list with log.
     * 
     * @param sql SQL
     * @return query result
     */
    public List<Map<String, Object>> queryForListWithLog(final String sql) {
        int retryNumber = 0;
        while (retryNumber <= 3) {
            try (Connection connection = proxyDataSource.getConnection()) {
                ResultSet resultSet = connection.createStatement().executeQuery(sql);
                return transformResultSetToList(resultSet);
            } catch (final SQLException ex) {
                log.error("data access error", ex);
            }
            ThreadUtil.sleep(3, TimeUnit.SECONDS);
            retryNumber++;
        }
        throw new RuntimeException("can't get result from proxy");
    }
    
    /**
     * Transform result set to list.
     * 
     * @param resultSet result set
     * @return transformed result
     * @throws SQLException SQL exception
     */
    public List<Map<String, Object>> transformResultSetToList(final ResultSet resultSet) throws SQLException {
        ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
        int columns = resultSetMetaData.getColumnCount();
        List<Map<String, Object>> result = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columns; i++) {
                row.put(resultSetMetaData.getColumnLabel(i).toLowerCase(), resultSet.getObject(i));
            }
            result.add(row);
        }
        return result;
    }
    
    /**
     * Start increment task.
     * 
     * @param baseIncrementTask base increment task
     */
    public void startIncrementTask(final BaseIncrementTask baseIncrementTask) {
        increaseTaskThread = new Thread(baseIncrementTask);
        increaseTaskThread.start();
    }
    
    /**
     * Wait increment task finished.
     * 
     * @param distSQL dist SQL
     * @return result
     * @throws InterruptedException interrupted exception
     */
    // TODO use DAO to query via DistSQL
    public List<Map<String, Object>> waitIncrementTaskFinished(final String distSQL) throws InterruptedException {
        if (null != increaseTaskThread) {
            TimeUnit.SECONDS.timedJoin(increaseTaskThread, 30);
        }
        for (int i = 0; i < 10; i++) {
            List<Map<String, Object>> listJobStatus = queryForListWithLog(distSQL);
            log.info("show status result: {}", listJobStatus);
            Set<String> actualStatus = new HashSet<>();
            Collection<Integer> incrementalIdleSecondsList = new LinkedList<>();
            for (Map<String, Object> each : listJobStatus) {
                assertTrue(Strings.isNullOrEmpty(each.get("error_message").toString()), "error_message is not null");
                actualStatus.add(each.get("status").toString());
                String incrementalIdleSeconds = each.get("incremental_idle_seconds").toString();
                incrementalIdleSecondsList.add(Strings.isNullOrEmpty(incrementalIdleSeconds) ? 0 : Integer.parseInt(incrementalIdleSeconds));
            }
            assertFalse(actualStatus.contains(JobStatus.PREPARING_FAILURE.name()), "status is JobStatus.PREPARING_FAILURE");
            assertFalse(actualStatus.contains(JobStatus.EXECUTE_INVENTORY_TASK_FAILURE.name()), "status is JobStatus.EXECUTE_INVENTORY_TASK_FAILURE");
            assertFalse(actualStatus.contains(JobStatus.EXECUTE_INCREMENTAL_TASK_FAILURE.name()), "status is JobStatus.EXECUTE_INCREMENTAL_TASK_FAILURE");
            if (Collections.min(incrementalIdleSecondsList) <= 5) {
                ThreadUtil.sleep(3, TimeUnit.SECONDS);
                continue;
            }
            if (actualStatus.size() == 1 && actualStatus.contains(JobStatus.EXECUTE_INCREMENTAL_TASK.name())) {
                return listJobStatus;
            }
        }
        return Collections.emptyList();
    }
    
    /**
     * Assert proxy order record exist.
     * 
     * @param tableName table name
     * @param orderId order id
     */
    public void assertProxyOrderRecordExist(final String tableName, final Object orderId) {
        String sql;
        if (orderId instanceof String) {
            sql = String.format("SELECT 1 FROM %s WHERE order_id = '%s'", tableName, orderId);
        } else {
            sql = String.format("SELECT 1 FROM %s WHERE order_id = %s", tableName, orderId);
        }
        assertProxyOrderRecordExist(sql);
    }
    
    /**
     * Assert proxy order record exist.
     * 
     * @param sql SQL
     */
    public void assertProxyOrderRecordExist(final String sql) {
        boolean recordExist = false;
        for (int i = 0; i < 5; i++) {
            List<Map<String, Object>> result = queryForListWithLog(sql);
            recordExist = !result.isEmpty();
            if (recordExist) {
                break;
            }
            ThreadUtil.sleep(2, TimeUnit.SECONDS);
        }
        assertTrue(recordExist, "The insert record must exist after the stop");
    }
    
    /**
     * Get target table records count.
     * 
     * @param tableName table name
     * @return target table records count
     */
    public int getTargetTableRecordsCount(final String tableName) {
        List<Map<String, Object>> targetList = queryForListWithLog("SELECT COUNT(1) AS count FROM " + tableName);
        assertFalse(targetList.isEmpty());
        return ((Number) targetList.get(0).get("count")).intValue();
    }
    
    /**
     * Assert greater than order table init rows.
     * 
     * @param tableInitRows table init rows
     * @param schema schema
     */
    public void assertGreaterThanOrderTableInitRows(final int tableInitRows, final String schema) {
        String tableName = Strings.isNullOrEmpty(schema) ? "t_order" : String.format("%s.t_order", schema);
        int recordsCount = getTargetTableRecordsCount(tableName);
        assertTrue(recordsCount > tableInitRows, "actual count " + recordsCount);
    }
    
    /**
     * Generate ShardingSphere data source from proxy.
     * 
     * @return ShardingSphere data source
     * @throws SQLException SQL exception
     */
    // TODO proxy support for some fields still needs to be optimized, such as binary of MySQL, after these problems are optimized, Proxy dataSource can be used.
    public DataSource generateShardingSphereDataSourceFromProxy() throws SQLException {
        Awaitility.await().atMost(5, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> !getYamlRootConfig().getRules().isEmpty());
        YamlRootConfiguration rootConfig = getYamlRootConfig();
        if (PipelineEnvTypeEnum.DOCKER == PipelineE2EEnvironment.getInstance().getItEnvType()) {
            DockerStorageContainer storageContainer = ((DockerContainerComposer) containerComposer).getStorageContainers().get(0);
            String sourceUrl = String.join(":", storageContainer.getNetworkAliases().get(0), Integer.toString(storageContainer.getExposedPort()));
            String targetUrl = String.join(":", storageContainer.getHost(), Integer.toString(storageContainer.getMappedPort()));
            for (Map<String, Object> each : rootConfig.getDataSources().values()) {
                each.put("url", each.get("url").toString().replaceFirst(sourceUrl, targetUrl));
            }
        }
        for (Map<String, Object> each : rootConfig.getDataSources().values()) {
            each.put("dataSourceClassName", "com.zaxxer.hikari.HikariDataSource");
        }
        return YamlShardingSphereDataSourceFactory.createDataSourceWithoutCache(rootConfig);
    }
    
    private YamlRootConfiguration getYamlRootConfig() {
        return YamlEngine.unmarshal(queryForListWithLog("EXPORT DATABASE CONFIGURATION").get(0).get("result").toString(), YamlRootConfiguration.class);
    }
    
    @Override
    public void close() {
        containerComposer.stop();
    }
}
