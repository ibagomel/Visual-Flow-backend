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

import by.iba.vfapi.dto.LogDto;
import by.iba.vfapi.dto.jobs.JobOverviewDto;
import by.iba.vfapi.dto.jobs.JobOverviewListDto;
import by.iba.vfapi.dto.jobs.JobRequestDto;
import by.iba.vfapi.dto.jobs.JobResponseDto;
import by.iba.vfapi.services.JobService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
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
 * Job controller class.
 */
@Slf4j
@Api(tags = "Job API")
@RequiredArgsConstructor
@RestController
@RequestMapping("api/project")
public class JobController {
    private final JobService jobService;

    /**
     * Get all jobs in project.
     *
     * @param projectId project id
     * @return ResponseEntity with jobs graphs
     */
    @ApiOperation(value = "Get all jobs in project")
    @GetMapping("{projectId}/job")
    public JobOverviewListDto getAll(@PathVariable String projectId) {
        LOGGER.info("Receiving all jobs in project '{}'", projectId);
        //TODO replace full method body on "return jobService.getAll(projectId)" and remove method transform
        JobOverviewListDto jobs = jobService.getAll(projectId);

        return JobOverviewDto.withPipelineJobs(jobs);
    }

    /**
     * Creating new job in project.
     *
     * @param projectId     project id
     * @param jobRequestDto object with name and graph
     * @return ResponseEntity with id of new job
     */
    @ApiOperation(value = "Creating new job in project")
    @PostMapping("{projectId}/job")
    public ResponseEntity<String> create(
        @PathVariable String projectId, @Valid @RequestBody JobRequestDto jobRequestDto) {
        LOGGER.info("Creating new job in project '{}'", projectId);
        String id = jobService.create(projectId, jobRequestDto);
        LOGGER.info("Job '{}' in project '{}' successfully created", id, projectId);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /**
     * Updating job in project by id.
     *
     * @param projectId     project id
     * @param id            job id
     * @param jobRequestDto object with name and graph
     */
    @ApiOperation(value = "Updating job in project by id")
    @PostMapping("{projectId}/job/{id}")
    public void update(
        @PathVariable String projectId, @PathVariable String id, @Valid @RequestBody JobRequestDto jobRequestDto) {
        LOGGER.info("Updating job '{}' in project '{}'", id, projectId);
        jobService.update(id, projectId, jobRequestDto);
        LOGGER.info("Job '{}' in project '{}' successfully updated", id, projectId);
    }

    /**
     * Getting job in project by id.
     *
     * @param projectId project id
     * @param id        job id
     * @return ResponseEntity with job graph
     */
    @ApiOperation(value = "Getting job in project by id")
    @GetMapping("{projectId}/job/{id}")
    public JobResponseDto get(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Receiving job '{}' in project '{}'", id, projectId);
        return jobService.get(projectId, id);
    }

    /**
     * Deleting job in project by id.
     *
     * @param projectId project id
     * @param id        job id
     */
    @ApiOperation(value = "Deleting job in project by id")
    @DeleteMapping("{projectId}/job/{id}")
    public ResponseEntity<String> delete(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Deleting '{}' job in project '{}'", id, projectId);
        jobService.delete(projectId, id);
        LOGGER.info("Job '{}' in project '{}' successfully deleted", id, projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Getting job logs.
     *
     * @param projectId project id
     * @param id        job id
     * @return ResponseEntity with list of logs objects
     */
    @ApiOperation(value = "Getting job logs")
    @GetMapping("{projectId}/job/{id}/logs")
    public List<LogDto> getLogs(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Receiving job '{}' logs in project '{}'", id, projectId);
        return jobService.getLogs(projectId, id);
    }

    /**
     * Run job.
     *
     * @param projectId project id
     * @param id        job id
     */
    @ApiOperation(value = "Run job")
    @PostMapping("{projectId}/job/{id}/run")
    public void run(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Running job '{}' in project '{}'", id, projectId);
        jobService.run(projectId, id);
        LOGGER.info("Job '{}' in project '{}' successfully started", id, projectId);
    }

    /**
     * Stop job.
     *
     * @param projectId project id
     * @param id        job id
     */
    @ApiOperation(value = "Stop job")
    @PostMapping("{projectId}/job/{id}/stop")
    public void stop(@PathVariable String projectId, @PathVariable String id) {
        LOGGER.info("Stopping job '{}' in project '{}'", id, projectId);
        jobService.stop(projectId, id);
        LOGGER.info("Job '{}' in project '{}' successfully stopped", id, projectId);
    }
}
