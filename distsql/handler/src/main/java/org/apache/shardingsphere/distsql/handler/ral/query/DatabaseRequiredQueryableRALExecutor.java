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

package org.apache.shardingsphere.distsql.handler.ral.query;

import org.apache.shardingsphere.distsql.parser.statement.ral.QueryableRALStatement;
import org.apache.shardingsphere.infra.merge.result.impl.local.LocalDataQueryResultRow;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;

import java.util.Collection;

/**
 * Database required queryable RAL executor.
 */
public interface DatabaseRequiredQueryableRALExecutor<T extends QueryableRALStatement> extends QueryableRALExecutor<T> {
    
    /**
     * Get query result rows.
     *
     * @param database ShardingSphere database
     * @param sqlStatement SQL statement
     * @return query result rows
     */
    Collection<LocalDataQueryResultRow> getRows(ShardingSphereDatabase database, T sqlStatement);
}
