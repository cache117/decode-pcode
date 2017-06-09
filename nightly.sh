# example of a script to use with cron - note that the directory and Java's full path are required
JAVA=/usr/bin/java

# work directory should contain 'bin' folder, and the .jar files that are needed: jgit.jar or svnkit.jar, and ojdbc5.jar (Oracle) or sqljdbc4.jar (SQL Server)
DIR=/usr/local/DecodePCODE
cd $DIR

# SVN: 
$JAVA  -classpath ./bin:ojdbc5.jar:sqljdbc4.jar:svnkit.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToSVN since-last-time
# Git:
# $JAVA  -classpath ./bin:ojdbc5.jar:sqljdbc4.jar:jgit.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToGit since-last-time
