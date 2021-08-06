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
import by.iba.vfapi.dto.exporting.ExportRequestDto;
import by.iba.vfapi.dto.exporting.ExportResponseDto;
import by.iba.vfapi.dto.exporting.PipelinesWithRelatedJobs;
import by.iba.vfapi.dto.importing.ImportResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.model.argo.WorkflowTemplate;
import by.iba.vfapi.model.argo.WorkflowTemplateSpec;
import com.google.common.collect.Sets;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * TransferService class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {
    private final ArgoKubernetesService argoKubernetesService;
    private final JobService jobService;
    private final PipelineService pipelineService;


    /**
     * Exporting jobs by ids.
     *
     * @param projectId project id
     * @param jobIds    jobs ids to export
     * @return list of jsons
     */
    private Set<ConfigMap> exportJobs(final String projectId, final Set<String> jobIds) {
        Set<ConfigMap> exportedJobs = Sets.newHashSetWithExpectedSize(jobIds.size());
        for (String jobId : jobIds) {
            ConfigMap configMap = argoKubernetesService.getConfigMap(projectId, jobId);
            ObjectMeta metadata = configMap.getMetadata();
            ConfigMap configMapForExport = new ConfigMapBuilder()
                .withMetadata(new ObjectMetaBuilder()
                                  .withName(metadata.getName())
                                  .addToAnnotations(metadata.getAnnotations())
                                  .addToLabels(metadata.getLabels())
                                  .build())
                .withData(configMap.getData())
                .build();
            exportedJobs.add(configMapForExport);
        }

        return exportedJobs;
    }

    /**
     * Exporting pipelines by ids.
     *
     * @param projectId project id
     * @param pipelines jobs ids to export
     * @return list of jsons
     */
    private Set<PipelinesWithRelatedJobs> exportPipelines(
        final String projectId, final Set<ExportRequestDto.PipelineRequest> pipelines) {
        Set<PipelinesWithRelatedJobs> exportedPipelines = Sets.newHashSetWithExpectedSize(pipelines.size());
        for (ExportRequestDto.PipelineRequest pipeline : pipelines) {
            WorkflowTemplate workflowTemplate =
                argoKubernetesService.getWorkflowTemplate(projectId, pipeline.getPipelineId());
            ObjectMeta metadata = workflowTemplate.getMetadata();
            WorkflowTemplateSpec spec = workflowTemplate.getSpec();

            WorkflowTemplate workflowTemplateForExport = new WorkflowTemplate();
            workflowTemplateForExport.setMetadata(new ObjectMetaBuilder()
                                                      .withName(metadata.getName())
                                                      .addToAnnotations(metadata.getAnnotations())
                                                      .addToLabels(metadata.getLabels())
                                                      .addToLabels("type", "pipeline")
                                                      .build());
            workflowTemplateForExport.setSpec(spec);
            PipelinesWithRelatedJobs pipelinesWithRelatedJobs =
                new PipelinesWithRelatedJobs(workflowTemplateForExport);

            if (pipeline.isWithRelatedJobs()) {
                List<String> jobIds = PipelineService
                    .getDagTaskFromWorkflowTemplateSpec(spec)
                    .stream()
                    .map(task -> task
                        .getArguments()
                        .getParameters()
                        .stream()
                        .filter(param -> K8sUtils.CONFIGMAP.equals(param.getName()))
                        .findFirst())
                    .filter(Optional::isPresent)
                    .map(parameter -> parameter.get().getValue())
                    .collect(Collectors.toList());
                pipelinesWithRelatedJobs.getRelatedJobIds().addAll(jobIds);
            }

            exportedPipelines.add(pipelinesWithRelatedJobs);
        }

        return exportedPipelines;
    }

    /**
     * Importing jobs.
     *
     * @param projectId project id
     * @param jsonJobs  jsonJobs for import
     * @return list with not imported jobs ids
     */
    private List<String> importJobs(final String projectId, final Set<ConfigMap> jsonJobs) {
        List<String> notImported = new LinkedList<>();
        for (ConfigMap configMap : jsonJobs) {
            String id = configMap.getMetadata().getName();
            String name = configMap.getMetadata().getLabels().get(Constants.NAME);
            configMap
                .getMetadata()
                .getAnnotations()
                .put(Constants.LAST_MODIFIED, ZonedDateTime.now().format(Constants.DATE_TIME_FORMATTER));
            try {
                jobService.checkJobName(projectId, id, name);
                argoKubernetesService.createOrReplaceConfigMap(projectId, configMap);
            } catch (BadRequestException ex) {
                LOGGER.error(ex.getMessage(), ex);
                notImported.add(id);
            }
        }

        return notImported;
    }

    /**
     * Importing pipelines.
     *
     * @param projectId     project id
     * @param jsonPipelines jsonPipelines for import
     * @return list with not imported pipelines ids
     */
    private List<String> importPipelines(final String projectId, final Set<WorkflowTemplate> jsonPipelines) {
        List<String> notImported = new LinkedList<>();

        for (WorkflowTemplate workflowTemplate : jsonPipelines) {
            String name = workflowTemplate.getMetadata().getLabels().get(Constants.NAME);
            String id = workflowTemplate.getMetadata().getName();
            workflowTemplate
                .getMetadata()
                .getAnnotations()
                .put(Constants.LAST_MODIFIED, ZonedDateTime.now().format(Constants.DATE_TIME_FORMATTER));
            try {
                pipelineService.checkPipelineName(projectId, id, name);
                argoKubernetesService.createOrReplaceWorkflowTemplate(projectId, workflowTemplate);
                argoKubernetesService.deleteWorkflow(projectId, id);
            } catch (BadRequestException ex) {
                LOGGER.error(ex.getMessage(), ex);
                notImported.add(id);
            } catch (ResourceNotFoundException e) {
                LOGGER.info("No workflows to remove");
            }
        }

        return notImported;
    }

    /**
     * Export jobs and pipelines.
     *
     * @param projectId project id
     * @param jobIds    job ids for import
     * @param pipelines pipelines ids and flag with jobs
     * @return pipelines and jobs in json format
     */
    public ExportResponseDto exporting(
        final String projectId, final Set<String> jobIds, final Set<ExportRequestDto.PipelineRequest> pipelines) {
        Set<PipelinesWithRelatedJobs> pipelinesWithRelatedJobs = exportPipelines(projectId, pipelines);

        Set<String> jobsWithPipelineJobsIds = new HashSet<>(jobIds);
        Set<WorkflowTemplate> exportedPipelines = new HashSet<>();

        pipelinesWithRelatedJobs.forEach((PipelinesWithRelatedJobs pipelineWithRelatedJobs) -> {
            jobsWithPipelineJobsIds.addAll(pipelineWithRelatedJobs.getRelatedJobIds());
            exportedPipelines.add(pipelineWithRelatedJobs.getWorkflowTemplate());
        });

        return ExportResponseDto
            .builder()
            .jobs(exportJobs(projectId, jobsWithPipelineJobsIds))
            .pipelines(exportedPipelines)
            .build();
    }

    /**
     * Import jobs and pipelines.
     *
     * @param projectId     project id
     * @param jsonJobs      jobs in json format
     * @param jsonPipelines pipelines in json format
     * @return not imported pipelines and jobs ids
     */
    public ImportResponseDto importing(
        String projectId, Set<ConfigMap> jsonJobs, Set<WorkflowTemplate> jsonPipelines) {
        List<String> notImportedJobs = importJobs(projectId, jsonJobs);
        List<String> notImportedPipelines = importPipelines(projectId, jsonPipelines);

        return ImportResponseDto
            .builder()
            .notImportedJobs(notImportedJobs)
            .notImportedPipelines(notImportedPipelines)
            .build();
    }

    /**
     * Checks if user has permission to use import functionality
     *
     * @param projectId id of the project
     * @return true if user has the access
     */
    public boolean checkImportAccess(String projectId) {
        return argoKubernetesService.isAccessible(projectId, "configmaps", "", Constants.CREATE_ACTION);
    }

    /**
     * Copies job.
     *
     * @param projectId project id
     * @param jobId     job id
     */
    public void copyJob(final String projectId, final String jobId) {
        ConfigMap configMap = argoKubernetesService.getConfigMap(projectId, jobId);
        copyEntity(
            projectId,
            configMap,
            (projId, confMap) -> jobService.createFromConfigMap(projId, confMap, false),
            argoKubernetesService.getAllConfigMaps(projectId));

    }

    /**
     * Copies pipeline.
     *
     * @param projectId  project id
     * @param pipelineId pipeline id
     */
    public void copyPipeline(final String projectId, final String pipelineId) {
        WorkflowTemplate workflowTemplate = argoKubernetesService.getWorkflowTemplate(projectId, pipelineId);
        copyEntity(
            projectId,
            workflowTemplate,
            (projId, wfTemplate) -> pipelineService.createFromWorkflowTemplate(projId, wfTemplate, false),
            argoKubernetesService.getAllWorkflowTemplates(projectId));
    }

    /**
     * Creates a copy of existing entity
     *
     * @param projectId          id of the project
     * @param entityMetadata     entity's metadata
     * @param saver              lambda for saving the copy
     * @param entityMetadataList list of all available entities(of the same type) from current project
     * @param <T>                entity's metadata
     */
    private <T extends HasMetadata> void copyEntity(
        final String projectId, T entityMetadata, BiConsumer<String, T> saver, List<T> entityMetadataList) {
        String currentName = entityMetadata.getMetadata().getLabels().get(Constants.NAME);
        int availableIndex = getNextEntityCopyIndex(currentName, entityMetadataList);
        if (availableIndex == 1) {
            entityMetadata.getMetadata().getLabels().replace(Constants.NAME, currentName, currentName + "-Copy");
        } else {
            entityMetadata
                .getMetadata()
                .getLabels()
                .replace(Constants.NAME, currentName, currentName + "-Copy" + availableIndex);

        }
        String newId = UUID.randomUUID().toString();
        entityMetadata.getMetadata().setName(newId);
        saver.accept(projectId, entityMetadata);
    }

    /**
     * Returns the next available index
     *
     * @param entityName         name of the entity
     * @param entityMetadataList list of all entities
     * @param <T>                entity type
     * @return number of copies
     */
    private <T extends HasMetadata> int getNextEntityCopyIndex(String entityName, List<T> entityMetadataList) {
        Pattern groupIndex = Pattern.compile(String.format("^%s-Copy(\\d+)?$", Pattern.quote(entityName)));
        return entityMetadataList.stream().map((T e) -> {
            String name = e.getMetadata().getLabels().get(Constants.NAME);
            Matcher matcher = groupIndex.matcher(name);
            if (!matcher.matches()) {
                return 0;
            }
            return Optional.ofNullable(matcher.group(1)).map(Integer::valueOf).orElse(1);
        }).max(Comparator.naturalOrder()).map(index -> index + 1).orElse(1);

    }
}
