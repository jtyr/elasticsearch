/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.join.query;

import org.elasticsearch.Version;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.join.ParentJoinPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.InternalSettingsPlugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ESIntegTestCase.ClusterScope(scope = ESIntegTestCase.Scope.SUITE)
public abstract class ParentChildTestCase extends ESIntegTestCase {

    @Override
    protected boolean ignoreExternalCluster() {
        return true;
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(InternalSettingsPlugin.class, ParentJoinPlugin.class);
    }

    @Override
    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return nodePlugins();
    }

    @Override
    public Settings indexSettings() {
        Settings.Builder builder =  Settings.builder().put(super.indexSettings())
            // aggressive filter caching so that we can assert on the filter cache size
            .put(IndexModule.INDEX_QUERY_CACHE_ENABLED_SETTING.getKey(), true)
            .put(IndexModule.INDEX_QUERY_CACHE_EVERYTHING_SETTING.getKey(), true);

        if (legacy()) {
            builder.put("index.version.created", Version.V_5_6_0);
        }

        return builder.build();
    }

    protected boolean legacy() {
        return false;
    }

    protected IndexRequestBuilder createIndexRequest(String index, String type, String id, String parentId, Object... fields) {
        Map<String, Object> source = new HashMap<>();
        for (int i = 0; i < fields.length; i += 2) {
            source.put((String) fields[i], fields[i + 1]);
        }
        return createIndexRequest(index, type, id, parentId, source);
    }

    protected IndexRequestBuilder createIndexRequest(String index, String type, String id, String parentId,
                                                   XContentBuilder builder) throws IOException {
        Map<String, Object> source = XContentHelper.convertToMap(JsonXContent.jsonXContent, Strings.toString(builder), false);
        return createIndexRequest(index, type, id, parentId, source);
    }

    public static Map<String, Object> buildParentJoinFieldMappingFromSimplifiedDef(String joinFieldName,
                                                                                   boolean eagerGlobalOrdinals,
                                                                                   String... relations) {
        Map<String, Object> fields = new HashMap<>();

        Map<String, Object> joinField = new HashMap<>();
        joinField.put("type", "join");
        joinField.put("eager_global_ordinals", eagerGlobalOrdinals);
        Map<String, Object> relationMap = new HashMap<>();
        for (int i = 0; i < relations.length; i+=2) {
            String[] children = relations[i+1].split(",");
            if (children.length > 1) {
                relationMap.put(relations[i], children);
            } else {
                relationMap.put(relations[i], children[0]);
            }
        }
        joinField.put("relations", relationMap);
        fields.put(joinFieldName, joinField);
        return Collections.singletonMap("properties", fields);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> addFieldMappings(Map<String, Object> map, String... fields) {
        Map<String, Object> propsMap = (Map<String, Object>) map.get("properties");
        for (int i = 0; i < fields.length; i+=2) {
            String field = fields[i];
            String type = fields[i + 1];
            propsMap.put(field, Collections.singletonMap("type", type));
        }
        return map;
    }

    private IndexRequestBuilder createIndexRequest(String index, String type, String id, String parentId, Map<String, Object> source) {
        String name = type;
        if (legacy() == false) {
            type = "doc";
        }

        IndexRequestBuilder indexRequestBuilder = client().prepareIndex(index, type, id);
        if (legacy()) {
            if (parentId != null) {
                indexRequestBuilder.setParent(parentId);
            }
            indexRequestBuilder.setSource(source);
        } else {
            Map<String, Object> joinField = new HashMap<>();
            if (parentId != null) {
                joinField.put("name", name);
                joinField.put("parent", parentId);
                indexRequestBuilder.setRouting(parentId);
            } else {
                joinField.put("name", name);
            }
            source.put("join_field", joinField);
            indexRequestBuilder.setSource(source);
        }
        return indexRequestBuilder;
    }

}
