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

package by.iba.vfapi.dto;

import by.iba.vfapi.exceptions.BadRequestException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Graph DTO class.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class GraphDto {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<NodeDto> nodes;
    private List<EdgeDto> edges;

    /**
     * Parse definition to nodes and edges.
     *
     * @param definition json
     * @return GraphDto with nodes and edges
     */
    public static GraphDto parseGraph(JsonNode definition) {
        ArrayNode nodesArray = definition.withArray("graph");

        List<GraphDto.NodeDto> nodes = new ArrayList<>();
        List<GraphDto.EdgeDto> edges = new ArrayList<>();
        for (JsonNode node : nodesArray) {
            if (node.get("vertex") != null) {
                nodes.add(MAPPER.convertValue(node, GraphDto.NodeDto.class));
            } else {
                edges.add(MAPPER.convertValue(node, GraphDto.EdgeDto.class));
            }
        }

        return new GraphDto(nodes, edges);
    }

    /**
     * Creating data for configMap.
     *
     * @return data Map(String, String)
     */
    public Map<String, String> createConfigMapData() {
        try {
            return Map.of(Constants.JOB_CONFIG_FIELD, MAPPER.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Bad graph structure", e);
        }
    }

    /**
     * Node Dto class.
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NodeDto {
        private String id;
        private Map<String, String> value;
    }

    /**
     * Edge DTO class
     */
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EdgeDto {
        private Map<String, String> value;
        private String source;
        private String target;
    }
}
