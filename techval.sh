#!/bin/sh
#
# Copyright © 2013 Peter Monks (pmonks@alfresco.com)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#     http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# 
# This file is part of an unsupported extension to Alfresco.

# Operating environment tunables - modify as needed to fit your system
DEPENDS_DIR=~/Development/Alfresco/certification/tools/depends
NEO4J_URL=http://localhost:7474/db/data/
NEO4J_DB_DIR=/usr/local/Cellar/neo4j/community-1.9.4-unix/libexec/data/

# Validate operating environment
if [ ! -d "${DEPENDS_DIR}" ]; then
  echo "Unable to find depends at ${DEPENDS_DIR}."
  exit -1
fi

if [ ! -d "${NEO4J_DB_DIR}" ]; then
  echo "Unable to find Neo4J database at ${NEO4J_DB_DIR}."
  exit -1
fi

# Parse & validate script arguments
BINARIES=
REPORT_FILE=./technicalValidationReport.txt
SOURCE_DIR=.

while getopts "b:r:s:h?" flag; do
  if [ "$flag" == "b" ]; then
    BINARIES=$OPTARG
  fi

  if [ "$flag" == "r" ]; then
    REPORT_FILE=$OPTARG
  fi

  if [ "$flag" == "s" ]; then
    SOURCE_DIR=$OPTARG
  fi

  if [ "$flag" == "h" ]; then
    echo "Usage:"
    echo "techval [-?h] [-s sourceDirectory] [-r reportFile] -b locationOfBinaries"
    echo "If not provided, sourceDirectory defaults to ."
    echo "If not provided, reportFile defaults to ./technicalValidationReport.txt"
    exit 0
  fi

  if [ "$flag" == "?" ]; then
    echo "Usage:"
    echo "techval [-?h] [-s sourceDirectory] [-r reportFile] -b locationOfBinaries"
    echo "If not provided, sourceDirectory defaults to ."
    echo "If not provided, reportFile defaults to ./technicalValidationReport.txt"
    exit 0
  fi
done

if [ -z "$BINARIES" ]; then
  echo "Usage:"
  echo "techval [-?h] [-s sourceDirectory] [-r reportFile] -b locationOfBinaries"
  echo "If not provided, sourceDirectory defaults to ."
  echo "If not provided, reportFile defaults to ./technicalValidationReport.txt"
  exit -1
fi

# Reporting logic
echo "\n                  Alfresco Technical Validation Report Script"
echo "                  -------------------------------------------"
echo "\nReport date:      $(date)"
echo "Source directory: ${SOURCE_DIR}"
echo "Binaries:         ${BINARIES}"
echo "Report file:      ${REPORT_FILE}"

echo "+----------------------------------------------------------------------+" > ${REPORT_FILE}
echo "| Alfresco Technical Validation Report                                 |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "Report date:      $(date)" >> ${REPORT_FILE}
echo "Source directory: ${SOURCE_DIR}" >> ${REPORT_FILE}
echo "Binaries:         ${BINARIES}" >> ${REPORT_FILE}

# Stop, clean and restart Neo4J
echo "\nReticulating splines..."
neo4j stop > /dev/null
rm -rf ${NEO4J_DB_DIR}/graph.db
rm -f  ${NEO4J_DB_DIR}/keystore
rm -rf ${NEO4J_DB_DIR}/log
rm -f  ${NEO4J_DB_DIR}/rrd
neo4j start > /dev/null

pushd ${DEPENDS_DIR} > /dev/null
lein run -- -n ${NEO4J_URL} ${BINARIES}
popd > /dev/null

echo "Summarising source code..."
echo "\n+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "| Source Code Stats (Summary)                                          |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
cloc --quiet --progress-rate=0 ${SOURCE_DIR} >> ${REPORT_FILE}
echo "Freemarker files:    `find ${SOURCE_DIR} -name \*.ftl | wc -l`" >> ${REPORT_FILE}
echo "Freemarker LoC:      `find ${SOURCE_DIR} -name \*.ftl -exec wc -l {} \; | awk '{ sum += $1 } END { print sum }'`" >> ${REPORT_FILE}
echo "Content models:      `find ${SOURCE_DIR} -name \*.xml -exec grep -l http://www.alfresco.org/model/dictionary/1.0 {} \; | wc -l`" >> ${REPORT_FILE}
echo "Spring app contexts: $((`find ${SOURCE_DIR} -name \*.xml -exec grep -l http://www.springframework.org/dtd/spring-beans.dtd {} \; | wc -l` + `find ${SOURCE_DIR} -name \*.xml -exec grep -l http://www.springframework.org/schema/beans {} \; | wc -l`))" >> ${REPORT_FILE}
echo "Web Scripts:         `find ${SOURCE_DIR} -name \*.desc.xml | wc -l`" >> ${REPORT_FILE}
echo "Actions:             `find ${SOURCE_DIR} -name \*.java -exec grep -l ActionExecutor {} \; | wc -l`" >> ${REPORT_FILE}
echo "Behaviours:          `find ${SOURCE_DIR} -name \*.java -exec grep -l bindClassBehaviour {} \; | wc -l`" >> ${REPORT_FILE}
echo "Quartz jobs:         `find ${SOURCE_DIR} -name \*.java -exec grep -l org.quartz.Job {} \; | wc -l`" >> ${REPORT_FILE}

[[ -n `find ${SOURCE_DIR} -name pom.xml -print -quit` ]] && echo "Build tool:                 Maven" >> ${REPORT_FILE}
[[ -n `find ${SOURCE_DIR} -name build.xml -print -quit` ]] && echo "Build tool:                 Ant" >> ${REPORT_FILE}
[[ -n `find ${SOURCE_DIR} -name build.gradle -print -quit` ]] && echo "Build tool:                 Gradle" >> ${REPORT_FILE}
[[ -n `find ${SOURCE_DIR} -name project.clj -print -quit` ]] && echo "Build tool:                 Leiningen" >> ${REPORT_FILE}
[[ -n `find ${SOURCE_DIR} -name build.sbt -print -quit` ]] && echo "Build tool:                 sbt" >> ${REPORT_FILE}


echo "Checking for use of blacklisted Alfresco APIs..."
echo "\n+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "| Blacklisted Alfresco API usage (API01,STB06,UP01)                    |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
neo4j-shell -readonly -c "cypher 1.9
START n=node(*)
MATCH (n)-->(m)
WHERE has(n.name)
  AND has(m.name)
  AND has(m.package)
  AND m.package =~ 'org.alfresco..*'
  AND NOT(m.package =~ 'org.alfresco.extension..*')
  AND NOT(m.name IN [
                      'org.alfresco.error.AlfrescoRuntimeException',
                      'org.alfresco.model.ContentModel',
                      'org.alfresco.query.PagingRequest',
                      'org.alfresco.query.PagingResults',
                      'org.alfresco.repo.cache.SimpleCache',
                      'org.alfresco.repo.module.ModuleComponent',
                      'org.alfresco.repo.node.NodeServicePolicies',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeAddAspectPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeArchiveNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeCreateNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeCreateStorePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeDeleteAssociationPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeDeleteChildAssociationPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeDeleteNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeMoveNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeRemoveAspectPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeSetNodeTypePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$BeforeUpdateNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnAddAspectPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnCreateAssociationPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnCreateChildAssociationPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnCreateNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnCreateStorePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnDeleteAssociationPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnDeleteChildAssociationPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnDeleteNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnMoveNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnRemoveAspectPolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnRestoreNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnSetNodeTypePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnUpdateNodePolicy',
                      'org.alfresco.repo.node.NodeServicePolicies$OnUpdatePropertiesPolicy',
                      'org.alfresco.repo.nodelocator.NodeLocatorService',
                      'org.alfresco.repo.policy.AssociationPolicy',
                      'org.alfresco.repo.policy.AssociationPolicyDelegate',
                      'org.alfresco.repo.policy.BaseBehaviour',
                      'org.alfresco.repo.policy.Behaviour',
                      'org.alfresco.repo.policy.Behaviour$NotificationFrequency',
                      'org.alfresco.repo.policy.BehaviourBinding',
                      'org.alfresco.repo.policy.BehaviourDefinition',
                      'org.alfresco.repo.policy.BehaviourIndex',
                      'org.alfresco.repo.policy.CachedPolicyFactory',
                      'org.alfresco.repo.policy.ClassBehaviourBinding',
                      'org.alfresco.repo.policy.ClassFeatureBehaviourBinding',
                      'org.alfresco.repo.policy.ClassPolicy',
                      'org.alfresco.repo.policy.ClassPolicyDelegate',
                      'org.alfresco.repo.policy.JavaBehaviour',
                      'org.alfresco.repo.policy.Policy',
                      'org.alfresco.repo.policy.PolicyComponent',
                      'org.alfresco.repo.policy.PolicyDefinition',
                      'org.alfresco.repo.policy.PolicyException',
                      'org.alfresco.repo.policy.PolicyList',
                      'org.alfresco.repo.policy.PolicyType',
                      'org.alfresco.repo.policy.PropertyPolicy',
                      'org.alfresco.repo.policy.PropertyPolicyDelegate',
                      'org.alfresco.repo.policy.ServiceBehaviourBinding',
                      'org.alfresco.repo.security.authentication.AuthenticationException',
                      'org.alfresco.repo.security.authentication.AuthenticationUtil',
                      'org.alfresco.repo.security.authentication.AuthenticationUtil$RunAsWork',
                      'org.alfresco.repo.security.authentication.TicketComponent',
                      'org.alfresco.repo.security.authentication.TicketExpiredException',
                      'org.alfresco.repo.site.SiteModel',
                      'org.alfresco.repo.tenant.Tenant',
                      'org.alfresco.repo.tenant.TenantService',
                      'org.alfresco.repo.tenant.TenantUserService',
                      'org.alfresco.repo.transaction.DoNotRetryException',
                      'org.alfresco.repo.transaction.RetryingTransactionHelper',
                      'org.alfresco.repo.transaction.RetryingTransactionHelper$RetryingTransactionCallback',
                      'org.alfresco.repo.transaction.TooBusyException',
                      'org.alfresco.service.cmr.action.Action',
                      'org.alfresco.service.cmr.action.ActionCondition',
                      'org.alfresco.service.cmr.action.ActionConditionDefinition',
                      'org.alfresco.service.cmr.action.ActionDefinition',
                      'org.alfresco.service.cmr.action.ActionList',
                      'org.alfresco.service.cmr.action.ActionService',
                      'org.alfresco.service.cmr.action.ActionStatus',
                      'org.alfresco.service.cmr.action.CompositeAction',
                      'org.alfresco.service.cmr.action.CompositeActionCondition',
                      'org.alfresco.service.cmr.action.ParameterConstraint',
                      'org.alfresco.service.cmr.action.ParameterDefinition',
                      'org.alfresco.service.cmr.action.ParameterizedItem',
                      'org.alfresco.service.cmr.action.ParameterizedItemDefinition',
                      'org.alfresco.service.cmr.attributes.AttributeService',
                      'org.alfresco.service.cmr.coci.CheckOutCheckInService',
                      'org.alfresco.service.cmr.dictionary.AspectDefinition',
                      'org.alfresco.service.cmr.dictionary.AssociationDefinition',
                      'org.alfresco.service.cmr.dictionary.ChildAssociationDefinition',
                      'org.alfresco.service.cmr.dictionary.ClassAttributeDefinition',
                      'org.alfresco.service.cmr.dictionary.ClassDefinition',
                      'org.alfresco.service.cmr.dictionary.Constraint',
                      'org.alfresco.service.cmr.dictionary.ConstraintDefinition',
                      'org.alfresco.service.cmr.dictionary.DataTypeDefinition',
                      'org.alfresco.service.cmr.dictionary.DictionaryService',
                      'org.alfresco.service.cmr.dictionary.InvalidAspectException',
                      'org.alfresco.service.cmr.dictionary.InvalidTypeException',
                      'org.alfresco.service.cmr.dictionary.ModelDefinition',
                      'org.alfresco.service.cmr.dictionary.NamespaceDefinition',
                      'org.alfresco.service.cmr.dictionary.PropertyDefinition',
                      'org.alfresco.service.cmr.dictionary.TypeDefinition',
                      'org.alfresco.service.cmr.i18n.MessageLookup',
                      'org.alfresco.service.cmr.lock.LockService',
                      'org.alfresco.service.cmr.lock.LockStatus',
                      'org.alfresco.service.cmr.lock.LockType',
                      'org.alfresco.service.cmr.model.FileExistsException',
                      'org.alfresco.service.cmr.model.FileFolderService',
                      'org.alfresco.service.cmr.model.FileFolderServiceType',
                      'org.alfresco.service.cmr.model.FileInfo',
                      'org.alfresco.service.cmr.model.FileNotFoundException',
                      'org.alfresco.service.cmr.model.SubFolderFilter',
                      'org.alfresco.service.cmr.module.ModuleDependency',
                      'org.alfresco.service.cmr.module.ModuleDetails',
                      'org.alfresco.service.cmr.module.ModuleService',
                      'org.alfresco.service.cmr.moduleModuleInstallState',
                      'org.alfresco.service.cmr.repository.AbstractStoreException',
                      'org.alfresco.service.cmr.repository.AspectMissingException',
                      'org.alfresco.service.cmr.repository.AssociationExistsException',
                      'org.alfresco.service.cmr.repository.AssociationRef',
                      'org.alfresco.service.cmr.repository.ChildAssociationRef',
                      'org.alfresco.service.cmr.repository.ContentAccessor',
                      'org.alfresco.service.cmr.repository.ContentData',
                      'org.alfresco.service.cmr.repository.ContentIOException',
                      'org.alfresco.service.cmr.repository.ContentReader',
                      'org.alfresco.service.cmr.repository.ContentService',
                      'org.alfresco.service.cmr.repository.ContentStreamListener',
                      'org.alfresco.service.cmr.repository.ContentWriter',
                      'org.alfresco.service.cmr.repository.CopyService',
                      'org.alfresco.service.cmr.repository.datatype.Duration',
                      'org.alfresco.service.cmr.repository.EntityRef',
                      'org.alfresco.service.cmr.repository.InvalidNodeRefException',
                      'org.alfresco.service.cmr.repository.InvalidStoreRefException',
                      'org.alfresco.service.cmr.repository.MalformedNodeRefException',
                      'org.alfresco.service.cmr.repository.MimetypeService',
                      'org.alfresco.service.cmr.repository.NodeRef',
                      'org.alfresco.service.cmr.repository.NodeService',
                      'org.alfresco.service.cmr.repository.NoTransformerException',
                      'org.alfresco.service.cmr.repository.Path',
                      'org.alfresco.service.cmr.repository.StoreExistsException',
                      'org.alfresco.service.cmr.repository.StoreRef',
                      'org.alfresco.service.cmr.search.CategoryService',
                      'org.alfresco.service.cmr.search.LimitBy',
                      'org.alfresco.service.cmr.search.NamedQueryParameterDefinition',
                      'org.alfresco.service.cmr.search.PermissionEvaluationMode',
                      'org.alfresco.service.cmr.search.QueryParameter',
                      'org.alfresco.service.cmr.search.QueryParameterDefinition',
                      'org.alfresco.service.cmr.search.ResultSet',
                      'org.alfresco.service.cmr.search.ResultSetMetaData',
                      'org.alfresco.service.cmr.search.ResultSetRow',
                      'org.alfresco.service.cmr.search.ResultSetSPI',
                      'org.alfresco.service.cmr.search.SearchParameters',
                      'org.alfresco.service.cmr.search.SearchService',
                      'org.alfresco.service.cmr.security.AccessPermission',
                      'org.alfresco.service.cmr.security.AccessStatus',
                      'org.alfresco.service.cmr.security.AuthenticationService',
                      'org.alfresco.service.cmr.security.NoSuchPersonException',
                      'org.alfresco.service.cmr.security.PermissionContext',
                      'org.alfresco.service.cmr.security.PermissionService',
                      'org.alfresco.service.cmr.security.PersonService',
                      'org.alfresco.service.cmr.site.SiteInfo',
                      'org.alfresco.service.cmr.site.SiteMemberInfo',
                      'org.alfresco.service.cmr.site.SiteRole',
                      'org.alfresco.service.cmr.site.SiteService',
                      'org.alfresco.service.cmr.site.SiteVisibility',
                      'org.alfresco.service.cmr.version.ReservedVersionNameException',
                      'org.alfresco.service.cmr.version.Version',
                      'org.alfresco.service.cmr.version.VersionHistory',
                      'org.alfresco.service.cmr.version.VersionService',
                      'org.alfresco.service.cmr.version.VersionType',
                      'org.alfresco.service.cmr.workflow.WorkflowDefinition',
                      'org.alfresco.service.cmr.workflow.WorkflowDeployment',
                      'org.alfresco.service.cmr.workflow.WorkflowInstance',
                      'org.alfresco.service.cmr.workflow.WorkflowInstanceQuery',
                      'org.alfresco.service.cmr.workflow.WorkflowNode',
                      'org.alfresco.service.cmr.workflow.WorkflowPath',
                      'org.alfresco.service.cmr.workflow.WorkflowService',
                      'org.alfresco.service.cmr.workflow.WorkflowTask',
                      'org.alfresco.service.cmr.workflow.WorkflowTaskDefinition',
                      'org.alfresco.service.cmr.workflow.WorkflowTaskQuery',
                      'org.alfresco.service.cmr.workflow.WorkflowTaskState',
                      'org.alfresco.service.cmr.workflow.WorkflowTimer',
                      'org.alfresco.service.cmr.workflow.WorkflowTransition',
                      'org.alfresco.service.namespace.NamespaceException',
                      'org.alfresco.service.namespace.NamespacePrefixResolver',
                      'org.alfresco.service.namespace.NamespaceService',
                      'org.alfresco.service.namespace.QName',
                      'org.alfresco.service.namespace.QNamePattern',
                      'org.alfresco.service.ServiceRegistry',
                      'org.alfresco.util.EqualsHelper',
                      'org.alfresco.util.FileNameValidator',
                      'org.alfresco.util.GUID',
                      'org.alfresco.util.ISO9075',
                      'org.alfresco.util.Pair',
                      'org.alfresco.util.VersionNumber'
                    ])
RETURN n.name as Class, collect(distinct m.name) as Blacklisted_Alfresco_APIs_Used
 ORDER BY n.name;
" >> ${REPORT_FILE}

echo "Checking for use of synchronisation..."
echo "\n+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "| Use of synchronised in Java (STB08,STB09)                            |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
find ${SOURCE_DIR} -name \*.java -exec grep -Hn synchronized {} \; | cut -d":" -f1-2 >> ${REPORT_FILE}

echo "Checking class version..."
echo "\n+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "| Java Class Versions (COM06)                                          |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
neo4j-shell -readonly -c "cypher 1.9
 START n=node(*)
 WHERE has(n.name)
   AND has(n.\`class-version\`)
   AND n.\`class-version\` < 50
RETURN n.name as Class, n.\`class-version-str\` as Class_Version
 ORDER BY n.name;
" >> ${REPORT_FILE}

echo "Checking for use of blacklisted JDK APIs..."
echo "\n+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "| Blacklisted JDK API usage (SEC04,STB03,STB04,STB10,STB12)            |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
neo4j-shell -readonly -c "cypher 1.9
 START n=node(*)
 MATCH (n)-->(m)
 WHERE has(n.name)
   AND has(m.name)
   AND (   m.name IN [
                      'java.lang.Throwable',
                      'java.lang.Error',
                      'java.lang.System',
                      'java.lang.Thread',
                      'java.lang.ThreadGroup',
                      'java.lang.ThreadLocal',
                      'java.lang.Runnable',
                      'java.lang.Process',
                      'java.lang.ProcessBuilder',
                      'java.lang.ClassLoader',
                      'java.security.SecureClassLoader'
                     ]
        OR (    has(m.package)
            AND m.package IN [
                               'java.sql',
                               'javax.sql',
                               'org.springframework.jdbc',
                               'com.ibatis',
                               'org.hibernate',
                               'java.util.concurrent',
                               'javax.servlet',
                               'javax.servlet.http',
                               'javax.transaction',
                               'javax.transaction.xa'
                             ]))
RETURN n.name as Class, collect(distinct m.name) as Blacklisted_JDK_APIs_Used
 ORDER BY n.name;
" >> ${REPORT_FILE}

echo "Checking for use of SearchService / ResultSet (manual followup required)..."
echo "\n+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "| Use of SearchService/ResultSet (STB07,UX01) - MANUAL FOLLOWUP        |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
neo4j-shell -readonly -c "cypher 1.9
 START n=node(*)
 MATCH (n)-->(m)
 WHERE has(n.name)
   AND has(m.name)
   AND m.name IN [
                   'org.alfresco.service.cmr.search.SearchService',
                   'org.alfresco.service.cmr.search.ResultSet'
                 ]
RETURN n.name as Class, collect(distinct m.name) as SearchService_ResultSet
 ORDER BY n.name;
" >> ${REPORT_FILE}

echo "Checking for use of AuthenticationUtil (manual followup required)..."
echo "\n+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "| Use of AuthenticationUtil (SEC02) - MANUAL FOLLOWUP                  |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
neo4j-shell -readonly -c "cypher 1.9
 START n=node(*)
 MATCH (n)-->(m)
 WHERE has(n.name)
   AND has(m.name)
   AND m.name = 'org.alfresco.repo.security.authentication.AuthenticationUtil'
RETURN n.name as Class, m.name as AuthenticationUtil
 ORDER BY n.name;
" >> ${REPORT_FILE}

echo "Checking for use of eval() in JavaScript..."
echo "\n+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "| Use of eval() in Javascript (SEC05)                                  |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
find ${SOURCE_DIR} -name \*.js -exec grep -H "eval(" {} \; >> ${REPORT_FILE}

# Stop neo4j once we're done
neo4j stop > /dev/null

echo "\n+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "| End of Technical Validation Report                                   |" >> ${REPORT_FILE}
echo "+----------------------------------------------------------------------+" >> ${REPORT_FILE}
echo "Script complete - report has been written to ${REPORT_FILE}."

# Note: I believe this next line s Mac OSX specific
open ${REPORT_FILE}