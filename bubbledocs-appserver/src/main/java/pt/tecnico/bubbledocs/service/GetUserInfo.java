package pt.tecnico.bubbledocs.service;

import pt.tecnico.bubbledocs.exception.BubbleDocsException;

public class GetUserInfo extends CheckLogin {

	private String username;
    private String email;
    private String name;

    public GetUserInfo(String username) {
        this.username = username;
        this.name = user.getName();
        this.email = user.getEmail();
    }
    
    public String getUsername(){
    	return username;
    }

    public String getName(){
        return name;
    }

    public String getEmail(){
        return email;
    }

}
