package decodepcode;

/*
 * Copyright (c)2011 Erik H (erikh3@users.sourceforge.net)

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


import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class PeopleCodeParser {
	static boolean debug = false;

	
	final int 
		SPACE_BEFORE = 0x1,
		SPACE_AFTER = 0x2,
		NEWLINE_BEFORE = 0x4,
		NEWLINE_AFTER  = 0x8,
		INCREASE_INDENT = 0x10,
		DECREASE_INDENT = 0x20,
		EVALUATE_STYLE = 0x40,
		RESET_INDENT_BEFORE = 0x80,
		RESET_INDENT_AFTER = 0x100,
		NO_SPACE_BEFORE = 0x200,
		NO_SPACE_AFTER = 0x400,
		INCREASE_INDENT_ONCE = 0x800,
		AND_INDICATOR = 0x1000,
		NEWLINE_ONCE = 0x2000,
		IN_DECLARE = 0x4000,
		SEMICOLON = 0x8000,
		SPACE_BEFORE2 = 0x10000,
		COMMENT_ON_SAME_LINE = 0x20000,
		PUNCTUATION = 0,
		SPACE_BEFORE_AND_AFTER = SPACE_BEFORE | SPACE_AFTER,
		SPACE_BEFORE_AND_AFTER2 = SPACE_BEFORE2 | SPACE_BEFORE | SPACE_AFTER,
		NEWLINE_BEFORE_AND_AFTER = NEWLINE_BEFORE | NEWLINE_AFTER,
		NEWLINE_BEFORE_SPACE_AFTER = NEWLINE_BEFORE | SPACE_AFTER,
		SPACE_BEFORE_NEWLINE_AFTER = SPACE_BEFORE | NEWLINE_AFTER,
		AND_STYLE =  NEWLINE_AFTER | SPACE_BEFORE2| SPACE_BEFORE | AND_INDICATOR,
		FOR_STYLE = NEWLINE_BEFORE | SPACE_AFTER | INCREASE_INDENT,
		IF_STYLE = NEWLINE_BEFORE | SPACE_BEFORE | SPACE_AFTER,
		THEN_STYLE = SPACE_BEFORE | SPACE_BEFORE2 | NEWLINE_AFTER | SPACE_AFTER | INCREASE_INDENT ,
		ELSE_STYLE = NEWLINE_BEFORE | DECREASE_INDENT | NEWLINE_AFTER | INCREASE_INDENT,
		ENDIF_STYLE = NEWLINE_BEFORE | SPACE_BEFORE |  DECREASE_INDENT | NEWLINE_AFTER,
		FUNCTION_STYLE= NEWLINE_BEFORE | SPACE_AFTER | INCREASE_INDENT | RESET_INDENT_BEFORE,
		END_FUNCTION_STYLE = NEWLINE_BEFORE | RESET_INDENT_BEFORE; 
	
	// for these references, quote the part after the period:
	static Set<String> specialRefs = new HashSet<String>();
	static String[] specialRefsArr = { "MenuName", "BarName", "ItemName", "Panel"};
	static
	{
		for (String s: specialRefsArr)
			specialRefs.add(s);
	}
	
	static Logger logger = Logger.getLogger(PeopleCodeParser.class.getName());
	PeopleCodeContainer container;
	Writer w;
	public static String eol = System.getProperty("line.separator");
	static int nSuccess, nFailed;
	
	abstract class ElementParser
	{
		byte b;
		int format;
		abstract void parse() throws IOException;
		abstract byte getStartByte();
		public int getFormat() {
			return format;
		}
		public void setFormat(int format) {
			this.format = format;
		}
		public boolean writesNonBlank()
		{
			return true;
		}
	}
	
	class SimpleElementParser extends ElementParser
	{
		String t;
		SimpleElementParser( byte _b, String _t, int _format)
		{
			b = _b;
			t = _t;
			format = _format;
		}
		SimpleElementParser( byte _b, String _t)
		{
			this(_b, _t, SPACE_BEFORE_AND_AFTER);
		}
		@Override
		void parse() throws IOException 
		{
/*			if (container.bytes[container.pos] != b)
				throw new IllegalArgumentException("expected " + b + " at pos " + container.pos);
*/
			w.write(t);
//			container.pos++;
		}		
		@Override
		byte getStartByte()
		{
			return b;
		}
		@Override
		public boolean writesNonBlank()
		{
			return t.trim().length() > 0;
		}
	}
	
	abstract class StringParser extends ElementParser
	{
		String getString()
		{
			StringWriter sw = new StringWriter();
			byte bb;
			while ((bb = container.get()) != 0)
			{
				container.pos++;		// skip 0
				if ( bb == (byte) 10)
					sw.write('\r');  // LF --> CRLF		    						
				sw.write((char) bb);
			}
			container.pos++;		
			return sw.toString();
		}
	}

	class PureStringParser extends StringParser
	{
		PureStringParser( byte _b) 
		{ 
			b = _b;
			format = SPACE_BEFORE;
		}
		PureStringParser( byte _b, int _format)
		{
			this(_b);
			format = _format;
		}
		
		@Override
		byte getStartByte() {
			return b;
		}
		@Override
		void parse() throws IOException {
			w.write(getString());			
		}		
	}
	
	class IdentifierParser extends StringParser
	{
		IdentifierParser( byte _b) 
		{ 
			b = _b;
			format = SPACE_BEFORE | SPACE_AFTER;
		}
		@Override
		byte getStartByte() {
			return b;
		}
		@Override
		void parse() throws IOException {
			container.pos--; container.pos--; // current byte is zero, back up
			w.write(getString());			
		}		
	}
	
	class EmbeddedStringParser extends PureStringParser
	{
		String pre, post;
		EmbeddedStringParser( byte b, String _pre, String _post, int _format) 
		{
			super(b);
			pre = _pre;
			post = _post;
			format = _format;
		}
		EmbeddedStringParser( byte b, String _pre, String _post)
		{
			this(b, _pre, _post, SPACE_BEFORE_AND_AFTER);
		}
		@Override
		void parse() throws IOException 
		{
			
			int start_pos = container.pos;
			while (container.get() != 0)
			{	
				container.pos++;
			}
			container.pos++;
			int end_pos = container.pos;
			
			w.write(pre);
			String s = new String(container.bytes, start_pos, (end_pos - 2) - start_pos , "UnicodeLittleUnmarked");
			w.write(s.replace("\n","\r\n").replace("\"", "\"\""));
			w.write(post);
		}				
	}
	
	class CommentParser extends ElementParser
	{
		public CommentParser( byte _b) {
			this (_b, //NEWLINE_AFTER | COMMENT_ON_SAME_LINE | SPACE_BEFORE
					NEWLINE_BEFORE_AND_AFTER
			);
		}
		public CommentParser( byte _b, int _format)
		{
			b = _b;
			format = _format;
		}
		@Override
		byte getStartByte() {
			return b;
		}

		@Override
		void parse() throws IOException {
		    /* The length byte is bit wide ANDed and cast to integer. */
		    int comment_length = (int) container.get() & 0xff;
		    comment_length = comment_length + ((int) container.get()& 0xff) * 256;
//		    logger.fine("Comment length = " + comment_length);
		    w.write(
		    	new String(container.bytes, container.pos, comment_length, "UnicodeLittleUnmarked")
		    	.replace("\n","\r\n")
		    );
		    container.pos += comment_length;
//		    logger.info("comment='" + sw.toString() + "'");
		}		
		@Override
		public boolean writesNonBlank()
		{
			return true;
		}		
	}
	
	class ReferenceParser extends ElementParser
	{
		public ReferenceParser( byte _b) {
			b = _b;
			format= SPACE_BEFORE_AND_AFTER;
		}
		@Override
		byte getStartByte() {
			return b;
		}
		@Override
		void parse() throws IOException {
			int b1 = (int) (container.get() & 0xff);
			int b2 = (int) (container.get() & 0xff);
			String ref = container.getReference(b2 * 256 + b1+1);
			if (ref == null)
				logger.severe("Could not find reference #"+ b1);
			else
			{
				if (b == 74 && (ref.startsWith("Field.") || ref.startsWith("Record.") ||ref.startsWith("Scroll.")))
				{
//					w.write("["+ b+"]");
					ref = ref.substring(ref.indexOf('.')+1);
				}
				int p1 = ref.indexOf('.');
				if (p1 > 0)
				{
					String rec = ref.substring(0, p1);
					if (b == (byte) 72)
						ref = rec + ".\"" + ref.substring(p1+1) + "\"";
				}
				w.write(ref);
			}
		}		
	}
	
	class NumberParser extends ElementParser
	{
		int nBytes;
		public NumberParser( byte _b, int _nBytes) 
		{
			b = _b;
			nBytes = _nBytes;
			format = SPACE_BEFORE | NO_SPACE_AFTER;
		}
		@Override
		byte getStartByte() 
		{
			return b;
		}

		@Override
		void parse() throws IOException 
		{
		    int     dValue          = 0;   /* decimal position from far right going left */
		    String  out_number      = "";
		    int     num_bytes       = nBytes - 3;

		    container.pos++;  /* Skip the first byte */
		    dValue = (int) container.get();
	    	long val = 0, fact = 1;
	    	for (int i = 0; i < num_bytes; i++)
	    	{		    		
	    		val += fact * (long) (container.get() & 0xff);
	    		fact = fact * (long) 256;
	    	}
    		out_number = Long.toString(val);
	    	
	    	if (dValue > 0 && !"0".equals(out_number))
	    	{
	    			while (dValue > out_number.length())
	    				out_number = "0" + out_number;
	    		
//	    			throw new IllegalArgumentException("Error parsing number; digits = '" + out_number + "', decimal position = " + dValue);
//	    			out_number = "!!PARSE_ERROR!! " + out_number + "!!" + dValue + "!!";
	    		out_number =  out_number.substring(0, out_number.length() - dValue) + "." 
	    					+ out_number.substring(out_number.length() - dValue);
	    		if (out_number.startsWith("."))
	    				out_number ="0" + out_number;
	    	}
		    w.write(out_number);
		}		
	}	
	
	final  ElementParser parserArray[] = 
	{
		new IdentifierParser((byte) 0),
		new PureStringParser((byte) 1),
		new SimpleElementParser((byte) 3, ",", NO_SPACE_BEFORE | SPACE_AFTER),
		new SimpleElementParser((byte) 4, "/"),
		new SimpleElementParser((byte) 5, ".", PUNCTUATION),
		new SimpleElementParser((byte) 6, "="),
		new SimpleElementParser((byte) 7, ""), // stop parsing?
		new SimpleElementParser((byte) 8 , ">="),
		new SimpleElementParser((byte) 9 , ">"),
		new PureStringParser((byte) 10), /* Function | Method | External Datatype | Class name  0x0A*/
		new SimpleElementParser((byte) 11, "(", NO_SPACE_AFTER),
		new SimpleElementParser((byte) 12, "<="),
		new SimpleElementParser((byte) 13, "<"),
		new SimpleElementParser((byte) 14, "-"),
		new SimpleElementParser((byte) 15, "*"),
		new SimpleElementParser((byte) 16, "<>"),
		new NumberParser((byte) 17, 14),
		new PureStringParser((byte) 18), /* System variable name */
		new SimpleElementParser((byte) 19, "+"),
		new SimpleElementParser((byte) 20, ")", NO_SPACE_BEFORE), //0x14
		new SimpleElementParser((byte) 21, ";", SEMICOLON | NEWLINE_AFTER | NO_SPACE_BEFORE), //0x15
		new EmbeddedStringParser((byte) 22, "\"", "\""),
		new SimpleElementParser((byte) 24, "And", AND_STYLE),
		new SimpleElementParser((byte) 25, "Else", ELSE_STYLE),
		new SimpleElementParser((byte) 26, "End-If", ENDIF_STYLE),
		new SimpleElementParser((byte) 27, "Error"),
		new SimpleElementParser((byte) 28, "If", IF_STYLE), // 0x1c
		new SimpleElementParser((byte) 29, "Not"),
		new SimpleElementParser((byte) 30, "Or", AND_STYLE),
		new SimpleElementParser((byte) 31, "Then", THEN_STYLE),
		new SimpleElementParser((byte) 32, "Warning"),
		new ReferenceParser((byte) 33),
		new SimpleElementParser((byte) 35, "|"),
		new CommentParser((byte) 36),		/* slash asterisk comment  or  REM comment */
		new SimpleElementParser((byte) 37, "While", FOR_STYLE),
		new SimpleElementParser((byte) 38, "End-While", ENDIF_STYLE),
		new SimpleElementParser((byte) 39, "Repeat", FOR_STYLE),
		new SimpleElementParser((byte) 40, "Until", IF_STYLE),
		new SimpleElementParser((byte) 41, "For", FOR_STYLE),
		new SimpleElementParser((byte) 42, "To"),
		new SimpleElementParser((byte) 43, "Step"),
		new SimpleElementParser((byte) 44, "End-For", ENDIF_STYLE),
		new SimpleElementParser((byte) 45, "", NEWLINE_ONCE), // NEWLINE_AFTER 0x2d
		new SimpleElementParser((byte) 46, "Break", SPACE_BEFORE),
		new SimpleElementParser((byte) 47, "True", SPACE_BEFORE_AND_AFTER2),
		new SimpleElementParser((byte) 48, "False", SPACE_BEFORE_AND_AFTER2),
		new SimpleElementParser((byte) 49, "Declare", NEWLINE_BEFORE_SPACE_AFTER | IN_DECLARE),
		new SimpleElementParser((byte) 50, "Function", FUNCTION_STYLE),
		new SimpleElementParser((byte) 51, "Library"),
		new SimpleElementParser((byte) 53, "As"),
		new SimpleElementParser((byte) 54, "Value"),
		new SimpleElementParser((byte) 55, "End-Function", END_FUNCTION_STYLE),
		new SimpleElementParser((byte) 56, "Return"),
		new SimpleElementParser((byte) 57, "Returns"),
		new SimpleElementParser((byte) 58, "PeopleCode"),
		new SimpleElementParser((byte) 59, "Ref"),
		new SimpleElementParser((byte) 60, "Evaluate", INCREASE_INDENT | SPACE_AFTER),
		new SimpleElementParser((byte) 61, "When", DECREASE_INDENT | NEWLINE_BEFORE_SPACE_AFTER | INCREASE_INDENT),
		new SimpleElementParser((byte) 62, "When-Other", DECREASE_INDENT | NEWLINE_BEFORE | NEWLINE_AFTER | INCREASE_INDENT),
		new SimpleElementParser((byte) 63, "End-Evaluate", NEWLINE_BEFORE |DECREASE_INDENT |SPACE_BEFORE),
		new PureStringParser((byte) 64), /* PeopleCode Variable Type Name */
		new SimpleElementParser((byte) 65, "", SPACE_AFTER), // 'And'-style?
		new SimpleElementParser((byte) 66, "", PUNCTUATION), //SPACE_BEFORE | NO_SPACE_AFTER),
		new SimpleElementParser((byte) 67, "Exit"),
		new SimpleElementParser((byte) 68, "Local", NEWLINE_BEFORE_SPACE_AFTER),		
		new SimpleElementParser((byte) 69, "Global", NEWLINE_BEFORE_SPACE_AFTER),
		new SimpleElementParser((byte) 70, "**", PUNCTUATION),
		new SimpleElementParser((byte) 71, "@", SPACE_BEFORE | NO_SPACE_AFTER), //, PUNCTUATION),
		new ReferenceParser((byte) 72),
		new SimpleElementParser((byte) 73, "set", INCREASE_INDENT_ONCE | SPACE_BEFORE),
		new ReferenceParser((byte) 74),
		new SimpleElementParser((byte) 75, "Null"),
		new SimpleElementParser((byte) 76, "[", SPACE_BEFORE | NO_SPACE_AFTER),
		new SimpleElementParser((byte) 77, "]", NO_SPACE_BEFORE | SPACE_AFTER),
		new CommentParser((byte) 78, NEWLINE_AFTER | COMMENT_ON_SAME_LINE | SPACE_BEFORE),
		new SimpleElementParser((byte) 79, "", NEWLINE_AFTER),
		new NumberParser((byte) 80, 18),
		new SimpleElementParser((byte) 81, "PanelGroup"),
		new SimpleElementParser((byte) 82, ""),
		new SimpleElementParser((byte) 83, "Doc", NEWLINE_BEFORE_SPACE_AFTER),
		new SimpleElementParser((byte) 84, "Component", NEWLINE_BEFORE_SPACE_AFTER),
		new CommentParser((byte) 85/*, NEWLINE_AFTER | COMMENT_ON_SAME_LINE | SPACE_BEFORE*/), /* Less-than asterisk comment */
		new SimpleElementParser((byte) 86, "Constant", NEWLINE_BEFORE_SPACE_AFTER),
		new SimpleElementParser((byte) 87, ":", PUNCTUATION),
		new SimpleElementParser((byte) 88, "import"),
		new SimpleElementParser((byte) 89, "*"),
		new SimpleElementParser((byte) 90, "class", FUNCTION_STYLE),
		new SimpleElementParser((byte) 91, "end-class", END_FUNCTION_STYLE),
		new SimpleElementParser((byte) 92, "extends"),
		new SimpleElementParser((byte) 93, "out"),
		new SimpleElementParser((byte) 94, "property", NEWLINE_BEFORE_SPACE_AFTER),
		new SimpleElementParser((byte) 95, "get", INCREASE_INDENT_ONCE | SPACE_BEFORE),
		new SimpleElementParser((byte) 96, "readonly"),
		new SimpleElementParser((byte) 97, "private", ELSE_STYLE),
		new SimpleElementParser((byte) 98, "instance"),
		new SimpleElementParser((byte) 99, "method", NEWLINE_BEFORE | SPACE_AFTER | INCREASE_INDENT_ONCE),
		new SimpleElementParser((byte) 100, "end-method", END_FUNCTION_STYLE),
		new SimpleElementParser((byte) 101, "try", SPACE_BEFORE_NEWLINE_AFTER),
		new SimpleElementParser((byte) 102, "catch"),
		new SimpleElementParser((byte) 103, "end-try"),
		new SimpleElementParser((byte) 104, "throw"),
		new SimpleElementParser((byte) 105, "create"),
		new SimpleElementParser((byte) 106, "end-get", RESET_INDENT_BEFORE | NEWLINE_BEFORE),
		new SimpleElementParser((byte) 107, "end-set", RESET_INDENT_BEFORE | NEWLINE_BEFORE),
		new SimpleElementParser((byte) 108, ""),
		new EmbeddedStringParser((byte) 109, "/+ ", " +/", NEWLINE_BEFORE_AND_AFTER),
		new SimpleElementParser((byte) 110, "Continue", SPACE_BEFORE_NEWLINE_AFTER),
		new SimpleElementParser((byte) 111, "abstract"),
		new SimpleElementParser((byte) 112, "interface"),
		new SimpleElementParser((byte) 113, "end-interface"),
		new SimpleElementParser((byte) 114, "implements"),
		new SimpleElementParser((byte) 115, "protected", ELSE_STYLE)
	};

	Map<Byte, ElementParser> parsers = new HashMap<Byte, ElementParser>();

	public PeopleCodeParser() {
		for (ElementParser p: parserArray)
			parsers.put(new Byte(p.getStartByte()), p);
	}
	
	public void parse( PeopleCodeContainer _container, Writer _w) throws IOException
	{
		container = _container;
		w = _w;
		boolean endDetected = false;
		container.pos = 37;
		Set<Byte> missing = new HashSet<Byte>();
		ElementParser lastParser = null;
		int nIndent = 0;
		boolean startOfLine = true,
			firstLine = true, 
			and_indicator = false,
			did_newline = false,
			in_declare = false,
			wroteSpace = false,
			at_ifwhileuntil_condition = false;
		
		while (container.pos < container.bytes.length && !endDetected)
		{
			endDetected = container.read() == (byte) 7;
			byte code = container.get();
			if (debug && code == (byte) 78)
				logger.info("and_indicator = " + and_indicator);
			ElementParser p = parsers.get(new Byte(code));
			if (p == null)
			{				
				missing.add(new Byte(code));
				if (code == 0)
					logger.severe("found byte code 0- can't parse");
				else
				{
					logger.warning("No parser for byte code " + code + " = " + hex(code) + " = '"+ (char) code + "' at " + "0x" + Integer.toString( container.pos, 16) + " in "+ container.getCompositeKey());
					if (debug)
					{
						w.write("0x" + Integer.toString( container.pos, 16) + " (!!! NO PARSER FOR " + code + ")' + eol");
					}
				}
			}
			else
			{
				in_declare = (in_declare 
						&& !((lastParser != null && (lastParser.format & NEWLINE_AFTER) > 0) || (lastParser.format == SEMICOLON))
									// ||(!((p.format & NEWLINE_BEFORE) > 0 
									);
				if (code == (byte) 28 || code == (byte) 37 || code == (byte) 40){
					at_ifwhileuntil_condition = true;
				} else if (code == (byte) 31 || code == (byte) 45 || code == (byte) 21){
					at_ifwhileuntil_condition = false;
				}
				
				
				if (debug)
					w.write("0x" + Integer.toString( container.pos, 16) + " (" + code + ")'");
				if (lastParser != null 
					&& !in_declare
					&& (       ((lastParser.format & INCREASE_INDENT) > 0) 
							|| ((lastParser.format & INCREASE_INDENT_ONCE) > 0 && nIndent == 0)))
					nIndent++;

				if ((p.format & RESET_INDENT_BEFORE) > 0 && !in_declare)
					nIndent = 0;
				if ((p.format & DECREASE_INDENT) > 0 && nIndent > 0 && !in_declare)
					nIndent--;
				
				if ( !firstLine 
					&& p.format != PUNCTUATION
					&& (p.format  & SEMICOLON) == 0
					&& !in_declare 
					&&	(  ((lastParser != null && ((lastParser.format & NEWLINE_AFTER) > 0) 
							|| (lastParser.format == SEMICOLON)) && (p.format & COMMENT_ON_SAME_LINE) == 0)
						|| (((p.format & NEWLINE_BEFORE) > 0) //&& !did_newline
							&& (lastParser.format != NEWLINE_ONCE)
							)
						)
						|| ((p.format & NEWLINE_ONCE) > 0 && !did_newline && container.readAhead() != (byte) 21)
						)
				{
					w.write(eol);
					startOfLine = true;
					did_newline = true;
//					if (lastParser == null || (lastParser.format & COMMENT_ON_SAME_LINE) == 0)
//						and_indicator = false;
				}
				else
				{
					if (
							(
								!startOfLine 
								&& !wroteSpace
								&& (p.format != PUNCTUATION) 
								&& (p.format != SEMICOLON)
								&& (( (lastParser != null && (lastParser.format & SPACE_AFTER) > 0))
									|| (p.format & SPACE_BEFORE) > 0) 
								&& ( lastParser == null
										|| ( 
										      (lastParser.format != PUNCTUATION  && (lastParser.format & NO_SPACE_AFTER) == 0)
										      || ((p.format & SPACE_BEFORE2) > 0)) 
										)					
								&& (p.format & NO_SPACE_BEFORE) == 0 
							)
						|| (code == (byte) 75 && lastParser.b == (byte) 11)
						)
					{
						if (!(code == (byte) 76 && lastParser.b == (byte) 20)){
							w.write(' ');
							wroteSpace = true;
						}
					}
				}
				if (startOfLine && (p.writesNonBlank() || code == (byte) 79)){
					for (int i = 0; i < nIndent + (and_indicator ? (at_ifwhileuntil_condition ? 2 : 1) : 0); i++)
					{
						w.write("   ");
					}
					if (at_ifwhileuntil_condition && !and_indicator && code != (byte) 28 && code != (byte) 37 && code != (byte) 40){
						w.write("   ");
					}
				}
				firstLine= false;
				int p0 =container.pos;
				p.parse();
				wroteSpace = wroteSpace && !p.writesNonBlank();
				in_declare = in_declare || (p.format & IN_DECLARE) > 0; 
				startOfLine = startOfLine && !p.writesNonBlank();
				did_newline = did_newline && container.pos == p0;
				and_indicator = (p.format & AND_INDICATOR) > 0 
								|| (and_indicator && (p.format & COMMENT_ON_SAME_LINE) != 0 
								|| (and_indicator && (code == (byte) 36))
								);
				lastParser = p;
				if ((p.format & RESET_INDENT_AFTER) > 0 )
					nIndent = 0;
				
				if (debug) 
					w.write("'" + eol);
			}
		}
		if (!missing.isEmpty())
			logger.info("Missing: " + missing);
	}
	
public void reverseEngineer(PeopleCodeContainer _container) throws IOException
{
//	logger.setLevel(Level.SEVERE);
	int defects = 0;
	container = _container;
	String peoplecode = container.getPeopleCodeText();
	w = new StringWriter();
	
	boolean endDetected = false;
	container.pos = 37;
	int posInPPC = 0, lastPos = -1;
	String lastCodes = "";
	StringWriter codeToAdd = new StringWriter();
	try {
		while (container.pos < container.bytes.length && !endDetected)
		{
			byte code = container.get();
			endDetected = code== (byte) 7;
			if (endDetected)
				break;
	
			ElementParser p = parsers.get(new Byte(code));
			if (p == null)
			{
				lastPos = posInPPC;
				lastCodes += new Integer( (int) code &0xff).toString() + " ";
				defects++;
			}
			else
			{
				int startPos = container.pos;
				p.parse();
				String generated = w.toString().trim();
				w = new StringWriter();
				int previousPos = posInPPC, foundPos = -2; 
				if (posInPPC >= 0)
				{
					foundPos = peoplecode.indexOf(generated, posInPPC);
					if (foundPos >= 0)
					{
						String inbetween = peoplecode.substring(previousPos, foundPos).trim();
						if (inbetween.length() > 0)
						{
							logger.info("Found '" + generated + "' at " + posInPPC + ", but after skipping '" + inbetween + "'");
							defects++;
						}
						posInPPC = foundPos;
					}
				}
				if (foundPos >= 0)
				{
//					logger.fine("Found '" + generated + "' at " + posInPPC);
					if (lastPos >= 0)
					{
						if (posInPPC < lastPos)
							throw new IllegalArgumentException("? previous = " + previousPos);
						if (lastPos>= peoplecode.length())
							lastPos = peoplecode.length()-1;
						if (posInPPC>= peoplecode.length())
							posInPPC = peoplecode.length()-1;
//						logger.fine("substring "+ lastPos + ".." + posInPPC);
						String segment = peoplecode.substring(lastPos, posInPPC).trim();
						logger.info("missing code(s) " + lastCodes + ": '" + segment + "'");
						logger.info("Found '" + generated + "' at " + posInPPC);
						if (lastCodes.length() < 4)
							codeToAdd.write("new SimpleElementParser((byte) " + lastCodes + ", \"" + segment + "\"),"+ eol);
						lastCodes = "";
						lastPos = -1;
					}
					posInPPC += generated.length();
				}
				else
				{
					defects++;
					logger.severe("NOT found '" + generated + "' at pos " + hex(startPos) + " in " + container.getCompositeKey() + ", code = " + ((int) code & 0xff));
					try {
						int p2 = posInPPC + (generated.length() < 100? 100: generated.length() + 50);
						if (p2 > peoplecode.length())
							p2 = peoplecode.length() - 1;
						if (posInPPC >  0 && posInPPC < peoplecode.length() - 1)						
							logger.severe("peoplecode = '" + peoplecode.substring(posInPPC, p2) + "'...");
						else
							if (posInPPC > peoplecode.length())
								logger.severe("PosInPPC > peoplecode.length()");
					} catch (StringIndexOutOfBoundsException ee)
					{
						logger.severe("PosInPPC = "+ posInPPC + ", ppc length = " + peoplecode.length());
						ee.printStackTrace();
					}
				}
			}	
		}
		if (defects > 0)
		{
			logger.info("" + defects + " defect(s) in " + 
					container.getCompositeKey());
			nFailed++;
		}
		else
			nSuccess++;
	}
	finally
	{
		if (codeToAdd.toString().length() > 0)
			System.out.println(codeToAdd.toString());
	}	
}
	

public static void testWithFile(File inFile) throws IOException
{
	testWithFile( inFile, null);
}
public static void testWithFile(File inFile, PeopleToolsObject obj) throws IOException 
{
	PeopleCodeContainer p = new PeopleCodeContainerFromFile( inFile, obj);
	File outFile = new File( new File(inFile.getParent()), p.getCompositeKey() + ".decoded");
	Writer w = new FileWriter(outFile);
	logger.fine("Parsing " + inFile);
	logger.fine("Output in " + outFile);
	new PeopleCodeParser().parse(p, w);
	w.close();
}


public static void reverseEngineerWithFile(File dir, File inFile) throws Exception
{
	PeopleCodeContainer p = new PeopleCodeContainerFromFile( inFile);
	File refFile = new File(dir, p.getCompositeKey() + ".pcode");
	if (!refFile.exists() )
		throw new IllegalAccessException("PCode file " + refFile + " does not seem to exist");
	logger.fine("ReverseEngineerWithFile: processing " + inFile);
	p.readPeopleCodeTextFromFile(refFile);
	new PeopleCodeParser().reverseEngineer(p);
}

/*
public static void tryAllInDirectory( File dir) throws Exception
{
	String[] bins = dir.list(new FilenameFilter() {
		
		@Override
		public boolean accept(File dir, String name) 
		{
			if (
					!name.endsWith(".bin"))
				return false;
			File pcode = new File(dir, name.substring(0, name.length()-4) +".pcode");
				
			return pcode.exists();
		}
	});
	logger.info("Start parsing "+ bins.length + " binary files");
	nSuccess = 0;
	nFailed = 0;
	for (String bin: bins)
	{
		File inFile = new File(dir , bin);
		logger.fine("======== "+ inFile);
		try {
			testWithFile(dir, inFile);
			reverseEngineerWithFile(dir, inFile);
		} catch (Exception e) 
		{ 
			e.printStackTrace(); 
			logger.severe("Parsing of " + inFile + " failed; continuing");
		}
	}
	logger.info("Ready; " + nSuccess + " .bins decoded successfully, " + nFailed + " with errors");	
}
*/

private static class BinWithObject implements PeopleToolsObject
{
	File bin;
	String[] keys;
	int type;
	BinWithObject( File _bin, String[] _keys, int _type)
	{
		bin = _bin;
		keys = _keys;
		type = _type;
	}
	public String[] getKeys() {
		return keys;
	}
	public int getPeopleCodeType() {
		return type;
	}
	public String getLastChangedBy() {
		// TODO Auto-generated method stub
		return null;
	}
	public Date getLastChangedDtTm() {
		// TODO Auto-generated method stub
		return null;
	}	
	public String getSource() { 
		return "bin-only test object";
	}
	public int[] getKeyTypes() {
		return CreateProjectDefProcessor.getObjTypesFromPCType(type, keys);
	} 
}

static List<BinWithObject> binFiles;

static void tryAllInDirectoryTree( File dir, boolean reverseEngineer)
{
	binFiles = new ArrayList<BinWithObject>();
	keysArr = new ArrayList<String>();
	nSuccess = 0;
	nFailed = 0;
	binsInDir(dir);
	for (BinWithObject f: binFiles)
		try {
			testWithFile(f.bin, f);
			if (reverseEngineer)
			{
				File refFile = new File(f.bin.getParent(), f.bin.getName().replace(".bin", ".pcode"));
				if (!refFile.exists() )
					logger.severe("PCode file " + refFile + " does not seem to exist");
				else
				{
					logger.fine("ReverseEngineerWithFile: processing " + f.bin);
					PeopleCodeContainer p = new PeopleCodeContainerFromFile(f.bin, f);
					p.readPeopleCodeTextFromFile(refFile);
					new PeopleCodeParser().reverseEngineer(p);
				}
			}
		} catch (IOException e) {
			logger.severe("Error parsing " + f + ": " + e);
		}
	logger.info("Ready; "+ (nSuccess + nFailed) + " peoplecode blocks processed; " + nFailed + " with error(s)");
}
static 	ArrayList<String> keysArr = null;
static int type = -1;

static void binsInDir(File dir)
{
	int t = JDBCPeopleCodeContainer.objectTypeFromString(dir.getName());
	if (t > 0)
		type = t;
	else
		keysArr.add(dir.getName());
	String[] bins = dir.list(new FilenameFilter() {		
		public boolean accept(File dir, String name) 
		{
			if (!name.endsWith(".bin"))
				return false;
			File pcode = new File(dir, name.substring(0, name.length()-4) +".pcode");
				
			return pcode.exists();
		}
	});	
	for (String bin: bins)
	{
		String[] keys = new String[keysArr.size()];
		for (int i = 1; i < keysArr.size(); i++)
			keys[i-1] = keysArr.get(i);
		keys[keys.length-1] = bin.substring(0, bin.length()-4);
		binFiles.add(new BinWithObject(new File(dir, bin), keys, type));
	}
	String[] dirs = dir.list(new FilenameFilter() {		
		public boolean accept(File dir, String name) {
			return new File(dir, name).isDirectory();
		}
	});
	for (String subdir: dirs)
		binsInDir( new File(dir, subdir));
	if (t < 0)
		keysArr.remove(keysArr.size()-1);
}



public static void main (String[] a)
{
	try {
		String project = "PPLTLS84CUR";
		File dir = new File("C:\\projects\\sandbox\\PeopleCode\\" + project);
		boolean all = true;
		if (all)
			tryAllInDirectoryTree(dir, true);
		else
		{
			File f = new File(dir, "Record_PeopleCode-PRCSDEFN-PRCSTYPE-FieldFormula.bin");
			reverseEngineerWithFile(dir, f);
			testWithFile(dir);
		}
	} catch (Exception e) {
		e.printStackTrace();
	}	
}

static String hex(int n)
{
	return "0x" + Integer.toString(n, 16);
}
}
