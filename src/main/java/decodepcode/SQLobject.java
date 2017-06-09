package decodepcode;

import java.util.Date;
import java.util.logging.Logger;

public class SQLobject implements PeopleToolsObject 
{
	static Logger logger = Logger.getLogger(SQLobject.class.getName());
	String recName, sql, lastChangedBy, source, market, dbType;
	int sqlType;
	Date lastChanged;
	
	public SQLobject( int _sqlType, String _recName, String _sql, String _lastChangedBy, Date _lastChanged, 
					String _market, String _dbType)
	{
		sqlType = _sqlType; recName = _recName; sql = _sql; lastChangedBy = _lastChangedBy; lastChanged = _lastChanged;
		market = _market; dbType = _dbType;
	}
	
	public String[] getKeys() 
	{
		String[] a ;
		if (sqlType != 1)
		{
			if (!"GBL".equals(market))
			{
				if (!" ".equals(dbType))
				{
					a = new String[3];
					a[2] = dbType;
					
				}
				else
					a = new String[2];
				a[1] = market;
			}
			else
				if (!" ".equals(dbType))
				{
					a = new String[2];
					a[1] = dbType;
				}
				else
					a = new String[1];
			a[0] = recName;
		}
		else
		{
			// AE SQL
			// example SQLID: 'ADMWEBPRS   AcadIns Step01  S'
			if (recName.length() != 29)
//				throw new IllegalArgumentException("expected SQLID with length 20 for AE: got '" + recName + "'");
			{
				logger.warning("Expected SQLID with length 29 for type 1 (AE): got '" + recName + "'" );
				a = new String[1];
				a[0] = recName.replace(' ', '_').replace('\t', '_');
				return a;
			}
			int l = 6;
			if ("GBL".equals(market))
				l--;
			if (" ".equals(dbType))
				l--;
			a = new String[l];
			a[0] = recName.substring(0,12).trim();
			a[1] = recName.substring(12,20).trim();
			a[2] = recName.substring(20,28).trim();
			a[3] = recName.substring(28,29).trim();
			l = 4;
			if (!"GBL".equals(market))
				a[l++] = market;
			if (!" ".equals(dbType))
				a[l] = dbType;
		}
		return a;
	}

	public int getPeopleCodeType() {
		return -1;
	}

	public String getLastChangedBy() {
		return lastChangedBy;
	}

	public Date getLastChangedDtTm() {
		return lastChanged;
	}

	public String getRecName() {
		return recName;
	}

	public String getSql() {
		return sql;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public int[] getKeyTypes() {
		// TODO Auto-generated method stub
		return null;
	}

}
