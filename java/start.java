import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

public class start {

	//config params
	private int numReaders;
	private int numWriters;

	private String server;
	private int serverPort;
	private ServerSocket me;
	private Socket actualServer;
	
	/**
	 * @param args nothing to be done
	 */
	public static void main(String[] args)
	{
		start s = new start();
		s.setup();
			
	}

	public void setup()
	{
		//read configuration from system.properties file
		Properties sysProp = new Properties();
		try {
			sysProp.load(new FileInputStream("system.properties"));
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
		
		int numAccesses = Integer.parseInt(sysProp.getProperty("RW.numberOfAccesses"));

		
		//start the server of start.java which starts actual server
		serverPort = startServerAndGetPort();
		
		//now start clients on remote machines
		String path = System.getProperty("user.dir"); // get current directory of the user
		
		for(int i = 1; i <= numReaders; i++)
		{
			String readerName = sysProp.getProperty("RW.reader" + (i));	//reader/writer's name starts from 1 to n (not 0 to n-1)
			try {
				Process remote = Runtime.getRuntime().exec("ssh " + readerName + " cd " + path + " ; java Client reader " + i + " " + numAccesses + " " + server + " " + serverPort);
				//new Thread(new ClientOutputStreamReader(remote, readerName, "input")).start();
				new Thread(new ClientOutputStreamReader(remote, readerName, "error")).start();
			} catch (IOException e) {
				System.err.println("Can't start remote reader client : " + readerName );
			}
		}
		
		for(int i = numReaders+1; i <= numReaders + numWriters; i++)
		{
			String writerName = sysProp.getProperty("RW.writer" + i);	//reader/writer's name starts from (numReaders + 1) to n (not 0 to n-1)
			try {
				Process remote = Runtime.getRuntime().exec("ssh " + writerName + " cd " + path + " ; java Client writer " + i + " " + numAccesses + " " + server + " " + serverPort);
				//new Thread(new ClientOutputStreamReader(remote, writerName, "input")).start();
				new Thread(new ClientOutputStreamReader(remote, writerName, "error")).start();
			} catch (IOException e) {
				System.err.println("Can't start remote writer client : " + writerName);
			}
		}
//		System.out.println("start.java (DEBUG) : I'm done, you guys play now :-)");
	}
	
	private int startServerAndGetPort() 
	{	
		int serverPort = 0; 
		try
		{
			me = new ServerSocket(0,0,InetAddress.getLocalHost());	
			//0 passed to ensure server starts on some system assigned port
			
			//now start.java should be ready to accept
			new Thread(new Runnable(){
					public void run()
					{ 
						try {
							actualServer = me.accept();
						} catch (IOException e) {
							System.out.println("start.java : not able to connect to actual server. **Exception - " + e.getMessage());
							System.exit(-1);
						}
					}
			}).start();
			
			//first start client (actual server)
			String path = System.getProperty("user.dir");
//			System.out.println("me.getInetAddress().getHostName(), me.getLocalPort()" + me.getInetAddress().getHostName() + ", " + me.getLocalPort());
			Process actualServerProcess = Runtime.getRuntime().exec("ssh " + server + " cd " + path + " ; javac Server.java ; java Server " + me.getInetAddress().getHostName() + " " + me.getLocalPort());
			new Thread(new ClientOutputStreamReader(actualServerProcess, "", "input")).start();	//leaving 2nd arg blank to 
			new Thread(new ClientOutputStreamReader(actualServerProcess, "", "error")).start();	//avoid printing any name before output
					
			System.out.print("Trying to establish connection with Actual server " + server + "...");
			while(actualServer == null)
			{
				System.out.print(".");
				try{Thread.sleep(1000);}	//hoping to connect pretty soon here, otherwise will have to wait()
				catch(InterruptedException iex){/*ignore*/}
			}
			System.out.println("Connected!");	//just a line break to help others in displaying neat output
			ObjectInputStream ois = new ObjectInputStream(actualServer.getInputStream());
//			System.out.println("DEBUG:Got OIS, now waiting for server to send port");
			serverPort = (Integer)ois.readObject();
//			System.out.println("DEBUG:Got port = "+ serverPort);
			
		}
		catch(ClassNotFoundException cnfe){
			System.err.println("Unidentified data received from actual server " + server + "; Exiting!");
//			cnfe.printStackTrace();
			System.exit(-1);}	//DEBUG
		catch(IOException ioex)
		{
			System.err.println("start.java :FATAL: can't start communicating with remote host : " + server + " **Exception : " + ioex.toString());
//			ioex.printStackTrace();	//DEBUG
			System.exit(-1);
		}
		return serverPort;
	}

	class ClientOutputStreamReader implements Runnable
	{
		Process clientProcID;
		String outputType;
		String clientName;
		String prefix;
		BufferedReader clientReader = null;
		public ClientOutputStreamReader(Process clientProcID, String clientName, String outputType)
		{
			this.clientProcID = clientProcID;
			this.clientName = clientName;
			this.outputType = outputType;
			if(clientName.equals(""))
				prefix = "";
			else
				prefix = clientName + ": ";
		}
		
		
		public void run()
		{
			try
			{
				if(outputType.equals("input"))
					clientReader = new BufferedReader(new InputStreamReader(clientProcID.getInputStream()));
				else
					clientReader = new BufferedReader(new InputStreamReader(clientProcID.getErrorStream()));
				String output = null;					
				while((output = clientReader.readLine()) != null)
				synchronized(ClientOutputStreamReader.class)
				{
					System.out.println(prefix + output);
				}
				
				int exitVal = 0;
				try {exitVal = clientProcID.waitFor();}
				catch (InterruptedException e) {/*Ignore*/}
				Runtime.getRuntime().exec("ssh " + clientName + " skill java");
				if(exitVal != 0)
					Runtime.getRuntime().exec("skill java");
			}
			catch(IOException ex)
			{
				/*Ignore*/
				ex.printStackTrace();	//DEBUG
			}
		}
	}
}
