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

package org.alfrescolabs.technical.validation.impl;

import java.util.List;
import java.util.Map;

import clojure.java.api.Clojure;
import clojure.lang.IFn;

import org.alfrescolabs.technical.validation.*;

public class AlfrescoTechnicalValidationImpl
    implements AlfrescoTechnicalValidation
{
    private final IFn techValImpl;

    public AlfrescoTechnicalValidationImpl()
    {
        // Bootstrap Clojure runtime and squirrel away a reference to the technical validation tool's entry point function
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("alfresco-technical-validation.core"));

        techValImpl = Clojure.var("alfresco-technical-validation.core", "validate-java");
    }

    @Override
    public List<Map<String,Object>> validate(final String sourceLocation,
                                             final String binaryLocation,
                                             final String neo4jUrl)
    {
        // PRECONDITIONS
        assert sourceLocation != null && sourceLocation.trim().length() > 0 : "sourceLocation must not be null, empty or blank.";
        assert binaryLocation != null && binaryLocation.trim().length() > 0 : "binaryLocation must not be null, empty or blank.";
        assert neo4jUrl       != null && neo4jUrl.trim().length()       > 0 : "neo4jUrl must not be null, empty or blank.";

        // BODY
        @SuppressWarnings("unchecked")
        List<Map<String,Object>> result = (List<Map<String,Object>>)techValImpl.invoke(sourceLocation, binaryLocation, neo4jUrl);

        return(result);
    }
}
