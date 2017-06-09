@echo off

REM connects to PeopleSoft database, decodes PeopleCode bytecode and submits it to a Subversion version control system

java -classpath .\bin;ojdbc5.jar;sqljdbc4.jar;svnkit.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToSVN %*