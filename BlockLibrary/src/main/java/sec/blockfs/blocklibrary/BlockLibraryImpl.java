package sec.blockfs.blocklibrary;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import sec.blockfs.blockutility.BlockLibrary;
import sec.blockfs.blockutility.BlockServer;
import sec.blockfs.blockutility.BlockUtility;
import sec.blockfs.blockutility.DataIntegrityFailureException;
import sec.blockfs.blockutility.OperationFailedException;
import sec.blockfs.blockutility.ServerErrorException;
import sec.blockfs.blockutility.WrongArgumentsException;;

public class BlockLibraryImpl extends UnicastRemoteObject implements BlockLibrary {

    // these attributes are only public because of the tests
    public PrivateKey privateKey;
    public PublicKey publicKey;
    public boolean ENABLE_CACHE = true;
    public static int NUM_REPLICAS = 4;
    public static int NUM_FAULTS = 1;

    private Signature signAlgorithm;
    private int writeTimestamp = 0;
    private List<BlockServer> blockServers = new ArrayList<BlockServer>();
    private byte[] hashesCache = null;
    private int libraryPort;
    private String libraryName;
    private String libraryUrl;

    public BlockLibraryImpl(String serviceName, String servicePort, String serviceUrl, String numFaults)
            throws RemoteException, InitializationFailureException {
        String serverName = "none";
        String serverPort = "none";
        libraryUrl = serviceUrl;


        try {
            NUM_FAULTS = Integer.parseInt(numFaults);
            NUM_REPLICAS = 3 * NUM_FAULTS + 1;
            
            for (int i = 0; i < NUM_REPLICAS; ++i) {

                serverName = serviceName + i;
                Integer port = new Integer(servicePort) + new Integer(i);
                serverPort = port.toString();
                blockServers.add((BlockServer) Naming.lookup(serviceUrl + ":" + serverPort + "/" + serverName));
                System.out.println("Connected to server: " + serviceUrl + ":" + serverPort + "/" + serverName);
            }
        } catch (NotBoundException | RemoteException | MalformedURLException e) {
            throw new InitializationFailureException(
                    "Couldn't connect to server " + serviceUrl + ":" + serverPort + "/" + serverName);
        }
    }

    public String FS_init() throws InitializationFailureException {
        try {
            writeTimestamp = new Integer(0);
            // instantiate key generator
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "SunRsaSign");
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG", "SUN");
            keyGen.initialize(BlockUtility.KEY_SIZE, random);

            // generate keys
            KeyPair pair = keyGen.generateKeyPair();
            privateKey = pair.getPrivate();
            publicKey = pair.getPublic();

            // initialize signing algorithm
            signAlgorithm = Signature.getInstance("SHA512withRSA", "SunRsaSign");

            // the server needs to provide a challenge
            libraryPort = 9876 + (int) (Math.random() * 10000);
            libraryName = BlockUtility.generateString(6);
            Registry registry = LocateRegistry.createRegistry(libraryPort);
            registry.rebind(libraryName, this);

            byte[] keyDigest = BlockUtility.digest(publicKey.getEncoded());
            return BlockUtility.getKeyString(keyDigest);
        } catch (Exception e) {
            throw new InitializationFailureException("Couldn't connect to server");
        }
    }

    public void FS_write(int position, int size, byte[] contents)
            throws OperationFailedException, WrongArgumentsException, DataIntegrityFailureException {
        try {
            if (position < 0 || size < 0 || contents == null || size > contents.length)
                throw new WrongArgumentsException("Invalid arguments");

            int startBlock = position / (BlockUtility.BLOCK_SIZE + 1);
            int endBlock = (position + size) / (BlockUtility.BLOCK_SIZE + 1);

            final byte[][] toWriteBlocks = new byte[endBlock - startBlock + 1][BlockUtility.BLOCK_SIZE];
            byte[][] toWriteHashes = new byte[endBlock - startBlock + 1][BlockUtility.DIGEST_SIZE];

            int writtenBytes = 0, num = 0;
            for (int i = startBlock; i <= endBlock; ++i) {
                int bytesToWrite = size - writtenBytes > BlockUtility.BLOCK_SIZE ? BlockUtility.BLOCK_SIZE : size - writtenBytes;
                System.arraycopy(contents, writtenBytes, toWriteBlocks[num], 0, bytesToWrite);
                System.arraycopy(BlockUtility.digest(toWriteBlocks[num]), 0, toWriteHashes[num], 0, BlockUtility.DIGEST_SIZE);
                writtenBytes += bytesToWrite;
                ++num;
            }

            byte[] rewrittenBlock = null;
            try {

                final String publicKeyString;
                final byte[] blockHashes;

                // retrieve block, if cache is empty
                if (!ENABLE_CACHE || hashesCache == null) {
                    publicKeyString = BlockUtility.getKeyString(BlockUtility.digest(publicKey.getEncoded()));
                    blockHashes = readPublicKeyBlockHashes(publicKeyString);
                    if (ENABLE_CACHE) {
                        hashesCache = new byte[blockHashes.length];
                        System.arraycopy(blockHashes, 0, hashesCache, 0, blockHashes.length);
                    }
                } else {
                    blockHashes = hashesCache;
                }

                // rewrite
                int publicBlockSize = blockHashes.length / BlockUtility.DIGEST_SIZE;
                int newPublicBlockSize = publicBlockSize > endBlock + 1 ? publicBlockSize : endBlock + 1;

                rewrittenBlock = new byte[1 + newPublicBlockSize * BlockUtility.DIGEST_SIZE];
                ++writeTimestamp;
                rewrittenBlock[0] = (byte) writeTimestamp;

                num = 0;
                for (int i = 0; i < newPublicBlockSize; ++i) {
                    if (i >= startBlock && i >= endBlock)
                        System.arraycopy(toWriteHashes[num], 0, rewrittenBlock, 1 + i * BlockUtility.DIGEST_SIZE,
                                BlockUtility.DIGEST_SIZE);
                    else
                        System.arraycopy(blockHashes, 0, rewrittenBlock, 1 + i * BlockUtility.DIGEST_SIZE,
                                BlockUtility.DIGEST_SIZE);
                }
            } catch (FileNotFoundException e) {
                // write new public key block
                rewrittenBlock = new byte[1 + toWriteHashes.length * BlockUtility.DIGEST_SIZE];
                ++writeTimestamp;
                rewrittenBlock[0] = (byte) writeTimestamp;

                for (int i = 0; i < toWriteHashes.length; ++i)
                    System.arraycopy(toWriteHashes[i], 0, rewrittenBlock, 1 + i * BlockUtility.DIGEST_SIZE,
                            BlockUtility.DIGEST_SIZE);
            }

            writePublicKeyBlock(rewrittenBlock);
            writeContentBlocks(toWriteBlocks);

        } catch (WrongArgumentsException e) {
            throw e;
        } catch (DataIntegrityFailureException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("Library - Couldn't write to server: " + e.getMessage());
            e.printStackTrace();
            throw new OperationFailedException(e.getMessage());
        }
    }

    public int FS_read(final byte[] publicKey, int position, int size, byte[] buffer)
            throws OperationFailedException, DataIntegrityFailureException {
        if (position < 0 || size < 0 || buffer == null)
            throw new OperationFailedException("Invalid arguments");

        try {
            final String publicKeyString;
            final byte[] dataHashes;

            // retrieve block, if cache is empty
            if (!ENABLE_CACHE || hashesCache == null) {
                publicKeyString = BlockUtility.getKeyString(BlockUtility.digest(publicKey));
                dataHashes = readPublicKeyBlockHashes(publicKeyString);
                if (ENABLE_CACHE) {
                    hashesCache = new byte[dataHashes.length];
                    System.arraycopy(dataHashes, 0, hashesCache, 0, dataHashes.length);
                }
            } else {
                dataHashes = hashesCache;
            }

            int startBlock = position / (BlockUtility.BLOCK_SIZE + 1);
            int endBlock = (position + size) / (BlockUtility.BLOCK_SIZE + 1);

            final byte[][] blockHashes = new byte[endBlock - startBlock + 1][BlockUtility.DIGEST_SIZE];

            int blockCount = 0;
            for (int i = startBlock; i <= endBlock; ++i) {
                System.arraycopy(dataHashes, i * BlockUtility.DIGEST_SIZE, blockHashes[blockCount], 0, BlockUtility.DIGEST_SIZE);
                blockCount++;
            }

            buffer = readContentBlocks(startBlock, endBlock, size, blockHashes, buffer);
            return buffer.length;
        } catch (DataIntegrityFailureException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new OperationFailedException(e.getMessage());
        }
    }

    public synchronized byte[] challenge(Long nonce) {
        try {
            byte[] hashNonce = BlockUtility.digest(new byte[] { nonce.byteValue() });
            signAlgorithm.initSign(privateKey);
            signAlgorithm.update(hashNonce, 0, hashNonce.length);
            return signAlgorithm.sign();
        } catch (Exception e) {
            System.out.println("Failed signing nonce. " + e.getMessage());
            return null;
        }
    }

    private byte[] readPublicKeyBlockHashes(final String keyBlockName)
            throws FileNotFoundException, InterruptedException, DataIntegrityFailureException {

        final Semaphore readSemaphore;
        if (NUM_FAULTS != 0)
            readSemaphore = new Semaphore(-((int) Math.ceil((NUM_REPLICAS + NUM_FAULTS) / 2.0)) + 1);
        else
            readSemaphore = new Semaphore(0);

        final AtomicInteger faultyServers = new AtomicInteger(0);
        final ConcurrentHashMap<Integer, byte[]> readBlocks = new ConcurrentHashMap<>();

        for (final BlockServer replica : blockServers) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        byte[] publicBlock = replica.get(keyBlockName);
                        // obtain signature
                        byte[] storedSignature = new byte[BlockUtility.SIGNATURE_SIZE];
                        System.arraycopy(publicBlock, 0, storedSignature, 0, BlockUtility.SIGNATURE_SIZE);

                        // obtain data
                        int dataLength = publicBlock.length - BlockUtility.SIGNATURE_SIZE;
                        byte[] data = new byte[dataLength];
                        System.arraycopy(publicBlock, BlockUtility.SIGNATURE_SIZE, data, 0, dataLength);

                        // verify public key block integrity
                        if (!BlockUtility.verifyDataIntegrity(data, storedSignature, publicKey)) {
                            faultyServers.incrementAndGet();
                            return;
                        }

                        int timestamp = (byte) data[0];
                        byte[] hashes = new byte[data.length - 1];
                        System.arraycopy(data, 1, hashes, 0, hashes.length);
                        readBlocks.put(timestamp, hashes);
                    } catch (FileNotFoundException e) {
                    } catch (RemoteException | WrongArgumentsException | ServerErrorException e) {
                        faultyServers.incrementAndGet();
                    } finally {
                        readSemaphore.release();
                    }
                }
            }).start();
        }

        // wait for the (N+f)/2+1 fastest responses
        readSemaphore.acquire();

        if (faultyServers.get() == 1)
            System.out.println("Invalid public key block from one server");
        else if (faultyServers.get() > 1) {
            System.out.println("Invalid public key block from " + faultyServers.get() + " servers");
        }

        if (faultyServers.get() > NUM_FAULTS)
            throw new DataIntegrityFailureException("Couldn't obtain a valid quorum.");

        byte[] chosenBlockHashes = null;
        Integer chosenTimestamp = 0;
        for (Integer readTimestamp : readBlocks.keySet()) {
            if (readTimestamp > chosenTimestamp) {
                chosenTimestamp = readTimestamp;
                chosenBlockHashes = readBlocks.get(readTimestamp);
            }
        }

        // there is no public block or the ones returned aren't enough to ensure byzantine fault tolerance
        if (chosenBlockHashes == null)
            throw new FileNotFoundException();
        else
            return chosenBlockHashes;
    }

    private byte[] readContentBlocks(int firstBlockIndex, int lastBlockIndex, int size, final byte[][] blockHashes, byte[] buffer)
            throws InterruptedException, DataIntegrityFailureException {
        final AtomicInteger faultyServers = new AtomicInteger(0);
        final AtomicInteger num = new AtomicInteger(0);
        int readLength = 0;

        for (int i = firstBlockIndex; i <= lastBlockIndex; ++i) {
            final String dataBlockName = BlockUtility.getKeyString(blockHashes[num.get()]);
            final Semaphore dataBlockSemaphore;

            if (NUM_FAULTS != 0)
                dataBlockSemaphore = new Semaphore(-NUM_FAULTS);
            else
                dataBlockSemaphore = new Semaphore(0);

            final ConcurrentLinkedQueue<byte[]> dataBlocks = new ConcurrentLinkedQueue<>();

            for (final BlockServer replica : blockServers) {
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            byte[] data = replica.get(dataBlockName);

                            if (!Arrays.equals(blockHashes[num.get()], BlockUtility.digest(data))) {
                                faultyServers.incrementAndGet();
                                return;
                            } else {
                                dataBlocks.add(data);
                            }
                        } catch (Exception e) {
                            faultyServers.incrementAndGet();
                        } finally {
                            dataBlockSemaphore.release();
                        }
                    }
                }).start();
            }

            dataBlockSemaphore.acquire();

            if (faultyServers.get() == 1)
                System.out.println("Invalid data block from one server");
            else if (faultyServers.get() > 1)
                System.out.println("Invalid data block from " + faultyServers.get() + " servers");

            try {
                int dataLength = size - readLength > BlockUtility.BLOCK_SIZE ? BlockUtility.BLOCK_SIZE : size - readLength;
                System.arraycopy(dataBlocks.remove(), 0, buffer, readLength, dataLength);
                readLength += dataLength;
            } catch (NoSuchElementException e) {
                throw new DataIntegrityFailureException("Couldn't obtain a valid response for the data block.");
            }

            num.getAndIncrement();
        }
        return buffer;
    }

    private void writePublicKeyBlock(byte[] toWriteBlock) throws SignatureException, InvalidKeyException, InterruptedException {
        // sign public key block
        signAlgorithm.initSign(privateKey);
        signAlgorithm.update(toWriteBlock, 0, toWriteBlock.length);
        final byte[] keyBlockSignature = signAlgorithm.sign();

        final Semaphore putkSemaphore;
        if (NUM_FAULTS != 0)
            putkSemaphore = new Semaphore(-((int) Math.ceil((NUM_REPLICAS + NUM_FAULTS) / 2.0)) + 1);
        else
            putkSemaphore = new Semaphore(0);

        final byte[] rewrittenBlockCopy = toWriteBlock;

        for (final BlockServer replica : blockServers) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        replica.put_k(rewrittenBlockCopy, keyBlockSignature, publicKey.getEncoded(), libraryUrl, libraryName,
                                libraryPort);
                    } catch (Exception e) {
                    } finally {
                        putkSemaphore.release();
                    }
                }
            }).start();
        }

        // don't copy the timestamp
        hashesCache = new byte[toWriteBlock.length - 1];
        System.arraycopy(toWriteBlock, 1, hashesCache, 0, hashesCache.length);
        putkSemaphore.acquire();
    }

    private void writeContentBlocks(final byte[][] toWriteBlocks) throws InterruptedException {
        // since the blocks are immutable and self-verifying, we only need to ensure a simple quorum
        final Semaphore puthSemaphore;
        if (NUM_FAULTS != 0)
            puthSemaphore = new Semaphore(-(NUM_REPLICAS - NUM_FAULTS - 1));
        else
            puthSemaphore = new Semaphore(0);

        for (final BlockServer replica : blockServers) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        // write data blocks
                        for (int i = 0; i < toWriteBlocks.length; ++i) {
                            replica.put_h(toWriteBlocks[i]);
                        }
                    } catch (Exception e) {
                    } finally {
                        puthSemaphore.release();
                    }
                }
            }).start();
        }
        puthSemaphore.acquire();
    }
}
