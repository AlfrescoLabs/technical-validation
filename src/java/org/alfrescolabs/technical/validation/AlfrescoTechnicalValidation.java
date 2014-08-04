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
     *
     * The returned data structure is a list of maps, with each entry in the list representing the
     * result of a single validation criteria. Note that not all validation criteria are checked by the
     * tool yet - those criteria are included in the result but are marked as not being checked.
     * 
     * Each result map may have the following keys:
     * 
     * <code>criteriaId</code> - a String identifying the criteria e.g. "API01" (always present in the map)
     * <code>checked</code>    - a Boolean identifying whether this criterion is checked or not (always
     *                           present in the map)
     * <code>passes</code>     - a Boolean identifying whether the extension passes the criteria or not
     *                           (optional - omission means the criteria can't be automatically checked)
     * <code>message</code>    - a String describing any supporting information for the given criteria
     *                           (e.g. evidence) - note that this value can be both multi-line and voluminous
     *
     * It's important to note that calling code will need to handle the case where these keys are not
     * present - the tool does not necessarily populate all keys in all cases.
     */
    public List<Map<String,Object>> validate(final String sourceLocation,
                                             final String binaryLocation,
                                             final String neo4jUrl);
}
