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
import by.iba.vfapi.dto.importing.ImportRequestDto;
import by.iba.vfapi.dto.importing.ImportResponseDto;
import by.iba.vfapi.exceptions.BadRequestException;
import by.iba.vfapi.services.TransferService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {
    @Mock
    private TransferService transferService;
    @InjectMocks
    private TransferController transferController;

    @Test
    void testExporting() {
        when(transferService.exporting("projectId",
                                       Set.of("jobId1", "jobId2"),
                                       Set.of(new ExportRequestDto.PipelineRequest("pipelineId1",
                                                                                   true)))).thenReturn(
            ExportResponseDto
                .builder()
                .jobs(Set.of("jobId1Json",
                             "jobId2Json",
                             "pipelineId1RelatedJob1Json"))
                .pipelines(Set.of("pipelineId1Json"))
                .build());

        ExportResponseDto response = transferController.exporting("projectId",
                                                                  new ExportRequestDto(Set.of("jobId1", "jobId2"),
                                                                                       Set.of(new ExportRequestDto.PipelineRequest(
                                                                                           "pipelineId1",
                                                                                           true))));
        ExportResponseDto exportResponseDto = ExportResponseDto
            .builder()
            .jobs(Set.of("jobId1Json", "jobId2Json", "pipelineId1RelatedJob1Json"))
            .pipelines(Set.of("pipelineId1Json"))
            .build();

        assertEquals(exportResponseDto, response);
    }

    @Test
    void testImporting() {
        when(transferService.importing("projectId",
                                       Set.of("JobForImport1", "JobForImport2"),
                                       Set.of("PipelineForImport1"))).thenReturn(ImportResponseDto
                                                                                     .builder()
                                                                                     .notImportedJobs(List.of())
                                                                                     .notImportedPipelines(List.of())
                                                                                     .build());

        ImportResponseDto importing = transferController.importing("projectId",
                                                                   new ImportRequestDto(List.of(
                                                                       "PipelineForImport1"),
                                                                                        List.of("JobForImport1",
                                                                                                "JobForImport2")));

        ImportResponseDto expected =
            ImportResponseDto.builder().notImportedJobs(List.of()).notImportedPipelines(List.of()).build();
        assertEquals(expected, importing);
        verify(transferService).importing(anyString(), anySet(), anySet());
    }

    @Test
    void testImportingNotUniqueJobs() {
        ImportRequestDto dto = new ImportRequestDto(List.of(), List.of("JobForImport", "JobForImport"));
        assertThrows(BadRequestException.class, () -> transferController.importing("projectId", dto));
    }

    @Test
    void testImportingNotUniquePipelines() {
        ImportRequestDto dto = new ImportRequestDto(List.of("PipelineForImport", "PipelineForImport"), List.of());
        assertThrows(BadRequestException.class, () -> transferController.importing("projectId", dto));
    }

    @Test
    void testCheckAccessToImport() {
        String projectId = "projectId";
        when(transferService.checkImportAccess(projectId)).thenReturn(true);
        assertTrue(transferController.checkAccessToImport(projectId).isAccess());
        verify(transferService).checkImportAccess(anyString());
    }

    @Test
    void testCopyJob() {
        String projectId = "projectId";
        String jobId = "jobId";
        transferController.copyJob(projectId, jobId);
        verify(transferService).copyJob(anyString(), anyString());
    }

    @Test
    void testCopyPipeline() {
        String projectId = "projectId";
        String pipelineId = "pipelineId";
        transferController.copyPipeline(projectId, pipelineId);
        verify(transferService).copyPipeline(anyString(), anyString());
    }
}