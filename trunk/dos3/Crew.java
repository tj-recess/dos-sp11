import java.rmi.Remote;
import java.rmi.RemoteException;


public interface Crew extends Remote
{
	ReplyPacket readData(int cNum) throws RemoteException;
	ReplyPacket writeData(int cNum) throws RemoteException;
}
