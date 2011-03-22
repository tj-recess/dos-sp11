import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
	private AtomicInteger mySequenceNum;	//number of times process has requested critical section
	private int numAccesses;
	private int[] sequenceVector;	//best known information about other processes
	private ClientConfig myConfig;	//my own sleepTime, opTime, name, etc.
	private String multiCastAddress;
	private AtomicInteger state;	// 0 = multicast, 1 = listener, 2 = unicast
	private int multiCastPort;
	private MulticastSocket socket;
	private Token myToken = null;
	
	public Client(int myID)
	{
		//read rest of the values from ConfigReader class
		ConfigReader cr = new ConfigReader("system.properties");
		multiCastAddress = cr.getMulticastAddress();
		multiCastPort = cr.getMulticastPort();
		numAccesses = cr.getNumAccesses();
		myConfig = cr.getClientConfig(myID);
		mySequenceNum = new AtomicInteger(0);	//should be 0 initially
		int totalClients = cr.getNumClients();
		sequenceVector = new int[totalClients];
		for (int i = 0; i < totalClients; i++)
			sequenceVector[i] = 0;
		if(myID == 1)	//initially only client 1 gets the token
			myToken = new Token(totalClients);
		else
			myToken = null;
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
			if(myToken == null)
			{
				try {
					myToken.tokenVector[myConfig.getClientNum() - 1] = mySequenceNum.incrementAndGet();
					socket.setTimeToLive(1);
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					ObjectOutputStream oos = new ObjectOutputStream(bos);					
					oos.writeObject(myToken);
					byte[] data = bos.toByteArray();
					DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(multiCastAddress), multiCastPort);
					socket.send(packet);
					System.out.println("Client" + myConfig.getClientNum() + " - DEBUG: a token sent");
				}
				catch (UnknownHostException uhex)
				{
					System.err.println("No host with address " + multiCastAddress + ". **Exception : " + uhex.getMessage());
				}
				catch (IOException e) 
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		else if(state.getAndIncrement() == 1)
		{
			//listen to others request
			byte[] buffer = new byte[10000];
			DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
			try {
				socket.receive(packet);
				ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
				ObjectInputStream ois = new ObjectInputStream(bis);
				Token receivedToken = (Token)ois.readObject();
				if(receivedToken.myIdentifier.equals("arpit"));
				{
					//do something with token
					System.out.println("Client" + myConfig.getClientNum() + " - DEBUG: proper token received");
				}
			} catch (IOException e)
			{
				System.err.println("Error receiving datagram packet. **Exception = " + e.getMessage());
			} catch (ClassNotFoundException e) {
				System.err.println("Unknown object received! **Exception = " + e.getMessage());
			}			
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
	String myIdentifier = "arpit";
	int[] tokenVector;
	Queue<Integer> tokenQueue;
	
	public Token(int vectorSize)
	{
		tokenVector = new int[vectorSize];
		tokenQueue = new ConcurrentLinkedQueue<Integer>();
	}
	public Token(int vectorSize, String identifier)
	{
		tokenVector = new int[vectorSize];
		tokenQueue = new ConcurrentLinkedQueue<Integer>();
		myIdentifier = identifier;
	}
}