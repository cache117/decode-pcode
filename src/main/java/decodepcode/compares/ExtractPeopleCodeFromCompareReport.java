package decodepcode.compares;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import decodepcode.ContainerProcessor;
import decodepcode.Controller;
import decodepcode.CreateProjectDefProcessor;
import decodepcode.DirTreePTmapper;
import decodepcode.PToolsObjectToFileMapper;
import decodepcode.PeopleCodeObject;



public class ExtractPeopleCodeFromCompareReport 
{
	static Logger logger = Logger.getLogger(ExtractPeopleCodeFromCompareReport.class.getName());
	public final static String eol = System.getProperty("line.separator"),
		PEOPLECODETREE="PeopleCodeTrees";
	public final static int
		PROCESS_SOURCE_PCODE = 0,
		PROCESS_TARGET_PCODE = 1,
		SET_LINKS_IN_XML = 2;
	final static String[] modeStr = { "process 'source' PeopleCode", "process 'target' PeopleCode", "set pcode links"}; 
	
	private static HashMap<String,String> htmlEntities;
	  static {
	    htmlEntities = new HashMap<String,String>();
	    htmlEntities.put("&lt;","<")    ; htmlEntities.put("&gt;",">");
	    htmlEntities.put("&amp;","&")   ; htmlEntities.put("&quot;","\"");
	    htmlEntities.put("&agrave;","à"); htmlEntities.put("&Agrave;","À");
	    htmlEntities.put("&acirc;","â") ; htmlEntities.put("&auml;","ä");
	    htmlEntities.put("&Auml;","Ä")  ; htmlEntities.put("&Acirc;","Â");
	    htmlEntities.put("&aring;","å") ; htmlEntities.put("&Aring;","Å");
	    htmlEntities.put("&aelig;","æ") ; htmlEntities.put("&AElig;","Æ" );
	    htmlEntities.put("&ccedil;","ç"); htmlEntities.put("&Ccedil;","Ç");
	    htmlEntities.put("&eacute;","é"); htmlEntities.put("&Eacute;","É" );
	    htmlEntities.put("&egrave;","è"); htmlEntities.put("&Egrave;","È");
	    htmlEntities.put("&ecirc;","ê") ; htmlEntities.put("&Ecirc;","Ê");
	    htmlEntities.put("&euml;","ë")  ; htmlEntities.put("&Euml;","Ë");
	    htmlEntities.put("&iuml;","ï")  ; htmlEntities.put("&Iuml;","Ï");
	    htmlEntities.put("&ocirc;","ô") ; htmlEntities.put("&Ocirc;","Ô");
	    htmlEntities.put("&ouml;","ö")  ; htmlEntities.put("&Ouml;","Ö");
	    htmlEntities.put("&oslash;","ø") ; htmlEntities.put("&Oslash;","Ø");
	    htmlEntities.put("&szlig;","ß") ; htmlEntities.put("&ugrave;","ù");
	    htmlEntities.put("&Ugrave;","Ù"); htmlEntities.put("&ucirc;","û");
	    htmlEntities.put("&Ucirc;","Û") ; htmlEntities.put("&uuml;","ü");
	    htmlEntities.put("&Uuml;","Ü")  ; htmlEntities.put("&nbsp;"," ");
	    htmlEntities.put("&copy;","\u00a9");
	    htmlEntities.put("&reg;","\u00ae");
	    htmlEntities.put("&euro;","\u20a0");
	  }
	  
	  public static final String unescapeHTML(String source) {
	      int i, j;

	      boolean continueLoop;
	      int skip = 0;
	      do {
	         continueLoop = false;
	         i = source.indexOf("&", skip);
	         if (i > -1) {
	           j = source.indexOf(";", i);
	           if (j > i) {
	             String entityToLookFor = source.substring(i, j + 1);
	             String value = (String) htmlEntities.get(entityToLookFor);
	             if (value != null) {
	               source = source.substring(0, i)
	                        + value + source.substring(j + 1);
	               continueLoop = true;
	             }
	             else if (value == null){
	                skip = i+1;
	                continueLoop = true;
	             }
	           }
	         }
	      } while (continueLoop);
	      return source;
	  }

	public class PeopleCodeSegment implements PeopleCodeObject 
	{
		ArrayList<String> keys = new ArrayList<String>();
		StringWriter peopleCode= new StringWriter();;

		public String getCompositeKey()
		{
			StringWriter w = new StringWriter();
			for (String s: keys)
				w.write(s + ";");
			return w.toString().trim();
		}
		void addKey( String k)
		{ 
			keys.add(k);
		}
		void addPeoplCodeLine(String l)
		{
			if (l != null && l.trim().length() > 0)
				peopleCode.write(l);
			peopleCode.write(eol);
		}
		public String toString()
		{
			return "PeopleCode for " + getCompositeKey() + ": " + eol + peopleCode; 
		}
		public String[] getKeys() {
			return (String[]) keys.toArray(new String[7]);
		}
		public String getLastChangedBy() {
			return "Not available";
		}
		public Date getLastChangedDtTm() {
			return null;
		}
		public int getPeopleCodeType() {
			return peopleCodeType;
		}
		public String getSource() {
			return xml.getName();
		}
		public String getPeopleCodeText() {
			return peopleCode.toString();
		}
		public boolean hasPlainPeopleCode() {
			return true;
		}
		public int[] getKeyTypes() {
			return CreateProjectDefProcessor.getObjTypesFromPCType(peopleCodeType, getKeys());
		}
	}

	ArrayList< PeopleCodeSegment> list;
	ContainerProcessor processor;
	PeopleCodeSegment segment;
	File xml;
	int peopleCodeType;
	int mode;
	DocumentBuilder docBuilder;
	Document d;
	String[] pcodeTreeURLs;
	String[] pcodeTreeNames;
	PToolsObjectToFileMapper[] mappers;
	
	
	public ExtractPeopleCodeFromCompareReport() throws ParserConfigurationException
	{
		docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	}
	
	static String getAttribute( Node n, String attrName)
	{
		if (n == null || attrName == null)
			return null;
		NamedNodeMap attrs = n.getAttributes();  
	    for(int i3 = 0 ; i3 < attrs.getLength() ; i3++) 
	    {
	        Attr attribute = (Attr) attrs.item(i3);
	        if (attrName.equals(attribute.getName()))
	        	return attribute.getValue();
	    }
		return null;
	}
	
	private void visit(Node node, int level)
	{
//		logger.info("Level = " + level + ", node = " + node.getNodeName());
		NodeList nl = node.getChildNodes();		
		Node firstAttribute = null;
		for(int i=0, cnt=nl.getLength(); i<cnt; i++)
		{
			boolean newSegment = false;
			Node n = nl.item(i);
			if ((level == 2)  && (n.getNodeType() == Node.ELEMENT_NODE) && "item".equals(((Element) n).getNodeName()))
			{
				segment = new PeopleCodeSegment();
				list.add(segment);
				newSegment = true;
				firstAttribute = null;
			}
			else
				if (level == 3 && n.getNodeType() == Node.ELEMENT_NODE && "objname".equals(((Element) n).getNodeName()))
					segment.addKey(n.getTextContent());
				else
					if (level == 4 && n.getNodeType() == Node.ELEMENT_NODE && "attribute".equals(((Element) n).getNodeName()))
					{
						if (firstAttribute == null)	
							firstAttribute = n;
					}
					else
					if (mode == PROCESS_SOURCE_PCODE && level == 5 && n.getNodeType() == Node.ELEMENT_NODE && "source".equals(((Element) n).getNodeName()))
						segment.addPeoplCodeLine(unescapeHTML(n.getTextContent()));
					else
						if (mode == PROCESS_TARGET_PCODE && level == 5 && n.getNodeType() == Node.ELEMENT_NODE && "target".equals(((Element) n).getNodeName()))
							segment.addPeoplCodeLine(unescapeHTML(n.getTextContent()));
			//  <attribute diff="targetonly" name="">

			if (level != 4 || (n.getNodeType() != Node.ELEMENT_NODE) 
					|| !("attribute").equals(((Element) n).getNodeName())
					|| "same".equals(((Element) n).getAttribute("diff"))
					|| ("sourceonly".equals(((Element) n).getAttribute("diff")) && mode == PROCESS_SOURCE_PCODE)
					|| ("targetonly".equals(((Element) n).getAttribute("diff")) && mode == PROCESS_TARGET_PCODE)
			)
				visit(n, level + 1);
			if (newSegment && mode == SET_LINKS_IN_XML)
				{
					try {
						if (processor instanceof Controller.WriteDecodedPPCtoDirectoryTree)
						{
//							PToolsObjectToFileMapper mapper = ((Controller.WriteDecodedPPCtoDirectoryTree) processor).getMapper();
							Element n2 = null;
							NodeList nl2 = n.getChildNodes();		
							for(int i2=0, cnt2=nl2.getLength(); i2<cnt2; i2++)
							{
								Node n0 = nl2.item(i2);
								if (n0.getNodeType() != Node.ELEMENT_NODE)
									continue;
								if (n0.getNodeName() == "secondary_key")
								{
									NodeList nl3 = n0.getChildNodes();
									for (int i3=0, cnt3=nl3.getLength(); i3<cnt3; i3++)									
										if (nl3.item(i3).getNodeType() == Node.ELEMENT_NODE)
										{										
											if ("pcode".equals(getAttribute( nl3.item(i3), "name")))
												n2 = (Element) nl3.item(i3);
										}
									if (n2 == null)
									{
										n2 = d.createElement("attribute");
										n2.setAttribute("diff", "same");
										n2.setAttribute("name", "pcode");
										n0.insertBefore(n2, nl3.item(0));
									}
								}
							}
							if (n2 == null)
								throw new IllegalArgumentException("?? cannot find or create 'pcode' node");
							NodeList nl2b = n2.getChildNodes();		
							while (nl2b.getLength()> 0)
							{
								Node n0 = nl2b.item(0);
								n2.removeChild(n0);
							}
							if (pcodeTreeURLs != null)
								for (int t = 0; t < pcodeTreeURLs.length; t++)
								{
									File f = mappers[t].getFile(segment, "pcode");
									if (f.exists())
									{
										Element n3 = d.createElement("link");
										n3.setAttribute("tree", pcodeTreeNames[t]);
										//URL u = f.toURL();
										URI uri = f.toURI();
										URL u = uri.toURL();
										if (u.toString().startsWith(pcodeTreeURLs[t]))
										{
											String relURL = "./"+ PEOPLECODETREE + "/"+ pcodeTreeNames[t] + "/" + u.toString().substring(pcodeTreeURLs[t].length());
											n3.setTextContent(relURL);
											logger.fine("Relative  url = "+ relURL);
										}
										else
										{
											n3 = d.createElement(pcodeTreeNames[t]);
											n3.setTextContent(u.toString());
											logger.fine("Absolute url = "+ u);											
										}
										File h = mappers[t].getFile(segment, "hover");
										if (h.exists())
										{
											StringWriter w = new StringWriter();
											BufferedReader br = new BufferedReader(new FileReader(h));
											String line;
											for (int count=0; count < 30 && (line = br.readLine()) != null; count++)
											{
												w.write(line);
												w.write(eol);
											}
											br.close();
											n3.setAttribute("hover", w.toString());
										}
										n2.appendChild(n3);										
									}
									File f2 = mappers[t].getFile(segment, "difftxt");
									if (f2.exists())
									{
										Element n3 = d.createElement("diff");
										n3.setAttribute("tree", pcodeTreeNames[t]);
										//URL u = f2.toURL();
										URI uri = f2.toURI();
										URL u = uri.toURL();
										if (u.toString().startsWith(pcodeTreeURLs[t]))
										{
											String relURL = "./"+ PEOPLECODETREE + "/"+ pcodeTreeNames[t] + "/" + u.toString().substring(pcodeTreeURLs[t].length());
											n3.setTextContent(relURL);
											logger.fine("Relative  url = "+ relURL);
										}
										else
										{
											n3 = d.createElement(pcodeTreeNames[t]);
											n3.setTextContent(u.toString());
											logger.fine("Absolute url = "+ u);											
										}
										n2.appendChild(n3);										
									}
								}
						}
						
					} catch (MalformedURLException e) {
						logger.severe("??? "+ e);
					}
					catch (IOException e) {
						logger.severe("??? "+ e);
					}
					
				}				
		}
	}
	
	static int nameToType( String name)
	{
		if (name.startsWith("Application Engine PeopleCode"))
			return 43;
		if (name.startsWith("Application Package PeopleCode"))
			return 58;		
		if (name.startsWith("Component PeopleCode"))
			return 46;
		if (name.startsWith("Comp. Interface PeopleCode"))
			return 42;		
		if (name.startsWith("Component Rec Fld PeopleCode"))
			return 48;
		if (name.startsWith("Component Record PeopleCode"))
			return 47;		
		if (name.startsWith("Page PeopleCode"))
			return 44;		
		if (name.startsWith("Record PeopleCode"))
			return 8;		
		if (name.startsWith("Subscription PeopleCode"))
			return 40;		
		return -1;
	}
	
	public void processPeopleCodeFromFile( File _xml, ContainerProcessor _processor,  int _mode) 
			throws SAXException, IOException, ParserConfigurationException, TransformerException
	{
		xml = _xml;
		processor = _processor;
		list = new ArrayList<PeopleCodeSegment>();
		peopleCodeType = nameToType(xml.getName());
		if (peopleCodeType <= 0)
			return;
		if (mode < 0 || mode > SET_LINKS_IN_XML)
			throw new IllegalArgumentException("Unknown mode in processPeopleCodeFromFile");
		mode = _mode;
		logger.fine("Now " + modeStr[mode] + " of " + xml);
		d = docBuilder.parse(xml);
		visit(d, 0);

		if (mode == SET_LINKS_IN_XML)
		{
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer t = tf.newTransformer();
			DOMSource s = new DOMSource(d);
			StreamResult sr1 = new StreamResult(xml);  
			t.transform(s,sr1);				
		}
		else
			for (PeopleCodeSegment segment: list)
				processor.process(segment);
	}

	public void processPeopleCodeFromTree( File compareDir, ContainerProcessor processor, 
			int _mode)
				throws SAXException, IOException, ParserConfigurationException, TransformerException
	{
		processPeopleCodeFromTree(compareDir, processor, _mode, (PToolsObjectToFileMapper[]) null);
	}
	
	public void processPeopleCodeFromTree( File compareDir, ContainerProcessor processor, 
			int _mode, PToolsObjectToFileMapper[] _mappers)
				throws SAXException, IOException, ParserConfigurationException, TransformerException
	{
		if (_mode == SET_LINKS_IN_XML)
			if (_mappers != null)
				mappers = _mappers;
			else				
			{
				File topDir = new File(compareDir, PEOPLECODETREE);
				pcodeTreeNames = topDir.list( new FilenameFilter() {				
					public boolean accept(File arg0, String arg1) {
						return new File(arg0, arg1).isDirectory();
					}
				});
				if (pcodeTreeNames != null)
				{
					pcodeTreeURLs = new String[pcodeTreeNames.length];
					mappers = new PToolsObjectToFileMapper[pcodeTreeNames.length];
					for (int i = 0; i < pcodeTreeNames.length; i++)
					{
						File tree = new File(topDir, pcodeTreeNames[i]);
						mappers[i] = new DirTreePTmapper(tree);
						//pcodeTreeURLs[i] = tree.toURL().toString();
						pcodeTreeURLs[i] = tree.toURI().toURL().toString();
					}
				}
			}
		
		
		processor.aboutToProcess();
		if (!(compareDir.exists() && compareDir.isDirectory()))
			throw new IllegalArgumentException("Expected top directory of compare reports; got "  + compareDir);
		for (String folder: compareDir.list(new FilenameFilter() {			
			public boolean accept(File dir, String name) {				
				return new File(dir, name).isDirectory() && name.contains("PeopleCode");
			}
		}))
		{
			File folderDir = new File(compareDir, folder);
			logger.fine("Going into directory "+ folderDir.getAbsolutePath());
			for (String xmlFileName: folderDir.list(new FilenameFilter() {				
				public boolean accept(File dir, String name) {
					return name.contains("PeopleCode") && name.endsWith(".xml");
				}
			}))
				processPeopleCodeFromFile(new File(folderDir, xmlFileName), processor, _mode);
		}
		processor.finishedProcessing();
	}
	
	public static void writeProjectDef( File compareDir, File outFile) 
		throws SAXException, IOException, ParserConfigurationException, TransformerException
	{
		new ExtractPeopleCodeFromCompareReport().
			processPeopleCodeFromTree(compareDir,
						new CreateProjectDefProcessor(outFile, true), PROCESS_SOURCE_PCODE);
	}
	
	public static void writeSourceAndTargetInSubtree( File compareDir, String sourceName, String targetName) 
						throws SAXException, IOException, ParserConfigurationException, TransformerException
	{
		new ExtractPeopleCodeFromCompareReport().
		processPeopleCodeFromTree(compareDir,
				new Controller.WriteDecodedPPCtoDirectoryTree(new File(compareDir, PEOPLECODETREE+ "\\" + sourceName)), 
				PROCESS_SOURCE_PCODE);

	new ExtractPeopleCodeFromCompareReport().
		processPeopleCodeFromTree(compareDir,
				new Controller.WriteDecodedPPCtoDirectoryTree(new File(compareDir, PEOPLECODETREE+ "\\" + targetName)), 
				PROCESS_TARGET_PCODE);
		
	}

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		logger.info("Start");
		try {
			File compareDir = //new File("C:\\projects\\sandbox\\PeopleCode\\compare_reports\\UPGCUST");
				new File("C:\\projects\\big\\compare_reports\\UPGCUST");

			new ExtractPeopleCodeFromCompareReport().
				processPeopleCodeFromTree(compareDir,
						new Controller.WriteDecodedPPCtoDirectoryTree(new File(compareDir, PEOPLECODETREE+ "\\PSNEW89")), 
						PROCESS_SOURCE_PCODE);

			new ExtractPeopleCodeFromCompareReport().
				processPeopleCodeFromTree(compareDir,
						new Controller.WriteDecodedPPCtoDirectoryTree(new File(compareDir, PEOPLECODETREE+ "\\PSVAN91")), 
						PROCESS_TARGET_PCODE);
	
			new ExtractPeopleCodeFromCompareReport().
			processPeopleCodeFromTree(compareDir,
					new Controller.WriteDecodedPPCtoDirectoryTree(new File(compareDir, PEOPLECODETREE+ "\\DUMMY")), 
					SET_LINKS_IN_XML);

					
		} catch (Exception e) {
			e.printStackTrace();
		} 
		logger.info("Ready");
	}

}
