package decodepcode.svn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNConflictHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNConflictChoice;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNConflictReason;
import org.tmatesoft.svn.core.wc.SVNConflictResult;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.xml.sax.SAXException;

import decodepcode.Controller;
import decodepcode.Controller.WriteDecodedPPCtoDirectoryTree;
import decodepcode.DirTreePTmapper;
import decodepcode.ProjectReader;
import decodepcode.compares.ExtractPeopleCodeFromCompareReport;
import decodepcode.compares.RunExternalDiffProgram;


public class MergePeopleCodeTrees 
{
	final public static String MERGED_TREE_NAME = "Merged";
	static Logger logger = Logger.getLogger(MergePeopleCodeTrees.class.getName());
	int count = 0, copied = 0;
	Set<File> dirsCreated = new HashSet<File>(), 
			filesCreated = new HashSet<File>(),
			filesModified = new HashSet<File>(),
			wcFilesNotMerged = new HashSet<File>();
	Set <SVNURL> deltaOldDMONewDMO = new HashSet<SVNURL>(),
				    deltaOldDMODev = new HashSet<SVNURL>(),
				    deltaMerge = new HashSet<SVNURL>(),
				    threeWayMerge = new HashSet<SVNURL>();			

	SVNRepository repos ;
	
	public MergePeopleCodeTrees()
	{
		
	}

	void emptyTree( File dir) throws IOException
	{
		String[] dirList = dir.list();
		for (String fStr: dirList)
		{
			File f = new File (dir, fStr);
			if (f.isDirectory() && !".svn".equals(fStr))
				emptyTree(f);
			else
				if (f.isFile())
					if (!f.delete())
						throw new IOException("unable to delete " + f);			
		}
	}
	
	static boolean fileExists( File dst)
	{
		return dst.exists() && dst.isFile();
	}
	
	static boolean filesAreIdentical( File src, File dst) throws IOException
	{
		if (!fileExists(src))
			throw new IllegalArgumentException("?? file " + src + " does not exist");
		if (!fileExists(dst))
			return false;
		FileInputStream fSrc = new FileInputStream(src), fDst = new FileInputStream(dst);
		try {
		if (src.length() != dst.length())
			return false;
		byte[] buf = new byte[10000], buf2 = new byte[10000];
		int n;
		while ( (n = fSrc.read(buf)) > 0  && fDst.read(buf2) > 0 )
			for (int i=0; i < n; i++)
				if (buf[i] != buf2[i])
					return false;
		} finally { fSrc.close(); fDst.close(); }
		return true;
	}

	void copyFile( File src, File dst) throws IOException
	{
		FileInputStream fis = new FileInputStream(src);
		byte[] buf = new byte[10000];
		int n;
		FileOutputStream fos = new FileOutputStream(dst);
		while ( (n = fis.read(buf)) > 0 )
			fos.write(buf, 0, n);
		fis.close();
		fos.close();		
		copied++;
	}
	
	
	void overwriteTree( File srcDir, File targetDir) throws IOException
	{
		if (!(srcDir.isDirectory() && targetDir.isDirectory()))
			throw new IllegalArgumentException("?? expected two directories");
		logger.fine("Now overwriting tree " + targetDir + " with " + srcDir);
		String[] dstList = targetDir.list();
		if (dstList != null)
			for (String dstStr: dstList)
			{
				File src = new File(srcDir, dstStr);
				if (!src.exists())
				{
					File dst = new File(targetDir, dstStr);
					if (dst.isFile())
					{
						if (!dst.delete())
							throw new IOException("Unable to delete file " + dst);
					}
					else
						if (dst.isDirectory() && !".svn".equals(dstStr))
							emptyTree(dst);
				}
			}
		String[] srcList = srcDir.list();
		if (srcList != null)
			for (String srcStr: srcList)
			{
				File src = new File(srcDir, srcStr);
				if (src.isDirectory() && !".svn".equals(srcStr))
				{
					File dstDir = new File(targetDir, srcStr);
					if  (!(dstDir.exists() && dstDir.isDirectory()))
					{
						if (!dstDir.mkdir())
							throw new IOException("Unable to create directory " + dstDir);
						dirsCreated.add(dstDir);
					}
					overwriteTree(src, dstDir);
				} else
				if (src.isFile())
				{
					File dst = new File(targetDir, srcStr);
					
					if (!dst.exists())
						filesCreated.add(dst);
					if (!filesAreIdentical(src, dst))
					{
						copyFile(src, dst);
						filesModified.add(dst);
					}
				}
			}
	}
	
	static File createTempDirectory()
	{
		File tempDir = new File(System.getProperty("java.io.tmpdir"));
		File f;
		for (int i = 0; ((f = new File(tempDir, "mergePcode" + i)).exists()); i++ )
			;
		return f;
	}
	
	@SuppressWarnings("deprecation")
	void mergeTrees( File oldDemoTree, File newDemoTree, File oldDevTree, File workDir, File destDir, 
					boolean mergeToDMO) throws SVNException, IOException
	{
		FSRepositoryFactory.setup();
        
		if (workDir == null)
			workDir = createTempDirectory();
		if (workDir.exists() && workDir.list().length > 0)
			throw new IllegalArgumentException("Work directory " + workDir + " exists and is not empty");
		logger.info("work directory = "+ workDir);
		workDir.mkdirs();
        File baseDirectory = workDir;
        File reposRoot = new File(baseDirectory, "tempRepository");
        File wcRoot = new File(workDir, "working_copy");
        
        SVNClientManager clientManager = SVNClientManager.newInstance();        
        SVNAdminClient adminClient = clientManager.getAdminClient();
        SVNDiffClient diffClient = clientManager.getDiffClient();     
        DefaultSVNOptions options = (DefaultSVNOptions) diffClient.getOptions();
        options.setConflictHandler(new ConflictResolverHandler());
        SVNCommitClient commitClient = clientManager.getCommitClient();
        
        boolean fileRepo = true;
        
        String url = "svn://192.168.56.101/project6";
        final SVNURL reposURL = (fileRepo? SVNURL.fromFile(reposRoot) : SVNURL.parseURIEncoded(url)),
    	demoURL = reposURL.appendPath("demo", false);        
        if (fileRepo)
        	adminClient.doCreateRepository(reposRoot, null, true, true, false, false);
        else
        {
        	SubversionSubmitter.setUpSVNKit();
        	//SVNRepository repository = SVNRepositoryFactory.create(reposURL);
        	ISVNAuthenticationManager m = SVNWCUtil.createDefaultAuthenticationManager("harry", "secret");
     		//repository.setAuthenticationManager(m);
     		clientManager.setAuthenticationManager(m);
        }
        logger.info("Repo URL = "+ reposURL);
        repos =	SVNRepositoryFactory.create(reposURL);
        
        SVNCommitInfo info;        
        String msg;

        msg = "Import of oldDMO tree";
        info = clientManager.getCommitClient( ).doImport( oldDemoTree , demoURL , msg , true);
        logger.info("Imported oldDemo tree: "+ info);
        //final long revOldDemo = info.getNewRevision();
            
        msg= "Copy demo to custom";
        SVNCopyClient copyClient = clientManager.getCopyClient();
        SVNURL customURL = reposURL.appendPath("custom", true);
        SVNCopySource copySource = new SVNCopySource(SVNRevision.UNDEFINED, SVNRevision.HEAD, demoURL); 
        info = copyClient.doCopy(new SVNCopySource[] { copySource }, customURL, false, false, true, 
                msg, null);
        final long revAfterCopyToCustom = info.getNewRevision();
        logger.info(msg + ": " + info);
                
        File  demoDir = new File(wcRoot, "demo"),
   	 		customDir = new File(wcRoot, "custom");
        
        SVNUpdateClient updateClient = clientManager.getUpdateClient();
        updateClient.doCheckout(reposURL, wcRoot, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNDepth.INFINITY, 
                false);
        logger.info("Checked out working copy to " + wcRoot);
        
        overwriteTree(newDemoTree, demoDir);
/*        
        for (File f: dirsCreated)
        	clientManager.getWCClient( ).doAdd( f, false , false , false , false );
        for (File f: filesCreated)
        	clientManager.getWCClient( ).doAdd( f, false , false , false , false );
*/
        msg = "Commit of newDMO (in demo tree)";
        info = commitClient.doCommit(new File[] { wcRoot }, false, msg, null, null, false, 
                              false, SVNDepth.INFINITY);        
        //final long revNewDMO = info.getNewRevision();
/*
        diffClient.doDiffStatus(reposURL, SVNRevision.create(revOldDemo), reposURL, SVNRevision.create(revNewDMO), 
        		true, true, new ISVNDiffStatusHandler() {				
					public void handleDiffStatus(SVNDiffStatus arg0) throws SVNException {
						if (arg0.getURL().toString().endsWith(".pcode"))
						{
							logger.info("Diff in NewDMO-OldDMO: " + arg0.getURL());

 							logger.info("path = " + arg0.getPath());
					        SVNProperties properties = SVNProperties.wrap(new HashMap());
					        if (repos.checkPath("/" + arg0.getPath(), revOldDemo) == SVNNodeKind.FILE 
					        		&& repos.checkPath("/" + arg0.getPath(), revNewDMO) == SVNNodeKind.FILE)
					        {
								deltaOldDMONewDMO.add(arg0.getURL());
						        repos.getFile( "/" + arg0.getPath(), revOldDemo, properties, null);
						        String oldCheckSum = properties.getStringValue(SVNProperty.CHECKSUM);
						        repos.getFile("/" +arg0.getPath(), revNewDMO, properties, null);
						        String newCheckSum = properties.getStringValue(SVNProperty.CHECKSUM);
						        logger.info("Checksums: " + oldCheckSum + " " + newCheckSum);
					        }
						}
					}
				});
*/
        logger.info(msg + ":" + info);

        dirsCreated.clear(); filesCreated.clear();
        overwriteTree(oldDevTree, customDir);
/*
        msg = "Commit of old DEV";
        info = commitClient.doCommit(new File[] { wcRoot }, false, msg, null, null, false, 
                              false, SVNDepth.INFINITY);        	
        logger.info(msg + ":" + info);
        long revOldDEV = info.getNewRevision();
        diffClient.doDiffStatus(reposURL, SVNRevision.create(revNewDMO), reposURL, SVNRevision.create(revOldDEV), 
        		true, true, new ISVNDiffStatusHandler() {				
					public void handleDiffStatus(SVNDiffStatus arg0) throws SVNException {
						if (arg0.getURL().toString().endsWith(".pcode"))
						{
							logger.fine("Diff in DEV-oldDMO: " + arg0.getURL());
							deltaOldDMODev.add(arg0.getURL());
						}
					}
				});
*/
        updateClient = clientManager.getUpdateClient();
        updateClient.doCheckout(reposURL, wcRoot, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNDepth.INFINITY, 
                false);
        logger.info("Checked out working copy to " + wcRoot);        
        
        File targetOfMerge = (mergeToDMO? demoDir: customDir); 
        SVNURL sourceOfMerge = (mergeToDMO? customURL : demoURL);
        SVNRevisionRange rangeToMerge = new SVNRevisionRange(SVNRevision.create(revAfterCopyToCustom), SVNRevision.HEAD);        
        wcFilesNotMerged.clear();
                
        diffClient.doMerge(sourceOfMerge, SVNRevision.create(revAfterCopyToCustom), Collections.singleton(rangeToMerge), 
                targetOfMerge, SVNDepth.INFINITY, true, false, false, false);
                
        logger.info("Finished merging");

        /*
        for (File f: dirsCreated)
        	clientManager.getWCClient( ).doAdd( f, false , false , false , false );
        for (File f: filesCreated)
        	clientManager.getWCClient( ).doAdd( f, false , false , false , false );
        */

        msg = "Commit of merged files";
        info = commitClient.doCommit(new File[] { wcRoot }, false, msg, null, null, false, 
                              false, SVNDepth.INFINITY);        	
        logger.info(msg + ":" + info);
        /*
        long revAfterMerge = info.getNewRevision();

        diffClient.doDiffStatus(reposURL, SVNRevision.create(1), reposURL, SVNRevision.create(revAfterMerge), 
        		true, true, new ISVNDiffStatusHandler() {				
					public void handleDiffStatus(SVNDiffStatus arg0) throws SVNException {
						if (arg0.getURL().toString().endsWith(".pcode"))
						{
							logger.info("Diff in Merge: " + arg0.getURL());
							deltaMerge.add(arg0.getURL());
						}
					}
				});
        */
        
        for (File failed: wcFilesNotMerged)
        	if (!failed.delete())
        		logger.warning("Could not delete working copy file " + failed);
        
       
        if (destDir != null )
        {
        	destDir.mkdirs();
        	copied = 0;
        	overwriteTree(targetOfMerge, destDir);
        	logger.info("Finished copying " + copied + " results to " + destDir);
        }		

        /*
        for (SVNURL m: deltaMerge)
        {
        	if (deltaOldDMODev.contains(m) && deltaOldDMONewDMO.contains(m))
        	{
        		logger.info("Three-way merge: " + m);
        		threeWayMerge.add(m);
        	}
        }
        logger.info("" + threeWayMerge.size() + " files processed in three-way merge");
        */
        File mergeResultsOut = new File("merge_results.txt");
        PrintWriter pw = new PrintWriter(mergeResultsOut);
        for (File f: filesModified)
        	if (f.toString().startsWith(customDir.toString()))
        	{
        		String path = f.toString().substring(customDir.toString().length());
        		File demo = new File( demoDir, path);
        		path = path.replace("\\", "/");
//        		SVNURL u =reposURL.appendPath(path, true);        		
    			boolean mergeWorked = !wcFilesNotMerged.contains(f);
        		boolean threeWay = filesModified.contains(demo);
        		String mergeStr = path + " ("+(threeWay? "three-way": "no change in DMO") +"): merge " + (mergeWorked ? "successful": "failed");
        		pw.println(mergeStr);
    			logger.info("Merge: " + mergeStr);        			
        	}
        pw.close();
        logger.info("Merge results in " + mergeResultsOut.getAbsolutePath());
        

        if (count > 0)
        	logger.warning("" + count + " files could not be merged");
        
        logger.warning("Three-way merge completed. You may want to delete work directory " + workDir + ".\nNow preparing diffs.");
        
	}
	
    private class ConflictResolverHandler implements ISVNConflictHandler {
        
        public SVNConflictResult handleConflict(SVNConflictDescription conflictDescription) throws SVNException {
            SVNConflictReason reason = conflictDescription.getConflictReason();
            SVNMergeFileSet mergeFiles = conflictDescription.getMergeFiles();
            
            SVNConflictChoice choice = SVNConflictChoice.THEIRS_FULL;
            if (reason == SVNConflictReason.EDITED) {
                //If the reason why conflict occurred is local edits, chose local version of the file
                //Otherwise the repository version of the file will be chosen.
                choice = SVNConflictChoice.MINE_FULL;
            }
            
            logger.fine("Automatically resolving conflict for " + mergeFiles.getWCFile() + 
                    ", choosing " + (choice == SVNConflictChoice.MINE_FULL ? "local file" : "repository file"));
            count++;
            wcFilesNotMerged.add( mergeFiles.getWCFile());
            
            return new SVNConflictResult(choice, mergeFiles.getResultFile()); 
        }       
    }
    
	String replaceExtension( String n, String newExt)
	{
		return n.substring(0, n.lastIndexOf(".")) + newExt;
	}
    
    void createDiffFiles( File tree1, File tree2, String tree1Name, String tree2Name) throws IOException
    {
    	if (!tree1.exists() || !tree1.isDirectory() 
    			|| !((tree2.exists() && tree2.isDirectory())
    					|| tree2 == null)
    			)
    		return;
    	String[] files = tree1.list();
    	for (String fs: files)
    	{
    		File f1 = new File(tree1,fs),
    			f2 = tree2 == null? null : new File(tree2, fs);
    		if (f1.isDirectory())
    			createDiffFiles(f1, f2, tree1Name, tree2Name);
    		else
    			if (fs.endsWith(".pcode"))
	    		{
    				String hover;
	    			if (f2 == null || !f2.exists())
	    				hover = "Does not exist in " + tree2Name;
	    			else	    				
	    			{
		    			String diff;
	    				File diffFile = new File(tree1, replaceExtension(fs, ".difftxt"));
	    				if (filesAreIdentical(f1, f2))
	    				{
	    					hover = "Is identical to version in " + tree2Name;
	    					if (diffFile.exists())
	    						diffFile.delete();
	    				}
	    				else
	    				{	    					
		    				diff = "Compare between " + tree1Name + " and " + tree2Name + ":" 
		    					+ RunExternalDiffProgram.eol
		    					+ RunExternalDiffProgram.eol 
		    					+ RunExternalDiffProgram.getLongDiff(f1, f2);
		    				FileWriter fw = new FileWriter(diffFile);
		    				fw.write(diff);
		    				fw.close();
		    				diff += RunExternalDiffProgram.getShortDiff(f1, f2);
		    				hover = "NOT identical to version in " + tree2Name
		    					+ RunExternalDiffProgram.eol + RunExternalDiffProgram.getShortDiff(f1, f2);
	    				}	    				
	    			}
    				FileWriter fw = new FileWriter(new File(tree1, replaceExtension(fs, ".hover")));
    				fw.write(hover);
    				fw.close();
	    		}    				
    	}    	
    }
    
    void createSetOfDiffFiles(File oldDemoTree, File newDemoTree, File oldDevTree, File mergeTree) throws IOException
    {
    	logger.info("Now creating diff files in " + oldDemoTree.getName() +", " + oldDemoTree.getName() 
    			+ " and " + mergeTree.getName() ); 
    	createDiffFiles( newDemoTree, oldDemoTree, newDemoTree.getName(), oldDemoTree.getName());
    	createDiffFiles( oldDevTree, oldDemoTree, oldDevTree.getName(), oldDemoTree.getName());
    	createDiffFiles( mergeTree, oldDevTree, mergeTree.getName(), oldDevTree.getName());
    }
    
    public static File patchProg = new File("C:\\program files\\gnu\\patch.exe");
    
    static void replaceStyleSheet( File compareDir) throws IOException
    {
    	File patchFile = new File(compareDir, "items.xsl.patch");
    	if (patchFile.exists())
    	{
    		logger.info("Found " + patchFile + "; assuming items.xsl has been patched");
    		return;
    	}
    	File xsl = new File( compareDir, "items.xsl");
    	logger.info("Replacing " + xsl + " with modified version");
    	if (!xsl.exists())
    		throw new IllegalArgumentException("Compare directory appears incorrect: should contain 'items.xsl'");
    	if  (!patchProg.exists())
    		throw new IllegalArgumentException("GNU patch program '" + patchProg + "' not found");
//    	xslOrig.renameTo(new File(compareDir, "items.xsl.orig"));
    	InputStream is = MergePeopleCodeTrees.class.getResourceAsStream("/items.xsl.patch");
    	OutputStream os = new FileOutputStream(patchFile);
    	int i;
    	while ((i=is.read()) >= 0)
    		os.write(i);
    	is.close(); os.close();
    	String[] cmdArray  = { patchProg.toString(), "", xsl.toString(), patchFile.toString()};
		Process p = Runtime.getRuntime().exec(cmdArray);
		BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream())),
			brErr  = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		String line = "", line2 = null;
		while ( (line = br.readLine()) != null || ((line2 = brErr.readLine()) != null))
		{
			logger.info(line);
			if (line2 != null) 
				logger.severe("patch:  " + line2);
		}

		int exit = p.exitValue();
		if (exit != 0)
			logger.severe("Unable to patch stylesheet " + xsl + "; exit code = " + exit);
    }
    
    public static void doExtractAndMergeCompareReports( File compareDir, 
    		String oldDemo, String newDemo, String oldDev) 
    			throws SAXException, IOException, ParserConfigurationException, TransformerException, SVNException 
    {
    	Properties props = Controller.readProperties();
    	String GNUdiff = props.getProperty("GNUdiff"),
    		GNUpatch = props.getProperty("GNUpatch");
    	if (GNUdiff != null)
    		RunExternalDiffProgram.diffProg = new File(GNUdiff);
    	if (GNUpatch != null)
    		patchProg = new File(GNUpatch);
    	
    	replaceStyleSheet( compareDir); 
    	ExtractPeopleCodeFromCompareReport.writeSourceAndTargetInSubtree(compareDir, oldDev, newDemo);
		MergePeopleCodeTrees m = new MergePeopleCodeTrees();
		File baseDir = new File(compareDir, ExtractPeopleCodeFromCompareReport.PEOPLECODETREE);
		baseDir.mkdir();
		File oldDemoDir =  new File(baseDir, oldDemo),
			  newDemoDir = new File(baseDir, newDemo),
			  oldDevDir = new File(baseDir, oldDev),
			  resultDir = new File(baseDir, MERGED_TREE_NAME);
		if (!(oldDemoDir.exists() && oldDemoDir.isDirectory()))
		{
			File oldDemoProjDir = new File(baseDir, oldDemo + "-" + "project"),
				oldDemoProjXML = new File(oldDemoProjDir, oldDemo.toUpperCase() + ".xml");
			if (oldDemoProjXML.exists())
			{
				logger.info("Extracting PeopleCode from " + oldDemoProjXML.getName());
				ProjectReader p = new ProjectReader();
				p.setProcessor(new WriteDecodedPPCtoDirectoryTree(new DirTreePTmapper( oldDemoDir), "pcode"));
				p.readProject(oldDemoProjXML);
			}
			else
			{	
				File dir = new File(oldDemoProjDir,"items");
				dir.mkdirs();
				logger.warning("No "+ oldDemo + " tree found....");
				ExtractPeopleCodeFromCompareReport.writeProjectDef(compareDir, new File(dir, oldDemo + ".items"));
				System.out.println("\n\nCannot proceed. Migrate the project definition to the "+ oldDemo + " environment and export the project to file.");
				System.out.println("\tThe exported project is expected as " + oldDemoProjXML.getAbsolutePath());
				return;
			}
		}
		m.mergeTrees(
			oldDemoDir, 
			newDemoDir, 
			oldDevDir, 
			null,
			resultDir,				
			false);
		m.createSetOfDiffFiles(						
				oldDemoDir, 
				newDemoDir, 
				oldDevDir, 
				resultDir);		
		new ExtractPeopleCodeFromCompareReport().
			processPeopleCodeFromTree(compareDir,
				new Controller.WriteDecodedPPCtoDirectoryTree(
						new File(compareDir, ExtractPeopleCodeFromCompareReport.PEOPLECODETREE+ "\\DUMMY")), 
				ExtractPeopleCodeFromCompareReport.SET_LINKS_IN_XML);
    }
    
public static void main(String[] args) 
{
	try 
	{
		if (args.length < 4)
		{
			System.err.println("Expected parameters: <compare directory> <ancestor> <child1> <child2>");
			System.err.println("\twhere <ancestor> is the name of the common ancestor environment (e.g. HRDMO89)");
			System.err.println("\t<child1> is the name of one branch (e.g. HRDMO91)");
			System.err.println("\tand <child2> is the name of the other branch (e.g. HRDEV)");			
			return;
		}
		doExtractAndMergeCompareReports( new File(args[0]), args[1], args[2], args[3] );
	} 
	catch (Exception e) 
	{ 
		e.printStackTrace();
	}
}
}


