import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

public class Client
{
	private String cType;
	private int cNum;
	private int numAccesses;
	private int cSleepTime;
	private Crew serverProxy;
	private PrintWriter fout = null;
	
	public Client(String clientType, String clientNumber, String numAccesses, String cSleepTime)
	{
		cType = clientType;
		cNum = Integer.parseInt(clientNumber);
		this.numAccesses = Integer.parseInt(numAccesses);
		this.cSleepTime = Integer.parseInt(cSleepTime);
	}
	
	public static void main(String[] args)
	{
		if(args.length < 6)
		{
			System.out.println("Usage : Client <reader | writer>  <clientNumber> <numAccesses> <sleepTime> <serverhost> <rmiPort>");
			System.exit(-1);
		}
		System.out.println("My values receieved from start.java - " + args[0] + ", " + args[1] + ", " + args[2] + ", " + args[3] + ", " + args[4] + ", " + args[5]);
		Client aClient = new Client(args[0], args[1], args[2], args[3]);
		//now try connecting to server

//		System.out.println("DEBUG: received arguments from start.java = " + args[3] + ", " + args[4]);
		try {
			aClient.getProxy(args[4], args[5]);
		} catch (AccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		aClient.talkViaRMI();
	}

	private void getProxy(String hostname, String rmiPort) throws AccessException, RemoteException, NotBoundException
	{
		serverProxy = (Crew)LocateRegistry.getRegistry(hostname, Integer.parseInt(rmiPort)).lookup("arpit");
	}


	private void talkViaRMI()
	{
//		System.out.println("Connected to Server, now attempting to get streams...");
		if(cType.equals("reader"))
			readData();
		else if(cType.equals("writer"))
			writeData();
		else
			System.out.println("ERROR: Unknown Client Type - " + cType);	
	}

	private void writeData()
	{
		/*
		 * send request type (write) and value to server, receive request token from server,
		 * wait for cSleepTime and then receive service token from server and then
		 * request again upto numAccesses time
		 */
		try{fout = new PrintWriter(new FileWriter("W" + cNum +".log"), true);}
		catch(IOException ioex){System.out.println("Can't write to log file at Client " + "W" + cNum + ".log" + "**Exception** : " + ioex.toString());}
		//print headers for output
		fout.println("Client type: Writer");
		fout.println("Client Name: " + cNum);
		String format = "%16s\t%16s%n";
		fout.format(format, "Request Sequence", "Service Sequence");
		fout.format(format, "----------------", "----------------");
		for(int i = 0; i < numAccesses; i++)
		{
			ReplyPacket myPacket = null;
			try {
				myPacket = serverProxy.writeData(cNum);
			} catch (RemoteException e) {
				System.err.println("DEBUG (Writer) : couldn't read data from server, **Exception.");
				e.printStackTrace();
			}
			
			if(myPacket == null)
			{	
				System.err.println("Couldn't write data in first attempt, trying again!");
				continue;
			}
//			System.out.println("DEBUG:Client(Reader) sent data to server " + i + "time, waiting for response");
			int myRequestNum = myPacket.getRequestNum();
			int myServiceNum = myPacket.getServiceNum();
			//print received values in proper format
			System.out.println("DEBUG:Client(Writer) myPacket recvd. values are -req =  " + myRequestNum + ", servNum = " + myServiceNum);
			fout.format(format, myRequestNum, myServiceNum);
			
			//now call printOutput to indicate I have received values
			try {
				serverProxy.printOutput();
			} catch (RemoteException e) 
			{
				System.out.println("DEBUG(Client): couldn't invoke printOutput bcoz of exception: ex = " + e.getMessage());
			}
			
			try{Thread.sleep(cSleepTime);}
			catch(InterruptedException iex){/*Ignore*/}
		}		
	}

	private void readData()
	{
		/*
		 * send request type (read) to server, receive request token from server,
		 * wait for cSleepTime and then request again upto numAccesses time
		 */
		try{fout = new PrintWriter(new FileWriter("R" + cNum +".log"), true);}
		catch(IOException ioex){System.out.println("Can't write to log file at Client " + "R" + cNum + ".log" + "**Exception** : " + ioex.toString());}
		//writing headers for output
		fout.println("Client type: Reader");
		fout.println("Client Name: " + cNum);
		String format = "%16s\t%16s\t%12s%n";
		fout.format(format, "Request Sequence", "Service Sequence", "Object Value");
		fout.format(format, "----------------", "----------------", "------------");
		for (int i = 0; i < numAccesses; i++)
		{
			ReplyPacket myPacket = null;
			try {
				myPacket = serverProxy.readData(cNum);
			} catch (RemoteException e) {
				System.err.println("DEBUG (Reader) : couldn't read data from server, **Exception.");
				e.printStackTrace();
			}
			if(myPacket == null)
			{	
				System.err.println("No data received in first attempt, trying again!");
				continue;
			}
			
			int myRequestNum = myPacket.getRequestNum();
			int valueReceived = myPacket.getSharedObjectVal();
			int myServiceNum = myPacket.getServiceNum();
			//print all three things in proper format
			System.out.println("DEBUG:Client(Reader) myPacket recvd. values are -req =  " + myRequestNum + ", val = " +  valueReceived + ", servNum = " + myServiceNum);
			fout.format(format, myRequestNum, myServiceNum, valueReceived);
			
			//now call printOutput to indicate I have received values
			try {
				serverProxy.printOutput();
			} catch (RemoteException e) 
			{
				System.out.println("DEBUG(Client): couldn't invoke printOutput bcoz of exception: ex = " + e.getMessage());
			}
			
			try{Thread.sleep(cSleepTime);}
			catch(InterruptedException iex){/*Ignore*/}					
		}
	}
} 
