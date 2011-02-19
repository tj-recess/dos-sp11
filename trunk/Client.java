import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;


public class Client
{
	String cType;
	int cNum;
	int numAccesses;
	Socket myServer = null;
	int cSleepTime;
	
	public Client(String clientType, String clientNumber, String numAccesses)
	{
		cType = clientType;
		cNum = Integer.parseInt(clientNumber);
		this.numAccesses = Integer.parseInt(numAccesses);
		setup();
	}
	
	private void setup()
	{
		//read configuration from system.properties file
		Properties sysProp = new Properties();
		try {
			sysProp.load(new FileInputStream("system.properties"));
		} catch (FileNotFoundException e) {
			System.out.println("\"system.properties\" - File not found!");
		} catch (IOException ioex) {
			System.out.println("\"system.properties\" - IO Exception! " + ioex.getMessage());
		}
		
		//set client's sleep time
		System.out.println("DEBUG: RW." + cType + cNum + ".sleepTime = " + sysProp.getProperty("RW." + cType + cNum + ".sleepTime"));
		cSleepTime = Integer.parseInt(sysProp.getProperty("RW." + cType + cNum + ".sleepTime"));
	}
	
	public static void main(String[] args)
	{
		if(args.length < 5)
		{
			System.out.println("Usage : Client <reader | writer>  <clientNumber> <numAccesses> <serverhost> <serverport>");
			System.exit(-1);
		}
		System.out.println("My values receieved from start.java - " + args[0] + ", " + args[1] + ", " + args[2]);
		Client aClient = new Client(args[0], args[1], args[2]);
		//now try connecting to server

		try
		{
			System.out.println("DEBUG: received arguments from start.java = " + args[3] + ", " + args[4]);
			aClient.connectToServer(args[3], args[4]);
		}
		catch (NumberFormatException e) {
			System.out.println("Client : bad port received from start.java, exception = " + e.toString());
			System.exit(-2);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(-2);
		} catch (IOException ioex) {
			System.out.println("Client : can't start connection with start.java, exception = " + ioex.toString());
			System.exit(-2);
		}
		aClient.talkOnSocket();
	}

	private void connectToServer(String host, String port) throws NumberFormatException, UnknownHostException, IOException
	{
		myServer = new Socket(host, Integer.parseInt(port));
	}

	private void talkOnSocket()
	{
		try {
			System.out.println("Connected to Server, now attempting to get streams...");
			ObjectOutputStream oos = new ObjectOutputStream(myServer.getOutputStream());
			ObjectInputStream ois = new ObjectInputStream(myServer.getInputStream());
			System.out.println("DEBUG: Got streams from server's socket, now sending data");
			if(cType.equals("reader"))
			{
				/*
				 * send request type (read) to server, receive request token from server,
				 * wait for cSleepTime and then request again upto numAccesses time
				 */
				for (int i = 0; i < numAccesses; i++)
				{
					oos.writeObject("read");
					oos.writeInt(cNum);
					oos.flush();
					System.out.println("DEBUG: sent data to server, waiting for response");
					int myRequestNum = ois.readInt();
					int valueReceived = ois.readInt();
					int myServiceNum = ois.readInt();
					System.out.println("Client " + cType + cNum + ": RequestNum = " + myRequestNum 
							+ ", value Received = " + valueReceived + ", ServiceNum = " + myServiceNum);
					try
					{
						Thread.sleep(cSleepTime);
					}catch(InterruptedException iex)
					{
						System.out.println("Thread interrupted : " + cType + cNum);
					}
					//TODO: print all three things in proper format
				}
				
				
			}
			else if(cType.equals("writer"))
			{
				/*
				 * send request type (write) and value to server, receive request token from server,
				 * wait for cSleepTime and then receive service token from server and then
				 * request again upto numAccesses time
				 */
				for(int i = 0; i < numAccesses; i++)
				{
					oos.writeObject("write");
					oos.writeInt(cNum);
					oos.flush();
					System.out.println("DEBUG: sent data to server, waiting for response");
					int myRequestNum = ois.readInt();
					int myServiceNum = ois.readInt();
					System.out.println("Client " + cType + cNum + ": RequestNum = " + myRequestNum 
							+ ", ServiceNum = " + myServiceNum);
					try
					{
						Thread.sleep(cSleepTime);
					}catch(InterruptedException iex)
					{
						System.out.println("Thread interrupted : " + cType + cNum);
					}
					//TODO: print all three things in proper format
				}

			}
			else
				System.out.println("ERROR: Unknown Client Type - " + cType);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
