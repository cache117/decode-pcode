package decodepcode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import decodepcode.JDBCPeopleCodeContainer.KeySet;
import decodepcode.JDBCPeopleCodeContainer.StoreInList;

/**
 * 
 * Contains the static methods to run the PeopleCode extraction / decoding
 *
 */

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

public class Controller {
	public static Connection dbconn;
	static Statement st;
	static Logger logger = Logger.getLogger(Controller.class.getName());
	static String dbowner;
	static boolean writePPC = false;
	static boolean reverseEngineer= false; 
	static long countPPC=0, countSQL=0, countCONT=0;
	static boolean getContentHtml;
	static boolean getContentImage;
	static boolean saveCodeInfo;
	static String oprid = null;
	static boolean onlyCustom = false;
	final static File lastTimeFile = new File("last-time.txt");
	private static Set<String> recsProcessed = new HashSet<String>(); // for SQL and CONT IDs 
	private static Map<String, CONTobject> contMap = new HashMap<String, CONTobject>(); // for CONT objects
	
	static Properties props;
	static
	{
		try
		{
			props= readProperties();
			getContentHtml = "true".equalsIgnoreCase(props.getProperty("getContentHtml"));
			getContentImage = "true".equalsIgnoreCase(props.getProperty("getContentImage"));
			saveCodeInfo = "true".equalsIgnoreCase(props.getProperty("saveCodeInfo"));
		} catch (IOException ex)
		{
			logger.severe("Unable to read properties : " + ex);
		}
	}
	
	public static List<PeopleCodeObject> getPeopleCodeContainers(String whereClause, boolean queryAllConnections) throws ClassNotFoundException, SQLException
	{
		List<PeopleCodeObject> list = new ArrayList<PeopleCodeObject>();
		StoreInList s = new StoreInList(list, null, null);
		List<ContainerProcessor> processors = new ArrayList<ContainerProcessor>();
		processors.add(s);
		try {
			makeAndProcessContainers( whereClause, queryAllConnections, processors);
		} catch (IOException io) {}
		return list;
	}	
	
	public static Connection getJDBCconnection( String suffix) throws ClassNotFoundException, SQLException
	{
		logger.info("Getting JDBC connection");
		if (props.getProperty("driverClass" + suffix) != null)
			Class.forName(props.getProperty("driverClass" + suffix));
		else
			Class.forName(props.getProperty("driverClass"));
		Connection c = DriverManager.getConnection(props.getProperty("url" + suffix), 
				props.getProperty("user"+ suffix), 
				props.getProperty("password"+ suffix));
		if (c.isClosed())
			logger.severe("Could not open connection with suffix "+ suffix);
		return c;		
	}
	
	public interface ParameterSetter
	{
		public void setParameters(PreparedStatement ps) throws SQLException;
	}

	public static void makeAndProcessContainers( String whereClause, boolean queryAllConnections, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		makeAndProcessContainers( whereClause, queryAllConnections, processors, null);
	}
	
	/* recursive procedure to commit ancestors to some version control system branch */
	public static void submitAncestors( ContainerProcessor vcsToSubmitTo, ContainerProcessor originatingProcessor, List<ContainerProcessor> processors, SortedMap<String, JDBCPeopleCodeContainer> pcodeVersions ) throws IOException
	{
		if (originatingProcessor == null || pcodeVersions.get(originatingProcessor.getTag()) == null)
			return;
		if (originatingProcessor.equals(vcsToSubmitTo))
			throw new IllegalArgumentException("Ancestor loop detected! " + originatingProcessor.getTag());
		if (originatingProcessor instanceof VersionControlSystem)
			submitAncestors(vcsToSubmitTo, ((VersionControlSystem) originatingProcessor).getAncestor(), processors, pcodeVersions);
		logger.info("Committing " + originatingProcessor.getTag() + " version to " + vcsToSubmitTo.getTag() );
		vcsToSubmitTo.process(pcodeVersions.get(originatingProcessor.getTag()));		
	}
	
	public static void makeAndProcessContainers( 
				String whereClause, 
				boolean queryAllConnections,
				List<ContainerProcessor> processors, 
				ParameterSetter callback) throws ClassNotFoundException, SQLException, IOException
	{
		for (ContainerProcessor processor0: processors)
			processor0.aboutToProcess();
		boolean canAccessPSPCMTXT = false;
		if ("true".equalsIgnoreCase(props.getProperty("AlwaysDecode")))
			logger.info("NOT trying to read PSPCMTXT because of AlwaysDecode parameter");
		else
		{
			Statement st = null ;
			try {
				ContainerProcessor pc1 = processors.listIterator().next();
				st = pc1.getJDBCconnection().createStatement();
				st.executeQuery("select 'x' from "+ pc1.getDBowner() + "PSPCMTXT");
				canAccessPSPCMTXT = true;
				logger.info("Can read PSPCMTXT (tools >= 8.52)");
			} catch (SQLException e) {logger.info("Can NOT access PSPCMTXT:"+ e.getMessage()); }
			finally { if (st != null) st.close(); }
		}
		Set<String> processedKeys = new HashSet<String>();
		for (ContainerProcessor processor1: processors)
		{
			logger.info("\n==================== Decode PeopleCode ==================== ");
			logger.info("Decoding PeopleCode to process by querying environment: " + processor1.getTag());
			String q = "select "+ KeySet.getList() + ", LASTUPDOPRID, LASTUPDDTTM from " 
				+ processor1.getDBowner() + "PSPCMPROG pc " + whereClause + " and pc.PROGSEQ=0";
			logger.fine(q);
			logger.info("\n");

			PreparedStatement st0 =  processor1.getJDBCconnection().prepareStatement(q);
			if (callback != null)
				callback.setParameters(st0);
			ResultSet rs = st0.executeQuery();
			while (rs.next())
			{
				if (queryAllConnections)
				{
					JDBCPeopleCodeContainer.KeySet key = new KeySet(rs);
					if (processedKeys.contains(key.compositeKey()))
					{
						logger.info("Already processed key " + key.compositeKey() + "; skipping");
						continue;
					}
					processedKeys.add(key.compositeKey());
				}
				SortedMap<String, JDBCPeopleCodeContainer> pcodeVersions = new TreeMap<String, JDBCPeopleCodeContainer>();
				for (ContainerProcessor processor: processors)
				{
					JDBCPeopleCodeContainer c = new JDBCPeopleCodeContainer(processor.getJDBCconnection(), processor.getDBowner(), rs, canAccessPSPCMTXT, processor.getTag());
					if (c.hasFoundPeopleCode())
					{
						pcodeVersions.put(processor.getTag(), c);
						logger.fine("Created JDBCPeopleCodeContainer for tag " + processor.getTag() );
					}
					else
						logger.fine("No PeopleCode found in environment "+ processor.getTag() + "; nothing to process");					
				}
				/* When a PeopleCode segment is added to a version control system, first commit its ancestors to the 
				 * same processor (branch), so that they appear in the repository history for this file.
				 */
				for (ContainerProcessor processor: processors)
				{
					JDBCPeopleCodeContainer c  = pcodeVersions.get(processor.getTag());
					if (c != null 
							&& c.hasFoundPeopleCode()
							&& processor instanceof VersionControlSystem 
							&& ((VersionControlSystem) processor).getAncestor() != null
							&& !((VersionControlSystem) processor).existsInBranch(c)
							)
					{
						logger.info("First commit of " + c.getCompositeKey() + " to " + processor.getTag() + "; first committing ancestor(s).");
						submitAncestors( processor, ((VersionControlSystem) processor).getAncestor(),  processors, pcodeVersions );
					}
				}
				
				for (ContainerProcessor processor: processors)
					if (pcodeVersions.containsKey(processor.getTag()))
						processor.process(pcodeVersions.get(processor.getTag()));
				countPPC++;
			}
			if (!queryAllConnections)
			{
				logger.info("Only processing Base environment");
				continue;
			}
		}
		for (ContainerProcessor processor0: processors)
			processor0.finishedProcessing();
	}
	public static String dbTypeXLAT( String dbType)
	{
		if (dbType == null || " ".equals(dbType)) return " ";
		if ("1".equals(dbType)) return	"DB2";
		if( "2".equals(dbType)) return	"Oracle";
		if( "3".equals(dbType)) return	"Informix";
		if( "4".equals(dbType)) return	"DB2_UNIX";
		if( "6".equals(dbType)) return	"Sybase";
		if( "7".equals(dbType)) return	"Microsoft";
		return dbType;
	}
	
	/*
	 * select 
d.LASTUPDOPRID, d.LASTUPDDTTM, 
td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT 
from PSSQLDEFN d, PSSQLTEXTDEFN td where d.SQLID=td.SQLID 
---and td.SQLID in (select OBJECTVALUE1 from PSPROJECTITEM where PROJECTNAME='TEST2')
	 */
	static void processSQLs( ResultSet rs, List<ContainerProcessor> processors) throws SQLException, IOException
	{
		for (ContainerProcessor processor: processors)
		{
			String q = 					"select td.SQLTEXT, d.LASTUPDDTTM, d.LASTUPDOPRID, td.MARKET from " 
					+ processor.getDBowner() + "PSSQLDEFN d, " 					
					+ processor.getDBowner() + "PSSQLTEXTDEFN td where d.SQLID=td.SQLID and td.SQLID = ?"
					+ " and td.MARKET=? and td.DBTYPE like ? and td.SQLTYPE=?";
			processor.setPs(processor.getJDBCconnection().prepareStatement(q));
		}
		while (rs.next())
		{
			String recName = rs.getString("SQLID");
			String dbType = rs.getString("DBTYPE");
			String market = rs.getString("MARKET");
			int sqlType = rs.getInt("SQLTYPE");
			//if (" ".equals(dbType))
			//	dbType = "%";
			String sqlKey = "" + sqlType + "-" + recName + "-" + rs.getString("MARKET") + "-" + dbType;
			if (recsProcessed.contains(sqlKey))
			{
				logger.info("Already processed SQL ID "+ sqlKey + "; skipping");
				continue;
			}
			recsProcessed.add(sqlKey);
			for (ContainerProcessor processor: processors)
			{
				processor.getPs().setString(1, recName);
				processor.getPs().setString(2, market);
				processor.getPs().setString(3, dbType);
				processor.getPs().setInt(4, sqlType);
				ResultSet rs2 = processor.getPs().executeQuery();
				if (rs2.next())
				{
					String sqlStr = rs2.getString("SQLTEXT");
					if (recName == null || sqlStr == null)
						continue;
					Timestamp d = rs2.getTimestamp("LASTUPDDTTM");
					Date date = d == null? new Date(0) : new Date(d.getTime());
					SQLobject sql = new SQLobject(sqlType, recName.trim(), 
							sqlStr.trim(), 
							rs2.getString("LASTUPDOPRID"),
							date,
							rs2.getString("MARKET"),
							dbTypeXLAT(dbType));
					processor.processSQL(sql);
				}
				else
					logger.info("SQLID '" + recName + "' not found in environment " + processor.getTag());
			}
			countSQL++;
		}
	}

	public static void processSQLforProject(String projectName, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("\n==================== Decode SQL ==================== ");
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td, " 
				+ dbowner + "PSPROJECTITEM pi  where d.SQLID=td.SQLID and d.SQLID=pi.OBJECTVALUE1 and pi.OBJECTID1=65 and pi.OBJECTVALUE2=td.SQLTYPE and pi.PROJECTNAME='" + projectName + "'";  
		Statement st0 =  dbconn.createStatement();
		logger.fine(q);
		logger.info("\n");
		ResultSet rs = st0.executeQuery(q);		
		processSQLs(rs, processors);
		st0.close();
	}
	
	
	public static void processSQLsinceDate( Timestamp date, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		// query all environments with the query on LASTUPDDTTM" 
		for (ContainerProcessor processor: processors)
		{
			logger.info("\n==================== Decode SQL ==================== ");
			String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
				+ processor.getDBowner() + "PSSQLDEFN d, " + processor.getDBowner()+ "PSSQLTEXTDEFN td " 
					+ " where d.SQLID=td.SQLID and d.LASTUPDDTTM >= ?";
			if (oprid != null)
				q += " and d.LASTUPDOPRID = '" + oprid + "'";
			if (onlyCustom)
				q += " and d.LASTUPDOPRID <> 'PPLSOFT' ";

			PreparedStatement st0 =  processor.getJDBCconnection().prepareStatement(q);
			st0.setTimestamp(1, date);
			logger.fine(q);
			logger.info("\n");
			ResultSet rs = st0.executeQuery();		
			processSQLs(rs, processors);
			st0.close();
		}
	}

	/*
	public static void writeCustomSQLtoFile(File baseDir) throws ClassNotFoundException, SQLException, IOException
	{
		processCustomSQLs( new WriteDecodedPPCtoDirectoryTree(baseDir));
	}
	*/
	
	public static void processCustomSQLs(List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("\n==================== Decode SQL ==================== ");
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td " 
				+ " where d.SQLID=td.SQLID and d.LASTUPDOPRID <> 'PPLSOFT'";  
		if (oprid != null)
			q += " and d.LASTUPDOPRID = '" + oprid + "'";
		PreparedStatement st0 =  dbconn.prepareStatement(q);
		logger.fine(q);
		logger.info("\n");
		ResultSet rs = st0.executeQuery();		
		processSQLs(rs, processors);
		st0.close();
	}

	/**
	 * Process rows from PSCONTDEFN table
	 * @param rs
	 * @param processors
	 * @throws SQLException
	 * @throws IOException
	 */
	static void processCONTs(ResultSet rs, List<ContainerProcessor> processors) throws SQLException, IOException
	{
		while (rs.next())
		{
			String contName = rs.getString("CONTNAME");
			int contType = rs.getInt("CONTTYPE");
			int altContNum = rs.getInt("ALTCONTNUM");
			int seqNo  = rs.getInt("SEQNUM");
			String languageCd = rs.getString("LANGUAGE_CD");
			
			String contUniqueKey = "CONT:" + contName + "-" + contType + "-" + altContNum + "-" + languageCd + "-" + seqNo;
			if (recsProcessed.contains(contUniqueKey)) {
				logger.info("Already processed PSCONTDEFN record ID "+ contUniqueKey + "; skipping");
				continue;
			}
			recsProcessed.add(contUniqueKey);
			
			int compAlg = rs.getInt("COMPALG");
			if (compAlg != 0){
				logger.info("PSCONTDEFN record ID "+ contUniqueKey + " has unsupported COMPALG (" + compAlg + "); skipping");
				continue;
			}
			
			Timestamp d = rs.getTimestamp("LASTUPDDTTM");
			Date date = d == null ? new Date(0) : new Date(d.getTime());
			
			
			// SEQNUM support
			String contMapKey = "CONT:" + contName + "-" + contType + "-" + altContNum + "-" + languageCd;
			CONTobject cont;
			if (contMap.containsKey(contMapKey)){
				cont = contMap.get(contMapKey);
				cont.addToContDataArrays(seqNo, rs.getBytes("CONTDATA"));
			} else {
				cont = new CONTobject(contName, rs.getString("CONTFMT"), rs.getString("LASTUPDOPRID"), 
						altContNum, contType, compAlg, seqNo, languageCd, date);
				cont.addToContDataArrays(seqNo, rs.getBytes("CONTDATA"));
				contMap.put(contMapKey, cont);
				countCONT++;
			}
			
		}
		
		rs.close();
		
		for (CONTobject readyCont : contMap.values()){
			for (ContainerProcessor processor: processors){
				processor.processCONT(readyCont);
			}
		}
		
	}
	
	
	private final static int CONT_DB_CONV_NONE = 0;
	private final static int CONT_DB_CONV_844 = 1;
	private final static int CONT_DB_CONV_845 = 2;
	/**
	 * Content database convention
	 * @param processors
	 * @return
	 *  CONT_DB_CONV_NONE - none or unknown
	 *  CONT_DB_CONV_844 - PeopleSoft 8.44 and before
	 *  CONT_DB_CONV_845 - PeopleSoft 8.45 and after
	 */
	private static int getContdataConvention(List<ContainerProcessor> processors) throws SQLException
	{
		ContainerProcessor pc1 = processors.listIterator().next();
		
		try {
			st = pc1.getJDBCconnection().createStatement();
			st.executeQuery("select CONTNAME, ALTCONTNUM, CONTTYPE from "+ pc1.getDBowner() + "PSCONTDEFN where 1=0");
			st.executeQuery("select CONTNAME, ALTCONTNUM, CONTTYPE, LANGUAGE_CD from "+ pc1.getDBowner() + "PSCONTDEFNLANG where 1=0");
			st.executeQuery("select CONTNAME, ALTCONTNUM, CONTTYPE, SEQNUM, CONTDATA from "+ pc1.getDBowner() + "PSCONTENT where 1=0");
			st.executeQuery("select CONTNAME, ALTCONTNUM, CONTTYPE, LANGUAGE_CD, SEQNUM, CONTDATA from "+ pc1.getDBowner() + "PSCONTENTLANG where 1=0");
			logger.info("getContdataConvention: PeopleSoft >= 8.45 CONT data structures found.");
			return CONT_DB_CONV_845;
		} catch (SQLException e) { logger.info("getContdataConvention: PeopleSoft >= 8.45 CONT data structures not found. " + e.getMessage()); }
		finally { if (st != null) st.close(); }
		
		try {
			st = pc1.getJDBCconnection().createStatement();
			st.executeQuery("select CONTNAME, ALTCONTNUM, CONTTYPE, CONTDATA from "+ pc1.getDBowner() + "PSCONTDEFN where 1=0");
			st.executeQuery("select CONTNAME, ALTCONTNUM, CONTTYPE, LANGUAGE_CD, CONTDATA from "+ pc1.getDBowner() + "PSCONTDEFNLANG where 1=0");
			logger.info("getContdataConvention: PeopleSoft <= 8.44 CONT data structures found.");
			return CONT_DB_CONV_844;
		} catch (SQLException e) { logger.info("getContdataConvention: PeopleSoft <= 8.44 CONT data structures not found. " + e.getMessage()); }
		finally { if (st != null) st.close(); }

		logger.info("getContdataConvention: No PeopleSoft CONT data structures found.");
		return CONT_DB_CONV_NONE;
		
	}

	public static void processCONTforProject(String projectName, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		
		if (!(getContentHtml || getContentImage)){
			return;
		}
		
		logger.info("\n==================== Decode content ==================== ");
		int contdataConvention = getContdataConvention(processors);
		StringBuffer q = new StringBuffer();
		
		switch (contdataConvention) {
		case CONT_DB_CONV_845:
			
			q.append("select c.CONTNAME, c.ALTCONTNUM, c.CONTTYPE, c.CONTFMT, c.COMPALG, c.LASTUPDOPRID, c.LASTUPDDTTM, cont.LANGUAGE_CD, cont.SEQNUM, cont.CONTDATA ");
			q.append("from " + dbowner + "PSCONTDEFN c ");
			q.append(" join ( ");
			q.append("     select cd.CONTNAME, cd.ALTCONTNUM, cd.CONTTYPE, 'default' as LANGUAGE_CD, cc.SEQNUM, cc.CONTDATA from " + dbowner + "PSCONTDEFN cd");
			q.append("        join " + dbowner + "PSCONTENT cc on cc.CONTNAME = cd.CONTNAME and cc.ALTCONTNUM = cd.ALTCONTNUM and cc.CONTTYPE = cd.CONTTYPE");
			q.append("     union all");
			q.append("     select cd.CONTNAME, cd.ALTCONTNUM, cd.CONTTYPE, cd.LANGUAGE_CD, cc.SEQNUM, cc.CONTDATA from " + dbowner + "PSCONTDEFNLANG cd");
			q.append("        join " + dbowner + "PSCONTENTLANG cc on cc.CONTNAME = cd.CONTNAME and cc.ALTCONTNUM = cd.ALTCONTNUM and cc.CONTTYPE = cd.CONTTYPE and cc.LANGUAGE_CD = cd.LANGUAGE_CD ");
			q.append("  ) cont on cont.CONTNAME = c.CONTNAME and cont.ALTCONTNUM = c.ALTCONTNUM and cont.CONTTYPE = c.CONTTYPE ");
			
			break;
		case CONT_DB_CONV_844:
			
			q.append("select c.CONTNAME, c.ALTCONTNUM, c.CONTTYPE, c.CONTFMT, c.COMPALG, c.LASTUPDOPRID, c.LASTUPDDTTM, cont.LANGUAGE_CD, 1 as SEQNUM, cont.CONTDATA ");
			q.append("from " + dbowner + "PSCONTDEFN c ");
			q.append(" join ( ");
			q.append("     select CONTNAME, ALTCONTNUM, CONTTYPE, 'default' as LANGUAGE_CD, CONTDATA from " + dbowner + "PSCONTDEFN");
			q.append("     union all");
			q.append("     select CONTNAME, ALTCONTNUM, CONTTYPE, LANGUAGE_CD, CONTDATA from " + dbowner + "PSCONTDEFNLANG ");
			q.append("  ) cont on cont.CONTNAME = c.CONTNAME and cont.ALTCONTNUM = c.ALTCONTNUM and cont.CONTTYPE = c.CONTTYPE ");
			
			break;
		default:
			return;
		}
		
		q.append("  join " + dbowner + "PSPROJECTITEM pi on c.CONTNAME = pi.OBJECTVALUE1 and to_char(c.CONTTYPE) = pi.OBJECTVALUE2 and pi.OBJECTID1 in (90, 91) and pi.PROJECTNAME='" + projectName + "' ");
		
		if (getContentHtml && getContentImage){
			q.append(" where c.CONTTYPE in (1,4) ");
		} else if (getContentHtml) {
			q.append(" where c.CONTTYPE = 4 ");
		} else if (getContentImage) {
			q.append(" where c.CONTTYPE = 1 ");
		}
		
		if (contdataConvention == CONT_DB_CONV_845)
			q.append(" order by cont.SEQNUM asc ");
		
		String q_str = q.toString();
		
		Statement st0 =  dbconn.createStatement();
		logger.fine(q_str);
		logger.info("\n");
		ResultSet rs = st0.executeQuery(q_str);		
		processCONTs(rs, processors);
		st0.close();
		
		logger.info("Finished writing content files for project " + projectName);
	}
	
	
	/**
	 * Get content (HTML, Images) from PSCONTDEFN table - since date
	 * @param date
	 * @param processors
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 * @throws IOException
	 */
	public static void processCONTsinceDate(Timestamp date, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		if (!(getContentHtml || getContentImage)){
			return;
		}
		
		logger.info("\n==================== Decode content ==================== ");
		int contdataConvention = getContdataConvention(processors);
		StringBuffer q = new StringBuffer();

		switch (contdataConvention) {
		case CONT_DB_CONV_845:
			
			q.append("select c.CONTNAME, c.ALTCONTNUM, c.CONTTYPE, c.CONTFMT, c.COMPALG, c.LASTUPDOPRID, c.LASTUPDDTTM, cont.LANGUAGE_CD, cont.SEQNUM, cont.CONTDATA ");
			q.append("from " + dbowner + "PSCONTDEFN c ");
			q.append(" join ( ");
			q.append("     select cd.CONTNAME, cd.ALTCONTNUM, cd.CONTTYPE, 'default' as LANGUAGE_CD, cc.SEQNUM, cc.CONTDATA from " + dbowner + "PSCONTDEFN cd");
			q.append("        join " + dbowner + "PSCONTENT cc on cc.CONTNAME = cd.CONTNAME and cc.ALTCONTNUM = cd.ALTCONTNUM and cc.CONTTYPE = cd.CONTTYPE");
			q.append("     union all");
			q.append("     select cd.CONTNAME, cd.ALTCONTNUM, cd.CONTTYPE, cd.LANGUAGE_CD, cc.SEQNUM, cc.CONTDATA from " + dbowner + "PSCONTDEFNLANG cd");
			q.append("        join " + dbowner + "PSCONTENTLANG cc on cc.CONTNAME = cd.CONTNAME and cc.ALTCONTNUM = cd.ALTCONTNUM and cc.CONTTYPE = cd.CONTTYPE and cc.LANGUAGE_CD = cd.LANGUAGE_CD ");
			q.append("  ) cont on cont.CONTNAME = c.CONTNAME and cont.ALTCONTNUM = c.ALTCONTNUM and cont.CONTTYPE = c.CONTTYPE ");
			
			break;
		case CONT_DB_CONV_844:
			
			q.append("select c.CONTNAME, c.ALTCONTNUM, c.CONTTYPE, c.CONTFMT, c.COMPALG, c.LASTUPDOPRID, c.LASTUPDDTTM, cont.LANGUAGE_CD, 1 as SEQNUM, cont.CONTDATA ");
			q.append("from " + dbowner + "PSCONTDEFN c ");
			q.append(" join ( ");
			q.append("     select CONTNAME, ALTCONTNUM, CONTTYPE, 'default' as LANGUAGE_CD, CONTDATA from " + dbowner + "PSCONTDEFN");
			q.append("     union all");
			q.append("     select CONTNAME, ALTCONTNUM, CONTTYPE, LANGUAGE_CD, CONTDATA from " + dbowner + "PSCONTDEFNLANG ");
			q.append("  ) cont on cont.CONTNAME = c.CONTNAME and cont.ALTCONTNUM = c.ALTCONTNUM and cont.CONTTYPE = c.CONTTYPE ");
			
			break;
		default:
			
			return;
		}
		
		q.append(" where c.LASTUPDDTTM >= ?");
		
		// query all environments with the query on LASTUPDDTTM" 
		for (ContainerProcessor processor: processors)
		{
			
			if (oprid != null)
				q.append(" and c.LASTUPDOPRID = '" + oprid + "'");
				
			if (onlyCustom)
				q.append(" and c.LASTUPDOPRID <> 'PPLSOFT' ");
			
			if (getContentHtml && getContentImage){
				q.append(" and c.CONTTYPE in (1,4) ");
			} else if (getContentHtml) {
				q.append(" and c.CONTTYPE = 4 ");
			} else if (getContentImage) {
				q.append(" and c.CONTTYPE = 1 ");
			}
			
			if (contdataConvention == CONT_DB_CONV_845)
				q.append(" order by cont.SEQNUM asc ");
			
			String q_str = q.toString();
			
			PreparedStatement st0 =  processor.getJDBCconnection().prepareStatement(q_str);
			st0.setTimestamp(1, date);
			logger.fine(q_str);
			logger.info("\n");
			ResultSet rs = st0.executeQuery();
			processCONTs(rs, processors);
			st0.close();
			
		}
		
	
	}
	
	/**
	 * Get content (HTML, Images) from PSCONTDEFN table - custom
	 * @param processors
	 * @throws ClassNotFoundException
	 * @throws SQLException
	 * @throws IOException
	 */
	public static void processCustomCONTs(List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		if (!(getContentHtml || getContentImage)){
			return;
		}
		
		logger.info("\n==================== Decode content ==================== ");
		int contdataConvention = getContdataConvention(processors);
		StringBuffer q = new StringBuffer();
		
		switch (contdataConvention) {
		case CONT_DB_CONV_845:
			
			q.append("select c.CONTNAME, c.ALTCONTNUM, c.CONTTYPE, c.CONTFMT, c.COMPALG, c.LASTUPDOPRID, c.LASTUPDDTTM, cont.LANGUAGE_CD, cont.SEQNUM, cont.CONTDATA ");
			q.append("from " + dbowner + "PSCONTDEFN c ");
			q.append(" join ( ");
			q.append("     select cd.CONTNAME, cd.ALTCONTNUM, cd.CONTTYPE, 'default' as LANGUAGE_CD, cc.SEQNUM, cc.CONTDATA from " + dbowner + "PSCONTDEFN cd");
			q.append("        join " + dbowner + "PSCONTENT cc on cc.CONTNAME = cd.CONTNAME and cc.ALTCONTNUM = cd.ALTCONTNUM and cc.CONTTYPE = cd.CONTTYPE");
			q.append("     union all");
			q.append("     select cd.CONTNAME, cd.ALTCONTNUM, cd.CONTTYPE, cd.LANGUAGE_CD, cc.SEQNUM, cc.CONTDATA from " + dbowner + "PSCONTDEFNLANG cd");
			q.append("        join " + dbowner + "PSCONTENTLANG cc on cc.CONTNAME = cd.CONTNAME and cc.ALTCONTNUM = cd.ALTCONTNUM and cc.CONTTYPE = cd.CONTTYPE and cc.LANGUAGE_CD = cd.LANGUAGE_CD ");
			q.append("  ) cont on cont.CONTNAME = c.CONTNAME and cont.ALTCONTNUM = c.ALTCONTNUM and cont.CONTTYPE = c.CONTTYPE ");
			
			break;
		case CONT_DB_CONV_844:
			
			q.append("select c.CONTNAME, c.ALTCONTNUM, c.CONTTYPE, c.CONTFMT, c.COMPALG, c.LASTUPDOPRID, c.LASTUPDDTTM, cont.LANGUAGE_CD, 1 as SEQNUM, cont.CONTDATA ");
			q.append("from " + dbowner + "PSCONTDEFN c ");
			q.append(" join ( ");
			q.append("     select CONTNAME, ALTCONTNUM, CONTTYPE, 'default' as LANGUAGE_CD, CONTDATA from " + dbowner + "PSCONTDEFN");
			q.append("     union all");
			q.append("     select CONTNAME, ALTCONTNUM, CONTTYPE, LANGUAGE_CD, CONTDATA from " + dbowner + "PSCONTDEFNLANG ");
			q.append("  ) cont on cont.CONTNAME = c.CONTNAME and cont.ALTCONTNUM = c.ALTCONTNUM and cont.CONTTYPE = c.CONTTYPE ");
			
			break;
		default:
			return;
		}
		
		q.append("where c.LASTUPDOPRID <> 'PPLSOFT'");
				
		if (oprid != null)
			q.append(" and c.LASTUPDOPRID = '" + oprid + "'");
		
		if (getContentHtml && getContentImage){
			q.append(" and c.CONTTYPE in (1,4) ");
		} else if (getContentHtml) {
			q.append(" and c.CONTTYPE = 4 ");
		} else if (getContentImage) {
			q.append(" and c.CONTTYPE = 1 ");
		}
		
		if (contdataConvention == CONT_DB_CONV_845)
			q.append(" order by cont.SEQNUM asc ");
		
		String q_str = q.toString();
		
		PreparedStatement st0 =  dbconn.prepareStatement(q_str);
		logger.fine(q_str);
		logger.info("\n");
		ResultSet rs = st0.executeQuery();		
		processCONTs(rs, processors);
		st0.close();

	}

	public static List<PeopleCodeObject> getPeopleCodeContainersForProject(Properties props, String projectName) throws ClassNotFoundException, SQLException
	{
		String where = " , " + dbowner + "PSPROJECTITEM pi where  (pi.OBJECTVALUE1= pc.OBJECTVALUE1 and pi.OBJECTID1= pc.OBJECTID1) "
		    + " and ((pi.OBJECTVALUE2= pc.OBJECTVALUE2 and pi.OBJECTID2= pc.OBJECTID2 and pi.OBJECTVALUE3= pc.OBJECTVALUE3 and pi.OBJECTID3= pc.OBJECTID3 and pi.OBJECTVALUE4= pc.OBJECTVALUE4 and pi.OBJECTID4= pc.OBJECTID4)"
			+ "  or (pi.OBJECTTYPE  = 43 and pi.OBJECTVALUE3 = pc.OBJECTVALUE6))  "
			+ " and pi.PROJECTNAME='" + projectName + "' and pi.OBJECTTYPE in (8 , 9 ,39 ,40 ,42 ,43 ,44 ,46 ,47 ,48 ,58)";
		return getPeopleCodeContainers( where, false);
	}
	
	public static Properties readProperties() throws IOException
	{
		Properties props= new Properties();
		props.load(new FileInputStream("DecodePC.properties"));
		dbowner = props.getProperty("dbowner");
		dbowner = dbowner == null? "" : dbowner + ".";
		return props;
	}
	
	public static List<PeopleCodeObject> getPeopleCodeContainersForApplicationPackage(String packageName) throws ClassNotFoundException, SQLException
	{
		String where = "  , " + dbowner + "PSPACKAGEDEFN pk  where pk.PACKAGEROOT  = '" + packageName + "' and pk.PACKAGEID    = pc.OBJECTVALUE1    and pc.OBJECTVALUE1 = pk.PACKAGEROOT ";
		return getPeopleCodeContainers( where, false);
	}
	
	public static List<PeopleCodeObject> getAllPeopleCodeContainers() throws ClassNotFoundException, SQLException
	{
		String where = " where (1=1) ";
		return getPeopleCodeContainers( where, false);
	}
	
	public static void writeToDirectoryTree( List<PeopleCodeObject> containers, File rootDir) throws IOException, SQLException, ClassNotFoundException
	{	
		logger.info("Writing to directory tree " + rootDir);
		rootDir.mkdirs();
		DirTreePTmapper mapper= new DirTreePTmapper(rootDir);
		for (PeopleCodeObject p: containers)
		{
			if (p instanceof JDBCPeopleCodeContainer)
			{
				((JDBCPeopleCodeContainer) p).writeBytesToFile(mapper.getFile(p, "bin"));
				((JDBCPeopleCodeContainer) p).writeReferencesToFile(mapper.getFile(p, "references"));
			}
		}
		logger.info("Finished writing to directory tree");
	}
		
	public static void writeProjectToDirectoryTree2( String project, File rootDir) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Retrieving bytecode for project " + project);
		List<PeopleCodeObject> containers;
		containers = getPeopleCodeContainersForProject(props, project);
		writeToDirectoryTree(containers, rootDir);
	}
	
	public static void processProject( String projectName, List<ContainerProcessor> processors) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Starting to write PeopleCode for project " + projectName );
		/* for most PeopleCode object types, the 4 OBJECTVALUE fields match in PSPROJECTITEM and in PSPCMPROG, but
		 * App Engine ppc (43) and Component Record Field ppc (48) are a bit different, as their PCMPROG uses more keys fit in the project definition
		 * Also, the third key of app package ppc (58) does not seem to be used in the project definition.
		 */
		String concat = processors.get(0).getJDBCconnection().getMetaData().getDatabaseProductName().toLowerCase().indexOf("sql server") >= 0? 
				"+" 
			: 
				"||";
		
		/*String whereClause = " , " + dbowner + "PSPROJECTITEM pi where  (pi.OBJECTVALUE1= pc.OBJECTVALUE1 and pi.OBJECTID1= pc.OBJECTID1) "
	    + " and ((pi.OBJECTVALUE2= pc.OBJECTVALUE2 and pi.OBJECTID2 = pc.OBJECTID2"
	    + "   and (pi.OBJECTTYPE=58 or (pi.OBJECTVALUE3= pc.OBJECTVALUE3 and pi.OBJECTID3= pc.OBJECTID3))) "
		+ " and ((pi.OBJECTVALUE4= pc.OBJECTVALUE4 and pi.OBJECTID4= pc.OBJECTID4)"
		+ "  or (pi.OBJECTTYPE = 48 and pi.OBJECTVALUE4 like (pc.OBJECTVALUE4 " + concat + " '%' " + concat + "  pc.OBJECTVALUE5))) "
		+ "  or (pi.OBJECTTYPE = 43 and pi.OBJECTVALUE2 like pc.OBJECTVALUE2 " + concat + " '%' and pi.OBJECTVALUE3 = pc.OBJECTVALUE6))  "
		+ " and pi.PROJECTNAME='" + projectName + "' and pi.OBJECTTYPE in (8, 9, 39 ,40 ,42 ,43 ,44 ,46 ,47 ,48 ,58)";
		*/
		String whereClause = " , " + dbowner + "PSPROJECTITEM pi where  " 
				+ " pi.OBJECTID1 = pc.OBJECTID1 AND pi.OBJECTVALUE1 = pc.OBJECTVALUE1 and ("
				 +"(pi.OBJECTTYPE = 58 AND ( ( pi.OBJECTID3 = 0 AND pi.OBJECTID2 = pc.OBJECTID2 AND pi.OBJECTVALUE2 = pc.OBJECTVALUE2 ) OR ( pi.OBJECTID3 <> 0 AND pi.OBJECTID4 = 0 AND pi.OBJECTID2 = pc.OBJECTID2 AND pi.OBJECTVALUE2 = pc.OBJECTVALUE2 AND pi.OBJECTID3 = pc.OBJECTID3 AND pi.OBJECTVALUE3 = pc.OBJECTVALUE3 ) OR ( pi.OBJECTID3 <> 0 AND pi.OBJECTID4 <> 0 AND pi.OBJECTID2 = pc.OBJECTID2 AND pi.OBJECTVALUE2 = pc.OBJECTVALUE2 AND pi.OBJECTID3 = pc.OBJECTID3 AND pi.OBJECTVALUE3 = pc.OBJECTVALUE3 AND pi.OBJECTID4 = pc.OBJECTID4 AND pi.OBJECTVALUE4 = pc.OBJECTVALUE4 ))) or "
				 +"   (pi.OBJECTTYPE = 48 and pi.OBJECTVALUE4 like (pc.OBJECTVALUE4 " + concat + " '%' " + concat + "  pc.OBJECTVALUE5)) OR"
				 +"   (pi.OBJECTTYPE = 43 and pi.OBJECTVALUE2 like pc.OBJECTVALUE2 " + concat + " '%' and pi.OBJECTVALUE3 = pc.OBJECTVALUE6)  OR "
				+   " ( pi.OBJECTTYPE NOT IN (43,48,58) AND pi.OBJECTID2 = pc.OBJECTID2 AND pi.OBJECTVALUE2 = pc.OBJECTVALUE2 AND pi.OBJECTID3 = pc.OBJECTID3 AND pi.OBJECTVALUE3 = pc.OBJECTVALUE3 AND pi.OBJECTID4 = pc.OBJECTID4 AND pi.OBJECTVALUE4 = pc.OBJECTVALUE4) "				
				+ ") and pi.PROJECTNAME='" + projectName + "' and pi.OBJECTTYPE in (8, 9, 39 ,40 ,42 ,43 ,44 ,46 ,47 ,48 ,58)";
		makeAndProcessContainers( whereClause, false, processors);
		logger.info("Finished writing .pcode files for project " + projectName);		
		processSQLforProject(projectName, processors); 		
		logger.info("Finished writing .sql files for project " + projectName);

		processCONTforProject(projectName, processors);
		
	}

	
	public static class WriteToDirectoryTree extends ContainerProcessor
	{
		String dBowner;
		Connection JDBCconnection;
		private File root;
		
		PToolsObjectToFileMapper mapper;
		WriteToDirectoryTree( PToolsObjectToFileMapper _mapper)
		{
			mapper = _mapper;
		}
		WriteToDirectoryTree( File rootDir)
		{
			this( new DirTreePTmapper(rootDir));
			root = rootDir;
		}
		public void process(PeopleCodeObject p) throws IOException 
		{
			if (p instanceof JDBCPeopleCodeContainer)
			{
				((JDBCPeopleCodeContainer) p).writeBytesToFile(mapper.getFile(p, "bin"));
				((JDBCPeopleCodeContainer) p).writeReferencesToFile(mapper.getFile(p, "references"));
			}
		}
		public void processSQL(SQLobject sql) throws IOException {
			File sqlFile = mapper.getFileForSQL(sql, "sql");
			FileWriter fw = new FileWriter(sqlFile);
//			dbedit.internal.parser.Formatter formatter = new Formatter();
//			sql = formatter.format(sql, 0, null, System.getProperty("line.separator"));
			fw.write(sql.sql);
			fw.close();
			if (saveCodeInfo && (sql.lastChangedBy != null && sql.lastChanged != null))
			{
				File infoFile = mapper.getFileForSQL(sql, "last_update");
				PrintWriter pw = new PrintWriter(infoFile);
				pw.println(sql.lastChangedBy);
				pw.println(ProjectReader.df2.format(sql.lastChanged));
				pw.close();
			}
			logger.info("SQL: " + sqlFile);
		}
		
		@Override
		public void processCONT(CONTobject cont) throws IOException {
			File contFile = mapper.getFileForCONT(cont, false);
			contFile.createNewFile();
			FileOutputStream contFileOs = new FileOutputStream(contFile);
			contFileOs.write(cont.getContDataBytes());
			contFileOs.flush();
			contFileOs.close();
			
			if (saveCodeInfo && (cont.getLastChangedBy() != null && cont.getLastChangedDtTm() != null))
			{
				File infoFile = mapper.getFileForCONT(cont, true);
				PrintWriter pw = new PrintWriter(infoFile);
				pw.println(cont.getLastChangedBy());
				pw.println(ProjectReader.df2.format(cont.getLastChangedDtTm()));
				pw.close();
			}
			
			logger.info("Content: " + contFile);
		}
		
		public String getDBowner() {
			return dBowner;
		}
		public void setDBowner(String dBowner) {
			this.dBowner = dBowner;
		}
		public Connection getJDBCconnection() {
			return JDBCconnection;
		}
		public void setJDBCconnection(Connection jDBCconnection) {
			JDBCconnection = jDBCconnection;
		}
		@Override
		public void aboutToProcess() {
			if (root != null)
				System.out.println("Output in " + root.getAbsolutePath() );			
		}		
	}
	
	static void writeToDirectoryTree( String whereClause, boolean queryAllConnections, File rootDir) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write bin/ref files to directory tree " + rootDir);
		List<ContainerProcessor> processors = new ArrayList<ContainerProcessor>();
		processors.add(new WriteToDirectoryTree(rootDir));
		makeAndProcessContainers( whereClause, queryAllConnections, processors);
		logger.info("Finished writing bin/ref files");
	}

	public static void writeAllPPCtoDirectoryTree( File rootDir) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Retrieving all PeopleCode bytecode in database" );
		writeToDirectoryTree(" where 1=1 ", false, rootDir);
	}

	public static class WriteDecodedPPCtoDirectoryTree extends ContainerProcessor
	{
		PToolsObjectToFileMapper mapper;
		String extension;
		File root;
		
		PeopleCodeParser parser = new PeopleCodeParser();
		
		public WriteDecodedPPCtoDirectoryTree( PToolsObjectToFileMapper _mapper, String _extension)
		{
			mapper = _mapper;
			if (mapper instanceof DirTreePTmapper)
				root = ((DirTreePTmapper) mapper).rootDir;
			extension = _extension;
		}
		public WriteDecodedPPCtoDirectoryTree( File rootDir, String _extension)
		{
			this( new DirTreePTmapper(rootDir), _extension);
			root = rootDir;
		}
		public WriteDecodedPPCtoDirectoryTree( File rootDir)
		{
			this(rootDir, "pcode");
		}
		public void process(PeopleCodeObject p) throws IOException 
		{
			File f = mapper.getFile(p, extension);
			logger.info("PeopleCode: " + f);
			FileWriter w = new FileWriter(f);
			try {
				if (p.hasPlainPeopleCode()) // why decode the bytecode if we have the plain text...
					w.write(p.getPeopleCodeText());
				else
					parser.parse(((PeopleCodeContainer) p), w);
				w.close();
				
				if (saveCodeInfo)
				{
					Date lastUpdt = p.getLastChangedDtTm();
					if (lastUpdt != null)
					{
						File infoFile = mapper.getFile(p, "last_update");
						PrintWriter pw = new PrintWriter(infoFile);
						pw.println(p.getLastChangedBy());
						pw.println(ProjectReader.df2.format(lastUpdt));
						pw.close();			
					}
				}
			} 
			catch (IOException e) { throw e; }
			catch (Exception e)
			{
				logger.severe("Error parsing PeopleCode for " + p.getCompositeKey() + ": " + e);
				FileWriter w1 = new FileWriter(mapper.getFile(p, "log"));
				PrintWriter w2 = new PrintWriter(w1); 
				w1.write("Error decoding PeopleCode: "+ e);
				e.printStackTrace(w2);
				w1.close();
			}
			w.close();			
		}
		public void processSQL(SQLobject sql) throws IOException 
		{
			File sqlFile = mapper.getFileForSQL(sql, "sql");
			FileWriter fw = new FileWriter(sqlFile);
			fw.write(sql.sql);
			fw.close();
			if (saveCodeInfo && (sql.getLastChangedBy() != null && sql.lastChanged != null))
			{
				File infoFile = mapper.getFileForSQL(sql, "last_update");
				PrintWriter pw = new PrintWriter(infoFile);
				pw.println(sql.lastChangedBy);
				pw.println(ProjectReader.df2.format(sql.lastChanged));
				pw.close();
			}
			logger.info("SQL: " + sqlFile);
		}
		
		@Override
		public void processCONT(CONTobject cont) throws IOException {
			
			File contFile = mapper.getFileForCONT(cont, false);
			contFile.createNewFile();
			FileOutputStream contFileOs = new FileOutputStream(contFile);
			contFileOs.write(cont.getContDataBytes());
			contFileOs.flush();
			contFileOs.close();
			
			if (saveCodeInfo && (cont.getLastChangedBy() != null && cont.getLastChangedDtTm() != null))
			{
				File infoFile = mapper.getFileForCONT(cont, true);
				PrintWriter pw = new PrintWriter(infoFile);
				pw.println(cont.getLastChangedBy());
				pw.println(ProjectReader.df2.format(cont.getLastChangedDtTm()));
				pw.close();
			}
			
			logger.info("Content: " + contFile);
		}
		
		@Override
		public void aboutToProcess() {
			if (root != null)
				System.out.println("Output in " + root.getAbsolutePath() );			
			
		}
		public PToolsObjectToFileMapper getMapper() {
			return mapper;
		}		
	}
		
	public static void writeDecodedPPC( String whereClause, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write decoded PeopleCode segments");
		makeAndProcessContainers( whereClause, false, processors);
		logger.info("Finished writing .pcode files");		
	}
	
	public static class DateSetter implements ParameterSetter
	{
		Timestamp d;
		public DateSetter( Timestamp _d) { d = _d; }
		public void setParameters(PreparedStatement ps) throws SQLException {
			ps.setTimestamp(1, d);			
		}
	}	
	
	public static void writeDecodedRecentPPC( Timestamp fromDate,
			List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write decoded PeopleCode with time stamp after " + fromDate );
		if (oprid == null)
		{
			FileWriter f = new FileWriter(lastTimeFile);
			f.write(ProjectReader.df2.format(new Date()));
			f.close();
		}
		String whereClause = " where LASTUPDDTTM > ?";
		if (oprid != null)
			whereClause += " and LASTUPDOPRID = '" + oprid + "'";
		if (onlyCustom)
			whereClause += " and LASTUPDOPRID <> 'PPLSOFT' ";

		// with queryAllConnections = true, so that all environments are tracked with this query:
		makeAndProcessContainers( whereClause, true, processors, new DateSetter(fromDate));
		logger.info("Finished writing .pcode files");		
	}

	public static void writeCustomizedPPC( List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write customized PeopleCode files ");
		String whereClause = " where pc.LASTUPDOPRID <> 'PPLSOFT'";
		if (oprid != null)
			whereClause += " and pc.LASTUPDOPRID = '" + oprid + "'";
		makeAndProcessContainers( whereClause, false, processors);
		logger.info("Finished writing .pcode files");		
	}	
	
	static void writeStats()
	{
		String msg = "\nProcessed "+ countPPC + " PeopleCode segment(s), and " + countSQL + " SQL definition(s)";
		if (getContentHtml || getContentImage){
			msg = msg + ", and " + countCONT + " Content definition(s)";
		}
		System.out.println(msg);
	}
	
	@SuppressWarnings("unchecked")
	static ContainerProcessorFactory getContainerProcessorFactory( String type) throws ClassNotFoundException, InstantiationException, IllegalAccessException
	{
		Class<ContainerProcessorFactory> factoryClass = null;
		if ("ProcessToFile".equals(type))
		{
			factoryClass = (Class<ContainerProcessorFactory>) Class.forName("decodepcode.FileProcessorFactory");
		}
		else
		if ("ProcessToSVN".equals(type))
		{
				factoryClass = (Class<ContainerProcessorFactory>) 
					Class.forName("decodepcode.svn.SubversionProcessorFactory");
		}
		else
		if ("ProcessToGit".equals(type))
		{
				factoryClass = (Class<ContainerProcessorFactory>) 
					Class.forName("decodepcode.git.GitProcessorFactory");
		}
		else
		if ("ProcessBinToFile".equals(type))
		{
			factoryClass = (Class<ContainerProcessorFactory>) 
				Class.forName("decodepcode.BinFileProcessorFactory");
		}
		else
				throw new IllegalArgumentException("Don't have a processor class for " + type );
		return (ContainerProcessorFactory) factoryClass.newInstance();
		
	}
	
	public static void doTest(List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		ContainerProcessor processor1 = processors.get(0);
		PeopleCodeParser parser = new PeopleCodeParser();;

		String whereClause = " where PROGSEQ > 3";
		String q = "select "+ KeySet.getList() + ", LASTUPDOPRID, LASTUPDDTTM from " 
				+ processor1.getDBowner() + "PSPCMPROG pc " + whereClause;
			logger.info(q);
			PreparedStatement st0 =  processor1.getJDBCconnection().prepareStatement(q);
			ResultSet rs = st0.executeQuery();
			while (rs.next())
			{
					JDBCPeopleCodeContainer c = new JDBCPeopleCodeContainer(processor1.getJDBCconnection(), processor1.getDBowner(), rs, true, processor1.getTag());					
					JDBCPeopleCodeContainer c2 = new JDBCPeopleCodeContainer(processor1.getJDBCconnection(), processor1.getDBowner(), rs, false, processor1.getTag());
					if (!(c.hasFoundPeopleCode() && c2.hasFoundPeopleCode()))
						continue;
					String pc1 = c.getPeopleCodeText();
					StringWriter w = new StringWriter();
					parser.parse(c2, w);
					String pc2 = w.toString();
					if (pc1.length() > 100 && pc2.length() > 100)
					{
						String end1 = pc1.substring(pc1.length()-100).trim();
						String end2 = pc2.substring(pc2.length()-100).trim();
						if (end1.equals(end2))
						{
							System.out.println("OK");							
						}
						else
						{
							System.out.println("NOT matching: " + c.keys.compositeKey());
							System.out.println(end1);
							System.out.println(end2);
							
						}
					}
			}
		
	}
	
	/**
	 * Run from command line:
	 *  Arguments: project name, or 'since' + date (in yyyy/MM/dd format), or 'since-days' + #days, or 'custom'"
	 * @param a
	 */	
	public static void main(String[] a)
	{
		try {
			if (a.length == 0 || !a[0].startsWith("Process"))
				throw new IllegalArgumentException("First argument should be ProcessToFile or similar");			
			ContainerProcessorFactory factory = null;

			factory = getContainerProcessorFactory(a[0]);
			factory.setParameters(props, "");
			List<ContainerProcessor> processors = new ArrayList<ContainerProcessor>();
			ContainerProcessor processor = factory.getContainerProcessor();
			processor.setTag("Base");
			
			processors.add(processor);

			boolean inputIsPToolsProject = a.length >= 2 && a[1].toLowerCase().endsWith(".xml");
			for (Object key1: props.keySet())
			{
				String key = (String) key1;
				if (key.toLowerCase().startsWith("process"))
				{
					String suffix = key.substring("process".length());
					String processType = props.getProperty(key);
					try 
					{
						ContainerProcessorFactory factory1 = getContainerProcessorFactory(processType);
						factory1.setParameters(props, suffix);
						ContainerProcessor processor1 = factory1.getContainerProcessor();
						if (!inputIsPToolsProject)
						{
							processor1.setJDBCconnection(getJDBCconnection(suffix));
							String schema = props.getProperty("dbowner" + suffix);
							schema = schema == null || schema.length() == 0? "" : schema + ".";
							processor1.setDBowner(schema);
						}
						processor1.setTag(suffix);
						processors.add(processor1);
					} catch (IllegalArgumentException ex)
					{
						logger.severe("Process type for "+ key + " not known - skipping");
					}
					catch (SQLException ex)
					{
						logger.severe(ex.getMessage());
						logger.severe("JDBC connection parameters for processor with suffix '" + suffix + "' absent or invalid");
					}
				}
			}						
			
			for (ContainerProcessor p: processors)
				if (p instanceof VersionControlSystem)
				{
					String ancestor = "Base".equals(p.getTag())? 
							props.getProperty("ancestor") 
						: 
							props.getProperty("ancestor" + p.getTag());
					if (ancestor != null)
					{
						for (ContainerProcessor p1: processors)
							if (p != p1 && ancestor.equals(p1.getTag()) && p1 instanceof VersionControlSystem)
									((VersionControlSystem) p).setAncestor(p1);
						if (((VersionControlSystem) p).getAncestor() == null )
							throw new IllegalArgumentException("Invalid value for 'ancestor" + p.getTag() + "' property");
					}
				}
			
			if (inputIsPToolsProject)
			{
				String target = (a.length >=3) ? a[2] : "Base";
				ProjectReader p = new ProjectReader();
				boolean found = false;
				for (ContainerProcessor processor1: processors)
					if (target.equals(processor1.getTag()))
					{
						File f = new File(a[1]);
						System.out.println("Reading PeopleTools project " + f.getName() + ("Base".equals(target)? "" : ", processing for environment " + target));
						found = true;
						p.setProcessor(processor1);
						p.readProject( f);
						writeStats();
					}
					if (!found)
						logger.severe("There is no target environment labeled '" + target + "' - file not processed");
					return;
			}

			// not reading project, so need to have JDBC Connection to read bytecode
			dbconn = getJDBCconnection("");
			processor.setJDBCconnection(dbconn);
			processor.setDBowner(dbowner);
			

			if (a.length >= 2 && "TEST".equalsIgnoreCase(a[1]))
			{
				doTest(processors);
				return;
			}
			
			if (a.length >= 3 && "OPRID".equalsIgnoreCase(a[a.length-2]))
				oprid = a[a.length-1];

			if (a.length >= 2 && "CUSTOM".equalsIgnoreCase(a[a.length-1]))
				onlyCustom = true;
			
			if (a.length > 2 && "since".equalsIgnoreCase(a[1]))
			{
				SimpleDateFormat sd = new SimpleDateFormat("yyyy/MM/dd");
				Date time = sd.parse(a[2]);
				Timestamp d = new Timestamp(time.getTime());
				writeDecodedRecentPPC(d, processors);
				processSQLsinceDate( d, processors);
				
				processCONTsinceDate(d, processors);
				
				writeStats();
				return;
			}

			if (a.length > 2 && "since-days".equalsIgnoreCase(a[1]))
			{
				Date time = new Date();
				long days = Long.parseLong(a[2]);
				Timestamp d = new Timestamp(time.getTime() - 24 * 60 * 60 * 1000 * days);
				writeDecodedRecentPPC(d, processors);
				processSQLsinceDate( d, processors);
				
				processCONTsinceDate(d, processors);
				
				writeStats();
				return;
			}
			
			if (a.length >= 2 && "since-last-time".equalsIgnoreCase(a[1]))
			{
				if (!lastTimeFile.exists())
				{
					logger.severe("Need file 'last-time.txt' to run with 'since-last-time' parameter");
					return;
				}
				BufferedReader br = new BufferedReader(new FileReader(lastTimeFile));
				String line = br.readLine();
				br.close();
				Date d;
				String timeOffset = null;
				try {
					d = ProjectReader.df2.parse(line);
					timeOffset = props.getProperty("last-time-offset");
					if (timeOffset != null)
						d = new Date(d.getTime() - Long.parseLong(timeOffset) * 60 * 1000);
				} catch (ParseException e) {
					logger.severe("Found " + lastTimeFile + ", but can't parse its contents to a date/time: " + e.getMessage());
					return;
				}
				logger.info("Processing objects modified since last time = " + line 
						+ (timeOffset == null? "" : "( with a " + timeOffset + "-min offset)"));
				writeDecodedRecentPPC(new Timestamp(d.getTime()), processors);
				processSQLsinceDate( new Timestamp(d.getTime()), processors);
				
				processCONTsinceDate(new Timestamp(d.getTime()), processors);
				
				writeStats();
				return;				
			}
			
			if (a.length >= 2 && "custom".equalsIgnoreCase(a[1]))
			{
				writeCustomizedPPC(processors);
				processCustomSQLs( processors);
				writeStats();
				return;				
			}
	
			if (a.length == 2)
			{
				processProject(a[1], processors);
				writeStats();
				return;
			}
			
			System.err.println("Arguments: ProcessToXXX followed by project name, or 'since' + date (in yyyy/MM/dd format), or 'since-days' + #days, or 'since-last-time', or 'custom'");

		} catch (Exception e) {
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	

}
