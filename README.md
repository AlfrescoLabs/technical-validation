# technical-validation

Tool(s) for the technical validation of custom code that extends the [Alfresco](http://www.alfresco.com) open source document management system.

## Dependencies
 1. [Java 1.7+](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
 2. [neo4j 2.0+](http://www.neo4j.org/)
 3. [leiningen](http://leiningen.org/)
 4. [depends](https://github.com/pmonks/depends)
 5. [bookmark-writer](https://github.com/pmonks/bookmark-writer)

The following tool is also useful, though not used directly by the tool:

 1. [Ohcount](https://github.com/blackducksw/ohcount)

Note: these dependencies should be installed via your OS package manager, where possible.  On Mac OSX, I strongly recommend [Homebrew](http://brew.sh/).

## Installation

...is a huge pain right now as it requires building [depends](https://github.com/pmonks/depends), [bookmark-writer](https://github.com/pmonks/bookmark-writer) and the [validation tool](https://github.com/AlfrescoLabs/technical-validation) from source, and deploying the first 2 into a local Maven repository.

I'm working on getting an uberjar functional, at which point this'll involve downloading a single behemoth JAR file and running it directly.

## Running / usage

Assuming you get the source built and installed properly, you can...

```shell
 $ lein run -- -h
 -------------------------------+-------------------------------+----------------------------------
  Parameter                     | Default Value                 | Description
 -------------------------------+-------------------------------+----------------------------------
  -s, --source SOURCE                                            Source folder
  -b, --binaries BINARIES                                        Binary folder or archive
  -n, --neo4j-url NEO4J_URL      http://localhost:7474/db/data/  URL of the Neo4J server to use
  -r, --report-file REPORT_FILE                                  The filename of the output report
  -h, --help
 -------------------------------+-------------------------------+----------------------------------
$ 
```

## Developer Information

[GitHub project](https://github.com/AlfrescoLabs/technical-validation)

[Bug Tracker](https://github.com/AlfrescoLabs/technical-validation/issues)

## License

Copyright © 2013,2014 Peter Monks (pmonks@alfresco.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

This file is part of an unsupported extension to Alfresco.
