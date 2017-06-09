package decodepcode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Date;
import java.util.logging.Logger;

public abstract class PeopleCodeContainer implements PeopleCodeObject 
{
	int pos;
	byte[] bytes;
	String lastChangedBy, source;
	Date lastChangedDtTm;
	
	public boolean hasPlainPeopleCode() 
	{ 
		return peopleCodeText != null; 
	}  
	abstract String getReference( int nameNum); 
	byte read() { return bytes[pos]; }
	byte get()  { return bytes[pos++]; }
	byte readAhead()
	{
		if (pos >= bytes.length - 1)
			return -1;
		else
			return bytes[pos];
	}
	String peopleCodeText;
	Logger logger = Logger.getLogger(getClass().getName());
	
	public abstract String getCompositeKey();
	public abstract String[] getKeys();
	public abstract int getPeopleCodeType();
	
	void writeBytesToStream( OutputStream os) throws IOException
	{
		os.write(bytes);
		os.flush();
	}
	void writeBytesToFile(File f) throws IOException
	{
		FileOutputStream fo = new FileOutputStream(f);
		writeBytesToStream(fo);
		fo.close();
	}
	abstract void writeReferencesInDirectory( File f) throws IOException;
	
	void writeInDirectory(File f) throws IOException
	{
		if (!f.isDirectory()) 
			throw new IllegalArgumentException(""+ f + " is not a directory");
		File binFile = new File(f, getCompositeKey() + ".bin");
		if (binFile.exists())
			logger.warning("Overwriting " + binFile);
		writeBytesToFile(binFile);
		writeReferencesInDirectory( f);
	}	
	public String getPeopleCodeText() 
	{
		if (peopleCodeText == null)
			throw new IllegalArgumentException("Text PeopleCode has not been set");
		return peopleCodeText;
	}
	public void setPeopleCodeText(String _PeopleCodeText) 
	{
		this.peopleCodeText = _PeopleCodeText;
	}
	public void readPeopleCodeTextFromFile( File f) throws IOException
	{
		StringWriter sw = new StringWriter();
		String line;
		BufferedReader br = new BufferedReader(new FileReader(f));
		while ((line = br.readLine()) != null)
		{
			sw.write(line);
			sw.write(PeopleCodeParser.eol);
		}
		br.close();
		setPeopleCodeText(sw.toString());
	}
	
	public static String objectTypeStr( int objType)
	{
		switch (objType) {
		     case   8 : return "Record_PeopleCode";
		     case   9 : return "Menu_PeopleCode";
		     case  39 : return "Message_PeopleCode";
		     case  40 : return "Subscription_PeopleCode";
		     case  42 : return "Component_Interface_PeopleCode";
		     case  43 : return "Application_Engine_PeopleCode";
		     case  44 : return "Page_PeopleCode";
		     case  46 : return "Component_PeopleCode";
		     case  47 : return "Component_Record_PeopleCode";
		     case  48 : return "Component_Record_Field_PeopleCode";
		     case  58 : return "App_Package_PeopleCode";		     
			default :
				return "objecttype_" + objType ;
		}
	}
	final static int[] objTypes={ 8 , 9 , 39 , 40 , 42 , 43 , 44 , 46 , 47 , 48 , 58} ;
	public static int objectTypeFromString( String s)
	{
		if (s == null) return -1;
		for (int i: objTypes)
			if (s.equals(objectTypeStr(i)))
					return i;
		return -1;
		
	}

	public String getLastChangedBy() {
		return lastChangedBy;
	}
	public void setLastChangedBy(String lastChangedBy) {
		this.lastChangedBy = lastChangedBy;
	}
	public Date getLastChangedDtTm() {
		return lastChangedDtTm;
	}
	public void setLastChangedDtTm(Date lastChangedDtTm) {
		this.lastChangedDtTm = lastChangedDtTm;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}
}
