package decodepcode.git;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;

import decodepcode.CONTobject;
import decodepcode.ContainerProcessor;
import decodepcode.JDBCPeopleCodeContainer;
import decodepcode.PToolsObjectToFileMapper;
import decodepcode.PeopleCodeParser;
import decodepcode.PeopleToolsObject;
import decodepcode.ProjectReader;
import decodepcode.SQLobject;
import decodepcode.VersionControlSystem;
import decodepcode.git.GitProcessorFactory.GitUser;

/* Submit to local Git repository with JGit */
public class GitSubmitter 
{
		static Logger logger = Logger.getLogger(GitSubmitter.class.getName());
		File gitWorkDir;
		Git git;
		HashMap<String, GitUser> userMap;
		
	    private void addFile(	String psoft_user,  
	    							String filePath, 
	    							String commitStr,
	    							byte[] data) throws UnmergedPathsException, GitAPIException, IOException 	    
	    {	
	    	int lastSlash = filePath.lastIndexOf("/");
	    	if (lastSlash < 1)
	    		throw new IllegalArgumentException("Expected file name with directory path, got " + filePath);
	    	String dirPath = filePath.substring(0, lastSlash), name = filePath.substring(lastSlash + 1);
		    String path1 = dirPath.replace("/", System.getProperty("file.separator"));
		    File dir1 = new File(gitWorkDir, path1);
		    dir1.mkdirs();
			File myfile = new File(dir1, name);
			FileOutputStream os = new FileOutputStream(myfile);
			os.write(data);
			os.close();
			AddCommand add = git.add();
			add.addFilepattern(filePath).call();
			GitUser user = userMap.get(psoft_user);
			if (user == null)
				user = userMap.get("default");
			CommitCommand commit = git.commit();
			commit.setMessage(commitStr).setAuthor(user.user, user.email).setCommitter("Decode Peoplecode", "nobody@dummy.org").call();
	    }
	    private boolean fileExistsInRepository( String filePath)
	    {
	    	int lastSlash = filePath.lastIndexOf("/");
	    	if (lastSlash < 1)
	    		throw new IllegalArgumentException("Expected file name with directory path, got " + filePath);
	    	String dirPath = filePath.substring(0, lastSlash), name = filePath.substring(lastSlash + 1);
		    String path1 = dirPath.replace("/", System.getProperty("file.separator"));
		    File dir1 = new File(gitWorkDir, path1);
			File myfile = new File(dir1, name);
		    return myfile.exists(); // only checks if file exists in work directory - room for improvement here
	    }
	    
	    public class GitContainerProcessor extends ContainerProcessor implements VersionControlSystem
		{	    	
			String basePath;
			PToolsObjectToFileMapper mapper;
			PeopleCodeParser parser = new PeopleCodeParser();
			ContainerProcessor ancestor;
			
			GitContainerProcessor(
					HashMap<String, GitUser> _userMap,
					File gitDir,
					String _basePath, 
					PToolsObjectToFileMapper _mapper) throws IOException 
			{
				basePath = _basePath;
				mapper = _mapper;
			    git = Git.open(gitDir);
			    gitWorkDir = gitDir;
			    userMap = _userMap ;
			    if (basePath != null)
			    	System.out.println("Submitting PeopleCode and SQL definitions to " + new File(gitDir, basePath));
			}

			public void process(decodepcode.PeopleCodeObject c) throws IOException 
			{
				if (basePath == null)
					return;
				StringWriter w = new StringWriter();
				if (c.hasPlainPeopleCode()) // why decode the bytecode if we have the plain text...
					w.write(c.getPeopleCodeText());
				else
				{
					parser.parse(((decodepcode.PeopleCodeContainer) c), w);
				}
				String path = basePath + mapper.getPath(c, "pcode");
				try {
					String comment = "";
					if (c instanceof JDBCPeopleCodeContainer)
						if (getJDBCconnection().equals(((JDBCPeopleCodeContainer) c).getOriginatingConnection()))
							comment = "Saved at " + ProjectReader.df2.format(c.getLastChangedDtTm()) + " by " + c.getLastChangedBy();
						else
							comment = "Version in " + ((JDBCPeopleCodeContainer) c).getSource() + " retrieved on " + ProjectReader.df3.format(new Date()); 
					
					addFile( c.getLastChangedBy(), path, comment, w.toString().getBytes() );
				}  catch (UnmergedPathsException se) {
					IOException e = new IOException("Error submitting pcode to Git");
					e.initCause(se);
					throw e; 				
				} catch (GitAPIException se) {
					IOException e = new IOException("Error submitting pcode to Git");
					e.initCause(se);
					throw e; 				
				}
			}

			public void processSQL(SQLobject sql) throws IOException 
			{
				if (basePath == null)
					return;
				String path = basePath + mapper.getPathForSQL(sql, "sql");
				try {
					addFile(sql.getLastChangedBy(),
							path, 
						"Saved at " + ProjectReader.df2.format(sql.getLastChangedDtTm()) + " by " + sql.getLastChangedBy(), 
						sql.getSql().getBytes());				
				} catch (UnmergedPathsException se) {
					IOException e = new IOException("Error submitting SQL to Git");
					e.initCause(se);
					throw e; 				
				} catch (GitAPIException se) {
					IOException e = new IOException("Error submitting SQL to Git");
					e.initCause(se);
					throw e; 				
				}
			}

			@Override
			public void processCONT(CONTobject cont) throws IOException {
				if (basePath == null)
					return;
				String path = basePath + mapper.getPathForCONT(cont, false);
				try {
					addFile(cont.getLastChangedBy(),
							path, 
							"Saved at " + ProjectReader.df2.format(cont.getLastChangedDtTm()) + " by " + cont.getLastChangedBy(), 
							cont.getContDataBytes());
				} catch (UnmergedPathsException se) {
					IOException e = new IOException("Error submitting Content to Git");
					e.initCause(se);
					throw e;
				} catch (GitAPIException se) {
					IOException e = new IOException("Error submitting Content to Git");
					e.initCause(se);
					throw e;
				}
				
			}

			@Override
			public void aboutToProcess() {
				if (basePath != null)
					System.out.println("Submitting to Git, base path = " + basePath);			
			}

			public boolean existsInBranch(PeopleToolsObject obj)
					throws IOException {
				if (basePath == null)
					throw new IllegalArgumentException("No base path set for " + getTag());
				return fileExistsInRepository(basePath + mapper.getPath(obj, "pcode"));
			}

			public ContainerProcessor getAncestor() {
				return ancestor;
			}

			public void setAncestor(ContainerProcessor _ancestor) {
				ancestor = _ancestor;				
			}
		}
}
