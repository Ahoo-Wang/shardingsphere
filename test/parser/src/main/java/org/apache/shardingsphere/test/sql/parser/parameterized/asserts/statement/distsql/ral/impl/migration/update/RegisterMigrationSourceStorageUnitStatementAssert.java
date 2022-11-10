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

package org.apache.shardingsphere.test.sql.parser.parameterized.asserts.statement.distsql.ral.impl.migration.update;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.shardingsphere.distsql.parser.segment.DataSourceSegment;
import org.apache.shardingsphere.migration.distsql.statement.RegisterMigrationSourceStorageUnitStatement;
import org.apache.shardingsphere.test.sql.parser.parameterized.asserts.SQLCaseAssertContext;
import org.apache.shardingsphere.test.sql.parser.parameterized.asserts.segment.distsql.DataSourceAssert;
import org.apache.shardingsphere.test.sql.parser.parameterized.jaxb.cases.domain.segment.impl.distsql.ExpectedDataSource;
import org.apache.shardingsphere.test.sql.parser.parameterized.jaxb.cases.domain.statement.distsql.ral.migration.RegisterMigrationSourceStorageUnitStatementTestCase;

import java.util.Collection;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Register migration source storage unit statement assert.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RegisterMigrationSourceStorageUnitStatementAssert {
    
    /**
     * Assert register migration source storage unit statement is correct with expected parser result.
     *
     * @param assertContext assert context
     * @param actual actual register migration source storage unit statement
     * @param expected expected register migration source storage unit statement test case
     */
    public static void assertIs(final SQLCaseAssertContext assertContext, final RegisterMigrationSourceStorageUnitStatement actual,
                                final RegisterMigrationSourceStorageUnitStatementTestCase expected) {
        if (null == expected) {
            assertNull(assertContext.getText("Actual statement should not exist."), actual);
        } else {
            assertNotNull(assertContext.getText("Actual statement should exist."), actual);
            assertDataSources(assertContext, actual.getDataSources(), expected.getDataSources());
        }
    }
    
    private static void assertDataSources(final SQLCaseAssertContext assertContext, final Collection<DataSourceSegment> actual, final List<ExpectedDataSource> expected) {
        if (null == expected) {
            assertNull(assertContext.getText("Actual datasource should not exist."), actual);
        } else {
            assertNotNull(assertContext.getText("Actual datasource should exist."), actual);
            assertThat(assertContext.getText(String.format("Actual datasource size should be %s , but it was %s", expected.size(), actual.size())), actual.size(), is(expected.size()));
            int count = 0;
            for (DataSourceSegment actualDataSource : actual) {
                DataSourceAssert.assertIs(assertContext, actualDataSource, expected.get(count));
                count++;
            }
        }
    }
}
