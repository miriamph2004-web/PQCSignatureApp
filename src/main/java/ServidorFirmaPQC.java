import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Logger;
//ServidorFirmaPQC.java   ← servidor HTTP (usa HttpServer del JDK, sin deps)

public class ServidorFirmaPQC {

    private static final Logger LOG    = Logger.getLogger(ServidorFirmaPQC.class.getName());
    private static final int    PUERTO = 8080;

    private static HttpServer servidor;

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    public static void iniciar() throws Exception {
        if (servidor != null) {
            LOG.info("El servidor ya está corriendo en el puerto " + PUERTO);
            return;
        }

        servidor = HttpServer.create(new InetSocketAddress(PUERTO), 0);
        servidor.createContext("/firmar", new FirmarHandler());
        servidor.createContext("/estado", new EstadoHandler());
        servidor.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        servidor.start();

        LOG.info("ServidorFirmaPQC iniciado en http://localhost:" + PUERTO);
        System.out.println("[ServidorFirmaPQC] Escuchando en http://localhost:" + PUERTO + "/firmar");
    }

    public static void detener() {
        if (servidor != null) {
            servidor.stop(0);
            servidor = null;
            System.out.println("[ServidorFirmaPQC] Servidor detenido.");
        }
    }

    public static boolean estaActivo() {
        return servidor != null;
    }

    // ── Handler: POST /firmar ─────────────────────────────────────────────────

    private static class FirmarHandler implements HttpHandler {

        private PQCSignatureService servicio;

        FirmarHandler() {
            try {
                ConfigLoader cfg = new ConfigLoader();
                servicio = new PQCSignatureService();
                servicio.loadKeystore(
                    cfg.getKeystorePath(),
                    cfg.getKeystorePassword().toCharArray()
                );
                System.out.println("[ServidorFirmaPQC] PQCSignatureService listo.");
            } catch (Exception e) {
                System.err.println("[ServidorFirmaPQC] Error cargando keystore: " + e.getMessage());
            }
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS para llamadas locales
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                responder(exchange, 405, "{\"estado\":\"ERROR\",\"mensaje\":\"Solo se acepta POST\"}");
                return;
            }

            try {
                // Leer body
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                // Parseo manual del JSON (sin dependencias externas)
                String motivo    = extraerCampo(body, "motivo");
                String dni       = extraerCampo(body, "dni");
                String pdfBase64 = extraerCampo(body, "pdf_base64");

                if (pdfBase64 == null || pdfBase64.isEmpty()) {
                    responder(exchange, 400, "{\"estado\":\"ERROR\",\"mensaje\":\"pdf_base64 es requerido\"}");
                    return;
                }

                // Validar motivo
                if (!"FIRMA_AUTOR".equals(motivo) && !"VISTO_BUENO".equals(motivo)) {
                    responder(exchange, 400,
                        "{\"estado\":\"ERROR\",\"mensaje\":\"motivo debe ser FIRMA_AUTOR o VISTO_BUENO\"}");
                    return;
                }

                System.out.println("[ServidorFirmaPQC] Solicitud recibida — DNI: " + dni + "  Motivo: " + motivo);

                // Decodificar PDF
                byte[] pdfOriginal = Base64.getDecoder().decode(pdfBase64);

                // Firmar usando archivos temporales (PQCSignatureService trabaja con rutas)
                File tmpIn  = File.createTempFile("srv_in_",  ".pdf");
                File tmpOut = File.createTempFile("srv_out_", ".pdf");
                try {
                    try (FileOutputStream fos = new FileOutputStream(tmpIn)) { fos.write(pdfOriginal); }

                    ConfigLoader cfg = new ConfigLoader();
                    servicio.addPQCSignatureToPdf(
                        tmpIn.getAbsolutePath(),
                        tmpOut.getAbsolutePath(),
                        cfg.getKeystoreAlias()
                    );

                    byte[] pdfFirmado = tmpOut.isFile()
                        ? new FileInputStream(tmpOut).readAllBytes()
                        : new byte[0];

                    String pdfFirmadoB64 = Base64.getEncoder().encodeToString(pdfFirmado);

                    String respuesta = "{" +
                        "\"estado\":\"OK\"," +
                        "\"motivo\":\"" + motivo + "\"," +
                        "\"dni\":\"" + (dni != null ? dni : "") + "\"," +
                        "\"pdf_base64\":\"" + pdfFirmadoB64 + "\"" +
                        "}";

                    System.out.println("[ServidorFirmaPQC] Firma aplicada OK — " + pdfFirmado.length + " bytes");
                    responder(exchange, 200, respuesta);

                } finally {
                    tmpIn.delete();
                    tmpOut.delete();
                }

            } catch (Exception e) {
                e.printStackTrace();
                String msg = e.getMessage() != null
                    ? e.getMessage().replace("\"", "'")
                    : "Error desconocido";
                responder(exchange, 500,
                    "{\"estado\":\"ERROR\",\"mensaje\":\"" + msg + "\"}");
            }
        }
    }

    // ── Handler: GET /estado ──────────────────────────────────────────────────

    private static class EstadoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            responder(exchange, 200,
                "{\"estado\":\"ACTIVO\",\"puerto\":" + PUERTO + ",\"servicio\":\"ServidorFirmaPQC\"}");
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private static void responder(HttpExchange ex, int codigo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    //──  Extrae el valor de un campo JSON simple sin librería externa.   */
    private static String extraerCampo(String json, String campo) {
        String buscar = "\"" + campo + "\"";
        int idx = json.indexOf(buscar);
        if (idx < 0) return null;

        int inicio = json.indexOf("\"", idx + buscar.length() + 1);
        if (inicio < 0) return null;
        int fin = json.indexOf("\"", inicio + 1);
        if (fin < 0) return null;

        return json.substring(inicio + 1, fin);
    }
}
