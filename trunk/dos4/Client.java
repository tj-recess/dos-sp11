
public class Client
{
	int mySequenceNum;	//number of times process has requested critical section
	int[] sequenceVector;	//best known information about other processes
	ClientConfig myConfig;	//my own sleepTime, opTime, name, etc.
	boolean token;
	Thread requesterThread,	//requester is used for multicast,
	listenerThread,	//listener listens to other's multicast requests,
	tokenThread;	//token sends and receives token through unicast.
				
	
	public Client(int numClients, ClientConfig config)
	{
		mySequenceNum = 0;	//should be 0 initially
		myConfig = config;
		if(myConfig.getClientNum() == 1)	//initially only client 1 gets the token
			token = true;
		else
			token = false;
		sequenceVector = new int[numClients];
		for (int i = 0; i < numClients; i++)
			sequenceVector[i] = 0;
	}
	
	public static void main(String[] args)
	{
		if(args.length < 1)
		{
			System.err.println("Usage : java Client <ID_of_Client>");
		}
	}
}
