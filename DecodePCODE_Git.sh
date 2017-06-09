# connects to PeopleSoft database, decodes PeopleCode bytecode and submits it to a Git repository
# to be run from the DecodePCode directory, with the 'bin' subfolder and the .jar files (jgit.jar, ojdbc5.jar for Oracle, sqljdbc4.jar for SQL Server)

$PS_HOME/jre/bin/java -classpath ./bin:ojdbc5.jar:sqljdbc4.jar:jgit.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToGit $*