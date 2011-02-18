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
			System.err.println("\"system.properties\" - File not found!");
		} catch (IOException ioex) {
			System.err.println("\"system.properties\" - IO Exception! " + ioex.getMessage());
		}
		
		//set client's sleep time
		System.out.println("DEBUG: " + sysProp.getProperty("RW." + cType + cNum + ".sleepTime"));
		cSleepTime = Integer.parseInt(sysProp.getProperty("RW." + cType + cNum + ".sleepTime"));
	}
	
	public static void main(String[] args)
	{
		if(args.length < 5)
		{
			System.err.println("Usage : Client <reader | writer>  <clientNumber> <numAccesses> <serverhost> <serverport>");
			System.exit(-1);
		}
		
		Client aClient = new Client(args[0], args[1], args[2]);
		//now try connecting to server

		try
		{
			aClient.connectToServer(args[3], args[4]);
		}
		catch (NumberFormatException e) {
			System.err.println("Client : bad port received from start.java, exception = " + e.toString());
			System.exit(-2);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(-2);
		} catch (IOException ioex) {
			System.err.println("Client : can't start connection with start.java, exception = " + ioex.toString());
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
			ObjectInputStream ois = new ObjectInputStream(myServer.getInputStream());
			ObjectOutputStream oos = new ObjectOutputStream(myServer.getOutputStream());
			
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
					int myRequestNum = ois.readInt();
					int valueReceived = ois.readInt();
					int myServiceNum = ois.readInt();

					try
					{
						Thread.sleep(cSleepTime);
					}catch(InterruptedException iex)
					{
						System.out.println("Thread interrupted : " + cType + cNum);
					}
					//TODO: print all three things in proper format
					System.out.println("Client " + cType + cNum + ": RequestNum = " + myRequestNum 
							+ ", value Received = " + valueReceived + ", ServiceNum = " + myServiceNum);
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
					int myRequestNum = ois.readInt();
					int myServiceNum = ois.readInt();
					try
					{
						Thread.sleep(cSleepTime);
					}catch(InterruptedException iex)
					{
						System.out.println("Thread interrupted : " + cType + cNum);
					}
					//TODO: print all three things in proper format
					System.out.println("Client " + cType + cNum + ": RequestNum = " + myRequestNum 
							+ ", ServiceNum = " + myServiceNum);
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
