package com.asanasoft.common.service.crypto;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.CompressionAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.PBEDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.jcajce.*;
import org.bouncycastle.util.io.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;

/**
 * This class is based on the Bouncy Castle example classes PBEFileProcessor and PGPExampleUtil. This class encrypts
 * using a passphrase, but I will add code to use keys, just in case we need it...
 *
 */
public class PGPUtils {
    private static Logger logger = LoggerFactory.getLogger(PGPUtils.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void decryptFile(String inputFileName, char[] passPhrase)
            throws IOException, NoSuchProviderException, PGPException {
        InputStream in = new BufferedInputStream(new FileInputStream(inputFileName));
        decryptFile(in, passPhrase);
        in.close();
    }

    /*
     * decrypt the passed in message stream
     */
    public static void decryptFile(
            InputStream in,
            char[] passPhrase)
            throws IOException, NoSuchProviderException, PGPException {
        in = PGPUtil.getDecoderStream(in);

        JcaPGPObjectFactory pgpF = new JcaPGPObjectFactory(in);
        PGPEncryptedDataList enc;
        Object object = pgpF.nextObject();

        //
        // the first object might be a PGP marker packet.
        //
        if (object instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) object;
        } else {
            enc = (PGPEncryptedDataList) pgpF.nextObject();
        }

        PGPPBEEncryptedData pbe = (PGPPBEEncryptedData) enc.get(0);

        InputStream clear = pbe.getDataStream(new JcePBEDataDecryptorFactoryBuilder(new JcaPGPDigestCalculatorProviderBuilder().setProvider("BC").build()).setProvider("BC").build(passPhrase));

        JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(clear);

        //
        // if we're trying to read a file generated by someone other than us
        // the data might not be compressed, so we check the return type from
        // the factory and behave accordingly.
        //
        object = pgpFact.nextObject();
        if (object instanceof PGPCompressedData) {
            PGPCompressedData cData = (PGPCompressedData) object;

            pgpFact = new JcaPGPObjectFactory(cData.getDataStream());

            object = pgpFact.nextObject();
        }

        PGPLiteralData ld = (PGPLiteralData) object;
        InputStream unc = ld.getInputStream();

        OutputStream fOut = new BufferedOutputStream(new FileOutputStream(ld.getFileName()));

        Streams.pipeAll(unc, fOut);

        fOut.close();

        if (pbe.isIntegrityProtected()) {
            if (!pbe.verify()) {
                System.err.println("message failed integrity check");
            } else {
                System.err.println("message integrity check passed");
            }
        } else {
            System.err.println("no message integrity check");
        }
    }

    public static void encryptFile(
            String outputFileName,
            String inputFileName,
            char[] passPhrase,
            boolean armor,
            boolean withIntegrityCheck)
            throws IOException, NoSuchProviderException {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFileName));
        encryptFile(out, inputFileName, passPhrase, armor, withIntegrityCheck);
        out.close();
    }

    public static void encryptFile(
            OutputStream out,
            String fileName,
            char[] passPhrase,
            boolean armor,
            boolean withIntegrityCheck)
            throws IOException, NoSuchProviderException {
        if (armor) {
            out = new ArmoredOutputStream(out);
        }

        try {
            byte[] compressedData = compressFile(fileName, CompressionAlgorithmTags.ZIP);

            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(new JcePGPDataEncryptorBuilder(PGPEncryptedData.CAST5)
                    .setWithIntegrityPacket(withIntegrityCheck).setSecureRandom(new SecureRandom()).setProvider("BC"));

            encGen.addMethod(new JcePBEKeyEncryptionMethodGenerator(passPhrase).setProvider("BC"));

            OutputStream encOut = encGen.open(out, compressedData.length);

            encOut.write(compressedData);
            encOut.close();

            if (armor) {
                out.close();
            }
        } catch (PGPException e) {
            System.err.println(e);
            if (e.getUnderlyingException() != null) {
                e.getUnderlyingException().printStackTrace();
            }
        }
    }

    static byte[] compressFile(String fileName, int algorithm) throws IOException {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        PGPCompressedDataGenerator comData = new PGPCompressedDataGenerator(algorithm);
        PGPUtil.writeFileToLiteralData(comData.open(bOut), PGPLiteralData.BINARY,
                new File(fileName));
        comData.close();
        return bOut.toByteArray();
    }

    /**
     * Search a secret key ring collection for a secret key corresponding to keyID if it
     * exists.
     *
     * @param pgpSec a secret key ring collection.
     * @param keyID  keyID we want.
     * @param pass   passphrase to decrypt secret key with.
     * @return the private key.
     * @throws PGPException
     * @throws NoSuchProviderException
     */
    static PGPPrivateKey findSecretKey(PGPSecretKeyRingCollection pgpSec, long keyID, char[] pass)
            throws PGPException, NoSuchProviderException {
        PGPSecretKey pgpSecKey = pgpSec.getSecretKey(keyID);

        if (pgpSecKey == null) {
            return null;
        }

        return pgpSecKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(pass));
    }

    static PGPPublicKey readPublicKey(String fileName) throws IOException, PGPException {
        InputStream keyIn = new BufferedInputStream(new FileInputStream(fileName));
        PGPPublicKey pubKey = readPublicKey(keyIn);
        keyIn.close();

        return pubKey;
    }

    /**
     * A simple routine that opens a key ring file and loads the first available key
     * suitable for encryption.
     *
     * @param input data stream containing the public key data
     * @return the first public key found.
     * @throws IOException
     * @throws PGPException
     */
    static PGPPublicKey readPublicKey(InputStream input) throws IOException, PGPException {
        PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(
                PGPUtil.getDecoderStream(input), new JcaKeyFingerprintCalculator());

        //
        // we just loop through the collection till we find a key suitable for encryption, in the real
        // world you would probably want to be a bit smarter about this.
        //

        Iterator keyRingIter = pgpPub.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPPublicKeyRing keyRing = (PGPPublicKeyRing) keyRingIter.next();

            Iterator keyIter = keyRing.getPublicKeys();
            while (keyIter.hasNext()) {
                PGPPublicKey key = (PGPPublicKey) keyIter.next();

                if (key.isEncryptionKey()) {
                    return key;
                }
            }
        }

        throw new IllegalArgumentException("Can't find encryption key in key ring.");
    }

    static PGPSecretKey readSecretKey(String fileName) throws IOException, PGPException {
        InputStream keyIn = new BufferedInputStream(new FileInputStream(fileName));
        PGPSecretKey secKey = readSecretKey(keyIn);
        keyIn.close();
        return secKey;
    }

    /**
     * A simple routine that opens a key ring file and loads the first available key
     * suitable for signature generation.
     *
     * @param input stream to read the secret key ring collection from.
     * @return a secret key.
     * @throws IOException  on a problem with using the input stream.
     * @throws PGPException if there is an issue parsing the input stream.
     */
    static PGPSecretKey readSecretKey(InputStream input) throws IOException, PGPException {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(input), new JcaKeyFingerprintCalculator());

        //
        // we just loop through the collection till we find a key suitable for encryption, in the real
        // world you would probably want to be a bit smarter about this.
        //

        Iterator keyRingIter = pgpSec.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPSecretKeyRing keyRing = (PGPSecretKeyRing) keyRingIter.next();

            Iterator keyIter = keyRing.getSecretKeys();
            while (keyIter.hasNext()) {
                PGPSecretKey key = (PGPSecretKey) keyIter.next();

                if (key.isSigningKey()) {
                    return key;
                }
            }
        }

        throw new IllegalArgumentException("Can't find signing key in key ring.");
    }

    public static byte[] createPbeEncryptedObject(char[] passwd, byte[] data)
            throws PGPException, IOException
    {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();

        OutputStream pOut = lData.open(bOut,
                PGPLiteralData.BINARY,
                PGPLiteralData.CONSOLE,
                data.length,
                new Date());

        pOut.write(data);
        pOut.close();

        byte[] plainText = bOut.toByteArray();

        ByteArrayOutputStream encOut = new ByteArrayOutputStream();

        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(
                new JcePGPDataEncryptorBuilder(
                        SymmetricKeyAlgorithmTags.AES_128)
                        .setWithIntegrityPacket(true)
                        .setSecureRandom(new SecureRandom())
                        .setProvider("BC"));

        encGen.addMethod(new JcePBEKeyEncryptionMethodGenerator(passwd).setProvider("BC"));

        OutputStream cOut = encGen.open(encOut, plainText.length);
        cOut.write(plainText);
        cOut.close();

        return encOut.toByteArray();
    }

    public static byte[] extractPbeEncryptedObject(char[] passwd, byte[] pgpEncryptedData)
            throws PGPException, IOException
    {
        PGPObjectFactory pgpFact = new JcaPGPObjectFactory(pgpEncryptedData);
        PGPEncryptedDataList encList = (PGPEncryptedDataList)pgpFact.nextObject();
        PGPPBEEncryptedData encData = (PGPPBEEncryptedData)encList.get(0);

        PBEDataDecryptorFactory dataDecryptorFactory = new JcePBEDataDecryptorFactoryBuilder(
                new JcaPGPDigestCalculatorProviderBuilder()
                        .setProvider("BC").build())
                .setProvider("BC").build(passwd);

        InputStream clear = encData.getDataStream(dataDecryptorFactory);
        byte[] literalData = Streams.readAll(clear);

        if (encData.verify())
        {
            PGPObjectFactory litFact = new JcaPGPObjectFactory(literalData);

            Object pgpObject = litFact.nextObject();

            if (pgpObject instanceof PGPCompressedData) {
                PGPCompressedData cData = (PGPCompressedData) pgpObject;
                litFact = new JcaPGPObjectFactory(cData.getDataStream());
                pgpObject = litFact.nextObject();
            }
            PGPLiteralData litData = (PGPLiteralData)pgpObject;
            byte[] data = Streams.readAll(litData.getInputStream());
            return data;
        }

        throw new IllegalStateException("modification check failed");
    }
}