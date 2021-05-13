/*
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

package by.iba.vfapi.controllers;

import by.iba.vfapi.dto.pipelines.CronPipelineDto;
import by.iba.vfapi.dto.pipelines.PipelineOverviewListDto;
import by.iba.vfapi.dto.pipelines.PipelineRequestDto;
import by.iba.vfapi.dto.pipelines.PipelineResponseDto;
import by.iba.vfapi.services.PipelineService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manage requests for pipelines.
 */
@Slf4j
@Api(tags = "Pipeline API")
@RequiredArgsConstructor
@RequestMapping("api/project")
@RestController
public class PipelineController {

    private final PipelineService pipelineService;

    /**
     * Create pipeline.
     *
     * @param projectId          project id
     * @param pipelineRequestDto id and graph for pipeline
     * @return ResponseEntity
     */
    @PostMapping(value = "{projectId}/pipeline")
    public ResponseEntity<String> create(
        @PathVariable String projectId, @Valid @RequestBody PipelineRequestDto pipelineRequestDto) {
        LOGGER.info("Creating pipeline in project '{}'", projectId);
        String id =
            pipelineService.create(projectId, pipelineRequestDto.getName(), pipelineRequestDto.getDefinition());
        LOGGER.info("Pipeline '{}' in project '{}' successfully created", id, projectId);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /**
     * Get pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     * @return pipeline graph
     */
    @GetMapping(value = "{projectId}/pipeline/{id}")
    public PipelineResponseDto get(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Receiving pipeline '{}' in project '{}'", id, projectId);
        return pipelineService.getById(projectId, id);
    }

    /**
     * Update pipeline.
     *
     * @param projectId          project id
     * @param id                 current pipeline id
     * @param pipelineRequestDto new id and graph for pipeline
     */
    @PostMapping(value = "{projectId}/pipeline/{id}")
    public void update(
        @PathVariable String projectId,
        @PathVariable String id,
        @Valid @RequestBody PipelineRequestDto pipelineRequestDto) {
        LOGGER.info("Updating pipeline '{}' in project '{}'", id, projectId);
        pipelineService.update(projectId, id, pipelineRequestDto.getDefinition(), pipelineRequestDto.getName());
        LOGGER.info("Pipeline '{}' in project '{}' successfully updated", id, projectId);

    }

    /**
     * Delete pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    @DeleteMapping(value = "{projectId}/pipeline/{id}")
    public ResponseEntity<String> delete(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Deleting pipeline '{}' in project '{}'", id, projectId);
        pipelineService.delete(projectId, id);
        LOGGER.info("Pipeline '{}' in project '{}' successfully deleted", id, projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all pipelines in project.
     *
     * @param projectId project id
     * @return ResponseEntity with jobs graphs
     */
    @ApiOperation(value = "Get all pipelines in project")
    @GetMapping("{projectId}/pipeline")
    public PipelineOverviewListDto getAll(@PathVariable String projectId) {
        LOGGER.info("Receiving all pipelines in project '{}'", projectId);
        return pipelineService.getAll(projectId);
    }

    /**
     * Run pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    @PostMapping(value = "{projectId}/pipeline/{id}/run")
    public void run(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Running pipeline '{}' in project '{}'", id, projectId);
        pipelineService.run(projectId, id);
        LOGGER.info("Pipeline '{}' in project '{}' successfully started", id, projectId);

    }

    /**
     * Stop pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    @PostMapping(value = "{projectId}/pipeline/{id}/stop")
    public void stop(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Stopping pipeline '{}' in project '{}'", id, projectId);
        pipelineService.stop(projectId, id);
        LOGGER.info("Pipeline '{}' in project '{}' stopped successfully ", id, projectId);

    }

    /**
     * Resume pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    @PostMapping(value = "{projectId}/pipeline/{id}/resume")
    public void resume(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Resuming pipeline '{}' in project '{}'", id, projectId);
        pipelineService.resume(projectId, id);
        LOGGER.info("Pipeline '{}' in project '{}' resumed successfully ", id, projectId);

    }

    /**
     * Create CRON pipeline.
     *
     * @param projectId       project id
     * @param id              pipeline id
     * @param cronPipelineDto cron data
     */
    @PostMapping(value = "{projectId}/pipeline/{id}/cron")
    public void createCron(
        @PathVariable String projectId,
        @PathVariable String id,
        @Valid @RequestBody CronPipelineDto cronPipelineDto) {
        LOGGER.info("Creating cron on pipeline '{}' in project '{}'", id, projectId);
        pipelineService.createCron(projectId, id, cronPipelineDto);
        LOGGER.info("Cron on pipeline '{}' in project '{}' successfully created", id, projectId);
    }

    /**
     * Stop CRON pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     */
    @DeleteMapping(value = "{projectId}/pipeline/{id}/cron")
    public void deleteCron(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Deleting cron on pipeline '{}' in project '{}' ", id, projectId);
        pipelineService.deleteCron(projectId, id);
        LOGGER.info("Cron on pipeline '{}' in project '{}' successfully deleted", id, projectId);
    }

    /**
     * Get cron pipeline.
     *
     * @param projectId project id
     * @param id        pipeline id
     * @return pipeline graph
     */
    @GetMapping(value = "{projectId}/pipeline/{id}/cron", produces = "application/json")
    public CronPipelineDto getCronPipeline(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Receiving cron on pipeline '{}' in project '{}'", id, projectId);
        return pipelineService.getCronById(projectId, id);
    }
}
