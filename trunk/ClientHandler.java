import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;


public class ClientHandler implements Runnable 
{
	private ObjectInputStream in;
	private ObjectOutputStream out;
	
	public ClientHandler(Socket aClient)
	{
		try
		{
			in = new ObjectInputStream(aClient.getInputStream());	
			out = new ObjectOutputStream(aClient.getOutputStream());
		}
		catch (IOException ioex) 
		{
			System.out.println("Can't get in/out stream of client, terminating ! Exception** : " + ioex.getMessage());
			System.exit(-2);
		}	
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
			String reqType = (String)in.readObject();
			int cNum = in.readInt();
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
				synchronized(Server.class)
				{
					if(Server.isWriterActive())
					{
						//wait on read condition until some writer notifies
						try {
							Server.READ_CONDITION.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				Server.removeWaitingReader();
				int myServiceNum = Server.addActiveReader();
				int sharedObjectValue = Server.sharedObject;
				//now sleep for opTime
				try {
					Thread.sleep(Server.getOpTime(cNum, "reader"));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Server.removeActiveReader();
				synchronized(Server.class)
				{
					if(Server.getActiveReadersCount() == 0 && Server.getWaitingReadersCount() == 0)
						Server.WRITE_CONDITION.notify();	//wake a random writer
				}
				out.writeInt(myRequestNum);
				out.writeInt(sharedObjectValue);
				out.writeInt(myServiceNum);
				out.flush();
				
			}
			else if(reqType.equals("write"))
			{
				int newVal = in.readInt();
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
				int myServiceNum = -1;	//to indicate error in case this gets transmitted
				synchronized(Server.class)
				{
					if(Server.isWriterActive())
					{
						//wait on read condition until some writer notifies
						try {
							Server.WRITE_CONDITION.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						Server.setWriterActive();
					}
					myServiceNum = Server.removeWaitingWriter();
					Server.sharedObject = newVal;
					//now sleep for opTime
					try {
						Thread.sleep(Server.getOpTime(cNum, "writer"));
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					if(Server.getActiveReadersCount() == 0 && Server.getWaitingReadersCount() == 0)
						Server.WRITE_CONDITION.notify();	//wake a random writer
					else
						Server.READ_CONDITION.notifyAll();
				}
				out.writeInt(myRequestNum);
				out.writeInt(myServiceNum);
				out.flush();
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
