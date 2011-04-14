import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class Start
{
	private ConfigReader cr;
	Start()
	{
		cr = new ConfigReader("system.properties");
	}
	public static void main(String[] args)
	{
		Start s = new Start();
		s.initClients();
	}

	private void initClients()
	{
		//before doing anything, write header for request.log file
		Formatter.writeHeader();
		int numClients = cr.getNumClients();
		String path = System.getProperty("user.dir"); // get current directory of the user
		for(int i = 1; i <= numClients; i++)
		{			
			try 
			{
				ClientConfig aClient= cr.getClientConfig(i);
//				aClient.print();	//DEBUG
				Process remote = Runtime.getRuntime().exec("ssh " + aClient.getAddress() + " cd " + path + " ; java Client " + aClient.getClientNum());
				//new Thread(new ClientOutputStreamReader(remote, aClient.getAddress(), "input")).start();
				new Thread(new ClientOutputStreamReader(remote, aClient.getAddress(), "error")).start();
			}
			catch (IOException e) 
			{			
				System.err.println("Error instantiating all the clients, exiting! **Exception = " + e.getMessage());
				System.exit(-2);
			}
			
		}		
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
				System.out.println(prefix + "died!");
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
