import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;


public class Server implements Runnable
{
	ServerSocket ss = null;
	int port = 0;
	
	private static int numReaders;
	private static int numWriters;
	private static ArrayList<RW> readers;
	private static ArrayList<RW> writers;
	public static int sharedObject;
	private static AtomicInteger requestNum;
	private static AtomicInteger serviceNum;
	private static AtomicInteger activeReadersCount;
	private static AtomicInteger waitingReadersCount;
	private static AtomicInteger waitingWritersCount;
	private static boolean writerActive;
	public static Object READ_CONDITION;
	public static Object WRITE_CONDITION;
	public static CopyOnWriteArrayList<Formatter> readersLog;
	public static CopyOnWriteArrayList<Formatter> writersLog;
	
	static 
	{
		sharedObject = -1;
		requestNum = new AtomicInteger(0);
		serviceNum = new AtomicInteger(0);
		activeReadersCount = new AtomicInteger(0);
		waitingReadersCount = new AtomicInteger(0);
		waitingWritersCount = new AtomicInteger(0);
		writerActive = false;
		READ_CONDITION = new Object();
		WRITE_CONDITION = new Object();
		readers = new ArrayList<RW>();
		writers = new ArrayList<RW>();
		readersLog = new CopyOnWriteArrayList<Formatter>();
		writersLog = new CopyOnWriteArrayList<Formatter>();
	}
	
	public Server()
	{
		try
		{
			ss = new ServerSocket(0,0,InetAddress.getLocalHost());	
			//0 passed to ensure server starts on some system assigned port
			port = ss.getLocalPort();
			setup();
		}
		catch (IOException ioex)
		{
			System.err.println("Can't start server, terminating... : Exception** : " + ioex.getMessage());
//			ioex.printStackTrace();	//DEBUG
			System.exit(-1);
		}
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
		
		//now read all the attributes in config file
		numReaders = Integer.parseInt(sysProp.getProperty("RW.numberOfReaders"));
		numWriters = Integer.parseInt(sysProp.getProperty("RW.numberOfWriters"));

		
		//setup data structures so that they can be passed to the actual server
		//setup readers from config file
		for(int i = 0; i < numReaders; i++)
		{
			String readerKey = "RW.reader" + (i + 1);
			String readerName = sysProp.getProperty(readerKey);	//reader/writer's name starts from 1 to n (not 0 to n-1)
			String opTime = sysProp.getProperty(readerKey + ".opTime");
			String sleepTime = sysProp.getProperty(readerKey + ".sleepTime");
//			System.out.println("DEBUG: Config : " + readerName + ", " + opTime + ", " + sleepTime);
			readers.add(new RW(readerName, Integer.parseInt(opTime), Integer.parseInt(sleepTime), i));	
		}
		//setup writers from config file
		for(int i = numReaders; i < numReaders + numWriters; i++)
		{
			String writerKey = "RW.writer" + (i + 1);
			String writerName = sysProp.getProperty(writerKey);	//reader/writer's name starts from (numReaders + 1) to n (not 0 to n-1)
			String opTime = sysProp.getProperty(writerKey + ".opTime");
			String sleepTime = sysProp.getProperty(writerKey + ".sleepTime");
//			System.out.println("DEBUG: Config : " + writerKey + ", " + opTime + ", " + sleepTime);
//			System.out.println("DEBUG: Config : " + writerName + ", " + opTime + ", " + sleepTime);
			writers.add(new RW(writerName, Integer.parseInt(opTime), Integer.parseInt(sleepTime), i));
		}		
	}
	@Override
	public void run()
	{
		if(ss == null)
			return;
		ArrayList<Thread> clientHandlers = new ArrayList<Thread>();
		//server started, now keep accepting client connections forever
		try
		{
			for(int i = 0; i < numReaders + numWriters; i++)
			{
//				System.out.println("DEBUG: waiting for connection(s) from client(s)");
				Socket aClient = ss.accept();
				Thread t = new Thread(new ClientHandler(aClient));
				t.start();
				clientHandlers.add(t);
//				System.out.println("DEBUG: got a new client, starting in another clienthandler thread");
			}
			//now done accepting all clients, make them join before printing output
			for(int i = 0; i < clientHandlers.size(); i++)
			{
				try {clientHandlers.get(i).join();}
				catch (InterruptedException e) {/*Ignore*/}
			}
			//now all threads are done, print both reading and writing logs
			System.out.println("Read Requests:");
			String readerFormat = "%16s\t%12s\t%7s\t%14s%n";
			System.out.format(readerFormat, "Service Sequence", "Object Value", "Read by", "Num of Readers");
			System.out.format(readerFormat, "----------------", "------------", "-------", "--------------");
			for(int i = 0; i < readersLog.size(); i++)
			{
				Formatter f = readersLog.get(i);
				System.out.format(readerFormat, f.getServiceNum(), f.getObjectVal(), f.getReadBy(), f.getNumActiveReaders());
			}
			
			System.out.println("Write Requests:");
			String writerFormat = "%16s\t%12s\t%10s%n";
			System.out.format(writerFormat, "Service Sequence", "Object Value", "Written by");
			System.out.format(writerFormat, "----------------", "------------", "----------");
			for(int i = 0; i < writersLog.size(); i++)
			{
				Formatter f = writersLog.get(i);
				System.out.format(writerFormat, f.getServiceNum(), f.getObjectVal(), f.getWrittenBy());
			}
		}
		catch (IOException ioex)
		{
			System.err.println("Can't start server, terminating... : Exception** : " + ioex.getMessage());
			System.exit(-1);
		}
	}
	public static void main(String[] args)
	{
		if(args.length < 2)
		{
			System.err.println("Usage : java Server <host> <port>");
			System.exit(-1);
		}
		
		/*logic - first connect to start.java (server for this class) 
		 * then send my new port (server port) to start.java which will
		 * in turn instantiate the clients
		 */
		Socket startSocket = null;
		try {
			startSocket = new Socket(args[0], Integer.parseInt(args[1]));
		} catch (NumberFormatException e) {
			System.err.println("Actual Server : bad port " + args[1] + " received from start.java, exception = " + e.getMessage());
			System.exit(-2);
		} catch (UnknownHostException e) {
			System.out.println("Client : Not able to resolve host. **Unknown host exception - " + e.getMessage());
			System.exit(-2);
		} catch (IOException ioex) {
			System.err.println("Actual Server : can't start connection with start.java, exception = " + ioex.getMessage());
			System.exit(-2);
		}
		
		//connection established, now start actual server
//		System.out.println("DEBUG://connection established, now start actual server");
//		System.out.println("DEBUG: Actual Server recieved args[0] = " + args[0] + ", args[1] = " + args[1]);
		
		Server aServer = new Server();
		new Thread(aServer).start();
		
		//server started, now start accepting clients indefinitely
//		System.out.println("DEBUG://server started, now start accepting clients indefinitely");
		
		//actual server started, now send actual port to start.java		
		ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(startSocket.getOutputStream());
			oos.writeObject(new Integer(aServer.port));
			oos.flush();
		} catch (IOException ioex) 
		{
			System.err.println("can't send port to start.java, exception = " + ioex.toString());
		}
	}
	
	public static int getRequestNum()
	{
			return requestNum.incrementAndGet();
	}
	
	public static int getServiceNum()
	{
		return serviceNum.incrementAndGet();
	}
	
	public static int addActiveReader() {
		activeReadersCount.incrementAndGet();
		return getServiceNum();
	}	
	public static void removeActiveReader(){
		activeReadersCount.decrementAndGet();
	}
	public static int getActiveReadersCount() {
		return activeReadersCount.get();
	}
	
	public static int addWaitingReader() {
			waitingReadersCount.incrementAndGet();
			return getRequestNum();
	}
	public static void removeWaitingReader() {
			waitingReadersCount.decrementAndGet();
	}	
	public static int getWaitingReadersCount() {
		return waitingReadersCount.get();
	}
	
	public static int addWaitingWriter() {
			waitingWritersCount.incrementAndGet();
			return getRequestNum();
	}
	public static int removeWaitingWriter() {
			waitingWritersCount.decrementAndGet();
			return getServiceNum();
	}
	public static int getWaitingWritersCount() {
		return waitingWritersCount.get();
	}

	public static boolean isWriterActive() {
		return writerActive;
	}
	public static void setWriterActive() {
		writerActive = true;
	}
	public static void setWriterNotActive() {
		writerActive = false;
	}
	
	public static int getOpTime(int cNum, String type) {
		if(type.equals("reader"))
			return readers.get(cNum - 1).getOpTime();
		else if(type.equals("writer"))
			return writers.get(cNum - numReaders - 1).getOpTime();
		else	//should never happen
			return 0;
	}
} 
