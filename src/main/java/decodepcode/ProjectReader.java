package decodepcode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import decodepcode.Controller.WriteDecodedPPCtoDirectoryTree;

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

public class ProjectReader 
{

	static Logger logger = Logger.getLogger( ProjectReader.class.getName() );
	final static String eol=System.getProperty("line.separator");
	private PeopleToolsProject project;
	java.util.Date timeStamp;
	String lastUpdOprid, sqlRecordName, sqlType, source, sqlMarket = "GBL", sqlDbType = " ";
	ProjectPeopleCodeContainer container = new ProjectPeopleCodeContainer();
	SQLobject sqlObject;
	ContainerProcessor processor;
	
	static class PeopleToolsProject
	{
	}
		
	public static SimpleDateFormat 
		df  = new SimpleDateFormat("yyyy-MM-dd-HH.mm.ss.000000"), //2006-10-24-15.42.43.000000
		df2 = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss"), 
		df3 = new SimpleDateFormat("yyyy-MM-dd");
	
	static String formatEOLchars(String in) throws IOException
	{
		StringWriter w = new StringWriter();
		BufferedReader br = new BufferedReader(new StringReader(in));
		String line, eol = System.getProperty("line.separator");
		while ((line = br.readLine()) != null)
		{
			w.write(line.replaceAll("\\s+$", "")); // also do rtrim
			w.write(eol);
		}
		return w.toString();
	}
	
	private void visit(Node node, int level) throws IOException
	{
		logger.fine("Level = " + level + ", node = '" + node.getNodeName() + "'");
		NodeList nl = node.getChildNodes();
		for(int i=0, cnt=nl.getLength(); i<cnt; i++)
		{
			Node n = nl.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().startsWith("szObjectValue_"))
			{
				int nr = Integer.parseInt(n.getNodeName().substring("szObjectValue_".length()));
				container.objectValue[nr] = n.getTextContent();
			}
			if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().startsWith("eObjectID_"))
			{
				int nr = Integer.parseInt(n.getNodeName().substring("eObjectID_".length()));
				container.objectIDs[nr] = Integer.parseInt(n.getTextContent());
			}
//	        <szLastUpdDttm>2006-10-24-15.42.43.000000</szLastUpdDttm>
//			<szLastUpdOprId>PPLSOFT</szLastUpdOprId>
			if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("szLastUpdDttm"))
			{
				try {
					timeStamp = df.parse(n.getTextContent());
				} catch (ParseException ex) {}
			}
			if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("szLastUpdOprId"))
			{
					lastUpdOprid = n.getTextContent();
			}
			if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("szSqlId"))
			{
					sqlRecordName = n.getTextContent();
			}
			if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("szSqlType"))
			{
					sqlType = n.getTextContent();
			}
			
			if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("szMarket"))
				sqlMarket = n.getTextContent();
			if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals("cDbType"))
			{
				sqlDbType = n.getTextContent();
				if (sqlDbType == null || sqlDbType.trim().length() == 0)
					sqlDbType = " ";
				sqlDbType = Controller.dbTypeXLAT(sqlDbType);
			}
	 
			if ((n.getNodeType() == Node.ELEMENT_NODE) 
					&& (   "peoplecode_text".equals(((Element) n).getNodeName()) ))				
			{
				String key = container.getKeyFromObjectValues();
				logger.fine("============== level = " + level + " key = '" + key + "'\n");
				container.setPeopleCodeText(formatEOLchars(n.getTextContent()));
				container.setLastChangedBy(lastUpdOprid);
				container.setLastChangedDtTm(timeStamp);
				container.setSource(source);
				processor.process(container);
				Controller.countPPC++;
				lastUpdOprid = null;
				timeStamp = null;				
			}
			
			if ("lpszSqlText".equals(n.getNodeName()) && level == 9)				
			{
				String key = container.getKeyFromObjectValues();
				logger.fine("==== lpszSqlText ========= level = " + level + " key = '" + key + "'");
				SQLobject sqlObj = new SQLobject(Integer.parseInt(sqlType), sqlRecordName, n.getTextContent(), lastUpdOprid, timeStamp,
						sqlMarket, sqlDbType);
				sqlObj.setSource(source);
				processor.processSQL( sqlObj);
				Controller.countSQL++;
				lastUpdOprid = null;
				timeStamp = null;
			}
			visit(n, level+1);
		}
	}

	/**
	 * Read the .xml project file, which is not a valid XML file because it has more than one root element.
	 * For this reason, read the project file line for line, create a temporary XML file for each <instance> block, 
	 * and process that file. 
	 */
	public PeopleToolsProject readProject( File file) throws IOException, SAXException, ParserConfigurationException
	{
		project = new PeopleToolsProject();
		source = file.getName();
		processor.aboutToProcess();
		File dir = new File(System.getProperty("java.io.tmpdir"));
		if (!dir.exists() || !dir.isDirectory())
			throw new IOException("Temp dir "+ dir + " not accessible");
		File file2 = new File(dir, "temp_" + file.getName());
		if (file2.exists())
			file2.delete();
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream( file), "utf-8"));
		Writer w = new OutputStreamWriter(new FileOutputStream(file2), "utf-8");
		String line, header;
		header = br.readLine();
		w.write(header);
		int count = 0;
		logger.info("Starting to process " + file + ", temp file is " + file2);
		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		while( (line = br.readLine()) != null)
		{
			if (w ==null)
			{
				w = new OutputStreamWriter(new FileOutputStream(file2), "utf-8");
				w.write(header);
			}
			w.write(line);
			w.write(eol);
			if (line.contains("</instance>"))
			{
				w.close();
				count++;
				logger.info("Created file # " + count);
				visit(builder.parse(file2), 0);
				file2.delete();
				w = null;
			}
		}
		br.close();
		return project;
	}
	
	
static class ProjectPeopleCodeContainer extends PeopleCodeContainer
{
	int objectIDs[] = new int[7];
	int objectType= -1;
	String objectValue[] = new String[7];
	String peopleCode, sql;
	
	@Override
	public String getCompositeKey() {
		// TODO Auto-generated method stub
		return null;
	}
	private String getKeyFromObjectValues()
	{
		String key = null;
		objectType = JDBCPeopleCodeContainer.getObjectType(objectIDs);
		if (objectType >= 0)
			key = JDBCPeopleCodeContainer.objectTypeStr(objectType);
		for (String s: objectValue)
		{
			String k = s == null? "NULL" : s.trim();
			if (k.length() > 0)
				key = key==null? k: key + "-"+ k;
		}
		key = key.substring(0, key.length());
		return key;
	}


	@Override
	String getReference(int nameNum) {
		throw new IllegalArgumentException("Not implemented");		
	}

	@Override
	void writeReferencesInDirectory(File f) throws IOException {
		throw new IOException("Not implemented");		
	}

	@Override
	public String[] getKeys() {
		return objectValue;
	}
	@Override
	public int getPeopleCodeType() {
		return JDBCPeopleCodeContainer.getObjectType(objectIDs);
	}
	public String getPeopleCode() {
		return peopleCode;
	}
	public void setPeopleCode(String peopleCode) {
		this.peopleCode = peopleCode;
	}
	public String getSql() {
		return sql;
	}
	public void setSql(String sql) {
		this.sql = sql;
	}
	public int[] getKeyTypes() {
		return objectIDs;
	}	
}

/**
 * @param args PeopleTools project (.xml)
 */
public static void main(String[] args) {
	try {
		ProjectReader p = new ProjectReader();
		if (args.length > 0)
		{
			String xmlFile = args[0];
			if (!xmlFile.toLowerCase().endsWith(".xml"))
			{
				logger.severe("Expected .xml project file for argument, but got " + xmlFile);
				return;
			}
			String fileName = new File(xmlFile).getName();
			String projName = fileName.substring(0, fileName.length() - 4);
			File dir = new File(".", projName);
			ContainerProcessor processor = new WriteDecodedPPCtoDirectoryTree(new DirTreePTmapper( dir), "pcode");
			processor.aboutToProcess();
			p.setProcessor(processor);
			dir.mkdir();
			System.out.println("Output in " + dir.getAbsolutePath() );
			p.readProject( new File(xmlFile));
			processor.finishedProcessing();
			Controller.writeStats();
		}
		else
		{
			System.out.println("Usage: java peoplecode.decoder.ProjectReader yourproject.xml");
			System.out.println("This will read this PeopleTools project and create a folder structure with the same name in the current directory");
		}
	}
	catch (Throwable e) {
		e.printStackTrace();
	}
}

public void setProcessor(ContainerProcessor processor) {
	this.processor = processor;
}	

}
