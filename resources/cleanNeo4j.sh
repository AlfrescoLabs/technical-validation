#!/bin/sh
# Clean the neo4j database between runs of the ATV

#NEO4J_DB_DIR=/usr/local/Cellar/neo4j/2.2.3/libexec/data/
NEO4J_DB_DIR=/home/richard/tmp/neo4j-community-2.2.3/data

if [ ! -d "${NEO4J_DB_DIR}" ]; then
  echo "Unable to find Neo4J database at ${NEO4J_DB_DIR}."
  exit -1
fi

rm -rf ${NEO4J_DB_DIR}/graph.db
rm -f  ${NEO4J_DB_DIR}/keystore
rm -rf ${NEO4J_DB_DIR}/log
rm -f  ${NEO4J_DB_DIR}/rrd
