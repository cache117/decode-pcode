package decodepcode;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;

public abstract class ContainerProcessor
{
	private String dBowner, tag;
	private Connection JDBCconnection;
	private PreparedStatement ps;

	public void aboutToProcess() {}
	public void finishedProcessing() {} 
	abstract public void process( PeopleCodeObject c) throws IOException;
	abstract public void processSQL( SQLobject sql) throws IOException;
	abstract public void processCONT(CONTobject cont) throws IOException;
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
	public String getTag() {
		return tag;
	}
	public void setTag( String _tag){
		tag = _tag;
	}
	public PreparedStatement getPs() {
		return ps;
	}
	public void setPs(PreparedStatement ps) {
		this.ps = ps;
	}
}
