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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphDtoTest {

    private static final String INPUT_GRAPH = "{\n" +
        "  \"graph\": [\n" +
        "    {\n" +
        "       \"id\": \"-jRjFu5yR\",\n" +
        "       \"vertex\": true,\n" +
        "      \"value\": {\n" +
        "        \"label\": \"Read\",\n" +
        "        \"text\": \"stage\",\n" +
        "        \"desc\": \"description\",\n" +
        "        \"type\": \"read\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "       \"id\": \"cyVyU8Xfw\",\n" +
        "       \"vertex\": true,\n" +
        "      \"value\": {\n" +
        "        \"label\": \"Write\",\n" +
        "        \"text\": \"stage\",\n" +
        "        \"desc\": \"description\",\n" +
        "        \"type\": \"write\"\n" +
        "      }\n" +
        "    },\n" +
        "    {\n" +
        "      \"value\": {},\n" +
        "      \"id\": \"4\",\n" +
        "      \"edge\": true,\n" +
        "      \"parent\": \"1\",\n" +
        "      \"source\": \"-jRjFu5yR\",\n" +
        "      \"target\": \"cyVyU8Xfw\",\n" +
        "      \"successPath\": true,\n" +
        "      \"mxObjectId\": \"mxCell#8\"\n" +
        "    }\n" +
        "  ]\n" +
        "}";

    @Test
    void parseGraph() throws JsonProcessingException {
        List<GraphDto.NodeDto> expectedNodes = List.of(new GraphDto.NodeDto("-jRjFu5yR",
                                                                            Map.of("label",
                                                                                   "Read",
                                                                                   "text",
                                                                                   "stage",
                                                                                   "desc",
                                                                                   "description",
                                                                                   "type",
                                                                                   "read")),
                                                       new GraphDto.NodeDto("cyVyU8Xfw",
                                                                            Map.of("label",
                                                                                   "Write",
                                                                                   "text",
                                                                                   "stage",
                                                                                   "desc",
                                                                                   "description",
                                                                                   "type",
                                                                                   "write")));
        List<GraphDto.EdgeDto> expectedEdges =
            List.of(new GraphDto.EdgeDto(Map.of(), "-jRjFu5yR", "cyVyU8Xfw"));

        GraphDto graphDto = GraphDto.parseGraph(new ObjectMapper().readTree(INPUT_GRAPH));
        assertEquals(expectedNodes, graphDto.getNodes(),"Nodes must be equal to expected");
        assertEquals(expectedEdges, graphDto.getEdges(), "Edges must be equal to expected");
    }
}
