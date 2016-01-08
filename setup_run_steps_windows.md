# Steps to configure and run ATV in windows

Once you have the source code, amps in the right location, running ATV will produce report (a word doc)

* Make sure you have Java running on your system and JAVA_HOME set correctly

* Create 4 folders C:\neo4j, C:\neo4j_source, C:\neo4j_amps and C:\atv

* Dowloading neo4j:
Download the 2.2.7 windows 32 bit zip file from neo4j site
You need to get the windows zip archive. Do not download the installer as it requires complex setup and configuration.
Get the community edition of the zip archive.
Once downloaded, unzip it to C:\neo4j folder

* Add an environment variable called NEO4J_HOME and set it to C:\neo4j (it is not mentioned anywhere but I added it)

* Configuring neo4j:
Open C:\neo4j\conf\neo4j.properties file and then add these 2 lines at the end:
node_auto_indexing=true
node_keys_indexable=name,package,typename

   Save the file

   Open C:\neo4j\conf\ neo4j-server.properties file and make sure to turn off DB security:
   dbms.security.auth_enabled=false

   Save this file as well.

* Running neo4j:
You must run neo4j before giving the atv command

   Open a command prompt
   Browse to folder C:\neo4j\bin

   C:\neo4j\bin>Neo4j.bat
WARNING! This batch script has been deprecated. Please use the provided PowerShell scripts instead: http://neo4j.com/docs/stable/powershell.html
C:\neo4j\bin>

   At this point neo4j will be running. You will see a java app console open.

* Verifying that neo4j running
Open browser and go to http://localhost:7474 

   You should directly see the page. If it asks for password, then you forgot to turn off the security for DBMS

* Running ATV:
Before you run ATV, make sure things are in place
You must have all of the source code in folder C:\neo4j_source
You must have all the amps in folder C:\neo4j_amps

   You have downloaded ATV and unzipped in folder C:\ATV

   Open windows command prompt. Go to folder C:\ATV\atv-0.6.0
   Here you will see atv.cmd file

   Next give this atv:
   C:\ATV\atv-0.6.0>atv  -s C:\neo_source  -b C:\neo_amps/  -w alfresco-technical-validation_report.docx
   Indexing binaries...
   Indexing source... \

   Give it a minute to run depending on the size of code and amps

* Once the command works, you will see alfresco-technical-validation_report.docx created in folder C:\ATV\atv-0.6.0

Note: In Windows the word doc will not have line count since ohcount doesn't work in Windows.
