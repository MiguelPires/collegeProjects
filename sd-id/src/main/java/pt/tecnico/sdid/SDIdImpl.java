package pt.tecnico.sdid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.jws.WebService;
import javax.xml.bind.JAXBElement;

import pt.tecnico.sdid.User;
import pt.ulisboa.tecnico.sdis.id.ws.AuthReqFailed;
import pt.ulisboa.tecnico.sdis.id.ws.AuthReqFailed_Exception;
import pt.ulisboa.tecnico.sdis.id.ws.CreateUser;
import pt.ulisboa.tecnico.sdis.id.ws.EmailAlreadyExists_Exception;
import pt.ulisboa.tecnico.sdis.id.ws.InvalidEmail_Exception;
import pt.ulisboa.tecnico.sdis.id.ws.ObjectFactory;
import pt.ulisboa.tecnico.sdis.id.ws.RemoveUser;
import pt.ulisboa.tecnico.sdis.id.ws.RenewPassword;
import pt.ulisboa.tecnico.sdis.id.ws.SDId;
import pt.ulisboa.tecnico.sdis.id.ws.UserAlreadyExists;
import pt.ulisboa.tecnico.sdis.id.ws.UserDoesNotExist;
import pt.ulisboa.tecnico.sdis.id.ws.UsernameProblem;

@WebService(endpointInterface = "pt.ulisboa.tecnico.sdis.id.ws.SDId", wsdlLocation = "SD-ID.1_1.wsdl", name = "SDId", portName = "SDIdImplPort", targetNamespace = "urn:pt:ulisboa:tecnico:sdis:id:ws", serviceName = "SDId")
public class SDIdImpl implements SDId {

    private List<User> users;

    public List<User> getUsers() {
        return users;
    }
    
    public User getUser(String userId) throws UserDoesNotExist {
    	for (User user : getUsers()) {
            if (user.getUserId().equals(userId)) {
                return user;
            }
        }
    	UsernameProblem up = new UsernameProblem();
    	up.setUserId(userId);
		throw new UserDoesNotExist("User "+ userId + " not found.", up); 	
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public SDIdImpl() {
        setUsers(new ArrayList<User>());
    }

    public void createUser(CreateUser parameters) throws EmailAlreadyExists_Exception,
            InvalidEmail_Exception,
            UserAlreadyExists {
        System.out.println("Create User");
    }

    public void renewPassword(RenewPassword parameters) throws UserDoesNotExist {
        System.out.println("Renew Password");

    }

    public void removeUser(RemoveUser parameters) throws UserDoesNotExist {
        System.out.println("Remove User");

    }

    public byte[] requestAuthentication(String userId, byte[] reserved) throws AuthReqFailed_Exception {
    	byte[] trueByte = new byte[] {(byte)1};
    	try {
    		User user = getUser(userId);
    		byte[] password = user.getPassword().getBytes();
    		if (Arrays.equals(reserved, password)){
    			return trueByte;
    		}
    		else {
    			AuthReqFailed arf = new AuthReqFailed();
    			JAXBElement<byte[]> element= (new ObjectFactory()).createAuthReqFailedReserved(reserved);
    			arf.setReserved(element);
    			throw new AuthReqFailed_Exception("Wrong password.", arf);
    		}
    	} catch (UserDoesNotExist e) {
    		byte[] userByte = userId.getBytes();
    		AuthReqFailed arf = new AuthReqFailed();
			JAXBElement<byte[]> element= (new ObjectFactory()).createAuthReqFailedReserved(userByte);
			arf.setReserved(element);
			throw new AuthReqFailed_Exception("Wrong password.", arf);
    	}
    }
}
