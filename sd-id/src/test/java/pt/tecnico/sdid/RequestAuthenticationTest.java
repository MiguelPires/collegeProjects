package pt.tecnico.sdid;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import pt.ulisboa.tecnico.sdis.id.ws.AuthReqFailed_Exception;

// WSDL contract test - Authentication Service
public class RequestAuthenticationTest extends SDIdServiceTest {

    private static final String USERNAME = "alice";
    private static final byte[] PW_BYTE = "Aaa1".getBytes();
    private static final byte[] WPW_BYTE = "aaa3".getBytes();
    private byte[] result;

    @SuppressWarnings("unused")
    private String generateEncodedKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        SecretKey newKey = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(newKey.getEncoded());
    }

    private SecretKey getDecodedKey() {
        String encodedKey = System.getProperty("test.key");
        
        // decode the base64 encoded string
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        // rebuild key using SecretKeySpec
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    @Test
    public void success() throws Exception {

        // get a AES cipher object
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

        // encrypt using the key and the plaintext
        cipher.init(Cipher.ENCRYPT_MODE, getDecodedKey());
        byte[] cipherBytes = cipher.doFinal(PW_BYTE);

        // create XML document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        // create root node 
        Element request = doc.createElement("Request");
        doc.appendChild(request);
        
        // append children nodes
        Element server = doc.createElement("Server");
        request.appendChild(server);
        Element nonce = doc.createElement("Nonce");
        request.appendChild(nonce);

        // append text to children nodes
        server.appendChild(doc.createTextNode("SD-STORE"));
        
        Date d = new Date();
        nonce.appendChild(doc.createTextNode(d.toString()));
        
        // write to byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Result res = new StreamResult(bos);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(doc), res);
        byte[] docBytes = bos.toByteArray();
        
        result = cServer.requestAuthentication(USERNAME, docBytes);
        byte[] byteTrue = new byte[1];
        byteTrue[0] = (byte) 1;

        assertEquals(byteTrue[0], result[0]);
    }

    @Test(expected = AuthReqFailed_Exception.class)
    public void userDoesNotExist() throws AuthReqFailed_Exception {
        cServer.requestAuthentication("francisco", PW_BYTE);
    }

    @Test(expected = AuthReqFailed_Exception.class)
    public void wrongPassword() throws AuthReqFailed_Exception {
        cServer.requestAuthentication(USERNAME, WPW_BYTE);
    }
}
