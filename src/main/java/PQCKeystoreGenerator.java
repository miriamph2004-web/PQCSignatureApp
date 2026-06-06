import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

//── Generador de keystores con claves Post-Cuánticas (ML-DSA-65 / Dilithium3). 
public class PQCKeystoreGenerator {

    // OID de ML-DSA-65 (Dilithium3) — NIST FIPS 204
    private static final String DILITHIUM3_OID = "2.16.840.1.101.3.4.3.18";

    static {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastlePQCProvider());
        System.out.println("Proveedores registrados: BouncyCastleProvider y BouncyCastlePQCProvider");
    }

    // -------------------------------------------------------------------------
    // Punto de entrada
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        try {
            String outputPath = (args.length >= 1) ? args[0] : "pqc_keystore.p12";
            char[] password  = (args.length >= 2) ? args[1].toCharArray() : "changeit".toCharArray();

            generatePQCKeystore(outputPath, password);

            System.out.println("Pasos siguientes:");
            System.out.println("  1. Ejecutar PQCSignatureService");
            System.out.println("  2. Keystore : " + outputPath);
            System.out.println("  3. Password : " + new String(password));
            System.out.println("  4. Alias    : pqc-key");

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Genera el keystore PKCS12 completo
    // -------------------------------------------------------------------------
    public static void generatePQCKeystore(String outputPath, char[] keystorePassword)
            throws Exception {

        // 1. Verificar disponibilidad de Dilithium
        try {
            KeyPairGenerator.getInstance("Dilithium3", "BCPQC");
            System.out.println("Algoritmo Dilithium disponible");
        } catch (Exception e) {
            throw new Exception("Dilithium no disponible. Verifica Bouncy Castle 1.79+", e);
        }

        // 2. Generar par de claves Dilithium3
        System.out.println("Generando par de claves Dilithium3...");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Dilithium3", "BCPQC");
        kpg.initialize(org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec.dilithium3, new SecureRandom());
        //kpg.initialize(DilithiumParameterSpec.dilithium3, new SecureRandom());

        KeyPair keyPair = kpg.generateKeyPair();
        
        

        System.out.println("  Algoritmo       : " + keyPair.getPrivate().getAlgorithm());
        System.out.println("  Clave privada   : " + keyPair.getPrivate().getEncoded().length + " bytes");
        System.out.println("  Clave pública   : " + keyPair.getPublic().getEncoded().length + " bytes");

        // 3. Certificado auto-firmado 100% Dilithium 
        System.out.println("\nCreando certificado auto-firmado con Dilithium3...");
        X509Certificate certificate = generateSelfSignedCertificate(keyPair);

        System.out.println("Certificado creado:");
        System.out.println("  Sujeto          : " + certificate.getSubjectX500Principal());
        System.out.println("  Algoritmo firma : " + certificate.getSigAlgName());
        System.out.println("  Válido desde    : " + certificate.getNotBefore());
        System.out.println("  Válido hasta    : " + certificate.getNotAfter());

        // 4. Crear y guardar keystore PKCS12
        System.out.println("\nCreando keystore PKCS12...");
        KeyStore keyStore = KeyStore.getInstance("PKCS12", "BC");
        keyStore.load(null, null);
        keyStore.setKeyEntry("pqc-key", keyPair.getPrivate(), "".toCharArray(),
                new Certificate[]{certificate});

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            keyStore.store(fos, keystorePassword);
        }

        System.out.println("\nKeystore guardado:");
        System.out.println("  Archivo : " + outputPath);
        System.out.println("  Tipo    : PKCS12");
        System.out.println("  Alias   : pqc-key");
    }

    // -------------------------------------------------------------------------
    // Certificado X.509 auto-firmado con Dilithium3. 
    // -------------------------------------------------------------------------
    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair)
            throws Exception {

        X500Name subject = new X500Name("CN=Miriam Polo, O=Test Organization, C=PE");
        BigInteger serial  = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore     = new Date();
        Date notAfter      = new Date(notBefore.getTime() + 365L * 24 * 60 * 60 * 1000);

        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(
                keyPair.getPublic().getEncoded());

        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, spki);

        // ContentSigner que usa la clave privada Dilithium directamente
        ContentSigner dilithiumSigner = buildDilithiumSigner(keyPair.getPrivate());

        X509CertificateHolder holder = certBuilder.build(dilithiumSigner);

        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(holder);
    }
    
    
  //── Construye un ContentSigner basado en Dilithium3.Se implementa directamente para garantizar compatibilidad **/
    
    private static ContentSigner buildDilithiumSigner(PrivateKey privateKey) throws Exception {

        // AlgorithmIdentifier con el OID oficial de Dilithium3
        final AlgorithmIdentifier sigAlgId = new AlgorithmIdentifier(
                new org.bouncycastle.asn1.ASN1ObjectIdentifier(DILITHIUM3_OID));

        // Motor de firma Dilithium del proveedor BCPQC
        final Signature engine = Signature.getInstance("Dilithium3", "BCPQC");
        engine.initSign(privateKey);

        return new ContentSigner() {

            private final org.bouncycastle.util.io.TeeOutputStream tee;

            // Buffer donde iText/BC escribe los bytes a firmar
            private final java.io.ByteArrayOutputStream buffer =
                    new java.io.ByteArrayOutputStream();

            // Bloque de inicialización del anonymous class
            {
                // Alimentar el motor de firma a través del OutputStream
                tee = new org.bouncycastle.util.io.TeeOutputStream(
                        buffer,
                        new OutputStream() {
                            @Override
                            public void write(int b) throws IOException {
                                try { engine.update((byte) b); }
                                catch (SignatureException e) { throw new IOException(e); }
                            }

                            @Override
                            public void write(byte[] b, int off, int len) throws IOException {
                                try { engine.update(b, off, len); }
                                catch (SignatureException e) { throw new IOException(e); }
                            }
                        });
            }

            @Override
            public AlgorithmIdentifier getAlgorithmIdentifier() {
                return sigAlgId;
            }

            @Override
            public OutputStream getOutputStream() {
                return tee;
            }

            @Override
            public byte[] getSignature() {
                try {
                    return engine.sign();
                } catch (SignatureException e) {
                    throw new RuntimeException("Error al generar firma Dilithium del certificado", e);
                }
            }
        };
    }
}
