package decodepcode;

import java.io.File;
import java.io.IOException;

/**
 * 
 * Assign a file location where to put or expect a PeopleTools object 
 *
 */
public abstract interface PToolsObjectToFileMapper {

	public abstract File getFile(PeopleToolsObject obj, String extension) throws IOException;
	public abstract File getFileForSQL(SQLobject slqObject, String extension) throws IOException;
	public abstract File getFileForCONT(CONTobject contObject, boolean lastUpdateExt) throws IOException;
	public abstract String getPath(PeopleToolsObject obj, String extension);
	public abstract String getPathForSQL(SQLobject slqObject, String extension);
	public abstract String getPathForCONT(CONTobject contObject, boolean lastUpdateExt);

}
