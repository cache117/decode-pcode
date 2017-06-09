@echo off

REM connects to PeopleSoft database, decodes PeopleCode bytecode and submits it to a Git repository

java -classpath .\bin;ojdbc5.jar;sqljdbc4.jar;jgit.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToGit %*