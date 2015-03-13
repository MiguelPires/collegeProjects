package pt.tecnico.bubbledocs;

import java.util.ArrayList;

import javax.transaction.*;

import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.joda.time.*;

import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.FenixFramework;
import pt.ist.fenixframework.TransactionManager;
import pt.tecnico.bubbledocs.domain.*;
import pt.tecnico.bubbledocs.exception.UserNotFoundException;

public class BubbleApplication {

	private static Bubbledocs bubbleapp;
	
    public static void main(String[] args) {

    	System.out.println("+--------------------------------------------+");
    	System.out.println("+   Welcome to the Bubbledocs application!   +");
    	System.out.println("+--------------------------------------------+");
    	
    	
    	TransactionManager tm = FenixFramework.getTransactionManager();
    	boolean committed = false;

    	try {
    		tm.begin();

    		bubbleapp =  Bubbledocs.getInstance();
    		setupIfNeed(bubbleapp);

    		tm.commit();
    		committed = true;
    	} catch (SystemException| NotSupportedException | RollbackException| HeuristicMixedException | HeuristicRollbackException ex) {
    		System.err.println("Error in execution of transaction: " + ex);
    	} finally {
    		if (!committed)
    		{
    		   	try {
    				tm.rollback();
    			} catch (SystemException ex) {
    				System.err.println("Error in roll back of transaction: " + ex);
    			}	
    		} 

    	}
    	
    	printUsers();
		printSpreadsheets("pf");
		printSpreadsheets("ra");
    	
    	//aceder as spreadsheets, converter e escrever o resultado
    	ArrayList <org.jdom2.Document> docList = exportFeature();
    	
    	//remover a spreadsheet do pf
    	deleteSpreadsheet();
    	
    	printSpreadsheetsID("pf");
    	
    	//importar spreadsheet        
       /* for(org.jdom2.Document doc: docList)
        {
           importFromXML(doc);
        }*/
           

        //aceder as spreadsheets, converter e escrever o resultado
        docList = exportFeature();

   }
	
 // setup the initial state if Bubbledocs is empty
    private static void setupIfNeed(Bubbledocs bubbleapp) {
		if (bubbleapp.getUsersSet().isEmpty())
		    SetupDomain.populateDomain();
    }
    
    @Atomic
    private static ArrayList <org.jdom2.Document> exportFeature()
    {
    	ArrayList <org.jdom2.Document> docList = new ArrayList <org.jdom2.Document>(); 
 
    	try {
			User u = bubbleapp.findUser("pf");
			
	    	org.jdom2.Document doc;
	    	
	    	for(Spreadsheet s : u.getCreatedDocsSet()){
	    	
	    	    System.out.println (s.getName());
	    		doc = exportToXML(s);
	    		docList.add(doc);
	    		printDomainInXML(doc);
	    	}
		} catch (UserNotFoundException e) {
			System.out.println(e.getMessage());
		}
    	return docList;
    }
    
    public static org.jdom2.Document exportToXML(Spreadsheet spreadsheet){
    	org.jdom2.Document jdomDoc = new org.jdom2.Document();
    	jdomDoc.setRootElement(spreadsheet.exportToXML());
    	
    	return jdomDoc;
    }
    
    public static void printDomainInXML(org.jdom2.Document jdomDoc) {
		XMLOutputter xml = new XMLOutputter();
		xml.setFormat(Format.getPrettyFormat());
		System.out.println(xml.outputString(jdomDoc));
    }
    

    @Atomic
    private static void importFromXML(org.jdom2.Document jdomDoc) {
		bubbleapp.importFromXML(jdomDoc.getRootElement());
    }
	
	
    @Atomic
	public static void deleteSpreadsheet() {
		for (Spreadsheet s : bubbleapp.getDocsSet()) {
			if (s.getCreator().getUsername().equals("pf") && s.getName().equals("Notas ES")) {
				bubbleapp.removeDocs(s);
                s.delete();
			}
		}
	}
	
	@Atomic
	public static void printUsers()
	{
		if (bubbleapp.getUsersSet().isEmpty())
		{
			System.out.println("No users were found.");
		}
		for (User user : bubbleapp.getUsersSet()) 
		{
			System.out.println("BubbleDocs User: " + user.getUsername());
			System.out.println("\t Name: " + user.getName());
			System.out.println("\t Password: " + user.getPassword());
		}
	}

	@Atomic
	public static void printSpreadsheets(String username)
	{
		User user;
		try {
			user = bubbleapp.findUser(username);
			
			if (user.getCreatedDocsSet().isEmpty())
			{
				System.out.println("No spreadsheets were created by: "+ user.getUsername() + ".");
			}
			else 
			{
				System.out.println("Documents created by: " + user.getUsername());
				
				for(Spreadsheet spreadsheet: user.getCreatedDocsSet()) 
				{
					System.out.println("\t - " + spreadsheet.getName());
				}
			}
				
			
			
		} catch (UserNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	
		
	}

	@Atomic
	public static void printSpreadsheetsID(String username)
	{
		User user;
		try {
			user = bubbleapp.findUser(username);
			
			if (user.getCreatedDocsSet().isEmpty())
			{
				System.out.println("No spreadsheets were created by: "+ user.getUsername() + ".");
			}
			else
			{
				System.out.println("Documents created by: " + user.getUsername());
				
			
				for(Spreadsheet spreadsheet: user.getCreatedDocs()) 
				{
					System.out.println("\t - " + spreadsheet.getName() + ", id = " + spreadsheet.getID());
				}
			}
		} catch (UserNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		
	}	
	
}