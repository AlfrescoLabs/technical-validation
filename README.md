# technical-validation

Tool(s) for the technical validation of custom code that extends or integrates with Alfresco.

## Dependencies
 1. [cloc](http://cloc.sourceforge.net/)
 2. [depends](https://github.com/pmonks/depends)
 3. [neo4j](http://www.neo4j.org/)

## Installation

Checkout the source from [GitHub](https://github.com/AlfrescoLabs/technical-validation).
Open techval.sh and modify the tunables (at the top of the file) as needed to match your operating environment.

## Running / usage

```shell
 ./techval.sh [args]
```
    Switch                 Default                          Description
    ------                 -------                          -----------
    -s [directory]         .                                Directory containing source of the solution to be validated.
    -b [file or directory] n/a                              Binary file or directory containing the binaries (AMP files, typically) of the solution to be validated.
    -r [file]              ./technicalValidationReport.txt  Name of the file to write the report to.
    -h, -?                 n/a                              Show help.

## Developer Information

[GitHub project](https://github.com/AlfrescoLabs/technical-validation)

[Bug Tracker](https://github.com/AlfrescoLabs/technical-validation/issues)

## License

Copyright © 2013 Peter Monks (pmonks@alfresco.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

This file is part of an unsupported extension to Alfresco.
