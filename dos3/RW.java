import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.io.Serializable;


public class RW implements Serializable
{
	private static final long serialVersionUID = 6591733251439326731L;
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


class ReplyPacket
{
	private int requestNum;
	private int serviceNum;
	private int sharedObjectVal;
	
	ReplyPacket(int reqNum, int sharedObjVal, int servNum)
	{
		requestNum = reqNum;
		serviceNum = servNum;
		sharedObjectVal = sharedObjVal;
	}

	public int getRequestNum() {
		return requestNum;
	}

	public int getServiceNum() {
		return serviceNum;
	}

	public int getSharedObjectVal() {
		return sharedObjectVal;
	}
}

class ConfigReader
{
	private int numReaders;
	private int numWriters;
	private ArrayList<RW> readers;
	private ArrayList<RW> writers;
	private String server;
	int numAccesses;
	int rmiPort;
	
	ConfigReader(String filePath)
	{
		readers = new ArrayList<RW>();
		writers = new ArrayList<RW>();
		
		//read configuration from system.properties file
		Properties sysProp = new Properties();
		try {
			sysProp.load(new FileInputStream(filePath));
		} catch (FileNotFoundException e) {
			System.err.println("\"system.properties\" - File not found, Exiting!");
			System.exit(-1);
		} catch (IOException ioex) {
			System.err.println("\"system.properties\" - IO Exception! " + ioex.getMessage() + " Exiting!");
			System.exit(-1);
		}
		
		
		server = sysProp.getProperty("RW.server");
						
		//now read all the attributes in config file
		numReaders = Integer.parseInt(sysProp.getProperty("RW.numberOfReaders"));
		numWriters = Integer.parseInt(sysProp.getProperty("RW.numberOfWriters"));
		
		numAccesses = Integer.parseInt(sysProp.getProperty("RW.numberOfAccesses"));
		rmiPort = Integer.parseInt(sysProp.getProperty("Rmiregistry.port"));
		
		//setup data structures so that they can be passed to the actual server
		//setup readers from config file
		for(int i = 0; i < numReaders; i++)
		{
			String readerKey = "RW.reader" + (i + 1);
			String readerName = sysProp.getProperty(readerKey);	//reader/writer's name starts from 1 to n (not 0 to n-1)
			String opTime = sysProp.getProperty(readerKey + ".opTime");
			String sleepTime = sysProp.getProperty(readerKey + ".sleepTime");
			System.out.println("DEBUG: Config : " + readerName + ", " + opTime + ", " + sleepTime);
			readers.add(new RW(readerName, Integer.parseInt(opTime), Integer.parseInt(sleepTime), i));	
		}
		//setup writers from config file
		for(int i = numReaders; i < numReaders + numWriters; i++)
		{
			String writerKey = "RW.writer" + (i + 1);
			String writerName = sysProp.getProperty(writerKey);	//reader/writer's name starts from (numReaders + 1) to n (not 0 to n-1)
			String opTime = sysProp.getProperty(writerKey + ".opTime");
			String sleepTime = sysProp.getProperty(writerKey + ".sleepTime");
//			System.out.println("DEBUG: Config : " + writerKey + ", " + opTime + ", " + sleepTime);
			System.out.println("DEBUG: Config : " + writerName + ", " + opTime + ", " + sleepTime);
			writers.add(new RW(writerName, Integer.parseInt(opTime), Integer.parseInt(sleepTime), i));
		}		
	}

	public int getRmiPort() {
		return rmiPort;
	}

	public int getNumReaders() {
		return numReaders;
	}

	public int getNumWriters() {
		return numWriters;
	}

	public ArrayList<RW> getReaders() {
		return readers;
	}

	public ArrayList<RW> getWriters() {
		return writers;
	}

	public String getServer() {
		return server;
	}

	public int getNumAccesses() {
		return numAccesses;
	}
}
