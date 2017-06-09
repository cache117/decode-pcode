# connects to PeopleSoft database, decodes PeopleCode bytecode and submits it to a Subversion repository
# to be run from the DecodePCode directory, with the 'bin' subfolder and the .jar files (svnkit.jar, ojdbc5.jar for Oracle, sqljdbc4.jar for SQL Server)

$PS_HOME/jre/bin/java -classpath ./bin:ojdbc5.jar:sqljdbc4.jar:svnkit.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToSVN $*