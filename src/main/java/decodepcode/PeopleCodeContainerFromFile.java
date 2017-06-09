package decodepcode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

public class PeopleCodeContainerFromFile extends PeopleCodeContainer implements  PeopleToolsObject 
{
	File binFile;
	String key;
	Logger logger = Logger.getLogger(this.getClass().getName());
	Map<Integer, String>references = new HashMap<Integer, String>();
	String[] keys;
	int objType;

	public PeopleCodeContainerFromFile( File _binFile) throws IOException
	{
		this( _binFile, null);
	}
	public PeopleCodeContainerFromFile( File _binFile, PeopleToolsObject obj) throws IOException 
	{
		binFile = _binFile;
		InputStream io = new FileInputStream(binFile);
		logger.fine("Reading byte code from " + binFile);
		bytes = new byte[(int) binFile.length()];
		io.read( bytes);
		io.close();
		String binFileName = binFile.getName();
		if (binFileName.endsWith(".bin"))
		{
			key = binFileName.substring(0, binFileName.length() - 4);
			String txtFileName = key + ".txt";
			File txtFile = new File( binFile.getParent(), txtFileName);
			if (txtFile.exists())
			{
				logger.fine("Trying to read PeopleCode from " + txtFile);
				readPeopleCodeTextFromFile(txtFile);
			}
			File refFile = new File( binFile.getParent(), key + ".references");
			if (refFile.exists())
			{
				logger.fine("Trying to read References from " + refFile);
				Properties p = new Properties();
				p.load(new FileInputStream(refFile));
				for ( Object r: p.keySet())
				{
					references.put( new Integer((String) r), (String) p.get(r));
				}					
			} 
			File stampFile = new File( binFile.getParent(), key + ".last_update");
			if (stampFile.exists())
			{
				logger.fine("Trying to read last-update info from " + stampFile);
				BufferedReader br = new BufferedReader(new FileReader(stampFile));
				String line = br.readLine();
				try {
					setLastChangedDtTm(ProjectReader.df2.parse(line));
				} catch ( ParseException e) {} 
				setLastChangedBy(br.readLine());
				br.close();
			} 
			if (obj == null)
			{
				try
				{
					parseFileName();				
				}
				catch (IllegalArgumentException ia)
				{
					logger.severe("Not setting pcode type and keys: cannot parse file name");
					logger.severe(ia.getMessage());
				}
			}
			else
			{
				objType = obj.getPeopleCodeType();
				keys = obj.getKeys();
				key = JDBCPeopleCodeContainer.objectTypeStr(objType);
				for (String k: keys)
					if (k != null && k.length() > 0)
						key += "-" + k;
			}					
		}
		else
			key = binFileName;
	}
	
	void parseFileName()
	{
		// e.g. App_Package_PeopleCode-PT_ANALYTICMODELDEFN-RuleExpressions-Assignment-OnExecute
		String[] parts = key.split("-");
		if (parts.length < 2)
			throw new IllegalArgumentException("Name convention for PeopleCode file '" + key + "' not used");
			
		objType = JDBCPeopleCodeContainer.objectTypeFromString(parts[0]);
		if (objType < 0) 
			throw new IllegalArgumentException("Don't recognize PeopleCode type '" + parts[0] + "'");
		keys = new String[parts.length - 1];
		for (int i = 1; i < parts.length; i++)
			keys[i-1] = parts[i];
		
	}
	
	@Override
	public String getCompositeKey() {
		return key;
	}

	@Override
	String getReference(int nameNum) {
		return references.get(nameNum);
	}

public static void main( String[] a)
{
	try {
		new PeopleCodeParser().reverseEngineer(
				new PeopleCodeContainerFromFile(new File("C:\\projects\\sandbox\\PeopleCode\\TEST", "BO_SEARCH_Runtime_Apps_ServiceOrder_BusinessContact_Contact_OnExecute.bin"))
		);
	} catch (Exception e) {
		e.printStackTrace();
	}
}

@Override
void writeReferencesInDirectory(File f) throws IOException {
	throw new IllegalArgumentException("Class " + getClass().getName() + " can not write its contents back to the file system");	
}

@Override
public String[] getKeys() {
	return keys;
}

@Override
public int getPeopleCodeType() {
	return objType;
}
public int[] getKeyTypes() {
	return CreateProjectDefProcessor.getObjTypesFromPCType(objType, keys);
}
	
}
