import java.io.IOException;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;


public class Client implements Runnable
{
	private int mySequenceNum;	//number of times process has requested critical section
	private int numAccesses;
	private int[] sequenceVector;	//best known information about other processes
	private ClientConfig myConfig;	//my own sleepTime, opTime, name, etc.
	private boolean token;
	private String multiCastAddress;
	private AtomicInteger state;	// 0 = multicast, 1 = listener, 2 = unicast
	private int multiCastPort;
	private MulticastSocket socket; 
	
	public Client(int myID)
	{
		if(myID == 1)	//initially only client 1 gets the token
			token = true;
		else
			token = false;

		//read rest of the values from ConfigReader class
		ConfigReader cr = new ConfigReader("system.properties");
		multiCastAddress = cr.getMulticastAddress();
		multiCastPort = cr.getMulticastPort();
		numAccesses = cr.getNumAccesses();
		myConfig = cr.getClientConfig(myID);
		mySequenceNum = 0;	//should be 0 initially
		int totalClients = cr.getNumClients();
		sequenceVector = new int[totalClients];
		for (int i = 0; i < totalClients; i++)
			sequenceVector[i] = 0;
		state = new AtomicInteger(0);
	}
	
	public static void main(String[] args)
	{
		if(args.length < 1)
		{
			System.err.println("Usage : java Client <ID_of_Client>");
			System.exit(-1);
		}
		Client aClient = new Client(Integer.parseInt(args[0]));	//args[0] is cNum (client's ID)
		aClient.setupMulticast();	//setup multicast address before starting either sending or receiving thread

		//create 3 threads, according to the value of state, automatically threads will acquire  
		//the role of multicast, listener or unicast
		for(int i = 0; i < 3; i++)
			new Thread(aClient).start();
	}

	private void setupMulticast()
	{
		try {
			socket = new MulticastSocket(multiCastPort);
			InetAddress group = InetAddress.getByName(multiCastAddress);
			socket.joinGroup(group);
		} catch (IOException ioex) {
			// TODO Auto-generated catch block
			ioex.printStackTrace();
		}		
	}

	@Override
	public void run()
	{
		if(state.getAndIncrement() == 0)
		{
			//request token through multicast if you don't have token already
			if(!token)
			{
				StringBuffer requestMsg = new StringBuffer("arpit");
				
			}
						
			
			byte[] data = new byte[1000];
			try {
				DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(multiCastAddress), multiCastPort);
			} catch (UnknownHostException uhex)
			{
				System.err.println("No host with address " + multiCastAddress + ". **Exception : " + uhex.getMessage());
			}
		}
		else if(state.getAndIncrement() == 1)
		{
			//listen to others request
		}
		else if(state.getAndIncrement() == 2)
		{
			//send and receive token through unicast
		}
		else
		{
			System.err.println("Failed to maintain \"state\". BAD!");
		}
			
		
	}
}

class Token implements Serializable
{
	private static final long serialVersionUID = 3043525584730649838L;
	int[] tokenVector;
	Queue<Integer> tokenQueue;
	
	public Token(int vectorSize)
	{
		tokenVector = new int[vectorSize];
		tokenQueue = new ConcurrentLinkedQueue<Integer>();
	}
}