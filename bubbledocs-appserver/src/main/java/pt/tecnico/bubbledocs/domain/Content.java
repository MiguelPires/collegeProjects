package pt.tecnico.bubbledocs.domain;

public class Content extends Content_Base {
    
    public Content() {
        super();
    }
    
    public void delete(){
    	
    	setForbiddenCell(null);
    	deleteDomainObject();
    }
    
}
