/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.core.metadata.datasource;

import com.google.common.base.Optional;
import io.shardingsphere.core.constant.DatabaseType;
import io.shardingsphere.core.metadata.datasource.dialect.H2DataSourceMetaDataParser;
import io.shardingsphere.core.metadata.datasource.dialect.MySQLDataSourceMetaDataParser;
import io.shardingsphere.core.metadata.datasource.dialect.OracleDataSourceMetaDataParser;
import io.shardingsphere.core.metadata.datasource.dialect.PostgreSQLDataSourceMetaDataParser;
import io.shardingsphere.core.metadata.datasource.dialect.SQLServerDataSourceMetaDataParser;
import io.shardingsphere.core.rule.ShardingRule;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Sharding data source meta data.
 *
 * @author panjuan
 */
public class ShardingDataSourceMetaData {
    
    private final Map<String, DataSourceMetaData> dataSourceMetaDataMap;
    
    public ShardingDataSourceMetaData(final Map<String, String> dataSourceURLs, final ShardingRule shardingRule, final DatabaseType databaseType) {
        dataSourceMetaDataMap = getDataSourceMetaDataMap(dataSourceURLs, shardingRule, databaseType);
    }
    
    private Map<String, DataSourceMetaData> getDataSourceMetaDataMap(final Map<String, String> dataSourceURLs, final ShardingRule shardingRule, final DatabaseType databaseType) {
        Map<String, DataSourceMetaData> dataSourceMetaDataMap = new LinkedHashMap<>(dataSourceURLs.size(), 1);
        for (Entry<String, String> entry : dataSourceURLs.entrySet()) {
            dataSourceMetaDataMap.put(entry.getKey(), createDataSourceMetaDataParser(databaseType).getDataSourceMetaData(entry.getValue(), databaseType));
        }
        return handleMasterSlaveDataSourceNames(shardingRule, dataSourceMetaDataMap);
    }
    
    private static DataSourceMetaDataParser createDataSourceMetaDataParser(final DatabaseType databaseType) {
        switch (databaseType) {
            case H2:
                return new H2DataSourceMetaDataParser();
            case MySQL:
                return new MySQLDataSourceMetaDataParser();
            case Oracle:
                return new OracleDataSourceMetaDataParser();
            case PostgreSQL:
                return new PostgreSQLDataSourceMetaDataParser();
            case SQLServer:
                return new SQLServerDataSourceMetaDataParser();
            default:
                throw new UnsupportedOperationException(String.format("Cannot support database [%s].", databaseType));
        }
    }
    
    private Map<String, DataSourceMetaData> handleMasterSlaveDataSourceNames(final ShardingRule shardingRule, final Map<String, DataSourceMetaData> dataSourceMetaDataMap) {
        Map<String, DataSourceMetaData> result = new LinkedHashMap<>();
        if (shardingRule.getMasterSlaveRules().isEmpty()) {
            return dataSourceMetaDataMap;
        }
        for (Entry<String, DataSourceMetaData> entry : dataSourceMetaDataMap.entrySet()) {
            Optional<String> masterSlaveRuleNameOptional = shardingRule.tryFindMasterSlaveRuleName(entry.getKey());
            if (masterSlaveRuleNameOptional.isPresent()) {
                result.put(masterSlaveRuleNameOptional.get(), entry.getValue());
            }
        }
        return result;
    }
    
    /**
     * Get all instance data source names.
     *
     * @return instance data source names
     */
    public Collection<String> getAllInstanceDataSourceNames() {
        Collection<String> result = new LinkedList<>();
        for (Entry<String, DataSourceMetaData> entry : dataSourceMetaDataMap.entrySet()) {
            if (!isExisted(entry.getKey(), result)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }
    
    private boolean isExisted(final String dataSourceName, final Collection<String> existedDataSourceNames) {
        for (String each : existedDataSourceNames) {
            if (dataSourceMetaDataMap.get(each).isInSameDatabaseInstance(dataSourceMetaDataMap.get(dataSourceName))) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get actual schema name.
     *
     * @param actualDataSourceName actual data source name
     * @return actual schema name
     */
    public String getActualSchemaName(final String actualDataSourceName) {
        return dataSourceMetaDataMap.get(actualDataSourceName).getSchemeName();
    }
}
