import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;


public class ClientHandler implements Runnable 
{
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private Socket myClient = null;
	
	public ClientHandler(Socket aClient)
	{
		myClient = aClient;
	}
	
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
			//new requirement - wait until all the clients have started
			synchronized(ClientHandler.class){/*Just keep waiting on monitor until server releases it*/}
//			System.out.println("ClientHandler:started for aClient, waiting for streams..."); 
			out = new ObjectOutputStream(myClient.getOutputStream());
			in = new ObjectInputStream(myClient.getInputStream());	
//			System.out.println("ClientHandler: received streams, now ready to get data");

//			System.out.println("DEBUG: ClientHandler is all set, waiting for args from client");
			int numAccesses = in.readInt();
			for(int i = 0; i < numAccesses; i++)
			{
				String reqType = (String)in.readObject();
				int cNum = in.readInt();
//				System.out.println("DEBUG: received vals from client - reqType = " + reqType + ", clientNum = " + cNum + ", numAccesses = " + numAccesses);
				if(reqType.equals("read"))
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
	
					int myRequestNum = Server.addWaitingReader();
					int myServiceNum = -1;	//to indicate error
//					System.out.println("Reader : requestNum = " + myRequestNum);
					synchronized(Server.WRITE_CONDITION)
					{
						while(Server.isWriterActive())	//no reading allowed if someone is writing
						{
//							System.out.println("DEBUG: Reader - wait on write condition until some writer notifies");
							//wait on read condition until some writer notifies
							try {Server.WRITE_CONDITION.wait();}
							catch (InterruptedException e) {/*Ignore*/}
						}
						//have to perform these 2 steps atomically
						Server.removeWaitingReader();						
						myServiceNum = Server.addActiveReader();
					}
					int sharedObjectValue = Server.sharedObject;
//					System.out.println("DEBUG: Reader: myServiceNum = " + myServiceNum + ", sharedObjectValue = " + sharedObjectValue);
					//now sleep for opTime
					try {Thread.sleep(Server.getOpTime(cNum, "reader"));}
					catch (InterruptedException e) {/*Ignore*/}
					//put output into reader's log
					Server.readersLog.add(new Formatter(myServiceNum, sharedObjectValue, "R" + cNum, Server.getActiveReadersCount()));
					Server.removeActiveReader();
					synchronized(Server.WRITE_CONDITION)
					{
//						System.out.println("DEBUG: Reader: trying to wake up everyone waiting");
						Server.WRITE_CONDITION.notifyAll();	//wake up everyone and then check for reader/writer conflict again
//						System.out.println("DEBUG: Reader: if writer was sleeping, it should wake up by now!\t Sending values back to client");
					}
					out.writeInt(myRequestNum);
					out.writeInt(sharedObjectValue);
					out.writeInt(myServiceNum);
					out.flush();
				}
				else if(reqType.equals("write"))
				{
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

						int myRequestNum = Server.addWaitingWriter();
//						System.out.println("DEBUG: Writer : requestNum = " + myRequestNum);
						int myServiceNum = -1;	//to indicate error in case this gets transmitted
						synchronized(Server.WRITE_CONDITION)
						{
							while(Server.isWriterActive() || Server.getActiveReadersCount() > 0 || Server.getWaitingReadersCount() > 0
									|| (i==0 && Server.getNumReaders() > 0))	//last condition ensures that if there is any expected 
							{													//reader then writer should first wait
								//wait on read condition until some writer notifies
//								System.out.println("DEBUG: Writer : waiting on write condition until someone notifies");
								try {Server.WRITE_CONDITION.wait();}
								catch (InterruptedException e) {/*Ignore*/}
								//if someone wakes this writer up then check for waiting or active readers again 
								//before attempting to change the value
							}
							myServiceNum = Server.removeWaitingWriter();
							Server.setWriterActive();
//							System.out.println("DEBUG: Writer woke up.. Now Active!");
//							System.out.println("DEBUG: Writer: ServiceNum = " + myServiceNum);
							Server.sharedObject = newVal;
							//now sleep for opTime
							try {Thread.sleep(Server.getOpTime(cNum, "writer"));}
							catch (InterruptedException e) {/*Ignore*/}
							//add output to writers log
							Server.writersLog.add(new Formatter(myServiceNum,Server.sharedObject, "W" + cNum));
							Server.setWriterNotActive();
							Server.WRITE_CONDITION.notifyAll();	//I am done, wake everyone up
//							System.out.println("DEBUG: Writer: Notified All. Now sending values back");
						}
						out.writeInt(myRequestNum);
						out.writeInt(myServiceNum);
						out.flush();
				}
				else
					System.out.println("ERROR : Unknown Request Type - " + reqType);
			}//end of for-running numAccesses times			
		} catch (IOException ioex) {
			System.out.println("Client Handler : IO Exception while communicating with client **Exception = " + ioex.getMessage());
		} catch (ClassNotFoundException e) {
			System.out.println("Client Handler : Unknown data received from client **Exception = " + e.getMessage());
		}
	}

} 
