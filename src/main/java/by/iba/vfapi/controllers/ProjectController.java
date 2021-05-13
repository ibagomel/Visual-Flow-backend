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

import by.iba.vfapi.dto.ResourceUsageDto;
import by.iba.vfapi.dto.projects.AccessTableDto;
import by.iba.vfapi.dto.projects.ParamDto;
import by.iba.vfapi.dto.projects.ParamsDto;
import by.iba.vfapi.dto.projects.ProjectOverviewListDto;
import by.iba.vfapi.dto.projects.ProjectRequestDto;
import by.iba.vfapi.dto.projects.ProjectResponseDto;
import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.ProjectService;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project controller class.
 */
@Slf4j
@Api(tags = "Project API")
@RequiredArgsConstructor
@RestController
@RequestMapping("api/project")
@Validated
public class ProjectController {
    private final ProjectService projectService;
    private final AuthenticationService authenticationService;

    /**
     * Creates new project.
     *
     * @param projectDto object that contains initial data
     * @return ResponseEntity with status code
     */
    @ApiOperation(value = "Create project")
    @PostMapping
    public ResponseEntity<String> create(@RequestBody @Valid final ProjectRequestDto projectDto) {
        LOGGER.info("Creating project");
        String id = projectService.create(projectDto);
        LOGGER.info("Project '{}' successfully created", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(id);
    }

    /**
     * Gets project by id.
     *
     * @param id project id.
     * @return ResponseEntity with status code and project date (ProjectDto).
     */
    @ApiOperation(value = "Get project by id")
    @GetMapping("/{id}")
    public ProjectResponseDto get(@PathVariable final String id) {
        LOGGER.info("Receiving project '{}' ", id);
        return projectService.get(id);
    }

    /**
     * Gets project list.
     *
     * @return project list.
     */
    @ApiOperation(value = "Get project list")
    @GetMapping
    public ProjectOverviewListDto getAll() {
        LOGGER.info("Receiving list of projects");
        return projectService.getAll();
    }

    /**
     * Change project params.
     *
     * @param id         project id.
     * @param projectDto new project params.
     */
    @ApiOperation(value = "Update project")
    @PostMapping("/{id}")
    public void update(
        @PathVariable final String id, @RequestBody @Valid final ProjectRequestDto projectDto) {
        LOGGER.info("Updating project '{}'", id);
        projectService.update(id, projectDto);
        LOGGER.info("Project '{}' description and resource quota successfully updated", id);
    }

    /**
     * Deletes project by id.
     *
     * @param id project id.
     * @return ResponseEntity with 204 status code.
     */
    @ApiOperation(value = "Delete project by id")
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable final String id) {
        LOGGER.info("Deleting project '{}'", id);
        projectService.delete(id);
        LOGGER.info("Project '{}' successfully deleted", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Gets project resource utilization.
     *
     * @param id project id.
     * @return project usage info.
     */
    @ApiOperation(value = "Get project resource utilization")
    @GetMapping("/{id}/usage")
    public ResourceUsageDto getUsage(@PathVariable String id) {
        LOGGER.info("Receiving project '{}' resource utilization", id);
        return projectService.getUsage(id);
    }

    /**
     * Create or updates params for given project.
     *
     * @param id        project id.
     * @param paramsDto list of parameters to create/update.
     */
    @ApiOperation(value = "Create or updates params for given project")
    @PostMapping("/{id}/params")
    public void updateParams(
        @PathVariable final String id, @RequestBody @Valid final List<ParamDto> paramsDto) {
        LOGGER.info("Updating params for project '{}'", id);
        projectService.updateParams(id, paramsDto);
        LOGGER.info("Params for project '{}' successfully updated", id);
    }

    /**
     * Gets params for given project.
     *
     * @param id project id.
     * @return project parameters.
     */
    @ApiOperation(value = "Gets params for given project")
    @GetMapping("/{id}/params")
    public ParamsDto getParams(@PathVariable final String id) {
        LOGGER.info("Receiving params for the '{}' project", id);
        return projectService.getParams(id);
    }

    /**
     * Applies access grants for given project.
     *
     * @param id          project id.
     * @param accessTable user - role map.
     */
    @ApiOperation(value = "Applies access grants for given project")
    @PostMapping("/{id}/users")
    public void applyAccessTable(
        @PathVariable final String id, @RequestBody final Map<String, String> accessTable) {
        LOGGER.info("Applying access grants for the project '{}'", id);
        UserInfo currentUser = authenticationService.getUserInfo();
        projectService.createAccessTable(id, accessTable, currentUser.getUsername());
        LOGGER.info("Grants for project '{}' successfully applied", id);
    }

    /**
     * Retrieves access grants for given project.
     *
     * @param id project id.
     * @return user - role map.
     */
    @ApiOperation(value = "Retrieves access grants for given project")
    @GetMapping("/{id}/users")
    public AccessTableDto getAccessTable(@PathVariable final String id) {
        LOGGER.info("Receiving access grants table for the project '{}'", id);
        return projectService.getAccessTable(id);
    }
}
