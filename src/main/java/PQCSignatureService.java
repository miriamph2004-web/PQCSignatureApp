import com.itextpdf.kernel.pdf.*;
import com.itextpdf.signatures.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;

import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x500.X500Name;

public class PQCSignatureService {

    private KeyStore keystore;

    //──  Resultado de firma con desglose de tiempos ────────────────────────────
    //──  Contiene el desglose de tiempos internos del proceso de firma criptográfica.     
    //──  tiempoFirmaCriptoMs : tiempo puro SHA-512 + Dilithium + construcción CMS
    //──  tiempoProcesoPdfMs  : tiempo total de addPQCSignatureToPdf(), que incluye carga de clave, preparación del PdfSigner y escritura del PDF firmado, además de la firma criptográfica
     
    public static class ResultadoFirma {
        public final long tiempoFirmaCriptoMs; // solo SHA-512 + Dilithium + CMS
        public final long tiempoProcesoPdfMs;  // todo addPQCSignatureToPdf()

        public ResultadoFirma(long tiempoFirmaCriptoMs, long tiempoProcesoPdfMs) {
            this.tiempoFirmaCriptoMs = tiempoFirmaCriptoMs;
            this.tiempoProcesoPdfMs  = tiempoProcesoPdfMs;
        }
    }

    static {
        Security.addProvider(new BouncyCastleProvider());
        Security.addProvider(new BouncyCastlePQCProvider());
    }

    public void loadKeystore(String keystorePath, char[] password) throws Exception {
        keystore = KeyStore.getInstance("PKCS12", "BC");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keystore.load(fis, password);
        }
        System.out.println("Keystore cargado correctamente");
        java.util.Enumeration<String> aliases = keystore.aliases();
        System.out.println("Aliases disponibles en el keystore:");
        while (aliases.hasMoreElements()) {
            System.out.println("  - " + aliases.nextElement());
        }
    }

    //──  Firma el PDF y retorna un ResultadoFirma con el desglose de tiempos.
    
    public ResultadoFirma addPQCSignatureToPdf(String inputPdfPath,
                                               String outputPdfPath,
                                               String keyAlias) throws Exception {

        // ── Cronómetro: proceso PDF completo (incluye firma criptográfica) ────
        long inicioProcesoPdf = System.nanoTime();

        char[]     keyPassword = "".toCharArray();
        PrivateKey privateKey  = (PrivateKey) keystore.getKey(keyAlias, keyPassword);
        Certificate[] chain    = keystore.getCertificateChain(keyAlias);

        if (privateKey == null)
            throw new Exception("No se encontró la clave privada con alias: " + keyAlias);

        System.out.println("Clave privada cargada:");
        System.out.println("  Algoritmo: " + privateKey.getAlgorithm());
        System.out.println("  Formato:   " + privateKey.getFormat());

        // Contenedor compartido para recibir el tiempo interno de firma criptográfica
        long[] tiempoFirmaCriptoHolder = { 0L };

        boolean   firmado     = false;
        Exception ultimoError = null;

        // ── Intento 1: modo append normal ────────────────────────────────────
        try {
            System.out.println("Intento 1: Firmando en modo append estándar...");
            firmarPDF(inputPdfPath, outputPdfPath, privateKey, chain, true,
                      tiempoFirmaCriptoHolder);
            firmado = true;
            System.out.println("Firmado exitosamente en modo append");
        } catch (Exception e) {
            System.out.println("Intento 1 fallido: " + e.getMessage());
            ultimoError = e;

            // ── Intento 2: reparar PDF y reintentar ──────────────────────────
            if (e.getMessage() != null && e.getMessage().contains("Se requiere modo Append")) {
                try {
                    System.out.println("Intento 2: Reparando PDF y firmando...");
                    String pdfReparado = repararPDF(inputPdfPath);
                    firmarPDF(pdfReparado, outputPdfPath, privateKey, chain, true,
                              tiempoFirmaCriptoHolder);
                    new File(pdfReparado).delete();
                    firmado = true;
                    System.out.println("PDF reparado y firmado exitosamente");
                } catch (Exception e2) {
                    System.out.println("Intento 2 fallido: " + e2.getMessage());
                    ultimoError = e2;
                }
            }
        }

        if (!firmado)
            throw new Exception("No se pudo firmar el PDF. " +
                (ultimoError != null ? ultimoError.getMessage() : "Error desconocido"));

        long tiempoProcesoPdfMs  = (System.nanoTime() - inicioProcesoPdf) / 1_000_000;
        long tiempoFirmaCriptoMs = tiempoFirmaCriptoHolder[0];

        System.out.printf("[PQC] Firma criptográfica (SHA-512+Dilithium+CMS) : %d ms%n",
            tiempoFirmaCriptoMs);
        System.out.printf("[PQC] Proceso PDF total (incluye firma criptográfica): %d ms%n",
            tiempoProcesoPdfMs);
        System.out.println("Firma PQC (Dilithium) añadida exitosamente → " + outputPdfPath);

        return new ResultadoFirma(tiempoFirmaCriptoMs, tiempoProcesoPdfMs);
    }

    // ── Métodos privados ──────────────────────────────────────────────────────

    private String repararPDF(String inputPath) throws Exception {
        System.out.println("Reparando PDF: " + inputPath);
        String tempPath = inputPath.replace(".pdf", "_temp_repaired.pdf");
        PdfReader   reader = new PdfReader(inputPath);
        PdfWriter   writer = new PdfWriter(tempPath);
        PdfDocument pdfDoc = new PdfDocument(reader, writer);
        pdfDoc.close();
        System.out.println("PDF reparado temporalmente en: " + tempPath);
        return tempPath;
    }

    //──   Realiza la firma del PDF.
    private void firmarPDF(String inputPath, String outputPath,
                           PrivateKey privateKey, Certificate[] chain,
                           boolean appendMode,
                           long[] tiempoFirmaCriptoHolder) throws Exception {

        // Verificar firmas existentes
        PdfReader   readerCheck = new PdfReader(inputPath);
        PdfDocument pdfDocCheck = new PdfDocument(readerCheck);
        SignatureUtil signUtil   = new SignatureUtil(pdfDocCheck);
        List<String> existentes = signUtil.getSignatureNames();
        pdfDocCheck.close();
        System.out.println("Firmas existentes en el PDF: " + existentes.size());

        PdfReader reader = new PdfReader(inputPath);
        StampingProperties sp = new StampingProperties();
        if (appendMode) sp.useAppendMode();

        PdfSigner signer = new PdfSigner(reader,
                new FileOutputStream(outputPath), sp);

        PdfSignatureAppearance appearance = signer.getSignatureAppearance();
        appearance.setReason("Firma PQC");
        appearance.setLocation("Peru");
        appearance.setPageRect(new com.itextpdf.kernel.geom.Rectangle(36, 36, 180, 60));
        appearance.setPageNumber(1);

        X509Certificate cert          = (X509Certificate) chain[0];
        String          nombreFirmante = extraerCN(cert);
        String          fecha          = new java.text.SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm:ss").format(new java.util.Date());

        appearance.setLayer2Text(
            "Firmante: " + nombreFirmante + "\n" +
            "Fecha: "    + fecha          + "\n" +
            "Razón: "    + appearance.getReason()   + "\n" +
            "Lugar: "    + appearance.getLocation()
        );
        appearance.setLayer2FontSize(7f);

        System.out.println("Algoritmo: " + privateKey.getAlgorithm());

        // El contenedor recibe el holder para escribir el tiempo de firma criptográfica
        DilithiumSignatureContainer container =
            new DilithiumSignatureContainer(privateKey, chain, nombreFirmante,
                                            tiempoFirmaCriptoHolder);
        signer.signExternalContainer(container, 32768);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Contenedor de firma Dilithium
    // ─────────────────────────────────────────────────────────────────────────

    private static class DilithiumSignatureContainer implements IExternalSignatureContainer {

        private static final ASN1ObjectIdentifier DILITHIUM3_OID =
                new ASN1ObjectIdentifier("2.16.840.1.101.3.4.3.18");
        private static final ASN1ObjectIdentifier SHA512_OID =
                new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.3");

        private final PrivateKey    privateKey;
        private final Certificate[] chain;
        private final String        nombreFirmante;
        // Holder para devolver el tiempo de firma criptográfica al método padre
        private final long[]        tiempoFirmaCriptoHolder;

        public DilithiumSignatureContainer(PrivateKey privateKey, Certificate[] chain,
                                           String nombreFirmante,
                                           long[] tiempoFirmaCriptoHolder) {
            this.privateKey             = privateKey;
            this.chain                  = chain;
            this.nombreFirmante         = nombreFirmante;
            this.tiempoFirmaCriptoHolder = tiempoFirmaCriptoHolder;
        }

        @Override
        public byte[] sign(InputStream rangeStream) throws GeneralSecurityException {
            try {
                // Leer datos del rango del PDF
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] temp = new byte[8192];
                int n;
                while ((n = rangeStream.read(temp)) != -1) buffer.write(temp, 0, n);
                byte[] dataToSign = buffer.toByteArray();

                // ── Cronómetro: firma criptográfica pura ─────────────────────
                // Mide exactamente: SHA-512 + operación Dilithium + construcción CMS
                long inicioFirmaCripto = System.nanoTime();

                MessageDigest sha512 = MessageDigest.getInstance("SHA-512", "BC");
                byte[] digest = sha512.digest(dataToSign);

                Signature dilithiumSig = Signature.getInstance("DILITHIUM", "BCPQC");
                dilithiumSig.initSign(privateKey);
                dilithiumSig.update(dataToSign);
                byte[] rawSignatureBytes = dilithiumSig.sign();

                byte[] cmsBytes = buildCMSSignedData(dataToSign, digest, rawSignatureBytes, chain);

                tiempoFirmaCriptoHolder[0] =
                    (System.nanoTime() - inicioFirmaCripto) / 1_000_000;
                // ─────────────────────────────────────────────────────────────

                System.out.println("Firma Dilithium generada: " + rawSignatureBytes.length + " bytes");
                return cmsBytes;

            } catch (GeneralSecurityException e) {
                throw e;
            } catch (Exception e) {
                throw new GeneralSecurityException("Error construyendo CMS SignedData", e);
            }
        }

        private static byte[] buildCMSSignedData(byte[] originalData, byte[] digest,
                                                  byte[] rawSignature,
                                                  Certificate[] chain) throws Exception {
            X509Certificate signerCert = (X509Certificate) chain[0];

            AlgorithmIdentifier digestAlgId = new AlgorithmIdentifier(SHA512_OID, DERNull.INSTANCE);
            AlgorithmIdentifier sigAlgId    = new AlgorithmIdentifier(DILITHIUM3_OID);

            X500Name issuer = X500Name.getInstance(
                    signerCert.getIssuerX500Principal().getEncoded());
            IssuerAndSerialNumber issuerAndSerial =
                    new IssuerAndSerialNumber(issuer, signerCert.getSerialNumber());
            SignerIdentifier signerIdentifier = new SignerIdentifier(issuerAndSerial);

            SignerInfo signerInfo = new SignerInfo(
                    signerIdentifier, digestAlgId, (ASN1Set) null,
                    sigAlgId, new DEROctetString(rawSignature), (ASN1Set) null);

            ContentInfo encapContentInfo = new ContentInfo(CMSObjectIdentifiers.data, null);

            ASN1EncodableVector certsVector = new ASN1EncodableVector();
            for (Certificate cert : chain)
                certsVector.add(org.bouncycastle.asn1.x509.Certificate.getInstance(cert.getEncoded()));
            ASN1Set certs = new DERSet(certsVector);

            ASN1EncodableVector digestAlgsVector = new ASN1EncodableVector();
            digestAlgsVector.add(digestAlgId);
            ASN1Set digestAlgs = new DERSet(digestAlgsVector);

            ASN1EncodableVector signerInfosVector = new ASN1EncodableVector();
            signerInfosVector.add(signerInfo);
            ASN1Set signerInfos = new DERSet(signerInfosVector);

            ASN1EncodableVector signedDataVector = new ASN1EncodableVector();
            signedDataVector.add(new ASN1Integer(1));
            signedDataVector.add(digestAlgs);
            signedDataVector.add(encapContentInfo);
            signedDataVector.add(new DERTaggedObject(false, 0, certs));
            signedDataVector.add(signerInfos);
            DERSequence signedData = new DERSequence(signedDataVector);

            ASN1EncodableVector contentInfoVector = new ASN1EncodableVector();
            contentInfoVector.add(CMSObjectIdentifiers.signedData);
            contentInfoVector.add(new DERTaggedObject(true, 0, signedData));
            DERSequence cmsContentInfo = new DERSequence(contentInfoVector);

            byte[] cmsBytes = cmsContentInfo.getEncoded(ASN1Encoding.DER);
            System.out.println("Tamaño CMS generado: " + cmsBytes.length + " bytes");
            System.out.println("Espacio reservado:   32768 bytes");

            if (cmsBytes.length > 32768)
                throw new GeneralSecurityException(
                    "CMS no cabe en el espacio reservado: " + cmsBytes.length + " bytes");
            return cmsBytes;
        }

        @Override
        public void modifySigningDictionary(PdfDictionary signDic) {
            signDic.put(PdfName.Filter,    PdfName.Adobe_PPKLite);
            signDic.put(PdfName.SubFilter, new PdfName("ETSI.CAdES.detached"));
            signDic.put(PdfName.Name,      new PdfString(nombreFirmante));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extraerCN(X509Certificate cert) {
        try {
            X500Name subject = X500Name.getInstance(
                    cert.getSubjectX500Principal().getEncoded());
            org.bouncycastle.asn1.x500.RDN[] rdns =
                    subject.getRDNs(org.bouncycastle.asn1.x500.style.BCStyle.CN);
            if (rdns.length > 0)
                return rdns[0].getFirst().getValue().toString();
        } catch (Exception e) {
            System.out.println("[extraerCN] " + e.getMessage());
        }
        return cert.getSubjectX500Principal().getName();
    }
}
