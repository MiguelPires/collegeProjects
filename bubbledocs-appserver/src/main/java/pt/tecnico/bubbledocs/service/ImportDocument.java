package pt.tecnico.bubbledocs.service;

import java.io.IOException;
import java.io.StringReader;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import pt.tecnico.bubbledocs.domain.Spreadsheet;
import pt.tecnico.bubbledocs.exception.BubbleDocsException;
import pt.tecnico.bubbledocs.exception.EmptyUsernameException;
import pt.tecnico.bubbledocs.exception.UnauthorizedOperationException;

public class ImportDocument extends CheckLogin {
	private org.jdom2.Document docXML;
    private int docId;
    private Spreadsheet spread;

    public org.jdom2.Document convertFromBytes(byte[] doc) {
       String xml = new String(doc);
       SAXBuilder builder = new SAXBuilder();
       org.jdom2.Document newDoc=null;
	try {
		newDoc = builder.build(new StringReader(xml));
	} catch (JDOMException e) {
		e.printStackTrace();
	} catch (IOException e) {
		e.printStackTrace();
	}
       
       return newDoc;
    }

    public void setDocXML(byte[] doc) {
    	docXML = convertFromBytes(doc);
    }

    public ImportDocument(int docId, String userToken) {
        this.userToken = userToken;
        this.docId = docId;
    }

    @Override
    protected void dispatch() throws BubbleDocsException {
        super.dispatch();

        Element doc = docXML.getRootElement();
        Element creatorElement = doc.getChild("creator");
        Element userElement = creatorElement.getChild("user");
        String xmlUsername = userElement.getAttribute("username").getValue();
        
        if(!getUserByToken(userToken).getUsername().equals(xmlUsername))
        	throw new UnauthorizedOperationException();
        
        spread = importFromXML(docXML);;
    }

    public Spreadsheet getSpread(){
    	return spread;
    }
}