package decodepcode.svn;

import java.util.Properties;
import java.util.logging.Logger;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import decodepcode.ContainerProcessor;
import decodepcode.ContainerProcessorFactory;
import decodepcode.DirTreePTmapper;
import decodepcode.PToolsObjectToFileMapper;
//import decodepcode.svn.SubversionSubmitter.AuthManagerMapper;

public class SubversionProcessorFactory implements ContainerProcessorFactory 
{
	Logger logger = Logger.getLogger(this.getClass().getName());
	boolean ok = false;
	SVNURL svnurl;
	String basePath; 
	PToolsObjectToFileMapper mapper = new DirTreePTmapper(); 
	SubversionSubmitter.FixedAuthManagerMapper authMapper = new SubversionSubmitter.FixedAuthManagerMapper();	
	
	public ContainerProcessor getContainerProcessor() {
		if (!ok)
			throw new IllegalArgumentException("Cannot instantiate SVN processor because of invalid properties");
		try {
			SubversionSubmitter.setUpSVNKit();
			return new SubversionSubmitter.SubversionContainerProcessor(svnurl, basePath, mapper, authMapper);
		} catch (SVNException ex)
		{
			logger.severe("?? " + ex.getMessage());
			throw new IllegalArgumentException("Problem in getContainerProcessor", ex);
		}
	}

	public PToolsObjectToFileMapper getMapper() {
		return mapper;
	}

	// e.g. svnuser1=PPLSOFT/harry/secret
	private void setSVNuser( String key, String triplet)
	{
		String[] u1 = triplet.split("/");
		if (u1 == null || u1.length != 3)
		{
			logger.severe("Value with content PSOFTUSER/SVNUSER/SVNPW expected for property "+ key);
			return;
		}
		authMapper.addCredentials(u1[0], u1[1], u1[2]);		
	}
	
	public void setParameters(Properties properties, String suffix) 
	{
		String url = properties.getProperty("svnurl" + suffix);
		if (url == null)
			url = properties.getProperty("svnurl");
		if (url == null)
		{
			logger.severe("No svnurl entry in properties");
			return;
		}
		try {
			svnurl = SVNURL.parseURIEncoded(url);
		} catch (SVNException ex)
		{
			logger.severe("Invalid Subversion URL: " + ex.getMessage());
			return;
		}
		basePath = properties.getProperty("svnbase" + suffix);
/*		if (basePath == null)
		{
			logger.severe("No svnbase" + suffix + " entry in properties");
			return;
		}
*/
		String u0 = properties.getProperty("svnuser"); // first process this one, so that it will be the default
		if (u0 != null)
			setSVNuser("svnuser", u0);
		for (Object key1: properties.keySet())
		{
			String key = (String) key1;
			if (key.startsWith("svnuser") && !key.equals("svnuser"))
			{
				String user = properties.getProperty(key);
				setSVNuser(key, user);
			}
		}
		if (authMapper.map.size() == 0)
		{
			logger.severe("At least one svnuser* entry expected");
			return;
		}
		ok = true;
	}
}
