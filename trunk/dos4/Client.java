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
	private AtomicInteger mySequenceNum;	//number of times process has requested critical section
	private int numAccesses;
	private int[] sequenceVector;	//best known information about other processes
	private ClientConfig myConfig;	//my own sleepTime, opTime, name, etc.
	private String multiCastAddress;
	private int multiCastPort;
	private MulticastSocket multiSocketReceiver;
	private MulticastSocket multiSocketSender;
	private Socket unicastSender;
	private ServerSocket unicastReceiver;
	private Token myToken = null;
	private Object csExecuted;
	private Object tokenWanted;
	private boolean allExecuted = false;
	private ConfigReader cr;
	private Object tokenLost;
	
	public Client(int myID)
	{
		//read rest of the values from ConfigReader class
		cr = new ConfigReader("system.properties");
		multiCastAddress = cr.getMulticastAddress();
		multiCastPort = cr.getMulticastPort();
		numAccesses = cr.getNumAccesses();
		myConfig = cr.getClientConfig(myID);
		mySequenceNum = new AtomicInteger(0);	//should be 0 initially
		csExecuted = new Object();
		tokenWanted = new Object();
		tokenLost = new Object();
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
		System.out.println("DEBUG: Client running now...");
		Client aClient = new Client(Integer.parseInt(args[0]));	//args[0] is cNum (client's ID)
		aClient.setupMulticast();	//setup multicast address before starting either sending or receiving thread
//		System.out.println("DEBUG: Multicast all set.");
		aClient.setupUnicast();
//		System.out.println("DEBUG: Unicast all set.");

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
		
		System.out.println("DEBUG: All threads running now.");
	}

	private void setupUnicast()
	{
		try {
			unicastReceiver = new ServerSocket(myConfig.getPort(),0,InetAddress.getByName(myConfig.getAddress()));
			System.out.println("DEBUG: Unicast server running on address=" + unicastReceiver.getInetAddress().getHostName() + ", and port=" + unicastReceiver.getLocalPort());
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
			InetAddress group = InetAddress.getByName(multiCastAddress);
			
			multiSocketReceiver = new MulticastSocket(multiCastPort);
			multiSocketReceiver.joinGroup(group);
			multiSocketReceiver.setTimeToLive(1);
			
			multiSocketSender = new MulticastSocket(multiCastPort);
			multiSocketSender.joinGroup(group);
			multiSocketSender.setTimeToLive(1);
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
			//request token through multicast if you don't have one already			
			while(mySequenceNum.get() < numAccesses)
//			while(!allExecuted)
			{
				if(myToken != null)
				{
					//wait until someone else takes the token and then request
					synchronized(tokenLost)
					{
						System.out.println("DEBUG: waiting for someone to take my token...");
						try {tokenLost.wait();}
						catch (InterruptedException e) {}
//						System.out.println("DEBUG: someone took my token, I am good to go now...");
					}
				}
				//request a token if you don't have one
				try 
				{
					RequestMsg rm = new RequestMsg(myConfig.getClientNum(), mySequenceNum.get());
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					ObjectOutputStream oos = new ObjectOutputStream(bos);
					oos.writeObject(rm);
					byte[] data = bos.toByteArray();
					DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(multiCastAddress), multiCastPort);
					multiSocketSender.send(packet);
//					System.out.println("Client" + myConfig.getClientNum() + " - DEBUG: a multicast request has been sent");
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
				
				//just sleep for some random time before reattempting
				try {Thread.sleep(myConfig.getSleepTime());}
				catch (InterruptedException e) {/*Ignore*/}
				
				//wait until token is received
//				synchronized(csExecuted)
//				{
//					try {csExecuted.wait();}
//					catch (InterruptedException e) {/*Ignore*/}
//				}
				
				//once notified request the token again until numAccesses time
			}
			System.out.println("DEBUG: multicaster thread exited");
		}
		else if(Thread.currentThread().getName().equals("listener"))
		{
			while(!allExecuted)
			//listen until either I have a token or I am still looking for token from someone 
//			while(myToken != null || mySequenceNum.get() < numAccesses)
			{
				//listen to others' multicast request
				byte[] buffer = new byte[1000];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				try {
					multiSocketReceiver.receive(packet);
//					System.out.println("DEBUG: packet of length = " + packet.getLength() + "received.");
					ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
					ObjectInputStream ois = new ObjectInputStream(bis);
					RequestMsg receivedReq = (RequestMsg)ois.readObject();
					if(receivedReq.myIdentifier.equals("arpit"));
					{
						//update the sequence vector
						sequenceVector[receivedReq.clientID - 1] = Math.max(sequenceVector[receivedReq.clientID - 1], receivedReq.sequenceNum);
						
						//notify the Token Dealer thread about the reception of request
						synchronized(tokenWanted)
						{
							tokenWanted.notify();
						}
//						System.out.println("Client" + myConfig.getClientNum() + " - DEBUG: proper request received");
					}
				} catch (IOException e)
				{
					System.err.println("Error receiving datagram packet. **Exception = " + e.getMessage());
					e.printStackTrace();	//TODO - remove the stack trace
				} catch (ClassNotFoundException e) {
					System.err.println("Unknown multicast request received! **Exception = " + e.getMessage());
				}
			}
			System.out.println("DEBUG: listener thread exited");
		}
		else if(Thread.currentThread().getName().equals("unicast"))
		{
			//send and receive token through unicast
			while(!allExecuted)
//			while(myToken != null || mySequenceNum.get() < numAccesses)
			{
//				allExecuted = true;
//				//first of all check if everyone is done, if yes, break
//				for(int i = 0; i < sequenceVector.length; i++)
//				{
//					if(sequenceVector[i] < numAccesses)
//						allExecuted = false;
//				}
				
				//as token is received, if myToken is not null, 
				if(myToken != null)
				{
					sendToken();
				}
				else
				{
					receiveToken();					
				}
			}
			if(unicastReceiver != null)
			{
				try {
					unicastReceiver.close();
				} catch (IOException e) {}
			}
			System.out.println("DEBUG: unicast thread exited");
		}
		else
		{
			System.err.println("Invalid thread with name = " + Thread.currentThread().getName() + ". BAD!");
		}
	}

	private void receiveToken() 
	{
		//wait for others to send token
		try 
		{
			System.out.println("DEBUG: ready to accept tokens from other clients now");
			Socket myClient = unicastReceiver.accept();	//accept request from some other node trying to handover the token
			System.out.println("DEBUG: accepted a client connection");
			//now receive the token from accepted client
			ObjectInputStream ois = new ObjectInputStream(myClient.getInputStream());
			myToken = (Token) ois.readObject();
			
			//now execute critical section, i.e. write output to file
			Formatter.print(myConfig.getClientNum(), sequenceVector, myToken);
			//now sleep for the time required to complete the operation
			try {Thread.sleep(myConfig.getOpTime());}
			catch (InterruptedException e) {/*Ignore*/}
			System.out.println("DEBUG: ***CS EXECUTED***");
			//just increment the sequence number
			mySequenceNum.incrementAndGet();
			
			//delete my own value from token queue's head
			if(myConfig.getClientNum() != myToken.tokenQueue.remove())
			{
				System.err.println("Token sent to wrong client. BAD!");
			}
			//check if all the clients are done, if yes -> we can exit, 
			//otherwise wait until someone requests the token
			for(int i = 0 ; i < myToken.tokenVector.length; i++)
			{
				if(myToken.tokenVector[i] != numAccesses)
				{
					allExecuted = false;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void sendToken()
	{
		//wait until listener signals of a token reception
		synchronized(tokenWanted)
		{
			System.out.println("DEBUG: waiting for someone to request a token...");
			try {tokenWanted.wait();}
			catch (InterruptedException e) {/*Ignore*/}
			System.out.println("DEBUG: someone reqeusted a token. I am good to go now...");
		}
		
		//scan the Sequence Vector and find which process should receive the token now
//		allExecuted = true;	//everyone is done
		for(int i = 0; i < sequenceVector.length; i++)
		{
//			if(sequenceVector[i] < numAccesses)
//				allExecuted = false;	//even if one client was found who is not done, set allExecuted = false;

			if(sequenceVector[i] == myToken.tokenVector[i] + 1)
			{
				//append ith client to queue
				myToken.tokenQueue.add(i + 1);	//as clients start from 1 to 5							
				System.out.println("DEBUG: client " + (i+1) + " appended to the end of token queue");
			}
		}

		//send token to the first process in queue
		Integer nextOwnerClient = myToken.tokenQueue.peek();
		if(nextOwnerClient != null)
		{
			System.out.println("DEBUG: client " + nextOwnerClient + " retrieved from the front of token queue");
			ClientConfig nextOwnerConfig = cr.getClientConfig(nextOwnerClient.intValue());
			try {
				//wait for sometime so that receiver is initialized 
				Thread.sleep(500);	//TODO - find alternative
				System.out.println("DEBUG: sending token to address: " + nextOwnerConfig.getAddress() + ", and port : " + nextOwnerConfig.getPort());  
				unicastSender = new Socket(nextOwnerConfig.getAddress(), nextOwnerConfig.getPort());
				ObjectOutputStream oos = new ObjectOutputStream(unicastSender.getOutputStream());
				oos.writeObject(myToken);
				//make my own taken null after transferring
				//to ensure only one client at a time has token
				myToken = null;
				//and notify thread performing multicast that token is lost :)
				synchronized(tokenLost)
				{
					tokenLost.notify();
				}
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			finally
			{
				if(unicastSender != null)
				{
					try {unicastSender.close();}
					catch (IOException e) {}
				}
			}
		}
		System.out.println("DEBUG: All executed = " + allExecuted);
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
		//init token vector with all 0s
		for (int i = 0; i < tokenVector.length; i++)
		{
			tokenVector[i] = 0;
		}
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
