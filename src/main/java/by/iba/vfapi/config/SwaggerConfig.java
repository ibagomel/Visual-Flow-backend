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

package by.iba.vfapi.config;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.Contact;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.OperationContext;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * Class for Swagger configuration.
 *
 * @author mkaleda
 */
@Configuration
public class SwaggerConfig {
    public static final String AUTHORIZATION_HEADER = "Authorization";

    private final String defaultIncludePattern;

    /**
     * Creates swagger configuration.
     *
     * @param basePath context path from servlet
     */
    public SwaggerConfig(@Value("${server.servlet.context-path}") String basePath) {
        defaultIncludePattern = new URIBuilder()
            .setPathSegments(List
                                 .of(basePath, "api/.*")
                                 .stream()
                                 .flatMap(s -> Arrays.stream(s.split("/")).filter(e -> !e.isEmpty()))
                                 .collect(Collectors.toList()))
            .toString();
    }

    /**
     * Provide api info.
     *
     * @return api info
     */
    private static ApiInfo apiInfo() {
        return new ApiInfo(
            "Visual Flow",
            "API FOR MANAGE SPARK JOBS",
            null,
            null,
            new Contact("Project owner", "", "ihardzeenka@gomel.iba.by"),
            null,
            null,
            Collections.emptyList());
    }

    /**
     * ApiKey for swagger.
     *
     * @return ApiKey
     */
    private static ApiKey apiKey() {
        return new ApiKey("JWT", AUTHORIZATION_HEADER, "header");
    }

    /**
     * Configuration SecurityContext for swagger.
     *
     * @param pattern regex pattern for path.
     * @return context
     */
    private static SecurityContext securityContext(String pattern) {
        return SecurityContext
            .builder()
            .securityReferences(defaultAuth())
            .operationSelector((OperationContext o) -> PathSelectors
                .regex(pattern)
                .test(o.requestMappingPattern()))
            .build();
    }

    /**
     * Configuration defaultAuth for swagger.
     *
     * @return List of SecurityReference
     */
    private static List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Lists.newArrayList(new SecurityReference("JWT", authorizationScopes));
    }

    /**
     * Swagger configuration.
     *
     * @return Docket value
     */
    @Bean
    public Docket docket() {
        Docket docket = new Docket(DocumentationType.SWAGGER_2)
            .pathMapping("/")
            .apiInfo(apiInfo())
            .forCodeGeneration(true)
            .securityContexts(Lists.newArrayList(securityContext(defaultIncludePattern)))
            .securitySchemes(Lists.newArrayList(apiKey()))
            .useDefaultResponseMessages(false);

        return docket
            .select()
            .paths(PathSelectors.regex(defaultIncludePattern))
            .apis(RequestHandlerSelectors.basePackage("by.iba.vfapi"))
            .build();
    }
}
