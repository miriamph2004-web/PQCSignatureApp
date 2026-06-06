import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class FirmaMasivaService {

    private static final Logger LOG       = Logger.getLogger(FirmaMasivaService.class.getName());
    private static final String TABLA_DOC = "IDOSGD.TDTV_ARCHIVO_DOC";
    private static final String TABLA_REM = "IDOSGD.TDTV_REMITOS";

    // ── Modelo de resultado ───────────────────────────────────────────────────

    public enum Estado { FIRMADO, ERROR, OMITIDO }

    public static class Resultado {
        public Estado    estado;
        public String    mensaje;
        public LocalDate fechaModif;
         // ── Desglose de tiempos por documento:───────────────────────────────────────────────────
         //   tiempoFirmaCriptoMs  : SHA-512 + Dilithium + construcción CMS. (Sin incluir lectura/escritura de BD ni de disco)
         //   tiempoIoMs           : tiempo de I/O puro (lectura blob BD + escritura PDF temporal + lectura PDF firmado + escritura blob BD).
         //   tiempoOverheadPdfMs  : verificación de firmas existentes+ preparación del signer, cálculo de rangos de bytes + embedding del CMS y escritura del PDF firmado.
         //                        = tiempoProcesoPdfMs - tiempoFirmaCriptoMs
         //  tiempoTotalDocMs     : tiempo real "de reloj" por documento.
         //  Verificación         : tiempoFirmaCriptoMs + tiempoIoMs  + tiempoOverheadPdfMs ≈ tiempoTotalDocMs
        
        public long tiempoFirmaCriptoMs; // T1: SHA-512 + Dilithium + CMS
        public long tiempoIoMs;          // T2: I/O BD + disco
        public long tiempoOverheadPdfMs; // T3: overhead iText/PdfSigner
        public long tiempoTotalDocMs;    // T4: tiempo real total por documento

        Resultado() { estado = Estado.ERROR; mensaje = "Sin procesar"; }

        void firmado(LocalDate f,
                     long firmaCriptoMs,
                     long ioMs,
                     long overheadPdfMs,
                     long totalDocMs) {
            estado               = Estado.FIRMADO;
            mensaje              = "Firma PQC aplicada";
            fechaModif           = f;
            tiempoFirmaCriptoMs  = firmaCriptoMs;
            tiempoIoMs           = ioMs;
            tiempoOverheadPdfMs  = overheadPdfMs;
            tiempoTotalDocMs     = totalDocMs;
        }

        void error(String m)   { estado = Estado.ERROR;   mensaje = m != null ? m : "Error desconocido"; }
        void omitido(String m) { estado = Estado.OMITIDO; mensaje = m; }
    }

    // ── Campos ────────────────────────────────────────────────────────────────

    private final PQCSignatureService firmador;
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String keystoreAlias;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FirmaMasivaService() {
        try {
            ConfigLoader cfg   = new ConfigLoader();
            this.dbUrl         = cfg.getDbUrl();
            this.dbUser        = cfg.getDbUser();
            this.dbPassword    = cfg.getDbPassword();
            this.keystoreAlias = cfg.getKeystoreAlias();

            this.firmador = new PQCSignatureService();
            this.firmador.loadKeystore(
                cfg.getKeystorePath(),
                cfg.getKeystorePassword().toCharArray()
            );
        } catch (Exception e) {
            throw new RuntimeException("Error al inicializar FirmaMasivaService: " + e.getMessage(), e);
        }
    }

    // ── API pública ───────────────────────────────────────────────────────────

    public List<String[]> obtenerIds(LocalDate desde, LocalDate hasta) throws Exception {
        List<String[]> lista = new ArrayList<>();

        String sql =
            "SELECT B.NU_ANN, B.NU_EMI " +
            "FROM " + TABLA_DOC + " B " +
            "LEFT JOIN " + TABLA_REM + " A " +
            "       ON A.NU_EMI = B.NU_EMI AND A.NU_ANN = B.NU_ANN " +
            "WHERE A.ES_DOC_EMI NOT IN (5, 7, 9) " + //Estados diferentes a En proyecto, Anulado o Para despacho
            "  AND B.ES_FIRMA = 0 " +
            "  AND A.CO_TIP_DOC_ADM NOT IN ('304', '013') " + //Tipos de documentos diferentes a proveidos y formulario
            "  AND A.fe_emi BETWEEN ? AND ? " +
            "ORDER BY A.NU_EMI ASC";

        try (Connection c = getConexion();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(desde));
            ps.setDate(2, Date.valueOf(hasta));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    lista.add(new String[]{ rs.getString("NU_ANN"), rs.getString("NU_EMI") });
            }
        }
        return lista;
    }

    public Resultado procesarDocumento(String nuAnn, String nuEmi) {
        Resultado r      = new Resultado();
        File      tmpIn  = null;
        File      tmpOut = null;

        // ── Cronómetro: tiempo total del documento ────────────────────────────
        long inicioDoc = System.nanoTime();
        long acumIoNs  = 0L; // acumula nanosegundos de I/O

        try (Connection c = getConexion()) {
            c.setAutoCommit(false);
            try {
                // ── I/O parte 1: lectura blob + escritura disco ───────────────
                long inicioIo1 = System.nanoTime();

                byte[] original = leerBlob(c, nuAnn, nuEmi);
                if (original == null || original.length == 0) {
                    r.omitido("BL_DOC vacío o nulo");
                    return r;
                }

                tmpIn  = File.createTempFile("pqc_in_",  ".pdf");
                tmpOut = File.createTempFile("pqc_out_", ".pdf");
                try (FileOutputStream fos = new FileOutputStream(tmpIn)) {
                    fos.write(original);
                }

                acumIoNs += System.nanoTime() - inicioIo1;
                // ── Firma criptográfica + proceso PDF ─────────────────────────
                PQCSignatureService.ResultadoFirma rf =
                    firmador.addPQCSignatureToPdf(
                        tmpIn.getAbsolutePath(),
                        tmpOut.getAbsolutePath(),
                        keystoreAlias
                    );

                // ── I/O parte 2: lectura PDF firmado + escritura blob BD ──────
                long inicioIo2 = System.nanoTime();

                byte[] firmado = leerArchivo(tmpOut);
                actualizarBlob(c, nuAnn, nuEmi, firmado);
                c.commit();

                acumIoNs += System.nanoTime() - inicioIo2;
                // ─────────────────────────────────────────────────────────────

                long tiempoTotalDocMs    = (System.nanoTime() - inicioDoc) / 1_000_000;
                long tiempoIoMs          = acumIoNs / 1_000_000;
                long tiempoFirmaCriptoMs = rf.tiempoFirmaCriptoMs;
                long tiempoOverheadPdfMs = rf.tiempoProcesoPdfMs - rf.tiempoFirmaCriptoMs;

                System.out.printf(
                    "[PQC] %s/%s | T.cripto: %d ms | T.PDF(iText): %d ms | T.BD/disco: %d ms | T.total: %d ms%n",
                    nuAnn, nuEmi,
                    tiempoFirmaCriptoMs,
                    tiempoOverheadPdfMs,
                    tiempoIoMs,
                    tiempoTotalDocMs);
                System.out.printf(
                    "[PQC]   Verificación: %d + %d + %d = %d ms (total: %d ms)%n",
                    tiempoFirmaCriptoMs, tiempoOverheadPdfMs, tiempoIoMs,
                    tiempoFirmaCriptoMs + tiempoOverheadPdfMs + tiempoIoMs,
                    tiempoTotalDocMs);

                r.firmado(leerFechaModif(c, nuAnn, nuEmi),
                          tiempoFirmaCriptoMs,
                          tiempoIoMs,
                          tiempoOverheadPdfMs,
                          tiempoTotalDocMs);

            } catch (Exception ex) {
                try { c.rollback(); } catch (Exception ignored) {}
                throw ex;
            }

        } catch (Exception ex) {
            LOG.warning("Error [" + nuAnn + "/" + nuEmi + "]: " + ex.getMessage());
            r.error(ex.getMessage());
        } finally {
            if (tmpIn  != null) tmpIn.delete();
            if (tmpOut != null) tmpOut.delete();
        }

        return r;
    }

  

    // ── JDBC ──────────────────────────────────────────────────────────────────

    private byte[] leerBlob(Connection c, String nuAnn, String nuEmi) throws Exception {
        String sql = "SELECT BL_DOC FROM " + TABLA_DOC +
                     " WITH (UPDLOCK, ROWLOCK)" +
                     " WHERE NU_ANN = ? AND NU_EMI = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nuAnn);
            ps.setString(2, nuEmi);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    throw new SQLException("Registro no encontrado: " + nuAnn + "/" + nuEmi);
                Blob b = rs.getBlob("BL_DOC");
                return b == null ? null : b.getBinaryStream().readAllBytes();
            }
        }
    }

    private void actualizarBlob(Connection c, String nuAnn, String nuEmi, byte[] pdf) throws Exception {
        String sql = "UPDATE " + TABLA_DOC +
                     " SET BL_DOC = ?, ES_FIRMA = 1, FEULA = GETDATE()" +
                     " WHERE NU_ANN = ? AND NU_EMI = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBytes(1, pdf);
            ps.setString(2, nuAnn);
            ps.setString(3, nuEmi);
            if (ps.executeUpdate() == 0)
                throw new SQLException("No se actualizó: " + nuAnn + "/" + nuEmi);
        }
    }

    private LocalDate leerFechaModif(Connection c, String nuAnn, String nuEmi) {
        try {
            String sql = "SELECT FEULA FROM " + TABLA_DOC +
                         " WHERE NU_ANN = ? AND NU_EMI = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, nuAnn);
                ps.setString(2, nuEmi);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Timestamp ts = rs.getTimestamp("FEULA");
                        if (ts != null) return ts.toLocalDateTime().toLocalDate();
                    }
                }
            }
        } catch (Exception ignored) {}
        return LocalDate.now();
    }

    private Connection getConexion() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    private byte[] leerArchivo(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            return fis.readAllBytes();
        }
    }
}
