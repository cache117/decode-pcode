package decodepcode.compares;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Properties;

import decodepcode.Controller;

public class RunExternalDiffProgram 
{
	public final static String  eol = System.getProperty("line.separator");
	public static File diffProg = new File("C:\\program files\\gnu\\diff.exe");
	
	public static String getLongDiff( File f1, File f2) throws IOException
	{
		String[] p = {"-y"};
		return getDiff(diffProg, p, f1, f2);
	}
	
	public static String getShortDiff( File f1, File f2) throws IOException
	{
		long count = 0;
		String[] p = {"-y", "--suppress-common-lines"};
		BufferedReader br = new BufferedReader(new StringReader( getDiff(diffProg, p, f1, f2)));
		String line;
		while ((line = br.readLine()) != null)
			if (line.length() > 0)
				count++;
		return "" + count + " line(s) in diff";
	}

	
	public static String getDiff( File diffProg, String[] params, File f1, File f2) throws IOException
	{
		if (!f1.exists() )
			throw new IllegalArgumentException("File "+ f1 + " does not exist");
		if (!f2.exists() )
			throw new IllegalArgumentException("File "+ f2 + " does not exist");
		
		if (!diffProg.exists())
			throw new IllegalArgumentException("Diff program " + diffProg + " not found" );
		ArrayList<String> a = new ArrayList<String>();
		a.add(diffProg.toString());
		for (String p: params)
			a.add(p);
		a.add(f1.toString());
		a.add(f2.toString());
		String[] cmdArray = new String[a.size()] ;
		a.toArray(cmdArray);
/*		String cmd = "";
		for (String s: cmdArray) 
			cmd += s + " ";
		System.out.println(cmd);*/
		Process p = Runtime.getRuntime().exec(cmdArray);
		InputStream stdout = p.getInputStream(), 
					stderr = p.getErrorStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(stdout)),
				brErr  = new BufferedReader(new InputStreamReader(stderr));
		String line = "", line2 = null;
		StringWriter w = new StringWriter();

		while ( (line = br.readLine()) != null || ((line2 = brErr.readLine()) != null))
		{
			if (line != null) 
			{
				w.write(line);
				w.write(eol);
			}
			if (line2 != null) w.write("stderr> " + line2 + eol);
		}
		//System.out.println("Exit value = " + p.exitValue());
		return w.toString();
	}
	
	public RunExternalDiffProgram() throws IOException, InterruptedException
	{
    	Properties props = Controller.readProperties();
    	String GNUdiff = props.getProperty("GNUdiff");
    	if (GNUdiff != null)
    		RunExternalDiffProgram.diffProg = new File(GNUdiff);

//		String diffProg = "C:\\progs\\sundries\\diff.exe",	params = "-y";
		String 	path = "Record_PeopleCode\\BANKING_DW\\ADDL_BANK_INFO_BTN\\FieldChange.pcode";
		File dir = new File("C:\\projects\\sandbox\\big\\UPGCUST\\PeopleCodeTrees\\"),
			  f1 = new File(dir, "HRDEV/" + path),
			  f2 = new File(dir, "Merged/" + path);
		System.out.println(getShortDiff(f1, f2));
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			new RunExternalDiffProgram();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
