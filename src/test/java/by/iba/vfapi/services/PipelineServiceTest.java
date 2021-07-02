/*
 * Copyright (c) 2021 IBA Group, a.s. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package by.iba.vfapi.services;

import by.iba.vfapi.dto.Constants;
import by.iba.vfapi.dto.pipelines.CronPipelineDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewListDto;
import by.iba.vfapi.dto.pipelines.PipelineResponseDto;
import by.iba.vfapi.exceptions.ArgoClientException;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.argo.Arguments;
import by.iba.vfapi.model.argo.CronWorkflow;
import by.iba.vfapi.model.argo.CronWorkflowSpec;
import by.iba.vfapi.model.argo.DagTask;
import by.iba.vfapi.model.argo.DagTemplate;
import by.iba.vfapi.model.argo.NodeStatus;
import by.iba.vfapi.model.argo.Parameter;
import by.iba.vfapi.model.argo.Template;
import by.iba.vfapi.model.argo.Workflow;
import by.iba.vfapi.model.argo.WorkflowStatus;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.argoproj.workflow.ApiException;
import io.argoproj.workflow.apis.WorkflowServiceApi;
import io.argoproj.workflow.models.WorkflowRetryRequest;
import io.argoproj.workflow.models.WorkflowTerminateRequest;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.argoproj.workflow.models.WorkflowResumeRequest;
import io.argoproj.workflow.models.WorkflowStopRequest;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static by.iba.vfapi.dto.Constants.NODE_TYPE_POD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {
    private static JsonNode GRAPH;

    static {
        try {
            GRAPH = new ObjectMapper().readTree("{\n" +
                                                    "  \"graph\": [\n" +
                                                    "    {\n" +
                                                    "      \"value\": {\n" +
                                                    "        \"jobId\": \"cm1\",\n" +
                                                    "        \"name\": \"testJob\",\n" +
                                                    "        \"operation\": \"JOB\"\n" +
                                                    "      },\n" +
                                                    "      \"id\": \"jRjFu5yR\",\n" +
                                                    "      \"vertex\": true\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"value\": {\n" +
                                                    "        \"jobId\": \"cm2\",\n" +
                                                    "        \"name\": \"testJob2\",\n" +
                                                    "        \"operation\": \"JOB\"\n" +
                                                    "      },\n" +
                                                    "      \"id\": \"cyVyU8Xfw\",\n" +
                                                    "      \"vertex\": true\n" +
                                                    "    },\n" +
                                                    "    {\n" +
                                                    "      \"value\": {\n" +
                                                    "        \"successPath\": true,\n" +
                                                    "        \"operation\": \"EDGE\"\n" +
                                                    "      },\n" +
                                                    "      \"source\": \"jRjFu5yR\",\n" +
                                                    "      \"target\": \"cyVyU8Xfw\"\n" +
                                                    "    }\n" +
                                                    "  ]\n" +
                                                    "}");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Mock
    private ArgoKubernetesService argoKubernetesService;
    @Mock
    private WorkflowServiceApi apiInstance;
    private PipelineService pipelineService;

    @BeforeEach
    void setUp() {
        this.pipelineService = new PipelineService("sparkImage",
                                                   "sparkMaster",
                                                   "spark",
                                                   "pullSecret",
                                                   "slackImage",
                                                   argoKubernetesService,
                                                   apiInstance);
    }

    @Test
    void testCreate() {
        when(argoKubernetesService.getWorkflowTemplate(eq("projectId"), anyString()))
            .thenThrow(new ResourceNotFoundException(""));
        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(new ConfigMap());

        pipelineService.create("projectId", "name", GRAPH);

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }

    @Test
    void testCreateNotUniqueName() {
        when(argoKubernetesService.getWorkflowTemplatesByLabels("projectId", Map.of(Constants.NAME, "name")))
            .thenReturn(List.of(new WorkflowTemplate(), new WorkflowTemplate()));
        assertThrows(BadRequestException.class,
                     () -> pipelineService.create("projectId", "name", GRAPH),
                     "Expected exception must be thrown");


        verify(argoKubernetesService, never())
            .createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }

    @Test
    void testGetById() throws IOException {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name("dagTemplate")
                                                                                  .dag(new DagTemplate().addTasksItem(
                                                                                      new DagTask())))));

        when(argoKubernetesService.getWorkflowTemplate("projectId", "id")).thenReturn(workflowTemplate);
        when(argoKubernetesService.getWorkflow("projectId", "id")).thenThrow(ResourceNotFoundException.class);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId", "workflows", "argoproj.io", Constants.CREATE_ACTION))
            .thenReturn(true);

        PipelineResponseDto expected = ((PipelineResponseDto) new PipelineResponseDto()
            .id("id")
            .name("name")
            .lastModified("lastModified")
            .status("Draft")
            .runnable(true)).definition(new ObjectMapper().readTree(GRAPH.toString().getBytes())).editable(true);

        assertEquals(expected.getDefinition().toString(), pipelineService.getById("projectId", "id").getDefinition().toString(), "Pipeline must be equals to expected");
    }

    @Test
    void testGetAllInProject() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();

        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id1")
                                         .addToLabels(Constants.NAME, "name1")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name("dagTemplate")
                                                                                  .dag(new DagTemplate().addTasksItem(
                                                                                      new DagTask()
                                                                                          .arguments(new Arguments()
                                                                                                         .addParametersItem(
                                                                                                             new Parameter()
                                                                                                                 .name(
                                                                                                                     "configMap")
                                                                                                                 .value(
                                                                                                                     "id1")))
                                                                                          .name("name"))))));
        List<WorkflowTemplate> workflowTemplates = List.of(workflowTemplate);
        Workflow workflow = new Workflow();
        WorkflowStatus status = new WorkflowStatus();
        status.setFinishedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setStartedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setPhase("Running");
        NodeStatus nodeStatus1 = new NodeStatus();
        nodeStatus1.setDisplayName("pipeline");
        nodeStatus1.setPhase("Running");
        nodeStatus1.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus1.setTemplateName("sparkTemplate");
        nodeStatus1.setType(NODE_TYPE_POD);
        NodeStatus nodeStatus2 = new NodeStatus();
        nodeStatus2.setDisplayName("pipeline-2681521834");
        nodeStatus2.setPhase("Pending");
        nodeStatus2.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus2.setTemplateName("notificationTemplate");
        nodeStatus2.setType(NODE_TYPE_POD);
        status.setNodes(List
                            .of(nodeStatus1, nodeStatus2)
                            .stream()
                            .collect(Collectors.toMap(NodeStatus::getDisplayName, ns -> ns)));
        DagTemplate dagTemplate = new DagTemplate();
        dagTemplate.setTasks(List.of(new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("1")))
                                         .name("pipeline")
                                         .template("sparkTemplate"),
                                     new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("2")))
                                         .name("pipeline-2681521834")
                                         .template("notificationTemplate")));
        status.setStoredTemplates(Map.of("dagTemplate",
                                         new Template()
                                             .name(Constants.DAG_TEMPLATE_NAME)
                                             .dag(dagTemplate)
                                             .name("dagTemplate"),
                                         "notificationTemplate",
                                         new Template().name(PipelineService.NOTIFICATION_TEMPLATE_NAME),
                                         "sparkTemplate",
                                         new Template().name(PipelineService.SPARK_TEMPLATE_NAME)));
        workflow.setStatus(status);

        when(argoKubernetesService.getAllWorkflowTemplates("projectId")).thenReturn(workflowTemplates);
        when(argoKubernetesService.getWorkflow("projectId", "id1")).thenReturn(workflow);
        when(argoKubernetesService.getCronWorkflow("projectId", "id1")).thenThrow(ResourceNotFoundException.class);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId", "workflows", "argoproj.io", Constants.CREATE_ACTION))
            .thenReturn(true);

        PipelineOverviewListDto pipelines = pipelineService.getAll("projectId");

        PipelineOverviewDto expected = new PipelineOverviewDto()
            .id("id1")
            .name("name1")
            .lastModified("lastModified")
            .startedAt("2020-10-27 10:14:46 +0000")
            .finishedAt("2020-10-27 10:14:46 +0000")
            .status("Running")
            .progress(0.0f)
            .cron(false)
            .runnable(true)
            .jobsStatuses(Map.of("1", "Running", "2", "Pending"));

        assertEquals(expected, pipelines.getPipelines().get(0), "Pipeline must be equals to expected");
        assertTrue(pipelines.isEditable(), "Must be true");
    }

    @Test
    void testGetAllInProjectCron() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();

        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id1")
                                         .addToLabels(Constants.NAME, "name1")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name("dagTemplate")
                                                                                  .dag(new DagTemplate().addTasksItem(
                                                                                      new DagTask()
                                                                                          .arguments(new Arguments()
                                                                                                         .addParametersItem(
                                                                                                             new Parameter()
                                                                                                                 .name(
                                                                                                                     "configMap")
                                                                                                                 .value(
                                                                                                                     "id1")))
                                                                                          .name("name"))))));

        List<WorkflowTemplate> workflowTemplates = List.of(workflowTemplate);
        Workflow workflow = new Workflow();
        CronWorkflow cronWorkflow = new CronWorkflow();
        WorkflowStatus status = new WorkflowStatus();
        status.setFinishedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setStartedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setPhase("Running");
        NodeStatus nodeStatus1 = new NodeStatus();
        nodeStatus1.setDisplayName("pipeline");
        nodeStatus1.setPhase("Running");
        nodeStatus1.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus1.setTemplateName("sparkTemplate");
        nodeStatus1.setType(NODE_TYPE_POD);
        NodeStatus nodeStatus2 = new NodeStatus();
        nodeStatus2.setDisplayName("pipeline-2681521834");
        nodeStatus2.setPhase("Pending");
        nodeStatus2.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus2.setTemplateName("notificationTemplate");
        nodeStatus2.setType(NODE_TYPE_POD);
        status.setNodes(List
                            .of(nodeStatus1, nodeStatus2)
                            .stream()
                            .collect(Collectors.toMap(NodeStatus::getDisplayName, ns -> ns)));
        DagTemplate dagTemplate = new DagTemplate();
        dagTemplate.setTasks(List.of(new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("1")))
                                         .name("pipeline")
                                         .template("sparkTemplate"),
                                     new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("2")))
                                         .name("pipeline-2681521834")
                                         .template("notificationTemplate")));
        status.setStoredTemplates(Map.of("dagTemplate",
                                         new Template()
                                             .name(Constants.DAG_TEMPLATE_NAME)
                                             .dag(dagTemplate)
                                             .name("dagTemplate"),
                                         "notificationTemplate",
                                         new Template().name(PipelineService.NOTIFICATION_TEMPLATE_NAME),
                                         "sparkTemplate",
                                         new Template().name(PipelineService.SPARK_TEMPLATE_NAME)));
        workflow.setStatus(status);

        when(argoKubernetesService.getAllWorkflowTemplates("projectId")).thenReturn(workflowTemplates);
        when(argoKubernetesService.getWorkflow("projectId", "id1")).thenReturn(workflow);
        when(argoKubernetesService.getCronWorkflow("projectId", "id1")).thenReturn(cronWorkflow);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId", "workflows", "argoproj.io", Constants.CREATE_ACTION))
            .thenReturn(true);
        PipelineOverviewListDto pipelines = pipelineService.getAll("projectId");

        PipelineOverviewDto expected = new PipelineOverviewDto()
            .id("id1")
            .name("name1")
            .lastModified("lastModified")
            .startedAt("2020-10-27 10:14:46 +0000")
            .finishedAt("2020-10-27 10:14:46 +0000")
            .status("Running")
            .progress(0.0f)
            .runnable(true)
            .jobsStatuses(Map.of("1", "Running", "2", "Pending"))
            .cron(true);

        assertEquals(expected, pipelines.getPipelines().get(0), "Pipeline must be equals to expected");
        assertTrue(pipelines.isEditable(), "Must be true");
    }

    @Test
    void testGetAllInProjectWithoutWorkflow() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id1")
                                         .addToLabels(Constants.NAME, "name1")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name("dagTemplate")
                                                                                  .dag(new DagTemplate().addTasksItem(
                                                                                      new DagTask())))));
        List<WorkflowTemplate> workflowTemplates = List.of(workflowTemplate);

        when(argoKubernetesService.getAllWorkflowTemplates("projectId")).thenReturn(workflowTemplates);
        when(argoKubernetesService.getWorkflow("projectId", "id1")).thenThrow(ResourceNotFoundException.class);
        when(argoKubernetesService.getCronWorkflow("projectId", "id1")).thenThrow(ResourceNotFoundException.class);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId", "workflows", "argoproj.io", Constants.CREATE_ACTION))
            .thenReturn(true);

        PipelineOverviewListDto pipelines = pipelineService.getAll("projectId");

        PipelineOverviewDto expected = new PipelineOverviewDto()
            .id("id1")
            .name("name1")
            .status("Draft")
            .lastModified("lastModified")
            .cron(false)
            .runnable(true);

        assertEquals(expected, pipelines.getPipelines().get(0), "Pipeline must be equals to expected");
        assertTrue(pipelines.isEditable(), "Must be true");
    }

    @Test
    void testGetAllInProjectWithoutWorkflowCron() {
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        CronWorkflow cronWorkflow = new CronWorkflow();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id1")
                                         .addToLabels(Constants.NAME, "name1")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name("dagTemplate")
                                                                                  .dag(new DagTemplate().addTasksItem(
                                                                                      new DagTask())))));
        List<WorkflowTemplate> workflowTemplates = List.of(workflowTemplate);

        when(argoKubernetesService.getAllWorkflowTemplates("projectId")).thenReturn(workflowTemplates);
        when(argoKubernetesService.getWorkflow("projectId", "id1")).thenThrow(ResourceNotFoundException.class);
        when(argoKubernetesService.getCronWorkflow("projectId", "id1")).thenReturn(cronWorkflow);
        when(argoKubernetesService.isAccessible("projectId",
                                                "workflowtemplates",
                                                "argoproj.io",
                                                Constants.UPDATE_ACTION)).thenReturn(true);
        when(argoKubernetesService.isAccessible("projectId", "workflows", "argoproj.io", Constants.CREATE_ACTION))
            .thenReturn(true);

        PipelineOverviewListDto pipelines = pipelineService.getAll("projectId");

        PipelineOverviewDto expected = new PipelineOverviewDto()
            .id("id1")
            .name("name1")
            .status("Draft")
            .lastModified("lastModified")
            .runnable(true)
            .cron(true);

        assertEquals(expected, pipelines.getPipelines().get(0), "Pipeline must be equals to expected");
        assertTrue(pipelines.isEditable(), "Must be true");
    }

    @Test
    void testUpdate() {
        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));
        when(argoKubernetesService.getConfigMap(anyString(), anyString())).thenReturn(new ConfigMap());

        pipelineService.update("projectId", "id", GRAPH, "newName");

        verify(argoKubernetesService).createOrReplaceWorkflowTemplate(anyString(), any(WorkflowTemplate.class));
    }

    @Test
    void testDelete() {
        doNothing().when(argoKubernetesService).deleteWorkflowTemplate("projectId", "id");
        doNothing().when(argoKubernetesService).deleteWorkflow("projectId", "id");

        pipelineService.delete("projectId", "id");

        verify(argoKubernetesService).deleteWorkflowTemplate("projectId", "id");
        verify(argoKubernetesService).deleteWorkflow("projectId", "id");
    }

    @Test
    void testRun() {
        doNothing().when(argoKubernetesService).createOrReplaceWorkflow(eq("projectId"), any(Workflow.class));
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(new DagTemplate()))));
        when(argoKubernetesService.getWorkflowTemplate(anyString(), anyString())).thenReturn(workflowTemplate);
        pipelineService.run("projectId", "id");
        verify(argoKubernetesService).createOrReplaceWorkflow(eq("projectId"), any(Workflow.class));
    }

    @Test
    void testStop()  {
        doNothing().when(argoKubernetesService).deleteWorkflow(anyString(), anyString());
        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name("dagTemplate")
                                                                                  .dag(new DagTemplate().addTasksItem(
                                                                                      new DagTask())))));
        Workflow workflow = new Workflow();
        WorkflowStatus status = new WorkflowStatus();
        status.setFinishedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setStartedAt(DateTime.parse("2020-10-27T10:14:46Z"));
        status.setPhase("Running");
        NodeStatus nodeStatus1 = new NodeStatus();
        nodeStatus1.setDisplayName("pipeline");
        nodeStatus1.setPhase("Running");
        nodeStatus1.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus1.setTemplateName("sparkTemplate");
        nodeStatus1.setType(NODE_TYPE_POD);
        NodeStatus nodeStatus2 = new NodeStatus();
        nodeStatus2.setDisplayName("pipeline-2681521834");
        nodeStatus2.setPhase("Pending");
        nodeStatus2.setFinishedAt(DateTime.parse("2021-10-28T07:37:46Z"));
        nodeStatus2.setTemplateName("notificationTemplate");
        nodeStatus2.setType(NODE_TYPE_POD);
        status.setNodes(List
                            .of(nodeStatus1, nodeStatus2)
                            .stream()
                            .collect(Collectors.toMap(NodeStatus::getDisplayName, ns -> ns)));
        DagTemplate dagTemplate = new DagTemplate();
        dagTemplate.setTasks(List.of(new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("1")))
                                         .name("pipeline")
                                         .template("sparkTemplate"),
                                     new DagTask()
                                         .arguments(new Arguments().addParametersItem(new Parameter()
                                                                                          .name("graphId")
                                                                                          .value("2")))
                                         .name("pipeline-2681521834")
                                         .template("notificationTemplate")));
        status.setStoredTemplates(Map.of("dagTemplate",
                                         new Template()
                                             .name(Constants.DAG_TEMPLATE_NAME)
                                             .dag(dagTemplate)
                                             .name("dagTemplate"),
                                         "notificationTemplate",
                                         new Template().name(PipelineService.NOTIFICATION_TEMPLATE_NAME),
                                         "sparkTemplate",
                                         new Template().name(PipelineService.SPARK_TEMPLATE_NAME)));
        workflow.setStatus(status);
        when(argoKubernetesService.getWorkflowTemplate(anyString(), anyString())).thenReturn(workflowTemplate);
        when(argoKubernetesService.getWorkflow(anyString(), anyString())).thenReturn(workflow);
        pipelineService.stop(anyString(), anyString());
        verify(argoKubernetesService).deleteWorkflow(anyString(), anyString());
    }

    @Test
    void testStopFailure() throws ResourceNotFoundException {
        when(argoKubernetesService.getWorkflow(anyString(), anyString())).thenThrow(ResourceNotFoundException.class);
        assertThrows(ResourceNotFoundException.class,
                     () -> pipelineService.stop(anyString(), anyString()),
                     "Expected exception must be thrown");
        verify(argoKubernetesService).getWorkflow(anyString(), anyString());
    }

    @Test
    void testResume() {
        doNothing().when(argoKubernetesService).createOrReplaceWorkflow(eq("projectId"), any(Workflow.class));
        doNothing().when(argoKubernetesService).createOrReplaceWorkflowTemplate(eq("projectId"), any(WorkflowTemplate.class));

        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName("id")
                                         .addToLabels(Constants.NAME, "name")
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(GRAPH.toString().getBytes()))
                                         .addToAnnotations(Constants.LAST_MODIFIED, "lastModified")
                                         .addToAnnotations(Constants.STOPPED_AT, "stoppedHere")
                                         .build());
        workflowTemplate.setSpec(new WorkflowTemplateSpec().templates(List.of(new Template()
                                                                                  .name(Constants.DAG_TEMPLATE_NAME)
                                                                                  .dag(new DagTemplate()))));
        when(argoKubernetesService.getWorkflowTemplate(anyString(), anyString())).thenReturn(workflowTemplate);
        pipelineService.resume("projectId", "id");
        verify(argoKubernetesService).createOrReplaceWorkflow(eq("projectId"), any(Workflow.class));
    }

    @Test
    void testCreateCron() {
        CronPipelineDto cronPipelineDto = new CronPipelineDto();
        doNothing()
            .when(argoKubernetesService)
            .createOrReplaceCronWorkflow(eq("projectId"), any(CronWorkflow.class));
        pipelineService.createCron("projectId", "id", cronPipelineDto);
        verify(argoKubernetesService).createOrReplaceCronWorkflow(eq("projectId"), any(CronWorkflow.class));
    }

    @Test
    void testDeleteCron() {
        doNothing().when(argoKubernetesService).deleteCronWorkflow("projectId", "id");
        pipelineService.deleteCron("projectId", "id");
        verify(argoKubernetesService).deleteCronWorkflow("projectId", "id");
    }

    @Test
    void testGetCronById() {
        CronPipelineDto expected = CronPipelineDto.builder().build();
        CronWorkflow cronWorkflow = new CronWorkflow();
        cronWorkflow.setSpec(new CronWorkflowSpec());
        when(argoKubernetesService.getCronWorkflow("projectId", "id")).thenReturn(cronWorkflow);
        assertEquals(expected.getSchedule(),
                     pipelineService.getCronById("projectId", "id").getSchedule(),
                     "Schedule must be equals to expected");
    }
}
