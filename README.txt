DecodePCODE v0.61


The current version contains a few scripts, to be used from a 
command line, to extract PeopleCode and SQL text from either 
a PeopleTools project file or directly from PeopleTools 
tables (using direct, read-only database access). 

If a PTools project file is read, .pcode files are produced exactly 
as they appear in Application Designer. 

If you have ptools >= 8.52, the PeopleCode is read from table PSPCMTXT.

Otherwise, the bytecode in the PSPCMPROG table is decoded and while this 
works quite well, the .pcode files it produces are not always formatted 
precisely as in AppDesigner. 

The produced files can either be saved to the local file system, or 
submitted to a version control system (currently, Subversion 
(SVN) and Git are supported). 

The app can also process several environments (e.g. DEV and TEST) 
in one go, saving each environment's version of a PeopleCode object 
to a separate directory or to a separate SVN/Git folder. Alternatively,
using the 'ancestor' directive, the tool can save the various versions 
of newly added objects (e.g. DEMO, PROD, DEV) to the same branch, 
so that the objects' history shows the customizations.

There is also a script to attempt three-way merging of PeopleCode, 
especially useful for functional upgrades. 


DecodePCODE.bat:

Connects to a database, reads PeopleCode bytecode and SQL definitions 
from the PeopleTools tables, and writes the decoded PeopleCode and 
the SQL text to the file system (below a top directory that you 
specify in the .properties file)


Usage: e.g.

DecodePCODE.bat PPLTLS88CUR
(Connects to the database and uses this project definition to determine 
what PeopleCode segments and SQL definitions to extract)

DecodePCODE.bat custom
(all PeopleCode/SQL with LASTUPDOPRID <> 'PPLSOFT')

DecodePCODE.bat since 2011/01/01
(LASTUPDDTTM on or after the specified day)

DecodePCODE.bat since-days 7
(same, but relative to current date)

DecodePCODE.bat since-last-time
(this reads a file 'last-time.txt' , which is created by this program 
whenever it runs with one of the 'since' parameters. You may want 
to use this command in a recurrent job)

DecodePCODE.bat since-last-time custom
(same, but only code with LASTUPDOPRID <> 'PPLSOFT'. Note that while 
this will keep your repository free of delivered code, it means you 
won't be able to tell if a bundle has wiped out a customization)


DecodePCODE.bat since 2012/01/01 oprid JOHNNY
(parameter 'OPRID' can be combined with 'custom' or any of the 'since' 
parameters; it adds a 'LASTUPDOPRID = ...' clause to the selects)

DecodePCODE.bat PPLTLS88CUR.xml
(Reads a PeopleTools project file, and extracts all PeopleCode segments 
it contains, as well as the SQL definitions)



DecodePCODE_SVN.bat

This script accepts the same parameters as DecodePCODE.bat, but instead 
of writing the extracted text to files, it submits it to a 
Subversion (SVN) version control system. Connection parameters for 
SVN should be entered in DecodePC.properties. You can specify more 
than one SVN user in the properties; the program will try to map 
each PeopleSoft user (LASTUPDOPRID) to an SVN user.



DecodePCODE_Git.bat

To submit the extracted text to a local Git repository (which will be 
created if it does not already exist).



DecodePCODE_Merge.bat

Extracts PeopleCode from a compare report and from a PeopleTools .xml 
project,and attempts to merge the three versions of each PeopleCode 
segment. Modifies the (browser) compare report that was used for input,
adding links to various version of the PeopleCode as well as to diffs.
See 'Using DecodePCODE_Merge.pdf'.




Installation:

Extract the .zip file, and ensure a suitable JDBC library is 
available in the working directory: sqljdbc4.jar for MS SQL 
Server, ojdbc6.jar for Oracle (retrieve from the vendor 
sites; the Oracle driver may also be found in jdbc/lib under 
the Oracle home). Other database types should work as well 
(adjust the url and driverclass entries, and specify the .jar 
file in the scripts).

If you want to commit files to Subversion, you need svnkit.jar 
(from svnkit.com; this program has been tested with the 
version included in org.tmatesoft.svn_1.3.5.standalone.zip). 

If you want to commit files to a Git repository, you will need 
jgit.jar (from http://www.eclipse.org/jgit/download, pick the 
'Raw API library' and rename the file).

See 'Setting up version tracking.pdf' for more details on using 
Subversion or Git with this application.

Specify the connection parameters in DecodePC.properties. For 
Oracle, you can use tnsping to determine server 
and port number.

For all three .bat files, you may have to specify the path for java 
(JRE 1.5+ required). You may be able to use $PS_HOME/jre/bin/java.
Ensure that the classpath parameters (the .jar files) exist.

Note that the batch files need to be started from the base directory
(with the subdirectory 'bin').

Installation on Unix: same, but use the .sh scripts instead of 
the batch files. You will have to do something like 
	chmod 700 *.sh
first. In Unix, the entries in the classpath are separated by :, not ; .



Miscellaneous:

Minimum persissions for the SQL user that you specify in 
the .properties file:

select rights on PSPCMPROG PSPCMNAME PSSQLDEFN PSSQLTEXTDEFN 
PSPROJECTITEM PSPACKAGEDEFN and (ptools >= 8.52) PSPCMTXT; 
no insert/delete/alter permissions required.

To retrieve or browse the source code, please use the 
Code link on the SourceForge project 
(https://sourceforge.net/projects/decodepcode).

If you run into a bug, please submit a bug report via the Tracker 
feature on the SourceForge pages.


Enjoy.

