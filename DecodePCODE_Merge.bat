@echo off

REM three-way merge
REM .jar files as present in SVNKit library (here for org.tmatesoft.svn_1.7.5-v1.standalone.zip)
REM unzip the jar files that this zip file contains, and check the exact names of jar files listed in the class path
REM Depending on the version of SVNKit, you may need to list more of the .jar files.

java -classpath .\bin;svnkit-1.7.5-v1.jar;sequence-library-1.0.2.jar;antlr-runtime-3.4.jar  -Djava.util.logging.config.file=logger.properties decodepcode.svn.MergePeopleCodeTrees %*