import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
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
	private String multiCastAddress;
	private int multiCastPort;
	private MulticastSocket multiSocket;
	private Socket unicastSender;
	private ServerSocket unicastReceiver;
	private Token myToken = null;
	private Object csExecuted;
	private Object tokenReceived;
	private boolean allExecuted = false;
	private ConfigReader cr;
	
	public Client(int myID)
	{
		//read rest of the values from ConfigReader class
		cr = new ConfigReader("system.properties");
		multiCastAddress = cr.getMulticastAddress();
		multiCastPort = cr.getMulticastPort();
		numAccesses = cr.getNumAccesses();
		myConfig = cr.getClientConfig(myID);
		mySequenceNum = 0;	//should be 0 initially
		csExecuted = new Object();
		int totalClients = cr.getNumClients();
		sequenceVector = new int[totalClients];
		for (int i = 0; i < totalClients; i++)
			sequenceVector[i] = 0;
		if(myID == 1)	//initially only client 1 gets the token
			myToken = new Token(totalClients);
		else
			myToken = null;
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
		aClient.setupUnicast();

		//create 3 threads, according to the value of state, automatically threads will acquire  
		//the role of multicast, listener or unicast
		Thread[] t = new Thread[3];
		for (int i = 0; i < t.length; i++)
			t[i] = new Thread(aClient);
		t[0].setName("listener");
		t[1].setName("multicast");
		t[2].setName("unicast");
		for (int i = 0; i < t.length; i++)
			t[i].start();
		
	}

	private void setupUnicast()
	{
		try {
			unicastReceiver = new ServerSocket(0,0,InetAddress.getLocalHost());
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//0 passed to ensure server starts on some system assigned port
	}

	private void setupMulticast()
	{
		try {
			multiSocket = new MulticastSocket(multiCastPort);
			InetAddress group = InetAddress.getByName(multiCastAddress);
			multiSocket.joinGroup(group);
			multiSocket.setTimeToLive(1);
		} catch (IOException ioex) {
			// TODO Auto-generated catch block
			ioex.printStackTrace();
		}		
	}

	@Override
	public void run()
	{
		if(Thread.currentThread().getName().equals("multicast"))
		{
			//request token through multicast if you don't have token already
			int tokenRequested = 0;
			while(tokenRequested < numAccesses)
			{
				//request a token
				try {
					RequestMsg rm = new RequestMsg(myConfig.getClientNum(), ++mySequenceNum);
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					ObjectOutputStream oos = new ObjectOutputStream(bos);
					oos.writeObject(rm);
					byte[] data = bos.toByteArray();
					DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(multiCastAddress), multiCastPort);
					multiSocket.send(packet);
					System.out.println("Client" + myConfig.getClientNum() + " - DEBUG: a request has been sent");
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
				
				//wait until token is received
				synchronized(csExecuted)
				{
					try {csExecuted.wait();}
					catch (InterruptedException e) {/*Ignore*/}
				}
				
				//once notified request the token again until numAccesses time
			}
		}
		else if(Thread.currentThread().getName().equals("listener"))
		{
			while(!allExecuted)
			{
				//listen to others' multicast request
				byte[] buffer = new byte[64];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				try {
					multiSocket.receive(packet);
					ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
					ObjectInputStream ois = new ObjectInputStream(bis);
					RequestMsg receivedReq = (RequestMsg)ois.readObject();
					if(receivedReq.myIdentifier.equals("arpit"));
					{
						//update the sequence vector
						sequenceVector[receivedReq.clientID - 1] = Math.max(sequenceVector[receivedReq.clientID - 1], receivedReq.sequenceNum);
						
						//notify the Token Dealer thread about the reception of request
						synchronized(tokenReceived)
						{
							tokenReceived.notify();
						}
						System.out.println("Client" + myConfig.getClientNum() + " - DEBUG: proper request received");
					}
				} catch (IOException e)
				{
					System.err.println("Error receiving datagram packet. **Exception = " + e.getMessage());
				} catch (ClassNotFoundException e) {
					System.err.println("Unknown object received! **Exception = " + e.getMessage());
				}
			}
		}
		else if(Thread.currentThread().getName().equals("unicast"))
		{
			//send and receive token through unicast
			//wait until listener signals of a token reception
			synchronized(tokenReceived)
			{				
				try {tokenReceived.wait();}
				catch (InterruptedException e) {/*Ignore*/}
				
				//as token is received, if myToken is not null, 
				if(myToken != null)
				{
					//scan the Sequence Vector and find which process should receive it now
					for(int i = 0; i < sequenceVector.length; i++)
					{
						if(sequenceVector[i] == myToken.tokenVector[i] + 1)
						{
							//append ith client to queue
							myToken.tokenQueue.add(i);
						}
					}
					//send token to the first process in queue
					Integer nextOwnerClient = myToken.tokenQueue.peek();
					if(nextOwnerClient != null)
					{
						ClientConfig nextOwnerConfig = cr.getClientConfig(nextOwnerClient.intValue()); 
						try {
							unicastSender = new Socket(nextOwnerConfig.getAddress(), nextOwnerConfig.getPort());
							ObjectOutputStream oos = new ObjectOutputStream(unicastSender.getOutputStream());
							oos.writeObject(myToken);
							myToken = null;		//make my own taken null after transferring 
												//to ensure only one client at a time has token
						} catch (UnknownHostException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} 
					}
				}
				else
				{
					//wait for others to send token
					try 
					{
						Socket myClient = unicastReceiver.accept();	//accept request from some other node trying to handover the token
						//now receive the token from accepted client
						ObjectInputStream ois = new ObjectInputStream(myClient.getInputStream());
						myToken = (Token) ois.readObject();
						
						//now execute critical section
						
						
						//delete my own value from token queue's head
						if(myConfig.getClientNum() != myToken.tokenQueue.remove())
						{
							System.err.println("Token sent to wrong client. BAD!");
						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
			}
			
		}
		else
		{
			System.err.println("Invalid thread with name = " + Thread.currentThread().getName() + ". BAD!");
		}
	}
}

class Token implements Serializable
{
	private static final long serialVersionUID = 3043525584730649838L;
	int[] tokenVector;
	ConcurrentLinkedQueue<Integer> tokenQueue;
	
	public Token(int vectorSize)
	{
		tokenVector = new int[vectorSize];
		tokenQueue = new ConcurrentLinkedQueue<Integer>();
	}
}

class RequestMsg implements Serializable
{
	private static final long serialVersionUID = 1L;
	int clientID;
	int sequenceNum;
	String myIdentifier;
	public RequestMsg(int cNum, int seqNum)
	{
		clientID = cNum;
		sequenceNum = seqNum;
		myIdentifier = "arpit";
	}
}