import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class start {

	//config params
	private int numReaders;
	private int numWriters;
	private ArrayList<RW> readers;
	private ArrayList<RW> writers;
	private String server;
	int numAccesses;
	int rmiPort;
	private ServerSocket me;
	private Socket actualServer;
	private Thread[] allWriterThreads;
	private Thread[] allReaderThreads;
	private Thread[] serverThreads;
	
	/**
	 * @param args nothing to be done
	 */
	public static void main(String[] args)
	{
		start s = new start();
		s.setup();
		
		//start the server of start.java which starts actual server
		//if it's not started properly then exit, don't instantiate clients
		if(!s.startServerRemotely())
			System.exit(-10);

		//now start clients on remote machines
		s.startReaders();		
		s.startWriters();
//		System.out.println("start.java (DEBUG) : I'm done, you guys play now :-)");
//		s.joinThreads();
	}
	
	private void joinThreads()
	{
		try{
		//join all the threads before exiting so that we keep getting server's output
		for(int i = 0; i < allWriterThreads.length; i++)
			allWriterThreads[i].join();

		for(int i = 0; i < allReaderThreads.length; i++)
			allReaderThreads[i].join();
		serverThreads[0].join();
		serverThreads[1].join();
		}catch(InterruptedException iex){/*ignore*/}	
	}

	public void setup()
	{
		ConfigReader conf = new ConfigReader("system.properties");
		numReaders = conf.getNumReaders();
		numWriters = conf.getNumWriters();
		readers = conf.getReaders();
		writers = conf.getWriters();
		numAccesses = conf.getNumAccesses();
		server = conf.getServer();
		rmiPort = conf.getRmiPort();
//		System.out.println("DEBUG: start.java: rmiPort = " + rmiPort);
		allWriterThreads = new Thread[numWriters*2];
		allReaderThreads = new Thread[numReaders*2];
		serverThreads = new Thread[2];
	}
	
	private void startWriters()
	{
		String path = System.getProperty("user.dir"); // get current directory of the user
		for(int i = 0; i < numWriters; i++)
		{
			RW aWriter = writers.get(i);
			String writerName = aWriter.getName();	//reader/writer's name starts from (numReaders + 1) to n (not 0 to n-1)
			int cNum = i + 1 + numReaders;
			try {
				Process remote = Runtime.getRuntime().exec("ssh " + writerName + " cd " + path + " ; java Client writer " + cNum + " " + numAccesses + " " + aWriter.getSleepTime() + " " + server + " " + rmiPort);
				allWriterThreads[i*2] = new Thread(new ClientOutputStreamReader(remote, writerName, "input"));
//				allWriterThreads[i*2].start();
				allWriterThreads[i*2 + 1] = new Thread(new ClientOutputStreamReader(remote, writerName, "error"));
//				allWriterThreads[i*2 + 1].start();
			} catch (IOException e) {
				System.err.println("Can't start remote writer client : " + writerName);
			}
		}		
	}

	private void startReaders()
	{
		String path = System.getProperty("user.dir"); // get current directory of the user

		for(int i = 0; i < numReaders; i++)
		{
			RW aReader = readers.get(i);
			String readerName = aReader.getName();	//reader/writer's name starts from 1 to n (not 0 to n-1)
			int cNum = i + 1;
			try {
				Process remote = Runtime.getRuntime().exec("ssh " + readerName + " cd " + path + " ; java Client reader " + cNum + " " + numAccesses + " " + aReader.getSleepTime() + " " + server + " " + rmiPort);
				allReaderThreads[i*2] = new Thread(new ClientOutputStreamReader(remote, readerName, "input"));
//				allReaderThreads[i*2].start();
				allReaderThreads[i*2 + 1] = new Thread(new ClientOutputStreamReader(remote, readerName, "error"));
//				allReaderThreads[i*2 + 1].start();
			} catch (IOException e) {
				System.err.println("Can't start remote reader client : " + readerName );
			}
		}		
	}

	private boolean startServerRemotely() 
	{	 
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
			serverThreads[0] = new Thread(new ClientOutputStreamReader(actualServerProcess, "", "input"));	//leaving 2nd arg blank to 
			serverThreads[0].start();
			serverThreads[1] = new Thread(new ClientOutputStreamReader(actualServerProcess, "", "error"));	//avoid printing any name before output
			serverThreads[1].start();
			System.out.print("Trying to establish connection with Actual server " + server + "...");
			while(actualServer == null)
			{
				System.out.print(".");
				try{Thread.sleep(1000);}	//hoping to connect pretty soon here, otherwise will have to wait()
				catch(InterruptedException iex){/*ignore*/}
			}
			System.out.println("Connected!");	//just a line break to help others in displaying neat output
			
			//now send some data to and receive from Actual Server
			ObjectOutputStream oos = new ObjectOutputStream(actualServer.getOutputStream());
			
			oos.writeObject(readers);
			oos.writeObject(writers);
			oos.writeInt(numAccesses);
			oos.writeInt(rmiPort);
			oos.flush();
			
			//now wait for response from server, if it started successfully then only start clients
			ObjectInputStream ois = new ObjectInputStream(actualServer.getInputStream());
			String response = (String)ois.readObject();
			if(response.equalsIgnoreCase("SUCCESS"))
				return true;
		}
		catch(IOException ioex)
		{
			System.err.println("start.java :FATAL: can't start communicating with remote host : " + server + " **Exception : " + ioex.toString());
//			ioex.printStackTrace();	//DEBUG
			System.exit(-1);
		} catch (ClassNotFoundException e)
		{
//			System.out.println("DEBUG: Unknown Data received from Client");
		}
		return false;
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
//				System.out.println(prefix + "DEBUG: died!");
				if(exitVal != 0)
					Runtime.getRuntime().exec("skill java");
			}
			catch(IOException ex)
			{
				/*Ignore*/
//				ex.printStackTrace();	//DEBUG
			}
		}
	}
}
