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

package org.apache.shardingsphere.sqltranslator.spring.namespace;

import org.apache.shardingsphere.sqltranslator.api.config.SQLTranslatorRuleConfiguration;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import javax.annotation.Resource;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertFalse;

@Ignore
@ContextConfiguration(locations = "classpath:META-INF/spring/sql-translator-application-context.xml")
public final class SQLTranslatorSpringNamespaceTest extends AbstractJUnit4SpringContextTests {
    
    @Resource
    private SQLTranslatorRuleConfiguration sqlTranslatorRuleConfiguration;
    
    @Test
    public void assertSQLTranslatorRule() {
        assertFalse(sqlTranslatorRuleConfiguration.isUseOriginalSQLWhenTranslatingFailed());
        assertThat(sqlTranslatorRuleConfiguration.getType(), is("MySQL"));
    }
}
