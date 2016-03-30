package sec.blockfs.blockutility;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface BlockLibrary extends Remote {
    byte[] challenge(Long nonce) throws RemoteException;
}
