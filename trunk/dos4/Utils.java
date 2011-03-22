import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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
		numClients = Integer.parseInt("ClientNum");
		numAccesses = Integer.parseInt(sysProp.getProperty("numberOfRequests"));
		
		//setup data structures so that they can be passed to the actual server
		//setup clients from config file
		for(int i = 0; i < numClients; i++)
		{
			String clientKey = "Client" + (i + 1);
			String clientName = sysProp.getProperty(clientKey);	//client's name starts from 1 to n (not 0 to n-1)
			String clientPort = sysProp.getProperty(clientKey + ".port");
			clients.add(new ClientConfig(clientName, i+1, Integer.parseInt(clientPort)));// (i+1) is client's number
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
		return clients.get(cNum);
	}

	public String getMulticastAddress() {
		return multicastAddress;
	}


	public int getMulticastPort() {
		return multicastPort;
	}

}