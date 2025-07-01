package gg.grumble.core.client;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Date;

public class PKCS12Generator {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void generate(String filename, String alias, String name, String email) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Build the subject name (minimal CN)
        X500Name subject = new X500Name("CN=" + sanitizeName(name));

        // Validity
        Date notBefore = new Date(System.currentTimeMillis());
        Date notAfter = new Date(System.currentTimeMillis() + 20L * 365 * 24 * 60 * 60 * 1000);

        // Serial number
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        // Builder for certificate
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.getPublic());

        // Add SAN extension with name and email
        GeneralName emailName = new GeneralName(GeneralName.rfc822Name, email);
        GeneralName nameName = new GeneralName(GeneralName.directoryName, subject);

        GeneralNames subjectAltNames = new GeneralNames(new GeneralName[]{emailName, nameName});

        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

        // Content signer
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC")
                .build(keyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);

        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);

        // Create PKCS12 keystore without password
        KeyStore pkcs12 = KeyStore.getInstance("PKCS12");
        pkcs12.load(null, null);
        pkcs12.setKeyEntry(alias, keyPair.getPrivate(), null, new java.security.cert.Certificate[]{cert});

        try (FileOutputStream fos = new FileOutputStream(filename)) {
            pkcs12.store(fos, null); // null password
        }

        System.out.println("PKCS12 file generated: " + filename);
    }

    // Sanitize name for safe X.500 usage
    private static String sanitizeName(String input) {
        if (input == null) return "unknown";
        return input.replaceAll("[\\\\,+=<>#;\"\\r\\n]", "").trim();
    }
}
