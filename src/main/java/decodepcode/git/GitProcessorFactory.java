package decodepcode.git;


import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Logger;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;

import decodepcode.ContainerProcessor;
import decodepcode.ContainerProcessorFactory;
import decodepcode.DirTreePTmapper;
import decodepcode.PToolsObjectToFileMapper;
//import decodepcode.svn.SubversionSubmitter.AuthManagerMapper;

	public class GitProcessorFactory implements ContainerProcessorFactory
	{
		Logger logger = Logger.getLogger(this.getClass().getName());
		boolean ok = false;
		String basePath; 
		PToolsObjectToFileMapper mapper = new DirTreePTmapper();
		File gitdir;
		
		public ContainerProcessor getContainerProcessor() 
		{
			if (!ok)
				throw new IllegalArgumentException("Cannot instantiate Git processor because of invalid properties");
			try {
				GitSubmitter submitter = new GitSubmitter();
				return submitter.new GitContainerProcessor(map, gitdir, basePath, mapper);
			} catch (IOException ex)
			{
				logger.severe("?? " + ex.getMessage());
				throw new IllegalArgumentException("Pzroblem in getContainerProcessor", ex);
			}
		}

		public PToolsObjectToFileMapper getMapper() {
			return mapper;
		}

		static class GitUser 
		{
			String user, email;
		}
		
		HashMap<String, GitUser> map = new HashMap<String, GitUser>();
		
		private void addGITuser( String key, String triplet)
		{
			String[] u1 = triplet.split("/");
			if (u1 == null || u1.length != 3)
			{
				logger.severe("Value with content PSOFTUSER/GIT_USER/GIT_EMAIL expected for property "+ key);
				return;
			}
			GitUser u = new GitUser();
			u.email = u1[2];
			u.user = u1[1];
			if (map.size() == 0)
				map.put("default", u);
			map.put(u1[0], u);
		}

		
		public void setParameters(Properties properties, String suffix) 
		{
			String dir = properties.getProperty("gitdir" + suffix);
			if (dir == null)
				dir = properties.getProperty("gitdir");
			if (dir == null)
			{
				logger.severe("No gitdir entry in properties");
				return;
			}
			try {
				gitdir = new File(dir); 
				if (gitdir.exists() && ! gitdir.isDirectory())
					throw new IOException("gitdir '" + gitdir + "' is not a directory");
				if (!(new File(gitdir, ".git").exists()))
				{
					logger.info("Creating Git repository at " + gitdir);
					gitdir.mkdirs();
					InitCommand initCommand = Git.init();
					initCommand.setDirectory(gitdir);
					initCommand.call();
				}
			} catch (IOException ex)
			{
				logger.severe("Invalid git directory: " + ex.getMessage());
				return;
			} catch (GitAPIException e) {
				logger.severe("Failed to create Git repository: " + e.getMessage());
				//IOException ie = new IOException("Failed to create Git repository");
				return;
			}
			basePath = properties.getProperty("gitbase" + suffix);
/*			if (basePath == null)
			{
				logger.severe("No gitbase" + suffix + " entry in properties");
				return;
			}
*/			
			String u0 = properties.getProperty("gituser"); // first process this one, so that it will be the default
			if (u0 != null)
				addGITuser("svnuser", u0);
			for (Object key1: properties.keySet())
			{
				String key = (String) key1;
				if (key.startsWith("gituser") && !key.equals("gituser"))
				{
					String user = properties.getProperty(key);
					addGITuser(key, user);
				}
			}
			if (map.size() == 0)
			{
				logger.severe("At least one gituser* entry expected");
				return;
			}
			
			ok = true;
		}
	}

