package sec.blockfs.blocklibrary;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.rmi.AccessException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.Signature;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import sec.blockfs.blockserver.DataIntegrityFailureException;
import sec.blockfs.blockserver.FileSystemImpl;
import sec.blockfs.blockserver.ServerImpl;
import sec.blockfs.blockutility.BlockUtility;
import sec.blockfs.blockutility.OperationFailedException;

public class LibraryTest {
    private static String servicePort = System.getProperty("service.port");
    private static String serviceName = System.getProperty("service.name");
    private static String serviceUrl = System.getProperty("service.url");
    private Registry registry;

    @Before
    public void setUp() throws NumberFormatException, RemoteException {
        try {
            registry = LocateRegistry.createRegistry(new Integer(servicePort));
            registry.rebind(serviceName, new ServerImpl());
        } catch (Exception e) {
            return;
        }
    }

    @After
    public void tearDown() throws AccessException, RemoteException, NotBoundException, MalformedURLException {
        if (registry != null) {
            try {
                Naming.unbind(serviceName);
            } catch (Exception e) {
                return;
            }
            try {

                registry.unbind(serviceName);
            } catch (Exception e) {
                return;
            }
        }
    }

    @Test
    public void successCreateLib() throws Exception {
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
    }

    @Test(expected = InitializationFailureException.class)
    public void wrongPort() throws Exception {
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort + 1, serviceUrl);
    }

    @Test(expected = InitializationFailureException.class)
    public void wrongName() throws Exception {
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName + "abc", servicePort, serviceUrl);
    }

    @Test
    public void successPerformWrite() throws Exception {
        String text = "Some random write";
        byte[] textBytes = text.getBytes();
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, textBytes.length, textBytes);
    }

    @Test
    public void successEmptyWrite() throws Exception {
        byte[] textBytes = "".getBytes();
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, textBytes.length, textBytes);
    }

    @Test(expected = OperationFailedException.class)
    public void failNullWrite() throws Exception {
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, 0, null);
    }

    @Test(expected = OperationFailedException.class)
    public void failNegativeSizeArgument() throws Exception {
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, -1, "".getBytes());
    }

    @Test(expected = OperationFailedException.class)
    public void failNegativeOffsetArgument() throws Exception {
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(-1, 0, "".getBytes());
    }

    @Test
    public void publicKeyBlockCheck() throws Exception {
        // write a message
        String text = "Some random content";
        byte[] textBytes = text.getBytes();
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, textBytes.length, textBytes);

        // compute hash of public key
        byte[] keyDigest = BlockUtility.digest(library.publicKey.getEncoded());
        String fileName = BlockUtility.getKeyString(keyDigest);

        // check for public key block
        String filePath = FileSystemImpl.BASE_PATH + File.separatorChar + fileName;
        File file = new File(filePath);
        assertTrue("Public key block '" + filePath + "' doesn't exist", file.exists());
    }

    @Test
    public void dataBlockCheck() throws Exception {
        // write a message
        String text = "Some random content";
        byte[] textBytes = text.getBytes();
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, textBytes.length, textBytes);

        byte[] data = new byte[BlockUtility.BLOCK_SIZE];
        System.arraycopy(textBytes, 0, data, 0, textBytes.length);

        // compute hash of data
        byte[] dataDigest = BlockUtility.digest(data);
        String fileName = BlockUtility.getKeyString(dataDigest);

        // check for data block
        String filePath = FileSystemImpl.BASE_PATH + File.separatorChar + fileName;
        File file = new File(filePath);
        assertTrue("Data block '" + filePath + "' doesn't exist", file.exists());
    }

    @Test
    public void publicKeyBlockContentsCheck() throws Exception {
        // write a message
        String text = "Some random content";
        byte[] textBytes = text.getBytes();
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, textBytes.length, textBytes);

        byte[] buffer = new byte[textBytes.length];
        int bytesRead = library.FS_read(library.publicKey.getEncoded(), 0, textBytes.length, buffer);
        assertTrue("Read returned wrong data: " + Arrays.toString(buffer) + "; Expected: " + Arrays.toString(textBytes),
                Arrays.equals(buffer, textBytes));
        assertTrue("Read wrong ammount of data. Should've read " + textBytes.length + " bytes instead of " + bytesRead,
                bytesRead == textBytes.length);

        // compute hash of public key
        byte[] keyDigest = BlockUtility.digest(library.publicKey.getEncoded());
        String fileName = BlockUtility.getKeyString(keyDigest);
        String filePath = FileSystemImpl.BASE_PATH + File.separatorChar + fileName;
        FileInputStream stream = new FileInputStream(filePath);
        byte[] publicBlock = new byte[BlockUtility.SIGNATURE_SIZE + BlockUtility.DIGEST_SIZE];
        stream.read(publicBlock);
        stream.close();

        // extract data
        byte[] publicKeyData = new byte[BlockUtility.DIGEST_SIZE];
        System.arraycopy(publicBlock, BlockUtility.SIGNATURE_SIZE, publicKeyData, 0, publicKeyData.length);
        byte[] dataBlock = new byte[BlockUtility.BLOCK_SIZE];
        System.arraycopy(textBytes, 0, dataBlock, 0, textBytes.length);
        assertTrue("Public key block contains wrong data", Arrays.equals(publicKeyData, BlockUtility.digest(dataBlock)));
    }

    @Test
    public void dataBlockContentsCheck() throws Exception {
        // write a message
        String text = "Some random content";
        byte[] textBytes = text.getBytes();
        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, textBytes.length, textBytes);

        // hash of data - expected contents
        byte[] data = new byte[BlockUtility.BLOCK_SIZE];
        System.arraycopy(textBytes, 0, data, 0, textBytes.length);
        String fileName = BlockUtility.getKeyString(BlockUtility.digest(data));
        String filePath = FileSystemImpl.BASE_PATH + File.separatorChar + fileName;

        // verify data block contents
        FileInputStream stream = new FileInputStream(filePath);
        byte[] buffer = new byte[textBytes.length];
        stream.read(buffer, 0, textBytes.length);
        stream.close();
        assertTrue("Data block contains wrong data", Arrays.equals(buffer, textBytes));
    }

    @Test
    public void twoBlocksDataCheck() throws Exception {
        String text = "Start_" + BlockUtility.generateString(BlockUtility.BLOCK_SIZE) + "_End";
        byte[] textBytes = text.getBytes();
        System.out.println("Expected: " + Arrays.toString(textBytes));

        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, textBytes.length, textBytes);

        byte[] readBytes = new byte[textBytes.length];
        int bytesRead = library.FS_read(library.publicKey.getEncoded(), 0, textBytes.length, readBytes);

        assertTrue("The written and read bytes don't match", Arrays.equals(textBytes, readBytes));
        assertTrue("Read wrong ammount of data. Should've read " + textBytes.length + " bytes instead of " + bytesRead,
                bytesRead == textBytes.length);

        byte[] firstBlock = new byte[BlockUtility.BLOCK_SIZE];
        System.arraycopy(textBytes, 0, firstBlock, 0, BlockUtility.BLOCK_SIZE);
        byte[] secondBlock = new byte[BlockUtility.BLOCK_SIZE];
        System.arraycopy(textBytes, BlockUtility.BLOCK_SIZE, secondBlock, 0, textBytes.length - BlockUtility.BLOCK_SIZE);

        String firstBlockName = BlockUtility.getKeyString(BlockUtility.digest(firstBlock));
        String secondBlockName = BlockUtility.getKeyString(BlockUtility.digest(secondBlock));

        // verify the first block's data
        String filePath = FileSystemImpl.BASE_PATH + File.separatorChar + firstBlockName;
        FileInputStream stream = new FileInputStream(filePath);
        byte[] buffer = new byte[BlockUtility.BLOCK_SIZE];
        stream.read(buffer, 0, BlockUtility.BLOCK_SIZE);
        stream.close();

        assertTrue("First block contains wrong data", Arrays.equals(buffer, firstBlock));

        // verify the second block's data
        filePath = FileSystemImpl.BASE_PATH + File.separatorChar + secondBlockName;
        stream = new FileInputStream(filePath);
        buffer = new byte[BlockUtility.BLOCK_SIZE];
        stream.read(buffer, 0, textBytes.length - BlockUtility.BLOCK_SIZE);
        stream.close();

        assertTrue("Second block contains wrong data", Arrays.equals(buffer, secondBlock));
    }
    
    @Test
    public void twoBlocksPublicCheck() throws Exception {
        String text = "Start_" + BlockUtility.generateString(BlockUtility.BLOCK_SIZE) + "_End";
        byte[] textBytes = text.getBytes();
   //     System.out.println("Expected: " + Arrays.toString(textBytes));

        BlockLibrary library = new BlockLibrary();
        library.FS_init(serviceName, servicePort, serviceUrl);
        library.FS_write(0, textBytes.length, textBytes);

        // get public key block
        String fileName = BlockUtility.getKeyString(BlockUtility.digest(library.publicKey.getEncoded()));
        String filePath = FileSystemImpl.BASE_PATH+File.separatorChar+fileName;
        FileInputStream stream = new FileInputStream(filePath);
        byte[] publicKeyBlock = new byte[BlockUtility.SIGNATURE_SIZE+BlockUtility.DIGEST_SIZE*2];
        stream.read(publicKeyBlock, 0, publicKeyBlock.length);
        stream.close();
        
        // compute data block hashes
        byte[] firstBlock = new byte[BlockUtility.BLOCK_SIZE];
        System.arraycopy(textBytes, 0, firstBlock, 0, BlockUtility.BLOCK_SIZE);
        byte[] secondBlock = new byte[BlockUtility.BLOCK_SIZE];
        System.arraycopy(textBytes, BlockUtility.BLOCK_SIZE, secondBlock, 0, textBytes.length - BlockUtility.BLOCK_SIZE);

        byte[] firstBlockHash = BlockUtility.digest(firstBlock);
        byte[] secondBlockHash = BlockUtility.digest(secondBlock);

        byte[] blockHashes = new byte[BlockUtility.DIGEST_SIZE*2];
        System.arraycopy(firstBlockHash, 0, blockHashes, 0, BlockUtility.DIGEST_SIZE);
        System.arraycopy(secondBlockHash, 0, blockHashes, BlockUtility.DIGEST_SIZE, BlockUtility.DIGEST_SIZE);
        
        // create signature
        Signature signAlgorithm = Signature.getInstance("SHA512withRSA", "SunRsaSign");
        signAlgorithm.initSign(library.privateKey);
        signAlgorithm.update(blockHashes, 0, blockHashes.length);
        byte[] keyBlockSignature = signAlgorithm.sign();
        
        byte[] storedSignature = new byte[BlockUtility.SIGNATURE_SIZE]; 
        System.arraycopy(publicKeyBlock, 0, storedSignature, 0, BlockUtility.SIGNATURE_SIZE);
        assertTrue("The public block's signature is wrong", Arrays.equals(keyBlockSignature, storedSignature));
        
        byte[] storedFirstHash = new byte[BlockUtility.DIGEST_SIZE];
        System.arraycopy(publicKeyBlock, BlockUtility.SIGNATURE_SIZE, storedFirstHash, 0, BlockUtility.DIGEST_SIZE);
        assertTrue("The first block's hash is wrong", Arrays.equals(storedFirstHash, firstBlockHash));
        
        byte[] storedSecondHash = new byte[BlockUtility.DIGEST_SIZE];
        System.arraycopy(publicKeyBlock, BlockUtility.SIGNATURE_SIZE+BlockUtility.DIGEST_SIZE, storedSecondHash, 0, BlockUtility.DIGEST_SIZE);
        assertTrue("The second block's hash is wrong", Arrays.equals(storedSecondHash, secondBlockHash));
    }
    
    @Test(expected=DataIntegrityFailureException.class)
    public void publicKeyBlockAttack() {
        // TODO: try to alter a hash of the pkb 
    }
    
    @Test
    public void dataBlockAttack() {
        // TODO: try to alter the contents of the data block
    }
}
