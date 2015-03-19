package pt.tecnico.bubbledocs.service;

import pt.tecnico.bubbledocs.domain.User;
import pt.tecnico.bubbledocs.exception.BubbleDocsException;
import pt.tecnico.bubbledocs.exception.UnauthorizedOperationException;

public class DeleteUser extends BubbleDocsService {

	private String userToken;
	private String deleteUsername;
	
    public DeleteUser(String userToken, String toDeleteUsername) {
		this.userToken = userToken;
		this.deleteUsername = toDeleteUsername;
    }

    @Override
    protected void dispatch() throws BubbleDocsException{
    	User user = getUserByToken(userToken);
    	
    	if (user.isRoot()) {
			getBubbledocs().removeUser(deleteUsername);
    	}
    	else
    		throw new UnauthorizedOperationException();
    }

}
