package decodepcode;

import java.util.Date;

public interface PeopleToolsObject 
{
	public String[] getKeys();
	public int[] getKeyTypes();
	public int getPeopleCodeType();
	public Date getLastChangedDtTm();
	public String getLastChangedBy();
	public String getSource();
}
