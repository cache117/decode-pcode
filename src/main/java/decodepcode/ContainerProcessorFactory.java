package decodepcode;

import java.util.Properties;

public interface ContainerProcessorFactory 
{
	public void setParameters(Properties properties, String suffix);
	public ContainerProcessor getContainerProcessor();
	public PToolsObjectToFileMapper getMapper();
}
