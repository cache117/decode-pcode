package decodepcode;

import java.io.IOException;

public interface VersionControlSystem
{
	public boolean existsInBranch(PeopleToolsObject obj) throws IOException;
	public ContainerProcessor getAncestor();
	public void setAncestor(ContainerProcessor _ancestor);
}
