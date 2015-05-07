package pt.tecnico.bubbledocs.service;

import static org.junit.Assert.*;

import org.junit.Test;

import pt.tecnico.bubbledocs.domain.User;
import pt.tecnico.bubbledocs.exception.*;
//import pt.tecnico.bubbledocs.integration.component.ImportDocument;

public class GetUsername4TokenTest extends BubbleDocsServiceTest{
	
	private static final String TOKEN = "arstoken";
	private static final String USERNAME = "ars";
    private static final String EMAIL = "ars@tecnico.pt";
    private static final String ROOT_USERNAME = "root";
    private static final String USERNAME_DOES_NOT_EXIST = "no-one";
	
	
    private User as;
    private String root;
    private String token;
    
    @Override
    public void populate4Test() throws BubbleDocsException {
        as = createUser(USERNAME, EMAIL, "António Rito Silva");
        root = addUserToSession(ROOT_USERNAME);
        token = addUserToSession("ars");
        
    }
    
    @Test
    public void success() throws BubbleDocsException {
    	GetUsername4Token service = new GetUsername4Token(token);
    	
    	service.execute();
    	
    	assertEquals(as.getUsername(), service.getUsername());
    	
    }
    
    @Test(expected = UserNotInSessionException.class)
    public void userDidNotLoginYet(){
    	GetUsername4Token service = new GetUsername4Token(USERNAME_DOES_NOT_EXIST);
    	
    	service.execute();
    }
    
    @Test(expected = EmptyUsernameException.class)
    public void userNull(){
    	GetUsername4Token service = new GetUsername4Token(null);
        service.execute();
    }
    
    @Test(expected = EmptyUsernameException.class)
    public void userEmpty(){
    	GetUsername4Token service = new GetUsername4Token("");
        service.execute();
    }
    
    
    

}
