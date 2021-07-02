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
import by.iba.vfapi.dto.GraphDto;
import by.iba.vfapi.dto.pipelines.CronPipelineDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewListDto;
import by.iba.vfapi.dto.pipelines.PipelineResponseDto;
import by.iba.vfapi.model.argo.RuntimeData;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.exceptions.InternalProcessingException;
import by.iba.vfapi.model.argo.Arguments;
import by.iba.vfapi.model.argo.ConfigMapRef;
import by.iba.vfapi.model.argo.Container;
import by.iba.vfapi.model.argo.CronWorkflow;
import by.iba.vfapi.model.argo.CronWorkflowSpec;
import by.iba.vfapi.model.argo.DagTask;
import by.iba.vfapi.model.argo.DagTemplate;
import by.iba.vfapi.model.argo.Env;
import by.iba.vfapi.model.argo.EnvFrom;
import by.iba.vfapi.model.argo.FieldRef;
import by.iba.vfapi.model.argo.ImagePullSecret;
import by.iba.vfapi.model.argo.Inputs;
import by.iba.vfapi.model.argo.NodeStatus;
import by.iba.vfapi.model.argo.Parameter;
import by.iba.vfapi.model.argo.SecretRef;
import by.iba.vfapi.model.argo.Template;
import by.iba.vfapi.model.argo.TemplateMeta;
import by.iba.vfapi.model.argo.ValueFrom;
import by.iba.vfapi.model.argo.Workflow;
import by.iba.vfapi.model.argo.WorkflowSpec;
import by.iba.vfapi.model.argo.WorkflowStatus;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateRef;
import by.iba.vfapi.model.argo.WorkflowTemplateSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import io.argoproj.workflow.apis.WorkflowServiceApi;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static by.iba.vfapi.dto.Constants.ANNOTATION_JOB_STATUSES;
import static by.iba.vfapi.dto.Constants.FINISHED_AT;
import static by.iba.vfapi.dto.Constants.PROGRESS;
import static by.iba.vfapi.dto.Constants.STARTED_AT;
import static by.iba.vfapi.dto.Constants.STATUS;
import static by.iba.vfapi.dto.Constants.STOPPED_AT;
import static by.iba.vfapi.services.K8sUtils.DRAFT_STATUS;

/**
 * PipelineService class.
 */
@Slf4j
@Service
public class PipelineService {
    public static final String LIMITS_CPU = "limitsCpu";
    public static final String REQUESTS_CPU = "requestsCpu";
    public static final String LIMITS_MEMORY = "limitsMemory";
    public static final String REQUESTS_MEMORY = "requestsMemory";
    static final String SPARK_TEMPLATE_NAME = "sparkTemplate";
    static final String NOTIFICATION_TEMPLATE_NAME = "notificationTemplate";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEPENDS_OPERATOR_LENGTH = 4;
    private static final String STATUS_ERROR = "Error";
    private static final String STATUS_FAILED = "Failed";
    private static final String GRAPH_ID = "graphId";

    private final String sparkImage;
    private final ArgoKubernetesService argoKubernetesService;
    private final WorkflowServiceApi apiInstance;
    private final String jobMaster;
    private final String serviceAccount;
    private final String imagePullSecret;
    private final String notificationImage;
    @Value("${job.slack.apiToken}")
    private String slackApiToken;

    public PipelineService(
        @Value("${job.spark.image}") String sparkImage,
        @Value("${job.spark.master}") final String jobMaster,
        @Value("${job.spark.serviceAccount}") final String serviceAccount,
        @Value("${job.imagePullSecret}") final String imagePullSecret,
        @Value("${job.slack.image}") final String notificationImage,
        ArgoKubernetesService argoKubernetesService,
        WorkflowServiceApi apiInstance) {
        this.sparkImage = sparkImage;
        this.jobMaster = jobMaster;
        this.serviceAccount = serviceAccount;
        this.imagePullSecret = imagePullSecret;
        this.notificationImage = notificationImage;
        this.argoKubernetesService = argoKubernetesService;
        this.apiInstance = apiInstance;
    }

    /**
     * Creating DAGTask for spark job.
     *
     * @param name           task name
     * @param depends        String of dependencies
     * @param parameterValue value of parameter 'configMap'
     * @param graphId        value of node id from graph
     * @return new DAGTask
     */
    private static DagTask createSparkDagTask(String name, String depends, String parameterValue, String graphId) {
        return new DagTask()
            .name(name)
            .template(SPARK_TEMPLATE_NAME)
            .depends(depends)
            .arguments(new Arguments()
                           .addParametersItem(new Parameter().name(K8sUtils.CONFIGMAP).value(parameterValue))
                           .addParametersItem(new Parameter().name(GRAPH_ID).value(graphId)));
    }

    /**
     * Creating DAGTask for slack job.
     *
     * @param name       task name
     * @param depends    String of dependencies
     * @param addressees value of parameter 'addressees'
     * @param message    value of parameter 'message'
     * @param graphId    value of node id from graph
     * @return new DAGTask
     */
    private static DagTask createNotificationDagTask(
        String name, String depends, String addressees, String message, String graphId) {
        return new DagTask()
            .name(name)
            .template(NOTIFICATION_TEMPLATE_NAME)
            .depends(depends)
            .arguments(new Arguments()
                           .addParametersItem(new Parameter()
                                                  .name(Constants.NODE_NOTIFICATION_RECIPIENTS)
                                                  .value(Arrays
                                                             .stream(addressees.split(" "))
                                                             .map(StringEscapeUtils::escapeXSI)
                                                             .collect(Collectors.joining(" "))))
                           .addParametersItem(new Parameter()
                                                  .name(Constants.NODE_NOTIFICATION_MESSAGE)
                                                  .value(StringEscapeUtils.escapeXSI(message)))
                           .addParametersItem(new Parameter().name(GRAPH_ID).value(graphId)));
    }

    /**
     * Set metadata to workflowTemplate.
     *
     * @param workflowTemplate     workflowTemplate
     * @param workflowTemplateId   workflowTemplate id
     * @param workflowTemplateName workflowTemplate name
     * @param definition           definition for workflowTemplate
     */
    private static void setMeta(
        WorkflowTemplate workflowTemplate,
        String workflowTemplateId,
        String workflowTemplateName,
        JsonNode definition) {
        workflowTemplate.setMetadata(new ObjectMetaBuilder()
                                         .withName(workflowTemplateId)
                                         .addToLabels(Constants.NAME, workflowTemplateName)
                                         .addToAnnotations(Constants.DEFINITION,
                                                           Base64.encodeBase64String(definition
                                                                                         .toString()
                                                                                         .getBytes(StandardCharsets.UTF_8)))
                                         .addToAnnotations(Constants.LAST_MODIFIED,
                                                           ZonedDateTime
                                                               .now()
                                                               .format(Constants.DATE_TIME_FORMATTER))
                                         .build());
    }

    /**
     * Replacing ids in pipelines nodes.
     *
     * @param nodes list of GraphDto.NodeDto
     * @param edges list of GraphDto.EdgeDto
     */
    private static void replaceIds(Iterable<GraphDto.NodeDto> nodes, Iterable<GraphDto.EdgeDto> edges) {
        for (GraphDto.NodeDto node : nodes) {
            String id = node.getId();
            String generatedId = RandomStringUtils.randomAlphabetic(1) + UUID.randomUUID().toString().substring(1);
            node.setId(generatedId);
            node.getValue().put(generatedId, id);

            for (GraphDto.EdgeDto edge : edges) {
                if (edge.getSource().equals(id)) {
                    edge.setSource(generatedId);
                    continue;
                }
                if (edge.getTarget().equals(id)) {
                    edge.setTarget(generatedId);
                }
            }
        }
    }

    /**
     * Create dag flow in templates.
     *
     * @param graphDto nodes and edges
     * @return DAGTemplate
     */
    private static DagTemplate createDagFlow(GraphDto graphDto) {
        List<GraphDto.NodeDto> nodes = graphDto.getNodes();
        List<GraphDto.EdgeDto> edges = graphDto.getEdges();
        replaceIds(nodes, edges);

        Map<String, String> idMapping = Maps.newHashMapWithExpectedSize(nodes.size());
        for (GraphDto.NodeDto node : nodes) {
            idMapping.put(node.getId(), node.getValue().get("jobId"));
        }

        checkJobCount(idMapping);

        checkSourceArrows(edges);

        DagTemplate dagTemplate = new DagTemplate();
        for (GraphDto.NodeDto node : nodes) {
            String id = node.getId();
            String depends = accumulateDepends(edges, id);

            DagTask dagTask;
            if (idMapping.get(id) != null) {
                dagTask = createSparkDagTask(id, depends, idMapping.get(id), node.getValue().get(id));
            } else {
                dagTask = createNotificationDagTask(id,
                                                    depends,
                                                    node.getValue().get(Constants.NODE_NOTIFICATION_RECIPIENTS),
                                                    node.getValue().get(Constants.NODE_NOTIFICATION_MESSAGE),
                                                    node.getValue().get(id));
            }
            dagTemplate.addTasksItem(dagTask);
        }

        return dagTemplate;
    }

    /**
     * Validate is pipeline have jobs duplicates.
     *
     * @param idMapping map of ids
     */
    private static void checkJobCount(Map<String, String> idMapping) {
        int notificationsCount = StringUtils.countMatches(String.valueOf(idMapping.values()), "null");
        if (new HashSet<>(idMapping.values()).size() < idMapping.size() - notificationsCount) {
            throw new BadRequestException("Job can't be used more than once in pipeline");
        }
    }

    /**
     * Accumulate dependencies for node.
     *
     * @param edges  edges
     * @param nodeId node id
     * @return String with dependencies
     */
    private static String accumulateDepends(Iterable<GraphDto.EdgeDto> edges, String nodeId) {
        StringBuilder depends = new StringBuilder();
        for (GraphDto.EdgeDto edge : edges) {
            if (edge.getTarget().equals(nodeId)) {
                boolean isSuccessPath = isSuccessPath(edge);
                if (isSuccessPath && depends.indexOf("|") == -1) {
                    depends.append(prepareDependency("&&", edge));
                } else if (!isSuccessPath && depends.indexOf("&") == -1) {
                    depends.append(prepareDependency("||", edge));
                } else {
                    throw new BadRequestException("Node can't have different type of income arrows");
                }
            }
        }

        if (depends.length() == 0) {
            return null;
        }
        return depends.substring(DEPENDS_OPERATOR_LENGTH);
    }

    /**
     * Get variable successPath.
     *
     * @param edge edge
     * @return successPath value
     */
    private static boolean isSuccessPath(GraphDto.EdgeDto edge) {
        return Boolean.parseBoolean(edge.getValue().get("successPath"));
    }

    /**
     * Check failure path count.
     *
     * @param edges edges
     */
    private static void checkSourceArrows(Iterable<GraphDto.EdgeDto> edges) {
        Set<String> set = new HashSet<>();
        for (GraphDto.EdgeDto edge : edges) {
            if (!isSuccessPath(edge)) {
                if (set.contains(edge.getSource())) {
                    throw new BadRequestException("Node can't have more than one failure path");
                } else {
                    set.add(edge.getSource());
                }
            }
        }
    }

    /**
     * Create str with dependency from edge.
     *
     * @param operator operator for depends
     * @param edge     edge
     * @return str with dependency from edge
     */
    private static String prepareDependency(String operator, GraphDto.EdgeDto edge) {
        String source = edge.getSource();
        if ("&&".equals(operator)) {
            return String.format(" && %s", source);
        }
        return String.format(" || %s.Failed || %s.Errored", source, source);
    }

    /**
     * Creating Template with DAGTemplate from graph.
     *
     * @param graphDto graph for workflow
     * @return Template with dag
     */
    private static Template createTemplateWithDag(GraphDto graphDto) {
        DagTemplate dagFlow = createDagFlow(graphDto);
        return new Template().name(Constants.DAG_TEMPLATE_NAME).dag(dagFlow);
    }

    static List<DagTask> getDagTaskFromWorkflowTemplateSpec(WorkflowTemplateSpec workflowTemplateSpec) {
        List<Template> templates = workflowTemplateSpec.getTemplates();
        Template dagTemplate = templates
            .stream()
            .filter(template -> Constants.DAG_TEMPLATE_NAME.equals(template.getName()))
            .findAny()
            .orElseThrow(() -> new InternalProcessingException("Pipeline config is corrupted"));
        if (dagTemplate.getDag().getTasks() == null) {
            return Collections.emptyList();
        }
        return dagTemplate.getDag().getTasks();
    }

    /**
     * Adding flag is pipeline runnable.
     *
     * @param dagTasks        list of dag tasks
     * @param dto             dto
     * @param accessibleToRun is user have permission for run
     */
    private static void appendRunnable(
        Collection<DagTask> dagTasks, PipelineOverviewDto dto, boolean accessibleToRun) {
        dto.runnable(accessibleToRun && !dagTasks.isEmpty());
    }

    /**
     * Adding parameters to dag tasks.
     *
     * @param workflowTemplate workflowTemplate
     * @param projectId        projectId
     */
    private void addParametersToDagTasks(WorkflowTemplate workflowTemplate, String projectId) {
        List<DagTask> tasks = getDagTaskFromWorkflowTemplateSpec(workflowTemplate.getSpec());
        for (DagTask dagTask : tasks) {
            Parameter configMapParameter = dagTask
                .getArguments()
                .getParameters()
                .stream()
                .filter(parameter -> K8sUtils.CONFIGMAP.equals(parameter.getName()))
                .findFirst()
                .orElse(null);
            if (configMapParameter != null) {
                ConfigMap configMap = argoKubernetesService.getConfigMap(projectId, configMapParameter.getValue());
                ResourceRequirements resourceRequirements = K8sUtils.getResourceRequirements(configMap);
                Map<String, Quantity> requests = resourceRequirements.getRequests();
                Map<String, Quantity> limits = resourceRequirements.getLimits();

                dagTask
                    .getArguments()
                    .setParameters(dagTask
                                       .getArguments()
                                       .getParameters()
                                       .stream()
                                       .filter(parameter -> K8sUtils.CONFIGMAP.equals(parameter.getName()) ||
                                           GRAPH_ID.equals(parameter.getName()))
                                       .collect(Collectors.toList()));

                dagTask
                    .getArguments()
                    .addParametersItem(new Parameter()
                                           .name(LIMITS_CPU)
                                           .value(limits.get(Constants.CPU_FIELD).toString()))
                    .addParametersItem(new Parameter()
                                           .name(LIMITS_MEMORY)
                                           .value(limits.get(Constants.MEMORY_FIELD).toString()))
                    .addParametersItem(new Parameter()
                                           .name(REQUESTS_CPU)
                                           .value(requests.get(Constants.CPU_FIELD).toString()))
                    .addParametersItem(new Parameter()
                                           .name(REQUESTS_MEMORY)
                                           .value(requests.get(Constants.MEMORY_FIELD).toString()));
            }
        }
    }

    /**
     * Creating template for spark-job.
     *
     * @return Template for spark-job
     */
    private Template createSparkTemplate(String namespace) {
        return new Template()
            .name(SPARK_TEMPLATE_NAME)
            .inputs(new Inputs()
                        .addParametersItem(new Parameter().name(K8sUtils.CONFIGMAP))
                        .addParametersItem(new Parameter().name(LIMITS_CPU))
                        .addParametersItem(new Parameter().name(LIMITS_MEMORY))
                        .addParametersItem(new Parameter().name(REQUESTS_CPU))
                        .addParametersItem(new Parameter().name(REQUESTS_MEMORY)))
            .podSpecPatch(String.format("{\"containers\": [{\"name\": \"main\", \"resources\": {\"limits\": " +
                                            "{\"cpu\": \"{{inputs.parameters.%s}}\", \"memory\": \"{{inputs" +
                                            ".parameters.%s}}\"}, \"requests\": {\"cpu\": \"{{inputs.parameters" +
                                            ".%s}}\", \"memory\": \"{{inputs.parameters.%s}}\"}}}]}",
                                        LIMITS_CPU,
                                        LIMITS_MEMORY,
                                        REQUESTS_CPU,
                                        REQUESTS_MEMORY))
            .container(new Container()
                           .name(K8sUtils.JOB_CONTAINER)
                           .image(sparkImage)
                           .command(List.of("/opt/spark/work-dir/entrypoint.sh"))
                           .imagePullPolicy("Always")
                           .env(List.of(new Env()
                                            .name("POD_IP")
                                            .valueFrom(new ValueFrom().name(new FieldRef()
                                                                                .fieldPath("status.podIP")
                                                                                .apiVersion("v1"))),
                                        new Env()
                                            .name("POD_NAME")
                                            .valueFrom(new ValueFrom().name(new FieldRef()
                                                                                .fieldPath("metadata.name")
                                                                                .apiVersion("v1"))),
                                        new Env()
                                            .name("PIPELINE_JOB_ID")
                                            .valueFrom(new ValueFrom().name(new FieldRef()
                                                                                .fieldPath("metadata.name")
                                                                                .apiVersion("v1"))),
                                        new Env().name("JOB_ID").value("{{inputs.parameters.configMap}}"),
                                        new Env().name("JOB_MASTER").value(jobMaster),
                                        new Env().name("JOB_IMAGE").value(sparkImage),
                                        new Env().name("IMAGE_PULL_SECRETS").value(imagePullSecret),
                                        new Env().name("POD_NAMESPACE").value(namespace)))
                           .envFrom(List.of(new EnvFrom().configMapRef(new ConfigMapRef().name(
                               "{{inputs.parameters" +
                                   ".configMap}}")),
                                            new EnvFrom().secretRef(new SecretRef().name(ParamsDto.SECRET_NAME)))))
            .metadata(new TemplateMeta().labels(Map.of(Constants.JOB_ID_LABEL,
                                                       "{{inputs.parameters.configMap}}")));
    }

    /**
     * Creating template for slack-job.
     *
     * @return Template for slack-job
     */
    private Template createNotificationTemplate() {
        return new Template()
            .name(NOTIFICATION_TEMPLATE_NAME)
            .inputs(new Inputs()
                        .addParametersItem(new Parameter().name(Constants.NODE_NOTIFICATION_RECIPIENTS))
                        .addParametersItem(new Parameter().name(Constants.NODE_NOTIFICATION_MESSAGE)))
            .container(new Container()
                           .image(notificationImage)
                           .command(List.of("/bin/bash", "-c", "--"))
                           .args(List.of(String.format(
                               "python3 /home/job-user/slack_job.py -m {{inputs.parameters.%s}} -a {{inputs" +
                                   ".parameters" +
                                   ".%s}}",
                               Constants.NODE_NOTIFICATION_MESSAGE,
                               Constants.NODE_NOTIFICATION_RECIPIENTS)))
                           .imagePullPolicy("Always")
                           .env(List.of(new Env().name("SLACK_API_TOKEN").value(slackApiToken)))
                           .envFrom(List.of(new EnvFrom().secretRef(new SecretRef().name(ParamsDto.SECRET_NAME))))
                           .resources(new ResourceRequirementsBuilder()
                                          .addToLimits(Map.of(Constants.CPU_FIELD,
                                                              Quantity.parse("500m"),
                                                              Constants.MEMORY_FIELD,
                                                              Quantity.parse("500M")))
                                          .addToRequests(Map.of(Constants.CPU_FIELD,
                                                                Quantity.parse("100m"),
                                                                Constants.MEMORY_FIELD,
                                                                Quantity.parse("100M")))
                                          .build()));
    }

    /**
     * Set spec to workflowTemplate.
     *
     * @param workflowTemplate workflowTemplate
     * @param graphDto         graph for workflowTemplate
     */
    private void setSpec(WorkflowTemplate workflowTemplate, String namespace, GraphDto graphDto) {
        workflowTemplate.setSpec(new WorkflowTemplateSpec()
                                     .serviceAccountName(serviceAccount)
                                     .entrypoint(Constants.DAG_TEMPLATE_NAME)
                                     .imagePullSecrets(List.of(new ImagePullSecret().name(imagePullSecret)))
                                     .templates(List.of(createNotificationTemplate(),
                                                        createSparkTemplate(namespace),
                                                        createTemplateWithDag(graphDto))));
    }

    /**
     * Create and save pipeline.
     *
     * @param id         pipeline id
     * @param name       pipeline name
     * @param definition definition for pipeline
     * @return WorkflowTemplate
     */
    private WorkflowTemplate createWorkflowTemplate(
        String projectId, String id, String name, JsonNode definition) {
        GraphDto graphDto = GraphDto.parseGraph(definition);
        GraphDto.validateGraphPipeline(graphDto, projectId, argoKubernetesService);

        WorkflowTemplate workflowTemplate = new WorkflowTemplate();
        setMeta(workflowTemplate, id, name, definition);
        setSpec(workflowTemplate, projectId, graphDto);

        return workflowTemplate;
    }

    /**
     * Check is pipeline name unique in project.
     *
     * @param projectId    projectId
     * @param pipelineId   pipeline id
     * @param pipelineName pipeline name
     */
    void checkPipelineName(String projectId, String pipelineId, String pipelineName) {
        List<WorkflowTemplate> workflowTemplatesByLabels =
            argoKubernetesService.getWorkflowTemplatesByLabels(projectId, Map.of(Constants.NAME, pipelineName));

        if (workflowTemplatesByLabels.size() > 1 ||
            (workflowTemplatesByLabels.size() == 1 &&
                !workflowTemplatesByLabels.get(0).getMetadata().getName().equals(pipelineId))) {
            throw new BadRequestException(String.format("Pipeline with name '%s' already exist in project %s",
                                                        pipelineName,
                                                        projectId));
        }
    }

    /**
     * Create or replace workflow template.
     *
     * @param projectId  project id
     * @param name       pipeline name
     * @param definition pipeline definition
     * @return pipeline id
     */
    public String create(String projectId, String name, JsonNode definition) {
        checkPipelineName(projectId, null, name);

        return createFromWorkflowTemplate(projectId,
                                          createWorkflowTemplate(projectId,
                                                                 UUID.randomUUID().toString(),
                                                                 name,
                                                                 definition),
                                          true);
    }

    /**
     * Creates a new pipeline from workflow template or replaces an existing one
     *
     * @param projectId       id of the project
     * @param template        workflow template
     * @param replaceIfExists determines whether wt should replace old one if their ids match
     * @return id of the pipeline
     */
    public String createFromWorkflowTemplate(
        final String projectId, WorkflowTemplate template, boolean replaceIfExists) {

        String id = template.getMetadata().getName();

        try {
            argoKubernetesService.getWorkflowTemplate(projectId, id);
            if (!replaceIfExists) {
                template.getMetadata().setName(UUID.randomUUID().toString());
            }
            return createFromWorkflowTemplate(projectId, template, replaceIfExists);
        } catch (ResourceNotFoundException ex) {
            LOGGER.info("It's ok, there is no job with such id: {}", id);
            argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, template);
            return id;
        }
    }

    /**
     * Get pipeline data.
     *
     * @param projectId project id
     * @param id        workflow id
     * @return json graph
     */
    public PipelineResponseDto getById(String projectId, String id) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, id);
        ObjectMeta metadata = workflowTemplate.getMetadata();
        Map<String, String> annotations = metadata.getAnnotations();
        try {
            boolean editable = isArgoResourceEditable(projectId, "workflowtemplates", Constants.UPDATE_ACTION);
            PipelineResponseDto pipelineResponseDto = ((PipelineResponseDto) new PipelineResponseDto()
                .id(id)
                .name(metadata.getLabels().get(Constants.NAME))
                .lastModified(annotations.get(Constants.LAST_MODIFIED))
                .status(DRAFT_STATUS))
                .editable(editable)
                .definition(MAPPER.readTree(Base64.decodeBase64(annotations.get(Constants.DEFINITION))));

            String value = annotations.get(STOPPED_AT);
            if (value == null) {
                appendRuntimeInfo(projectId, id, pipelineResponseDto);
            } else {
                Map<String, String> jobStatuses = new HashMap<>();
                for (Map.Entry<String, String> entry : annotations.entrySet()) {
                    if (entry.getKey().contains(ANNOTATION_JOB_STATUSES)) {
                        jobStatuses.put(entry.getKey().substring(ANNOTATION_JOB_STATUSES.length()),
                                        entry.getValue());
                    }

                }
                pipelineResponseDto
                    .startedAt(annotations.get(STARTED_AT))
                    .finishedAt(annotations.get(FINISHED_AT))
                    .status(annotations.get(STATUS))
                    .progress(Double.parseDouble(annotations.get(PROGRESS)))
                    .jobsStatuses(jobStatuses);
            }
            boolean accessibleToRun = isArgoResourceEditable(projectId, "workflows", Constants.CREATE_ACTION);
            appendRunnable(getDagTaskFromWorkflowTemplateSpec(workflowTemplate.getSpec()),
                           pipelineResponseDto,
                           accessibleToRun);

            return pipelineResponseDto;
        } catch (IOException e) {
            throw new InternalProcessingException("Unable to parse definition JSON", e);
        }
    }

    /**
     * Get runtime data.
     *
     * @param projectId project id
     * @param id        pipeline id
     * @return runtime data for dto
     */
    private RuntimeData getRunTimeData(String projectId, String id) {
        RuntimeData runtimeData = new RuntimeData();
        try {
            Workflow workflow = argoKubernetesService.getWorkflow(projectId, id);
            WorkflowStatus status = workflow.getStatus();
            String currentStatus = status.getPhase();
            if (STATUS_FAILED.equals(currentStatus)) {
                currentStatus = STATUS_ERROR;
            }

            Map<String, String> statuses = new HashMap<>();
            Map<String, NodeStatus> nodes = status.getNodes();
            Collection<NodeStatus> nodeStatuses = new ArrayList<>();
            if (nodes != null) {
                nodeStatuses = nodes.values();
            } else {
                LOGGER.error(status.getMessage());
            }

            runtimeData.setStartedAt(DateTimeUtils.getFormattedDateTime(status.getStartedAt().toString()));
            runtimeData.setFinishedAt(DateTimeUtils.getFormattedDateTime(String.valueOf(status.getFinishedAt())));
            runtimeData.setStatus(currentStatus);
            runtimeData.setProgress(status.getProgress());
            runtimeData.setJobsStatuses(statuses);

            for (NodeStatus nodeStatus : nodeStatuses) {
                if (Constants.NODE_TYPE_POD.equals(nodeStatus.getType())) {
                    String displayName = nodeStatus.getDisplayName();
                    Collection<Template> storedTemplates = status.getStoredTemplates().values();
                    statuses.putAll(storedTemplates
                                        .stream()
                                        .filter((Template storedTemplate) -> Constants.DAG_TEMPLATE_NAME.equals(
                                            storedTemplate.getName()))
                                        .flatMap((Template storedTemplate) -> storedTemplate
                                            .getDag()
                                            .getTasks()
                                            .stream())
                                        .filter((DagTask dagTask) -> displayName.equals(dagTask.getName()))
                                        .flatMap((DagTask dagTask) -> dagTask
                                            .getArguments()
                                            .getParameters()
                                            .stream())
                                        .filter((Parameter parameter) -> parameter.getName().equals(GRAPH_ID))
                                        .collect(Collectors.toMap(Parameter::getValue,
                                                                  (Parameter parameter) -> nodeStatus.getPhase())));
                }
            }
        } catch (ResourceNotFoundException e) {
            LOGGER.info("Pipeline {} has not started yet", id);
            runtimeData.setStatus(DRAFT_STATUS);
        }

        return runtimeData;
    }

    /**
     * Append runtime info.
     *
     * @param projectId project id
     * @param id        pipeline id
     * @param dto       dto
     */
    private void appendRuntimeInfo(String projectId, String id, PipelineOverviewDto dto) {
        RuntimeData runtimeData = getRunTimeData(projectId, id);

        dto
            .startedAt(runtimeData.getStartedAt())
            .finishedAt(runtimeData.getFinishedAt())
            .status(runtimeData.getStatus())
            .progress(runtimeData.getProgress())
            .jobsStatuses(runtimeData.getJobsStatuses());

    }

    /**
     * Getting all pipelines in project.
     *
     * @param projectId project id
     * @return pipelines list
     */
    public PipelineOverviewListDto getAll(String projectId) {
        List<WorkflowTemplate> allWorkflowTemplates = argoKubernetesService.getAllWorkflowTemplates(projectId);
        boolean accessibleToRun = isArgoResourceEditable(projectId, "workflows", Constants.CREATE_ACTION);

        List<PipelineOverviewDto> pipelinesList = new ArrayList<>(allWorkflowTemplates.size());
        for (WorkflowTemplate workflowTemplate : allWorkflowTemplates) {
            ObjectMeta metadata = workflowTemplate.getMetadata();
            String id = metadata.getName();
            PipelineOverviewDto pipelineOverviewDto = new PipelineOverviewDto()
                .id(id)
                .name(metadata.getLabels().get(Constants.NAME))
                .status(DRAFT_STATUS)
                .lastModified(metadata.getAnnotations().get(Constants.LAST_MODIFIED));
            try {
                argoKubernetesService.getCronWorkflow(projectId, id);
                pipelineOverviewDto.cron(true);
            } catch (ResourceNotFoundException e) {
                LOGGER.info("There is no cron: {}", id);
            }
            Map<String, String> annotations = metadata.getAnnotations();
            String value = annotations.get(STOPPED_AT);
            if (value == null) {
                appendRuntimeInfo(projectId, id, pipelineOverviewDto);
            } else {
                Map<String, String> jobStatuses = new HashMap<>();
                for (Map.Entry<String, String> entry : annotations.entrySet()) {
                    if (entry.getKey().startsWith(ANNOTATION_JOB_STATUSES)) {
                        jobStatuses.put(entry.getKey().substring(ANNOTATION_JOB_STATUSES.length()), entry.getValue());
                    }

                }
                pipelineOverviewDto
                    .startedAt(annotations.get(STARTED_AT))
                    .finishedAt(annotations.get(FINISHED_AT))
                    .status(annotations.get(STATUS))
                    .progress(Double.parseDouble(annotations.get(PROGRESS)))
                    .jobsStatuses(jobStatuses);
            }
            appendRunnable(getDagTaskFromWorkflowTemplateSpec(workflowTemplate.getSpec()),
                           pipelineOverviewDto,
                           accessibleToRun);

            pipelinesList.add(pipelineOverviewDto);
        }

        return PipelineOverviewListDto
            .builder()
            .pipelines(pipelinesList)
            .editable(isArgoResourceEditable(projectId, "workflowtemplates", Constants.UPDATE_ACTION))
            .build();
    }

    private boolean isArgoResourceEditable(String projectId, String resource, String action) {
        return argoKubernetesService.isAccessible(projectId, resource, "argoproj.io", action);
    }

    /**
     * Updating pipeline.
     *
     * @param id         pipeline id
     * @param projectId  project id
     * @param definition new definition
     * @param name       name
     */
    public void update(final String projectId, final String id, final JsonNode definition, final String name) {
        argoKubernetesService.getWorkflowTemplate(projectId, id);
        checkPipelineName(projectId, id, name);
        try {
            argoKubernetesService.deleteWorkflow(projectId, id);
        } catch (ResourceNotFoundException e) {
            LOGGER.info("No workflows to remove");
        }
        WorkflowTemplate newWorkflowTemplate = createWorkflowTemplate(projectId, id, name, definition);
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, newWorkflowTemplate);
    }

    /**
     * Delete workflow template.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    public void delete(String projectId, String id) {
        argoKubernetesService.deleteWorkflowTemplate(projectId, id);
        argoKubernetesService.deleteWorkflow(projectId, id);
    }


    /**
     * Running pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    public void run(String projectId, String id) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, id);
        addParametersToDagTasks(workflowTemplate, projectId);
        Map<String, String> annotations = workflowTemplate.getMetadata().getAnnotations();
        annotations.remove(STOPPED_AT);
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, workflowTemplate);
        argoKubernetesService.deleteWorkflow(projectId, id);
        Workflow workflow = new Workflow();
        workflow.setMetadata(new ObjectMetaBuilder().withName(id).build());
        workflow.setSpec(new WorkflowSpec().workflowTemplateRef(new WorkflowTemplateRef().name(id)));
        argoKubernetesService.createOrReplaceWorkflow(projectId, workflow);
    }


    /**
     * Stopping pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    public void stop(String projectId, String id) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, id);
        Workflow workflow = argoKubernetesService.getWorkflow(projectId, id);
        RuntimeData runtimeData = getRunTimeData(projectId, id);
        argoKubernetesService.deleteWorkflow(projectId, id);
        WorkflowStatus status = workflow.getStatus();
        Map<String, NodeStatus> nodes = status.getNodes();
        Collection<NodeStatus> nodeStatuses = new ArrayList<>();
        if (nodes != null) {
            nodeStatuses = nodes.values();
        } else {
            LOGGER.error(status.getMessage());
        }
        Map<String, String> annotations = workflowTemplate.getMetadata().getAnnotations();
        annotations.put(STOPPED_AT, " ");
        for (NodeStatus nodeStatus : nodeStatuses) {
            if (Constants.NODE_TYPE_POD.equals(nodeStatus.getType())) {
                if (nodeStatus.getPhase().equals("Running")) {
                    annotations.compute(STOPPED_AT, (key, value) -> value.concat(" " + nodeStatus.getName()));
                }
            }
        }
        annotations.put(STARTED_AT, runtimeData.getStartedAt());
        annotations.put(FINISHED_AT, runtimeData.getFinishedAt());
        annotations.put(STATUS, runtimeData.getStatus());
        annotations.put(PROGRESS, String.valueOf(runtimeData.getProgress()));
        Map<String, String> jobsStatuses = runtimeData.getJobsStatuses();
        for (Map.Entry<String, String> entry : jobsStatuses.entrySet()) {
            annotations.put(ANNOTATION_JOB_STATUSES.concat(entry.getKey()), entry.getValue());
        }
        workflowTemplate.getMetadata().setAnnotations(annotations);
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, workflowTemplate);

    }

    /**
     * Resuming pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    public void resume(String projectId, String id) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, id);
        addParametersToDagTasks(workflowTemplate, projectId);
        Map<String, String> annotations = workflowTemplate.getMetadata().getAnnotations();
        String stoppedStage = annotations.get(STOPPED_AT);
        annotations.remove(STOPPED_AT);
        argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, workflowTemplate);
        WorkflowTemplateSpec templateSpec = workflowTemplate.getSpec();
        List<DagTask> dagTasks = getDagTaskFromWorkflowTemplateSpec(templateSpec);
        int index = 0;
        for (DagTask dagTask : dagTasks) {
            if (stoppedStage.contains(dagTask.getName())) {
                index = dagTasks.indexOf(dagTask);
                break;
            }
        }
        List<DagTask> newDagTasks = dagTasks.subList(index, dagTasks.size());
        List<Template> templates = templateSpec.getTemplates();
        Template dagTemplate = templates
            .stream()
            .filter(template -> Constants.DAG_TEMPLATE_NAME.equals(template.getName()))
            .findAny()
            .orElseThrow(() -> new InternalProcessingException("Pipeline config is corrupted"));
        dagTemplate.getDag().setTasks(newDagTasks);
        WorkflowSpec workflowSpec = new WorkflowSpec()
            .entrypoint(templateSpec.getEntrypoint())
            .imagePullSecrets(templateSpec.getImagePullSecrets())
            .templates(templates)
            .serviceAccountName(templateSpec.getServiceAccountName());
        Workflow workflow = new Workflow();
        workflow.setMetadata(new ObjectMetaBuilder().withName(id).build());
        workflow.setSpec(workflowSpec);
        argoKubernetesService.createOrReplaceWorkflow(projectId, workflow);
    }

    /**
     * Create cron pipeline.
     *
     * @param projectId       project id
     * @param id              pipeline id
     * @param cronPipelineDto cron data
     */
    public void createCron(String projectId, String id, @Valid CronPipelineDto cronPipelineDto) {
        CronWorkflow cronWorkflow = new CronWorkflow();
        cronWorkflow.setMetadata(new ObjectMetaBuilder().withName(id).build());
        cronWorkflow.setSpec(CronWorkflowSpec.fromDtoAndWFTMPLName(cronPipelineDto, id));
        argoKubernetesService.createOrReplaceCronWorkflow(projectId, cronWorkflow);
    }

    /**
     * Delete cron pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    public void deleteCron(String projectId, String id) {
        argoKubernetesService.deleteCronWorkflow(projectId, id);
    }

    /**
     * Get cron pipeline data.
     *
     * @param projectId project id
     * @param id        workflow id
     * @return json graph
     */
    public CronPipelineDto getCronById(String projectId, String id) {
        CronWorkflow cronWorkflow = argoKubernetesService.getCronWorkflow(projectId, id);
        return CronPipelineDto.fromSpec(cronWorkflow.getSpec());
    }
}
