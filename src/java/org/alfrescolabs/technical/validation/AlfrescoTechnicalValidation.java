/*
 * Copyright (C) 2014 Peter Monks.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This file is part of an unsupported extension to Alfresco.
 * 
 */

package org.alfrescolabs.technical.validation;

import java.util.List;
import java.util.Map;

public interface AlfrescoTechnicalValidation
{
    /**
     * Validates the extension whose source code is available on disk at <code>sourceLocation</code>,
     * whose binaries (i.e. AMP files) are available on disk at <code>binaryLocation</code>, and with
     * an empty Neo4J database server available at <code>neo4jUrl</code>.
     */
    public List<Map<String,Object>> validate(final String sourceLocation,
                                             final String binaryLocation,
                                             final String neo4jUrl);
}
