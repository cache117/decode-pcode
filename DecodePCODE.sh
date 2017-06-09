# 
# connects to PeopleSoft database, and creates subdirectories in working dir with extracted PeopleCode and SQL text.

java -classpath ./bin:ojdbc5.jar:sqljdbc4.jar  -Djava.util.logging.config.file=logger.properties decodepcode.Controller ProcessToFile $*