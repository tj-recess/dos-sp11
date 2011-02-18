
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
