package sec.blockfs.blockclient;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;

import sec.blockfs.blocklibrary.BlockLibraryImpl;
import sec.blockfs.blocklibrary.InitializationFailureException;
import sec.blockfs.blockserver.FileSystemImpl;
import sec.blockfs.blockutility.BlockUtility;
import sec.blockfs.blockutility.DataIntegrityFailureException;

public class PublicBlockAttackTwoFaults {
    public static void main(String[] args) throws Exception {
        String servicePort = args[0];
        String serviceName = args[1];
        String serviceUrl = args[2];
        String numFaults = args[3];

        BlockLibraryImpl library = null;
        try {
            library = new BlockLibraryImpl(serviceName, servicePort, serviceUrl, numFaults);
            // the cache needs to be disabled, otherwise the error is masked since the public key block isn't read twice
            library.ENABLE_CACHE = false;
            library.FS_init();
        } catch (InitializationFailureException e) {
            System.out.println("Error - " + e.getMessage());
            return;
        }

        String text = BlockUtility.generateString(BlockUtility.BLOCK_SIZE);
        byte[] textBytes = text.getBytes();
        library.FS_write(0, textBytes.length, textBytes);

        // get public key blocks
        String fileName = BlockUtility.getKeyString(BlockUtility.digest(library.publicKey.getEncoded()));
        String filePath = FileSystemImpl.BASE_PATH + "-0" + File.separatorChar + fileName;
        FileInputStream stream;
        
        // this might fail because the server thread didn't write yet
        // it's not an error, the tests need to change the file to simulate an attack
        // waiting a second will probably give enough time to the thread to complete
        try {
            stream = new FileInputStream(filePath);
        } catch (FileNotFoundException e) {
            Thread.sleep(1000);
            stream = new FileInputStream(filePath);
        }
        
        byte[] publicKeyBlock = new byte[BlockUtility.SIGNATURE_SIZE + BlockUtility.DIGEST_SIZE];
        stream.read(publicKeyBlock, 0, publicKeyBlock.length);
        stream.close();

        byte[] alteredTextBytes = BlockUtility.generateString(BlockUtility.BLOCK_SIZE).getBytes();
        byte[] alteredHash = BlockUtility.digest(alteredTextBytes);

        // write new block in first server
        String newBlockName = BlockUtility.getKeyString(alteredHash);
        String firstBlockPath = FileSystemImpl.BASE_PATH + "-0" + File.separatorChar + newBlockName;
        FileOutputStream outStream = new FileOutputStream(firstBlockPath);
        outStream.write(alteredTextBytes);
        outStream.close();

        // write new block in second server
        String secondBlockPath = FileSystemImpl.BASE_PATH + "-1" + File.separatorChar + newBlockName;
        outStream = new FileOutputStream(secondBlockPath);
        outStream.write(alteredTextBytes);
        outStream.close();

        // rewrite public key blocks to ignore the previous blocks and point to the new ones
        byte[] rewrittenPublicKeyBlock = new byte[BlockUtility.SIGNATURE_SIZE + BlockUtility.DIGEST_SIZE];
        System.arraycopy(publicKeyBlock, 0, rewrittenPublicKeyBlock, 0, BlockUtility.SIGNATURE_SIZE);
        System.arraycopy(alteredHash, 0, rewrittenPublicKeyBlock, BlockUtility.SIGNATURE_SIZE, BlockUtility.DIGEST_SIZE);
        outStream = new FileOutputStream(filePath);
        outStream.write(rewrittenPublicKeyBlock);
        outStream.close();

        outStream = new FileOutputStream(FileSystemImpl.BASE_PATH + "-1" + File.separatorChar + fileName);
        outStream.write(rewrittenPublicKeyBlock);
        outStream.close();

        byte[] readBuffer = new byte[BlockUtility.BLOCK_SIZE];

        try {
            library.FS_read(library.publicKey.getEncoded(), 0, BlockUtility.BLOCK_SIZE, readBuffer);
            assert Arrays.equals(textBytes, readBuffer) : "Read data was different from expected.";
            System.out.println("Successfuly read data.");
        } catch (DataIntegrityFailureException e) {
            System.out.println("Couldn't read data. " + e.getMessage());
        }

    }
}
