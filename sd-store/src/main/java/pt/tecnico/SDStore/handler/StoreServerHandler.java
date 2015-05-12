package pt.tecnico.SDStore.handler;

import java.util.*;

import javax.xml.namespace.QName;
import javax.xml.soap.*;
import javax.xml.ws.handler.*;
import javax.xml.ws.handler.MessageContext.Scope;
import javax.xml.ws.handler.soap.*;

public class StoreServerHandler implements SOAPHandler<SOAPMessageContext> {

    public static final String REQUEST_PROPERTY = "request.pt.ulisboa.tecnico.sdis.store.ws.SDStore";
    public static final String RESPONSE_PROPERTY = "response.pt.ulisboa.tecnico.sdis.store.ws.SDStore";

    public static final String REQUEST_HEADER = "clientRequest";
    public static final String REQUEST_NS = "tag";

    public static final String RESPONSE_HEADER = "storeResponse";
    public static final String RESPONSE_NS = "tag";

    public static final String CLASS_NAME = StoreServerHandler.class.getSimpleName();

    public boolean handleMessage(SOAPMessageContext smc) {
        Boolean outbound = (Boolean) smc.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        if (outbound) {
            String propertyValue = (String) smc.get(RESPONSE_PROPERTY);

            try {
                SOAPMessage msg = smc.getMessage();
                SOAPPart sp = msg.getSOAPPart();
                SOAPEnvelope se = sp.getEnvelope();

                // add header
                SOAPHeader sh = se.getHeader();
                if (sh == null)
                    sh = se.addHeader();

                Name name = se.createName(RESPONSE_HEADER, "e", RESPONSE_NS);
                SOAPHeaderElement element = sh.addHeaderElement(name);

                String[] args = null;
                String newValue;
                
                if(propertyValue!=null){
                    if(propertyValue.equals("ack"))
                        newValue = "ack";
                    else {
                	   args = propertyValue.split(";");
                	   newValue = "Seq Number:"+args[0]+";"+"client:"+args[1];
                	   }
                }else
                	newValue = "Seq Number:"+args+";"+"client:"+args;
                    
                element.addTextNode(newValue);


            } catch (SOAPException e) {
                System.out.printf("Failed to add SOAP header because of %s%n", e);
            }


        } else {
            // inbound message
            try {
                SOAPMessage msg = smc.getMessage();
                SOAPPart sp = msg.getSOAPPart();
                SOAPEnvelope se = sp.getEnvelope();
                SOAPHeader sh = se.getHeader();

                if (sh == null) {
                    System.out.println("Header not found.");
                    return true;
                }

                Name name = se.createName(REQUEST_HEADER, "e", REQUEST_NS);
                Iterator it = sh.getChildElements(name);
                
                if (!it.hasNext()) {
                    System.out.printf("Header element %s not found.%n", REQUEST_HEADER);
                    return true;
                }
                SOAPElement element = (SOAPElement) it.next();

                String headerValue = element.getValue();
                String newValue = headerValue + "";
                smc.put(REQUEST_PROPERTY, newValue);
                // set property scope to application so that server class can access property
                smc.setScope(REQUEST_PROPERTY, Scope.APPLICATION);

            } catch (SOAPException e) {
                System.out.printf("Failed to get SOAP header because of %s%n", e);
            }

        }

        return true;
    }

    public boolean handleFault(SOAPMessageContext smc) {
        return true;
    }

    public Set<QName> getHeaders() {
        return null;
    }

    public void close(MessageContext messageContext) {
    }

}
