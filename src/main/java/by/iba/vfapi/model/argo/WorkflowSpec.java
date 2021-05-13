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

package by.iba.vfapi.model.argo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Class represents spec for custom resource Workflow.
 */
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowSpec implements Serializable {
    private static final long serialVersionUID = 1;

    private Arguments arguments;
    private WorkflowTemplateRef workflowTemplateRef;

    /**
     * Setter for workflowTemplateRef.
     *
     * @param workflowTemplateRef WorkflowTemplateRef object
     * @return this
     */
    public WorkflowSpec workflowTemplateRef(WorkflowTemplateRef workflowTemplateRef) {
        this.workflowTemplateRef = workflowTemplateRef;
        return this;
    }
}
