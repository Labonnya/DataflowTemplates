/*
 * Copyright (C) 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.templates;

import static com.google.cloud.teleport.it.common.matchers.TemplateAsserts.assertThatPipeline;
import static com.google.cloud.teleport.it.common.matchers.TemplateAsserts.assertThatResult;
import static com.google.cloud.teleport.it.gcp.bigtable.BigtableResourceManagerUtils.generateTableId;
import static com.google.cloud.teleport.it.gcp.bigtable.matchers.BigtableAsserts.assertThatBigtableRecords;

import com.google.cloud.bigtable.data.v2.models.Row;
import com.google.cloud.teleport.bigtable.AvroToBigtable;
import com.google.cloud.teleport.it.common.PipelineLauncher;
import com.google.cloud.teleport.it.common.PipelineOperator;
import com.google.cloud.teleport.it.common.utils.ResourceManagerUtils;
import com.google.cloud.teleport.it.gcp.TemplateTestBase;
import com.google.cloud.teleport.it.gcp.bigtable.BigtableResourceManager;
import com.google.cloud.teleport.metadata.TemplateIntegrationTest;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration test for {@link AvroToBigtable} classic template. */
@Category(TemplateIntegrationTest.class)
@TemplateIntegrationTest(AvroToBigtable.class)
@RunWith(JUnit4.class)
public class AvroToBigtableIT extends TemplateTestBase {

  private BigtableResourceManager bigtableResourceManager;

  @Before
  public void setUp() throws IOException {
    bigtableResourceManager =
        BigtableResourceManager.builder(testName, PROJECT)
            .setCredentialsProvider(credentialsProvider)
            .build();
    gcsClient.uploadArtifact(
        "input/bigtable-to-avro-file_test.avro",
        Resources.getResource("AvroToBigtableIT/bigtable-to-avro-file_test.avro").getPath());
  }

  @After
  public void tearDown() {
    ResourceManagerUtils.cleanResources(bigtableResourceManager);
  }

  @Test
  public void testAvroToBigtable() throws IOException {
    // Arrange
    String tableId = generateTableId(testName);
    bigtableResourceManager.createTable(tableId, ImmutableList.of("family1", "family2"));

    PipelineLauncher.LaunchConfig.Builder options =
        PipelineLauncher.LaunchConfig.builder(testName, specPath)
            .addParameter("bigtableProjectId", PROJECT)
            .addParameter("bigtableInstanceId", bigtableResourceManager.getInstanceId())
            .addParameter("bigtableTableId", tableId)
            .addParameter("inputFilePattern", getGcsPath("input/bigtable-to-avro-file_test.avro"));

    // Act
    PipelineLauncher.LaunchInfo info = launchTemplate(options);
    assertThatPipeline(info).isRunning();

    PipelineOperator.Result result = pipelineOperator().waitUntilDone(createConfig(info));

    // Assert
    assertThatResult(result).isLaunchFinished();
    List<Row> rows = bigtableResourceManager.readTable(tableId);

    assertThatBigtableRecords(rows, "family1")
        .hasRecordsUnordered(List.of(Map.of("column1", "value1")));
    assertThatBigtableRecords(rows, "family2")
        .hasRecordsUnordered(List.of(Map.of("column2", "value2")));
    assertThatBigtableRecords(rows, "family2")
        .hasRecordsUnordered(List.of(Map.of("column2", "value3")));
  }
}