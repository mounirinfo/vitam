#! /bin/bash

source /vitam/conf/${unix.name}/sysconfig/java_opts

JAVA_ENTRYPOINT=/vitam/lib/${unix.name}/logbook-administration-audit-${project.version}.jar
/usr/bin/env java -jar $JAVA_OPTS $JAVA_ENTRYPOINT