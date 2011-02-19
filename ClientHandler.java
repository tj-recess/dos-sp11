import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;


public class ClientHandler implements Runnable 
{
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private Socket myClient = null;
	private PrintWriter fout = null;
	
	public ClientHandler(Socket aClient)
	{
		try{
		//starting my own log files
		try{fout = new PrintWriter(new FileWriter("CH" + this.hashCode() + ".log"), true);}
		catch(IOException ioex){System.out.println("Can't write to log file at Client Handler " + "CH" + this.hashCode() + ".log" + "**Exception** : " + ioex.toString());}
		myClient = aClient;
		}finally{System.out.println("exiting CH's ctor");}
	}
	@Override
	public void run() 
	{
		/*
		 * here we wait for client's request and respond accordingly
		 * Protocol - 
		 * 1) Readers send request "read" and it's ID and receive the shared object's value
		 * 2) Writers send request "write" and it's ID (new value) which would be written as object's value
		 */
		try 
		{
			System.out.println("ClientHandler:started for aClient, waiting for streams..."); 
			fout.println("ClientHandler:started for aClient, waiting for streams...");
			out = new ObjectOutputStream(myClient.getOutputStream());
			in = new ObjectInputStream(myClient.getInputStream());	
			System.out.println("ClientHandler: received streams, now ready to get data");
			fout.println("ClientHandler: received streams, now ready to get data");

			fout.println("DEBUG: ClientHandler is all set, waiting for args from client");
			System.out.println("DEBUG: ClientHandler is all set, waiting for args from client");
			String reqType = (String)in.readObject();
			int cNum = in.readInt();
			fout.println("DEBUG: received vals from client - reqType = " + reqType + ", clientNum = " + cNum);
			System.out.println("DEBUG: received vals from client - reqType = " + reqType + ", clientNum = " + cNum);
			if(reqType.equals("read"))
			{
				/*
				 * TODO:
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
				
				int myRequestNum = Server.addWaitingReader();
				System.out.println("Reader : requestNum = " + myRequestNum);
				synchronized(Server.WRITE_CONDITION)
				{
					if(Server.isWriterActive())
					{
						System.out.println("DEBUG: Writer Active - //wait on read condition until some writer notifies");
						//wait on read condition until some writer notifies
						try {
							//Server.READ_CONDITION.wait();
							Server.WRITE_CONDITION.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				Server.removeWaitingReader();
				System.out.println("DEBUG: Reader now reading the data");
				
				int myServiceNum = Server.addActiveReader();
				int sharedObjectValue = Server.sharedObject;
				System.out.println("DEBUG: Reader: myServiceNum = " + myServiceNum + ", sharedObjectValue = " + sharedObjectValue);
				//now sleep for opTime
				try {
					System.out.println("DEBUG: Reader: now sleeping for opTime time.");
					Thread.sleep(Server.getOpTime(cNum, "reader"));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Server.removeActiveReader();
				synchronized(Server.WRITE_CONDITION)
				{
					System.out.println("DEBUG: Reader: trying to wake up a writer");
					if(Server.getActiveReadersCount() == 0 && Server.getWaitingReadersCount() == 0)
						Server.WRITE_CONDITION.notifyAll();	//wake a random writer
					System.out.println("DEBUG: Reader: if writer was sleeping, it should wake up by now!\t Sending values back to client");
				}
				out.writeInt(myRequestNum);
				out.writeInt(sharedObjectValue);
				out.writeInt(myServiceNum);
				out.flush();
				System.out.println("Sent all data, reader's CH is done!!");
			}
			else if(reqType.equals("write"))
			{
				int newVal = cNum;
				/*
				 * TODO:
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
				int myRequestNum = Server.addWaitingWriter();
				System.out.println("DEBUG: Writer : requestNum = " + myRequestNum);
				int myServiceNum = -1;	//to indicate error in case this gets transmitted
				synchronized(Server.WRITE_CONDITION)
				{
					if(Server.isWriterActive()|| Server.getActiveReadersCount() > 0 || Server.getWaitingReadersCount() > 0)
					{
						//wait on read condition until some writer notifies
						System.out.println("DEBUG: Writer : waiting on write condition until some writer notifies");
						try {
							Server.WRITE_CONDITION.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						Server.setWriterActive();
						System.out.println("DEBUG: Writer woke up.. Now Active!");
					}
					myServiceNum = Server.removeWaitingWriter();
					System.out.println("DEBUG: Writer: ServiceNum = " + myServiceNum);
					Server.sharedObject = newVal;
					//now sleep for opTime
					try {
						System.out.println("DEBUG: Writer - now sleeping for opTime");
						Thread.sleep(Server.getOpTime(cNum, "writer"));
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					if(Server.getActiveReadersCount() == 0 && Server.getWaitingReadersCount() == 0)
						Server.WRITE_CONDITION.notifyAll();	//wake a random writer : LOGIC NEEDED
					else
						Server.WRITE_CONDITION.notifyAll();
					System.out.println("DEBUG: Writer: Notified, either reader or writer. Now sending values back");
				}
				out.writeInt(myRequestNum);
				out.writeInt(myServiceNum);
				out.flush();
				System.out.println("DEBUG: Writer everthing sent");
			}
			else
				System.out.println("ERROR : Unknown Request Type - " + reqType);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
