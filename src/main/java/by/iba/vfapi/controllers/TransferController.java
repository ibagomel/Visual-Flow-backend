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

package by.iba.vfapi.controllers;

import by.iba.vfapi.dto.exporting.ExportRequestDto;
import by.iba.vfapi.dto.exporting.ExportResponseDto;
import by.iba.vfapi.dto.importing.ImportAccessDto;
import by.iba.vfapi.dto.importing.ImportRequestDto;
import by.iba.vfapi.dto.importing.ImportResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.services.TransferService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.HashSet;
import java.util.Set;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transfer controller class.
 */

@Api(tags = "Import/Export API")
@RequiredArgsConstructor
@RestController
@RequestMapping("api/project")
public class TransferController {
    private final TransferService transferService;

    /**
     * Export.
     *
     * @param projectId        project id
     * @param exportRequestDto dto with job ids and pipelines for export
     * @return object with exported jobs and pipelines
     */
    @ApiOperation(value = "Export")
    @PostMapping("{projectId}/exportResources")
    public ExportResponseDto exporting(
        @PathVariable String projectId, @RequestBody @Valid ExportRequestDto exportRequestDto) {
        return transferService.exporting(projectId, exportRequestDto.getJobIds(), exportRequestDto.getPipelines());
    }

    /**
     * Import.
     *
     * @param projectId        project id
     * @param importRequestDto dto with jobs ids and pipelines ids for export
     * @return object witch contains not imported ids of pipelines and jobs
     */
    @ApiOperation(value = "Import")
    @PostMapping("{projectId}/importResources")
    public ImportResponseDto importing(
        @PathVariable String projectId, @RequestBody @Valid ImportRequestDto importRequestDto) {
        Set<String> jobs = new HashSet<>(importRequestDto.getJobs());
        Set<String> pipelines = new HashSet<>(importRequestDto.getPipelines());
        if (jobs.size() != importRequestDto.getJobs().size()) {
            throw new BadRequestException("Jobs not unique");
        }
        if (pipelines.size() != importRequestDto.getPipelines().size()) {
            throw new BadRequestException("Pipelines not unique");
        }
        return transferService.importing(projectId, jobs, pipelines);
    }

    /**
     * Get access flag.
     *
     * @param projectId project id
     * @return pipeline flag
     */
    @GetMapping(value = "{projectId}/checkAccess")
    public ImportAccessDto checkAccessToImport(@PathVariable String projectId) {
        return new ImportAccessDto(transferService.checkImportAccess(projectId));
    }

    /**
     * Copies job.
     *
     * @param projectId project id
     * @param jobId job id
     */
    @PostMapping("{projectId}/{jobId}/copyJob")
    public void copyJob(@PathVariable String projectId, @PathVariable String jobId) {
        transferService.copyJob(projectId, jobId);
    }

    /**
     * Copies pipeline.
     *
     * @param projectId project id
     * @param pipelineId pipelineId id
     */
    @PostMapping("{projectId}/{pipelineId}/copyPipeline")
    public void copyPipeline(@PathVariable String projectId, @PathVariable String pipelineId) {
        transferService.copyPipeline(projectId, pipelineId);
    }
}
