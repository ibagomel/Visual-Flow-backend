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

import by.iba.vfapi.model.auth.UserInfo;
import by.iba.vfapi.services.UserService;
import by.iba.vfapi.services.auth.AuthenticationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User controller class.
 */
@Slf4j
@Api(tags = "User API")
@RequiredArgsConstructor
@RestController
@RequestMapping("api")
public class UserController {
    private final AuthenticationService authenticationService;
    private final UserService userService;

    /**
     * Retrieves current user information.
     *
     * @return user information.
     */
    @ApiOperation(value = "Retrieves current user information")
    @GetMapping("/user")
    public UserInfo whoAmI() {
        LOGGER.info("Receiving information about current user");
        return authenticationService.getUserInfo();
    }

    /**
     * Retrieves application users.
     *
     * @return application users.
     */
    @ApiOperation(value = "Retrieves application users")
    @GetMapping("/users")
    public List<Map<String, String>> getUsers() {
        LOGGER.info("Receiving users of application");
        return userService.getUsers();
    }

    /**
     * Retrieves application roles.
     *
     * @return application roles.
     */
    @ApiOperation(value = "Retrieves application roles")
    @GetMapping("/roles")
    public List<String> getRoles() {
        LOGGER.info("Receiving roles of application");
        return userService.getRoleNames();
    }
}
