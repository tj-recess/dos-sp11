
public class RW
{
	private String name;
	private int opTime;
	private int sleepTime;
	private int clientNum;
	
	public RW(String rwName, int opTime, int sleepTime, int cNum)
	{
		name = rwName;
		this.opTime = opTime;
		this.sleepTime = sleepTime;
		clientNum = cNum;
	}
	public String getName() {
		return name;
	}
	public int getOpTime() {
		return opTime;
	}
	public int getSleepTime() {
		return sleepTime;
	}
	public int getClientNum()
	{
		return clientNum;
	}

}

class Formatter
{
	int serviceNum;
	int objectVal;
	String writtenBy;
	String readBy;
	int numActiveReaders;
	public Formatter(int serviceNum, int objectVal, String writtenBy)
	{
		this.serviceNum = serviceNum;
		this.objectVal = objectVal;
		this.writtenBy = writtenBy;
	}
	
	public Formatter(int serviceNum, int objectVal, String readBy, int numActiveReaders)
	{
		this.serviceNum = serviceNum;
		this.objectVal = objectVal;
		this.readBy = readBy;
		this.numActiveReaders = numActiveReaders;
	}
	
	public int getServiceNum() {
		return serviceNum;
	}

	public int getObjectVal() {
		return objectVal;
	}

	public String getWrittenBy() {
		return writtenBy;
	}

	public String getReadBy() {
		return readBy;
	}

	public int getNumActiveReaders() {
		return numActiveReaders;
	}
}