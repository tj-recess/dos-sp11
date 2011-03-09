import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;


public class Server implements Crew{

	ServerSocket ss = null;
	int port = 0;
	
	private int numReaders;
	private int numWriters;
	private ArrayList<RW> readers;
	private ArrayList<RW> writers;
	private static int sharedObject = -1;
	private int rmiPort;
	private int numAccesses;
	private AtomicInteger requestNum;
	private AtomicInteger serviceNum;
	private AtomicInteger activeReadersCount;
	private AtomicInteger waitingReadersCount;
	private AtomicInteger waitingWritersCount;
	private int finishedClientsCount = 0;
	private static AtomicInteger finishedClients;
	private boolean writerActive;
	private boolean readerArrived = false;
	private static Object WRITE_CONDITION;
	private CopyOnWriteArrayList<Formatter> readersLog;
	private CopyOnWriteArrayList<Formatter> writersLog;
	private	boolean firstCaterReaderCond = true;
	
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
		
		Server aServer;
		aServer = new Server();
		
		//get and return required objects from start.java
		aServer.receiveData(startSocket);
				
		//server started, now register this with RMI registry
//		System.out.println("DEBUG://server started, now register this with RMI registry");
		try {
			aServer.register();
		} catch (RemoteException e) {
			//can't register server now, send failure to start so that clients are not started
			aServer.sendData(startSocket, false);
			System.exit(-10);
		}
		
		aServer.sendData(startSocket, true);
	}
	
	private void sendData(Socket startSocket, boolean result)
	{
		try
		{
			ObjectOutputStream oos = new ObjectOutputStream(startSocket.getOutputStream());
			if(result)
				oos.writeObject("SUCCESS");
			else
				oos.writeObject("FAILURE");
		}
		catch (IOException ioex)
		{
//			System.out.println("Server: DEBUG: " + ioex.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private void receiveData(Socket startSocket) {
		try 
		{
			ObjectInputStream ois = new ObjectInputStream(startSocket.getInputStream());
						
			//receive readers and writers ArrayLists from start.java			
			readers = (ArrayList<RW>)ois.readObject();
			writers = (ArrayList<RW>)ois.readObject();
			numAccesses = ois.readInt();
			rmiPort = ois.readInt();
			
			numReaders = readers.size();
			numWriters = writers.size();
			
			System.out.println("RMI Port = " + rmiPort);
//			Registry reg = LocateRegistry.getRegistry(rmiPort);
//			System.out.println("reg = " + reg);
//			try{LocateRegistry.getRegistry(rmiPort);}
//			catch(Exception ex)
//			{
//				System.out.println("registry is not running already, starting now - ex = " + ex.toString());
//				LocateRegistry.createRegistry(rmiPort);
//			}
			LocateRegistry.createRegistry(rmiPort);
		}
		catch (RemoteException e) {
			//System.err.println("DEBUG: Server: Can't create Registry at " + rmiPort + " port.");
			//e.printStackTrace();
			//registry already running so just use it
		}
		catch (IOException ioex)
		{
//			System.err.println("can't send port to start.java, exception = " + ioex.toString());
		} catch (ClassNotFoundException e) {
//			System.err.println("DEBUG: Unknown data received from start.java");
		}
	}

	private void register() throws RemoteException{
		Crew stub = null;
		stub = (Crew) UnicastRemoteObject.exportObject(this, 0);
		Registry reg = LocateRegistry.getRegistry(rmiPort); 
		if(reg == null)
		{
			System.err.println("FATAL: Server can't loacte registery at port = " + rmiPort + ". Exiting...");
			System.exit(-1);
		}
		reg.rebind("arpit", stub);
		System.out.println("Server is bound now!!!");
	}
	static 
	{
		finishedClients = new AtomicInteger(0);
	}

	public Server()
	{
		//setup other params
		requestNum = new AtomicInteger(0);
		serviceNum = new AtomicInteger(0);
		activeReadersCount = new AtomicInteger(0);
		waitingReadersCount = new AtomicInteger(0);
		waitingWritersCount = new AtomicInteger(0);
		writerActive = false;
		WRITE_CONDITION = new Object();
		readers = new ArrayList<RW>();
		writers = new ArrayList<RW>();
		readersLog = new CopyOnWriteArrayList<Formatter>();
		writersLog = new CopyOnWriteArrayList<Formatter>();
	}

	@Override
	public ReplyPacket readData(int cNum) throws RemoteException
	{
		/*
		 * Algorithm:
		 * 0. in loop until numAccesses times
		 * 1. increment waiting reader's count
		 * 2. check if there is any writer already in Critical Section (busy flag), 
		 * 3.	if yes, wait on readCondition until someone wakes you up
		 * 4. 	waitingReaderCount--;
		 * 		activeReaderCount++;
		 *	now read the value.
		 * 5. once done increment active readers count
		 * 6. then sleep for opTime
		 * opTime = read from server for this cNum
		 * Thread.sleep(opTime)
		 * 7. activeReaderCount--; 
		 * 8. now check if other readers are active or not
		 * 		if not, wake up a writer
		 * 9. now send the values back to client
		 * 
		 */

		int myRequestNum = addWaitingReader();
		int myServiceNum = -1;	//to indicate error
		System.out.println("Reader : requestNum = " + myRequestNum);
		synchronized(WRITE_CONDITION)
		{
			while(isWriterActive())	//no reading allowed if someone is writing
			{
//				System.out.println("DEBUG: Reader - wait on write condition until some writer notifies");
				//wait on read condition until some writer notifies
				try {WRITE_CONDITION.wait();}
				catch (InterruptedException e) {/*Ignore*/}
			}
			//have to perform these 2 steps atomically
			removeWaitingReader();						
			myServiceNum = addActiveReader();
		}
		int sharedObjectValue = sharedObject;
//		System.out.println("DEBUG: Reader: myServiceNum = " + myServiceNum + ", sharedObjectValue = " + sharedObjectValue);
		//now sleep for opTime
		try {Thread.sleep(getOpTime(cNum, "reader"));}
		catch (InterruptedException e) {/*Ignore*/}
		//put output into reader's log
		readersLog.add(new Formatter(myServiceNum, sharedObjectValue, "R" + cNum, getActiveReadersCount()));
		removeActiveReader();
		synchronized(WRITE_CONDITION)
		{
//			System.out.println("DEBUG: Reader: trying to wake up everyone waiting");
			WRITE_CONDITION.notifyAll();	//wake up everyone and then check for reader/writer conflict again
//			System.out.println("DEBUG: Reader: if writer was sleeping, it should wake up by now!\t Sending values back to client");
		}
		
//		System.out.println("DEBUG: ReadData: sending reply packet");		
		return new ReplyPacket(myRequestNum, sharedObjectValue, myServiceNum);

	}

	public void printOutput() throws RemoteException
	{
		synchronized(Server.class)
		{
			int howMany = finishedClients.incrementAndGet();
//			System.out.println("Server: DEBUG: print : finishedClients = " + howMany);
			if(howMany != numAccesses*(numReaders + numWriters))
				return;
		}
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
		try{
			Registry reg = LocateRegistry.getRegistry(rmiPort);
			reg.unbind("arpit");
			if(UnicastRemoteObject.unexportObject(this, true))
			{
//				System.out.println("DEBUG: Successfully unexported server object.");
			}
		}
		catch(Exception re){/*Ignore*/}
	}

	@Override
	public ReplyPacket writeData(int cNum) throws RemoteException {
		int newVal = cNum;
		/*
		 * Algorithm:
		 * 0. in loop numAccesses times, repeat 1 to 8
		 * 1. waitingWriterCout++;
		 * 2. if activeReaderCount > 0 || waitingReaderCount is > 0
		 * 		--keep waiting on writeCondition object until someone wakes u up
		 * 3. busy = true; 
		 * 4. waitingWriterCount--;
		 * 5. set shared object value
		 * 6. get opTime from server for this writer
		 * 7. sleep for opTime
		 * 8. now return the value and sequence number 
		 */

		int myRequestNum = addWaitingWriter();
//			System.out.println("DEBUG: Writer : requestNum = " + myRequestNum);
		int myServiceNum = -1;	//to indicate error in case this gets transmitted
		synchronized(WRITE_CONDITION)
		{
			while(isWriterActive() || getActiveReadersCount() > 0 || getWaitingReadersCount() > 0
					|| (firstCaterReaderCond && getNumReaders() > 0))	//last condition ensures that if there is any expected 
			{													//reader then writer should first wait
				//wait on read condition until some writer notifies
//					System.out.println("DEBUG: Writer : waiting on write condition until someone notifies. i = " + i + ", numReaders = " + getNumReaders() + ", activeReaders = " + getActiveReadersCount() + ", waitingReaders = " + getWaitingReadersCount());
					System.out.println("firstCaterReaderCond = " + firstCaterReaderCond);
				if(hasReaderArrived())
					firstCaterReaderCond = false;
				try {WRITE_CONDITION.wait();}
				catch (InterruptedException e) {/*Ignore*/}
				//if someone wakes this writer up then check for waiting or active readers again 
				//before attempting to change the value, but do change ur firstCaterReaderCond value to false
			}
			myServiceNum = removeWaitingWriter();
			setWriterActive();
//				System.out.println("DEBUG: Writer woke up.. Now Active!");
//				System.out.println("DEBUG: Writer: ServiceNum = " + myServiceNum);
			sharedObject = newVal;
			//now sleep for opTime
			try {Thread.sleep(getOpTime(cNum, "writer"));}
			catch (InterruptedException e) {/*Ignore*/}
			//add output to writers log
			writersLog.add(new Formatter(myServiceNum,sharedObject, "W" + cNum));
			
			setWriterNotActive();
			WRITE_CONDITION.notifyAll();	//I am done, wake everyone up
//			System.out.println("DEBUG: Writer: Notified All. Now sending values back");
		}
		return new ReplyPacket(myRequestNum, newVal, myServiceNum);
	}
	
	private int getRequestNum()
	{
			return requestNum.incrementAndGet();
	}
	
	private int getServiceNum()
	{
		return serviceNum.incrementAndGet();
	}
	
	private int addActiveReader() {
		activeReadersCount.incrementAndGet();
		return getServiceNum();
	}	
	private void removeActiveReader(){
		activeReadersCount.decrementAndGet();
	}
	private int getActiveReadersCount() {
		return activeReadersCount.get();
	}
	
	private int addWaitingReader() {
		synchronized(Server.class) {
			waitingReadersCount.incrementAndGet();
			readerArrived = true;
			return getRequestNum();
		}
	}
	private void removeWaitingReader() {
			waitingReadersCount.decrementAndGet();
	}	
	private int getWaitingReadersCount() {
		return waitingReadersCount.get();
	}
	
	private int addWaitingWriter() {
			waitingWritersCount.incrementAndGet();
			return getRequestNum();
	}
	private int removeWaitingWriter() {
			waitingWritersCount.decrementAndGet();
			return getServiceNum();
	}
	
	private boolean isWriterActive() {
		return writerActive;
	}
	private void setWriterActive() {
		writerActive = true;
	}
	private void setWriterNotActive() {
		writerActive = false;
	}
	private boolean hasReaderArrived(){
		return readerArrived;
	}	
	private int getOpTime(int cNum, String type) {
		if(type.equals("reader"))
			return readers.get(cNum - 1).getOpTime();
		else if(type.equals("writer"))
			return writers.get(cNum - numReaders - 1).getOpTime();
		else	//should never happen
			return 0;
	}
	
	private int getNumReaders() {
		return numReaders;
	}
	private int getNumWriters() {
		return numWriters;
	}
}
