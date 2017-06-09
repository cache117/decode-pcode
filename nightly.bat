@echo off

REM work directory should contain 'bin' folder, and the .jar files that are needed: jgit.jar or svnkit.jar, and ojdbc5.jar (Oracle) or sqljdbc4.jar (SQL Server)
cd /d D:\java\decodepcode

REM Git version:
D:\java\jdk1.7.0_13\bin\java -classpath .\bin;ojdbc5.jar;sqljdbc4.jar;jgit.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToGit since-last-time

REM Subversion:
REM D:\java\jdk1.7.0_13\bin\java -classpath .\bin;ojdbc5.jar;sqljdbc4.jar;svnkit.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToSVN since-last-time