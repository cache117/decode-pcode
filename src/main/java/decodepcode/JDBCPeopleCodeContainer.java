package decodepcode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/*
 * Copyright (c) 2011 Erik H (erikh3@users.sourceforge.net)

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

*/

/**
 * Extends the base PeopleCodeContainer with functionality to retrieve the bytecode from the PeopleTools tables
 */

public class JDBCPeopleCodeContainer extends PeopleCodeContainer implements PeopleCodeObject 
{
	public static class KeySet
	{
		private String[] values;
		private int[] objIDs;
		private int objType = -1;
		private int nrOfValuesToCompare = 7;
		
		public KeySet(ResultSet rs) throws SQLException
		{
			this( rs, true);
		}
		public KeySet( ResultSet rs, boolean limitType58) throws SQLException
		{
			values = new String[7];
			objIDs = new int[7];
			for (int i = 0; i < 7; i++)
			{
				values[i] = rs.getString("OBJECTVALUE" + (i+1));
				objIDs[i] = rs.getInt("OBJECTID" + (i+1));
			}
			objType = getObjectType(objIDs);
			if (objType == 58 && limitType58)
				setNrOfValuesToCompare(3);
		}
		public KeySet( int _objType, ResultSet rs) throws SQLException
		{
			this(rs);
			objType =_objType;
		}
		public static String getList()		
		{
			String s = "";
			for (int i = 0; i < 7; i++)
				s = s + (i==0? "": ", ") + " pc.OBJECTID"+ (i+1) + ", pc.OBJECTVALUE"+ (i+1);
			return s;
		}
		public String getWhere( String prefix)
		{
			String s = "";
			for (int i = 0; i < nrOfValuesToCompare; i++)
				s = s + (i==0? "": " and ") + prefix + "OBJECTVALUE"+ (i+1) + " = '" + values[i] + "'";
			return s;
		}
		public String getWhere()
		{
			return getWhere(" ");
		}
		public String compositeKey()
		{
			String s;
			if (objType <= 0)
			{
				s = "";
				for (int i = 0; i < 7; i++)
					if (values[i] != null && values[i].trim().length() > 0)
						s = s + (i==0? "": "-") + values[i].trim();

			}
			else
			{
				s = PeopleCodeContainer.objectTypeStr(objType);
				for (int i = 0; i < 7; i++)
					if (values[i] != null && values[i].trim().length() > 0)
						s = s + "-" + values[i].trim();
			}
			return s;
		}
		@Override
		public boolean equals( Object o)
		{
			return compositeKey().equals( ((KeySet) o).compositeKey());
		}
		@Override
		public String toString()
		{
			return compositeKey();
		}
		public int getNrOfValuesToCompare() {
			return nrOfValuesToCompare;
		}
		public void setNrOfValuesToCompare(int nrOfValuesToCompare) {
			this.nrOfValuesToCompare = nrOfValuesToCompare;
		}
	}
	
	
	static Logger logger = Logger.getLogger(JDBCPeopleCodeContainer.class.getName());
	static final String keywordArray[] = {"Component","Panel","RecName", "Scroll", "MenuName", "BarName", "ItemName", "CompIntfc", 
		"Image", "Interlink", "StyleSheet", "FileLayout", "Page", "PanelGroup", "Message", "BusProcess", "BusEvent", "BusActivity"
		, "Field", "Record"
		};
	Map<Integer, String>references = new HashMap<Integer, String>();
	KeySet keys;
	static Map<String, String> keyWords = new HashMap<String, String>();
	static
	{
		for (String s: keywordArray)
			keyWords.put(s.toUpperCase(), s);
//		keyWords.put("FIELD", "");
//		keyWords.put("RECORD", "");		
	}
	boolean foundPeopleCode = false;
	Connection originatingConnection;
	String originatingName;
	/**
	 * 
	 * @param dbconn Connection from which bytecode is to be read
	 * @param dbowner schema for this connection
	 * @param rs ResultSet containing key info for the PCMPROG row(s)to be read; this ResultSet may or may not come
	 * 			from the same database as the Connection parameter
	 * @param canAccessPROGTXT if true, get plain text from PSPROGTEXT
	 * @param sourceDB  
	 */
	public JDBCPeopleCodeContainer( Connection dbconn, 
			String dbowner, 
			ResultSet rs , 
			boolean canAccessPROGTXT,
			String dbName) throws SQLException, ClassNotFoundException
	{
		originatingName = dbName;
		String cat = dbconn.getCatalog();
		if (cat != null && cat.trim().length() > 0)
			source = cat.trim();
		else
			source = dbName;
		if (!canAccessPROGTXT)
			initJDBCPeopleCodeContainerViaDecode(dbconn, dbowner, rs);
		else
		{
			keys = new KeySet(rs, false);
			Statement st = dbconn.createStatement();
			
			String q ="select LASTUPDDTTM, LASTUPDOPRID from " + dbowner + "PSPCMPROG pc where " + keys.getWhere() + " and PROGSEQ = 0";
			logger.fine(q);
			ResultSet rs0 = st.executeQuery(q);
			foundPeopleCode = rs0.next();
			if (!foundPeopleCode)
			{
				logger.fine("Nothing found - setting foundPeopleCode=false for this environment");
				st.close();
				return;
			}
			setLastChangedDtTm(rs0.getTimestamp("LASTUPDDTTM"));
			setLastChangedBy(rs0.getString("LASTUPDOPRID").trim());
			
			q = "select PCTEXT from " + dbowner + "PSPCMTXT pt where" 
					+ keys.getWhere(" pt.") 
					 + " order by pt.PROGSEQ";
			logger.fine(q);
			ResultSet rs2 = st.executeQuery(q);
			StringBuffer sb = new StringBuffer();
			while (rs2.next())
			{	
				sb.append(rs2.getString("PCTEXT"));
				foundPeopleCode = true;
			}
			st.close();
			this.setPeopleCodeText(sb.toString());
		}
		if (foundPeopleCode)
			originatingConnection = dbconn;
	}

	/**
	 * 
	 * @param dbconn Connection from which bytecode is to be read
	 * @param dbowner schema for this connection
	 * @param rs ResultSet containing key info for the PCMPROG row(s)to be read; this ResultSet may or may not come
	 * 			from the same database as the Connection parameter. 
	 */
	void initJDBCPeopleCodeContainerViaDecode( Connection dbconn, String dbowner, ResultSet rs ) throws SQLException, ClassNotFoundException
	{
		keys = new KeySet(rs, false);
		Statement st = dbconn.createStatement();
		
		String sqlWhere = keys.getWhere();
		
		String q ="select LASTUPDDTTM, LASTUPDOPRID, PROGTXT from " + dbowner + "PSPCMPROG pc where " + sqlWhere + " order by PROGSEQ";
		logger.fine(q);
		ResultSet rs2 = st.executeQuery(q);
		while (rs2.next())
		{	
			setLastChangedDtTm(rs2.getTimestamp("LASTUPDDTTM"));
			setLastChangedBy(rs2.getString("LASTUPDOPRID").trim());
			byte[] b = rs2.getBytes("PROGTXT");
			if (bytes == null)
				bytes = b;
			else
			{
				byte[] b1 = new byte[bytes.length + b.length];
				for (int i = 0; i < bytes.length; i++)
					b1[i] = bytes[i];
				for (int i = bytes.length; i < bytes.length + b.length; i++)
					b1[i] = b[i - bytes.length];
				bytes = b1;
			}
		}
		rs2.close();
		if (bytes == null || bytes.length == 0)
		{
			//logger.severe("Nothing retrieved from PSPCMPRPG with "+ q);
			st.close();
			return;
		}
		foundPeopleCode = true;
		logger.fine("PeopleCode byte length = " + bytes.length + " (0x" + Integer.toString(bytes.length, 16) + ")" );		
		
		q ="select RECNAME, REFNAME, NAMENUM from " + dbowner + "PSPCMNAME where " + sqlWhere;
		logger.fine(q);
		rs2 = st.executeQuery(q);
		while (rs2.next())
		{
			if (references.get(rs2.getInt("NAMENUM")) != null)
				logger.warning("Duplicate reference: " + rs2.getInt("NAMENUM") + " in " + sqlWhere);
			String recname = rs2.getString("RECNAME").trim(),
			special = keyWords.get(recname);
			if (special != null)
				recname = special;
			String refName = rs2.getString("REFNAME").trim();
			
			references.put(rs2.getInt("NAMENUM"), 
					(recname != null && recname.length()> 0? recname + "." : "") 
					+ refName);
		}
		rs2.close();
		logger.fine("" + references.size() + " references found");
		st.close();
	}
	
	@Override
	String getReference(int nameNum) {
		return references.get(nameNum);
	}

	@Override
	public String getCompositeKey()
	{
		return keys.compositeKey();
	}

	void writeReferencesToFile( File ff) throws IOException
	{
		FileWriter w= new FileWriter(ff);
		for (Integer ref: references.keySet())
			w.write(ref + "=" + references.get(ref) + PeopleCodeParser.eol);
		w.close();			
	}
	
	@Override
	void writeReferencesInDirectory(File f) throws IOException 
	{
		File ff = new File( f, keys.compositeKey() + ".references");
		writeReferencesToFile(ff);
	}

	static int getObjectType( int[] objIDs)
	{
		int objType = -1;
		if (objIDs[1-1] == 1  && objIDs[2-1] == 2 && objIDs[3-1] == 12  && objIDs[4-1] == 0) objType=     8;
		if (objIDs[1-1] == 3  && objIDs[2-1] == 4 && objIDs[3-1] == 5  && objIDs[4-1] == 12)  objType=     9;
		if (objIDs[1-1] == 60  && objIDs[2-1] == 12 && objIDs[3-1] == 0  && objIDs[4-1] == 0)  objType=     39;
		if (objIDs[1-1] == 60  && objIDs[2-1] == 87 && objIDs[3-1] == 12  && objIDs[4-1] == 0)  objType=     40;
		if (objIDs[1-1] == 66  && objIDs[2-1] == 77 && objIDs[3-1] == 78  && objIDs[4-1] == 12)  objType=     43;
		if (objIDs[1-1] == 66  && objIDs[2-1] == 77 && objIDs[3-1] == 39  && objIDs[4-1] == 20)  objType=     43;
		if (objIDs[1-1] == 74) objType = 42;
		if (objIDs[1-1] == 9  && objIDs[2-1] == 12 && objIDs[3-1] == 0  && objIDs[4-1] == 0)  objType=     44;
		if (objIDs[1-1] == 10  && objIDs[2-1] == 39 && objIDs[3-1] == 12  && objIDs[4-1] == 0)  objType=     46;
		if (objIDs[1-1] == 10  && objIDs[2-1] == 39 && objIDs[3-1] == 1  && objIDs[4-1] == 12)  objType=     47;
		if (objIDs[1-1] == 10  && objIDs[2-1] == 39 && objIDs[3-1] == 1  && objIDs[4-1] == 2)  objType=     48;
		if (objIDs[1-1] == 104  )  objType=     58;

		// File reference (added 11/2012)
		if (objIDs[1-1] == 121  && objIDs[2-1] == 122 && objIDs[3-1] == 0  && objIDs[4-1] == 0)  objType=     68;
		
		
		if (objType < 0)
			logger.warning("Could not determine the object type for "+ objIDs[1-1] + "/" + objIDs[2-1]+ "/" + objIDs[3-1] + "/" + objIDs[4-1]);
		
		return objType;
	}


	
	
	static class StoreInList extends ContainerProcessor
	{
		
		List<PeopleCodeObject> list;
		List<SQLobject> sqlList;
		List<CONTobject> contList;

		public StoreInList(List<PeopleCodeObject> _list, List<SQLobject> _sqlList, List<CONTobject> _contList) 
		{
			list = _list;
			sqlList = _sqlList;
			contList = _contList;
		}

		public void process(PeopleCodeObject c) 
		{
			list.add(c);
		}
		public void processSQL(SQLobject sql) throws IOException {
			sqlList.add(sql);
		}
		
		public void processCONT(CONTobject cont) throws IOException {
			contList.add(cont);
		}
		
		@Override
		public void aboutToProcess() {
			// TODO Auto-generated method stub
			
		}		
	}
	
	
	@Override
	public String[] getKeys() 
	{
		return keys.values;
	}
	
	@Override
	public int getPeopleCodeType() {
		return keys.objType;
	}

	public boolean hasFoundPeopleCode() {
		return foundPeopleCode;
	}

	public int[] getKeyTypes() {
		return keys.objIDs;
	}

	public Connection getOriginatingConnection() {
		return originatingConnection;
	}

	public String getOriginatingName() {
		return originatingName;
	}
}
