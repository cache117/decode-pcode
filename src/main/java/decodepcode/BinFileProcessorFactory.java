package decodepcode;

import java.io.File;
import java.util.Properties;

public class BinFileProcessorFactory implements ContainerProcessorFactory 
{
	File outDir;
	public ContainerProcessor getContainerProcessor() 
	{
		return new Controller.WriteToDirectoryTree(getMapper());	
	}

	public void setParameters(Properties properties, String suffix) 
	{
		String outDirStr = properties.getProperty("outdir" + suffix);
		if (outDirStr != null)
			outDir = new File(outDirStr);
		else
			outDir = new File(".", "output"); 
	}

	public PToolsObjectToFileMapper getMapper() 
	{
		return new DirTreePTmapper( outDir );
	}
}
