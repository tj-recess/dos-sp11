import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;

class ClientConfig implements Serializable
{
	private static final long serialVersionUID = 6591733251439326731L;
	private String address;
	private int clientNum;
	private int port;
	private int sleepTime;
	private int opTime;
	
	public ClientConfig(String name, int cNum, int port)
	{
		this.address = name;
		clientNum = cNum;
		this.port = port;
		
		Random random = new Random();
		sleepTime = random.nextInt(1000);
		opTime = random.nextInt(1000);
	}
	
	public int getClientNum()
	{
		return clientNum;
	}
	public int getPort() {
		return port;
	}
	public String getAddress() {
		return address;
	}
	public int getSleepTime() {
		return sleepTime;
	}
	public int getOpTime() {
		return opTime;
	}
	public void print()
	{
		System.out.println("\nClient" + getClientNum() + ":\n\tAddress = " + getAddress() + "\n\tPort = " + getPort() + 
			"\n\tSleepTime = " + getSleepTime() + "\n\tOpTime = " + getOpTime());
	}

}

class ConfigReader
{
	private int numClients;
	private ArrayList<ClientConfig> clients;
	private String multicastAddress;
	private int multicastPort;
	private int numAccesses;
	
	ConfigReader(String filePath)
	{
		clients = new ArrayList<ClientConfig>();
		
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
		
		//now read all the attributes in config file
		multicastAddress = sysProp.getProperty("Multicast.address");
		multicastPort = Integer.parseInt(sysProp.getProperty("Multicast.port"));
		numClients = Integer.parseInt(sysProp.getProperty("ClientNum"));
		numAccesses = Integer.parseInt(sysProp.getProperty("numberOfRequests"));
		
		//setup data structures so that they can be passed to the actual server
		//setup clients from config file
		for(int i = 1; i <= numClients; i++)
		{
			String clientKey = "Client" + i;
			String clientName = sysProp.getProperty(clientKey);	//client's name starts from 1 to n (not 0 to n-1)
			String clientPort = sysProp.getProperty(clientKey + ".port");
			clients.add(new ClientConfig(clientName, i, Integer.parseInt(clientPort)));// i is client's number
		}
	}


	public int getNumAccesses() {
		return numAccesses;
	}
	
	public int getNumClients() {
		return numClients;
	}


	public ArrayList<ClientConfig> getClients() {
		return clients;
	}
	
	public ClientConfig getClientConfig(int cNum)
	{
		return clients.get(cNum-1);
	}

	public String getMulticastAddress() {
		return multicastAddress;
	}


	public int getMulticastPort() {
		return multicastPort;
	}

}

class Formatter
{
	static PrintWriter fout;
	static ConfigReader cr = new ConfigReader("system.properties");
	static String outputFormat = "%9s\t%15s\t%12s\t%11s\n";
	
	static
	{
		try {
			fout = new PrintWriter(new FileWriter("request.log", false), true);
		}
		catch (IOException e) {
			System.err.println("File \"request.log\" can't be created, make sure you have access to the direcctory.");
			e.printStackTrace();	//DEBUG
		}
		
		writeHeader();
	}
	
	private static void writeHeader() 
	{
		fout.println("GROUP MEMBER: " + cr.getNumClients());
		fout.println("# of each Member'sRequst : " + cr.getNumAccesses());
		fout.println();
		fout.format(outputFormat, "Member ID", "Sequence Vector", "Token Vector", "Token Queue");
		fout.format(outputFormat, "=========", "===============", "============", "===========");
	}
	
	static void print(int clientID, int[] seqVector, Token token)
	{
		//get string representation of objects passed
		
		//sequenceVector and tokenVector
		StringBuffer seqVectorString = new StringBuffer();
		StringBuffer tokenVectorString = new StringBuffer();
		for(int i = 0; i < seqVector.length; i++)
		{
			seqVectorString.append(seqVector[i] + " ");
			tokenVectorString.append(token.tokenVector[i] + " ");
		}
		seqVectorString.deleteCharAt(seqVectorString.length() - 1);	//removing the last " " (space)
		tokenVectorString.deleteCharAt(tokenVectorString.length() - 1);//removing the last " " (space)
		
		//tokenQueue
		StringBuffer tokenQueueString = new StringBuffer();
		Integer[] tokenQueueArray = (Integer[])token.tokenQueue.toArray();
		if(tokenQueueArray.length > 0)
		{
			for(int i = 0; i < tokenQueueArray.length; i++)
			{
				tokenQueueString.append(tokenQueueArray[i] + ",");
			}		
			tokenQueueString.deleteCharAt(tokenQueueString.length() - 1);//removing the last " " (space)
		}
		else
		{
			tokenQueueString.append("NULL");
		}
		
		System.out.format(outputFormat, clientID, seqVectorString.toString(), tokenVectorString.toString(), tokenQueueString.toString());
	}
}