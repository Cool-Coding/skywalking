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

package org.apache.skywalking.oap.server.core.storage.model;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.FunctionCategory;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.annotation.Storage;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.util.StringUtil;

/**
 * StorageModels manages all models detected by the core.
 */
@Slf4j
public class StorageModels implements IModelManager, ModelCreator, ModelManipulator {
    private final List<Model> models;
    private final HashMap<String, String> columnNameOverrideRule;
    private final List<CreatingListener> listeners;

    public StorageModels() {
        this.models = new ArrayList<>();
        this.columnNameOverrideRule = new HashMap<>();
        this.listeners = new ArrayList<>();
    }

    @Override
    public Model add(Class<?> aClass, int scopeId, Storage storage, boolean record) throws StorageException {
        // Check this scope id is valid.
        DefaultScopeDefine.nameOf(scopeId);

        List<ModelColumn> modelColumns = new ArrayList<>();
        ShardingKeyChecker checker = new ShardingKeyChecker();
        retrieval(aClass, storage.getModelName(), modelColumns, scopeId, checker);
        checker.check(storage.getModelName());

        Model model = new Model(
            storage.getModelName(),
            modelColumns,
            scopeId,
            storage.getDownsampling(),
            record,
            isSuperDatasetModel(aClass),
            FunctionCategory.uniqueFunctionName(aClass),
            storage.isTimeRelativeID()
        );

        this.followColumnNameRules(model);
        models.add(model);

        for (final CreatingListener listener : listeners) {
            listener.whenCreating(model);
        }
        return model;
    }

    private boolean isSuperDatasetModel(Class<?> aClass) {
        return aClass.isAnnotationPresent(SuperDataset.class);
    }

    /**
     * CreatingListener listener could react when {@link #add(Class, int, Storage, boolean)} model happens. Also, the
     * added models are being notified in this add operation.
     */
    @Override
    public void addModelListener(final CreatingListener listener) throws StorageException {
        listeners.add(listener);
        for (Model model : models) {
            listener.whenCreating(model);
        }
    }

    /**
     * Read model column metadata based on the class level definition.
     */
    private void retrieval(final Class<?> clazz,
                           final String modelName,
                           final List<ModelColumn> modelColumns,
                           final int scopeId,
                           ShardingKeyChecker checker) {
        if (log.isDebugEnabled()) {
            log.debug("Analysis {} to generate Model.", clazz.getName());
        }

        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                // Use the column#length as the default column length, as read the system env as the override mechanism.
                // Log the error but don't block the startup sequence.
                int columnLength = column.length();
                final String lengthEnvVariable = column.lengthEnvVariable();
                if (StringUtil.isNotEmpty(lengthEnvVariable)) {
                    final String envValue = System.getenv(lengthEnvVariable);
                    if (StringUtil.isNotEmpty(envValue)) {
                        try {
                            columnLength = Integer.parseInt(envValue);
                        } catch (NumberFormatException e) {
                            log.error("Model [{}] Column [{}], illegal value {} of column length from system env [{}]",
                                      modelName, column.columnName(), envValue, lengthEnvVariable
                            );
                        }
                    }
                }

                // SQL Database extension
                SQLDatabaseExtension sqlDatabaseExtension = new SQLDatabaseExtension();
                List<SQLDatabase.QueryUnifiedIndex> indexDefinitions = new ArrayList<>();
                if (field.isAnnotationPresent(SQLDatabase.QueryUnifiedIndex.class)) {
                    indexDefinitions.add(field.getAnnotation(SQLDatabase.QueryUnifiedIndex.class));
                }

                if (field.isAnnotationPresent(SQLDatabase.MultipleQueryUnifiedIndex.class)) {
                    Collections.addAll(
                        indexDefinitions, field.getAnnotation(SQLDatabase.MultipleQueryUnifiedIndex.class).value());
                }

                indexDefinitions.forEach(indexDefinition -> {
                    sqlDatabaseExtension.appendIndex(new SQLDatabaseExtension.MultiColumnsIndex(
                        column.columnName(),
                        indexDefinition.withColumns()
                    ));
                });

                // ElasticSearch extension
                final ElasticSearch.MatchQuery elasticSearchAnalyzer = field.getAnnotation(
                    ElasticSearch.MatchQuery.class);
                ElasticSearchExtension elasticSearchExtension = new ElasticSearchExtension(
                    elasticSearchAnalyzer == null ? null : elasticSearchAnalyzer.analyzer()
                );

                // BanyanDB extension
                final BanyanDB.ShardingKey banyanDBShardingKey = field.getAnnotation(
                    BanyanDB.ShardingKey.class);
                final BanyanDB.GlobalIndex banyanDBGlobalIndex = field.getAnnotation(
                    BanyanDB.GlobalIndex.class);
                final BanyanDB.NoIndexing banyanDBNoIndex = field.getAnnotation(
                    BanyanDB.NoIndexing.class);
                BanyanDBExtension banyanDBExtension = new BanyanDBExtension(
                    banyanDBShardingKey == null ? -1 : banyanDBShardingKey.index(),
                    banyanDBGlobalIndex == null ? false : true,
                    banyanDBNoIndex != null ? false : column.storageOnly()
                );

                final ModelColumn modelColumn = new ModelColumn(
                    new ColumnName(
                        modelName,
                        column.columnName()
                    ),
                    field.getType(),
                    field.getGenericType(),
                    column.storageOnly(),
                    column.indexOnly(),
                    column.dataType().isValue(),
                    columnLength,
                    sqlDatabaseExtension,
                    elasticSearchExtension,
                    banyanDBExtension
                );
                if (banyanDBExtension.isShardingKey()) {
                    checker.accept(modelName, modelColumn);
                }

                modelColumns.add(modelColumn);
                if (log.isDebugEnabled()) {
                    log.debug("The field named [{}] with the [{}] type", column.columnName(), field.getType());
                }
                if (column.dataType().isValue()) {
                    ValueColumnMetadata.INSTANCE.putIfAbsent(
                        modelName, column.columnName(), column.dataType(), column.function(),
                        column.defaultValue(), scopeId
                    );
                }
            }
        }

        if (Objects.nonNull(clazz.getSuperclass())) {
            retrieval(clazz.getSuperclass(), modelName, modelColumns, scopeId, checker);
        }
    }

    @Override
    public void overrideColumnName(String columnName, String newName) {
        columnNameOverrideRule.put(columnName, newName);
        models.forEach(this::followColumnNameRules);
        ValueColumnMetadata.INSTANCE.overrideColumnName(columnName, newName);
    }

    private void followColumnNameRules(Model model) {
        columnNameOverrideRule.forEach((oldName, newName) -> {
            model.getColumns().forEach(column -> {
                column.getColumnName().overrideName(oldName, newName);
                column.getSqlDatabaseExtension()
                      .getIndices()
                      .forEach(extraQueryIndex -> extraQueryIndex.overrideName(oldName, newName));
            });
        });
    }

    @Override
    public List<Model> allModels() {
        return models;
    }

    private class ShardingKeyChecker {
        private ArrayList<ModelColumn> keys = new ArrayList<>();

        /**
         * @throws IllegalStateException if sharding key indices are conflicting.
         */
        private void accept(String modelName, ModelColumn modelColumn) throws IllegalStateException {
            final int idx = modelColumn.getBanyanDBExtension().getShardingKeyIdx();
            while (idx + 1 > keys.size()) {
                keys.add(null);
            }
            ModelColumn exist = keys.get(idx);
            if (exist != null) {
                throw new IllegalStateException(
                    modelName + "'s "
                        + "Column [" + exist.getColumnName() + "] and column [" + modelColumn.getColumnName()
                        + " are conflicting with sharding key index=" + modelColumn.getBanyanDBExtension()
                                                                                   .getShardingKeyIdx());
            }
            keys.set(idx, modelColumn);
        }

        /**
         * @param modelName model name of the entity
         * @throws IllegalStateException if sharding key indices are not continuous
         */
        private void check(String modelName) throws IllegalStateException {
            for (int i = 0; i < keys.size(); i++) {
                final ModelColumn modelColumn = keys.get(i);
                if (modelColumn == null) {
                    throw new IllegalStateException("Sharding key index=" + i + " is missing in " + modelName);
                }
            }
        }
    }
}
