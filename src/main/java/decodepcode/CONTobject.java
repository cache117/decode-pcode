package decodepcode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CONTobject implements PeopleToolsObject {

	
	String contName, contFmt, lastChangedBy;
	int altContNum, contType, compAlg, seqNo;
	Date lastChanged;
	String languageCd;
	List<byte[]> contDataArrays = new ArrayList<byte[]>();
	
	
	public CONTobject(String contName, String contFmt, String lastChangedBy, int altContNum, int contType, int compAlg, int seqNo,
			String languageCd, Date lastChanged) {
		super();
		this.contName = contName;
		this.contFmt = contFmt;
		this.lastChangedBy = lastChangedBy;
		this.altContNum = altContNum;
		this.contType = contType;
		this.compAlg = compAlg;
		this.seqNo = seqNo;
		this.languageCd = languageCd;
		this.lastChanged = lastChanged;
	}
	
	public void addToContDataArrays(int seqNo, byte[] contDataBytes) throws IOException {
		this.seqNo = seqNo;		
		if (contType == 4){
			String contData = new String(contDataBytes, "UnicodeLittleUnmarked");
			contDataArrays.add(contData.getBytes());
		} else {
			contDataArrays.add(contDataBytes);
		}
	}
	
	public byte[] getContDataBytes() {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		for (byte[] b : contDataArrays) {
			os.write(b, 0, b.length);
		}
	 	return os.toByteArray();
		
	}
	
	public String[] getKeys() {
		// TODO Auto-generated method stub
		return null;
	}

	public int[] getKeyTypes() {
		// TODO Auto-generated method stub
		return null;
	}

	public int getPeopleCodeType() {
		return -1;
	}

	public Date getLastChangedDtTm() {
		return lastChanged;
	}

	public String getLastChangedBy() {
		return lastChangedBy;
	}

	public String getSource() {
		// TODO Auto-generated method stub
		return null;
	}

}
