import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;

//── Panel de firma remota PQC — simula el flujo se un sistema que firma e invoca un servicio de firmas ────────────
public class PanelFirmaRemota extends JPanel {

    private static final String URL_SERVIDOR = "http://localhost:8080/firmar";
    private static final String URL_ESTADO   = "http://localhost:8080/estado";

    // ── Componentes ───────────────────────────────────────────────────────────
    private JTextField     pdfFileField;
    private JTextField     dniField;
    private JComboBox<String> comboMotivo;
    private JButton        selectPdfButton;
    private JButton        btnFirmar;
    private JButton        btnIniciarServidor;
    private JButton        btnDetenerServidor;
    private JProgressBar   progressBar;
    private JLabel         statusLabel;
    private JLabel         lblEstadoServidor;
    private JTable         tablaLog;
    private DefaultTableModel modeloLog;

    private File archivoSeleccionado;
    private final HttpClient httpClient;

    public PanelFirmaRemota() {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        buildUI();
        verificarEstadoServidor();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UI
    // ═════════════════════════════════════════════════════════════════════════

    private void buildUI() {
        setLayout(new BorderLayout(15, 15));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ── Título ────────────────────────────────────────────────────────────
        JLabel titleLabel = new JLabel("Firma Remota Post-Cuántica");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.BLACK);
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.NORTH;
        mainPanel.add(titleLabel, gbc);

        // ── Subtítulo ─────────────────────────────────────────────────────────
        JLabel subtitleLabel = new JLabel(
            "Simula el flujo Firma remota y aplica la firma Dilithium");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        gbc.gridy = 1;
        mainPanel.add(subtitleLabel, gbc);

        // ── Separador ─────────────────────────────────────────────────────────
        gbc.gridy = 2;
        gbc.insets = new Insets(15, 8, 15, 8);
        mainPanel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(8, 8, 8, 8);

        // ── Panel de estado del servidor ──────────────────────────────────────
        gbc.gridy = 3; gbc.gridwidth = 3;
        mainPanel.add(buildPanelServidor(), gbc);

        // ── Separador ─────────────────────────────────────────────────────────
        gbc.gridy = 4;
        gbc.insets = new Insets(10, 8, 10, 8);
        mainPanel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;

        // ── PDF a firmar ──────────────────────────────────────────────────────
        gbc.gridy = 5; gbc.gridx = 0;
        JLabel lPdf = new JLabel("Documento PDF:");
        lPdf.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainPanel.add(lPdf, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        pdfFileField = new JTextField(30);
        pdfFileField.setEditable(false);
        pdfFileField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pdfFileField.setBackground(new Color(245, 245, 245));
        pdfFileField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        mainPanel.add(pdfFileField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        selectPdfButton = new JButton("Seleccionar");
        styleButton(selectPdfButton, new Color(240, 240, 240));
        selectPdfButton.addActionListener(e -> seleccionarPdf());
        mainPanel.add(selectPdfButton, gbc);

        // ── DNI del firmante ──────────────────────────────────────────────────
        gbc.gridy = 6; gbc.gridx = 0;
        JLabel lDni = new JLabel("DNI firmante:");
        lDni.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainPanel.add(lDni, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        dniField = new JTextField(30);
        dniField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dniField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        dniField.setToolTipText("DNI del Firmante");
        mainPanel.add(dniField, gbc);

        // ── Motivo de firma ───────────────────────────────────────────────────
        gbc.gridy = 7; gbc.gridx = 0; gbc.weightx = 0;
        JLabel lMotivo = new JLabel("Motivo:");
        lMotivo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainPanel.add(lMotivo, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        comboMotivo = new JComboBox<>(new String[]{
            "FIRMA_AUTOR  —  Firma de autor del documento",
            "VISTO_BUENO  —  Visto bueno / aprobación"
        });
        comboMotivo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        comboMotivo.setBackground(Color.WHITE);
        mainPanel.add(comboMotivo, gbc);


        // ── Botón Firmar ──────────────────────────────────────────────────────
        gbc.gridy = 9; 
        gbc.gridx = 0;
        gbc.gridwidth = 3;
        gbc.insets = new Insets(20, 8, 8, 8);
        gbc.anchor = GridBagConstraints.NORTH;

        btnFirmar = new JButton("Firmar Remotamente PQC");
        btnFirmar.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnFirmar.setBackground(new Color(76, 175, 80));
        btnFirmar.setForeground(Color.BLACK);
        btnFirmar.setFocusPainted(false);
        btnFirmar.setBorder(BorderFactory.createEmptyBorder(12, 30, 12, 30));
        btnFirmar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnFirmar.addActionListener(e -> firmarRemotamente());
        btnFirmar.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btnFirmar.setBackground(new Color(30, 136, 229)); }
            public void mouseExited(MouseEvent e)  { btnFirmar.setBackground(new Color(33, 150, 243)); }
        });
        mainPanel.add(btnFirmar, gbc);

        // ── Barra de progreso ─────────────────────────────────────────────────
        gbc.gridy = 10;
        gbc.insets = new Insets(15, 50, 5, 50);
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        mainPanel.add(progressBar, gbc);

        // ── Estado ────────────────────────────────────────────────────────────
        gbc.gridy = 11;
        gbc.insets = new Insets(5, 8, 15, 8);
        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusLabel.setForeground(Color.GRAY);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(statusLabel, gbc);

        // ── Tabla de historial de llamadas ────────────────────────────────────
        gbc.gridy = 12;
        gbc.insets = new Insets(10, 8, 8, 8);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        String[] cols = {"Hora", "DNI", "Motivo", "Archivo", "Estado", "Tamaño respuesta"};
        modeloLog = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tablaLog = new JTable(modeloLog);
        tablaLog.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tablaLog.setRowHeight(25);
        tablaLog.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        tablaLog.getTableHeader().setBackground(new Color(240, 240, 240));
        tablaLog.setSelectionBackground(new Color(220, 237, 254));
        tablaLog.setDefaultRenderer(Object.class, new EstadoRenderer());

        tablaLog.getColumnModel().getColumn(0).setPreferredWidth(70);
        tablaLog.getColumnModel().getColumn(0).setMaxWidth(90);
        tablaLog.getColumnModel().getColumn(1).setPreferredWidth(80);
        tablaLog.getColumnModel().getColumn(1).setMaxWidth(100);
        tablaLog.getColumnModel().getColumn(2).setPreferredWidth(110);
        tablaLog.getColumnModel().getColumn(2).setMaxWidth(140);
        tablaLog.getColumnModel().getColumn(4).setPreferredWidth(80);
        tablaLog.getColumnModel().getColumn(4).setMaxWidth(90);
        tablaLog.getColumnModel().getColumn(5).setPreferredWidth(120);
        tablaLog.getColumnModel().getColumn(5).setMaxWidth(140);

        JScrollPane scroll = new JScrollPane(tablaLog);
        scroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            "Historial de solicitudes al servidor",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 12)));
        mainPanel.add(scroll, gbc);

        add(mainPanel, BorderLayout.CENTER);
    }

    // ── Panel de control del servidor simulado */
    private JPanel buildPanelServidor() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 210, 230)),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));

        JLabel lSrv = new JLabel("Servidor simulado:");
        lSrv.setFont(new Font("Segoe UI", Font.BOLD, 12));
        p.add(lSrv);

        lblEstadoServidor = new JLabel(" INACTIVO");
        lblEstadoServidor.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblEstadoServidor.setForeground(new Color(183, 28, 28));
        p.add(lblEstadoServidor);

        JLabel lUrl = new JLabel("  localhost:8080/firmar");
        lUrl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lUrl.setForeground(Color.WHITE);
        p.add(lUrl);

        p.add(Box.createHorizontalStrut(20));

        btnIniciarServidor = new JButton("Iniciar servidor");
        btnIniciarServidor.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnIniciarServidor.setBackground(new Color(76, 175, 80));
        btnIniciarServidor.setForeground(Color.BLACK);
        btnIniciarServidor.setFocusPainted(false);
        btnIniciarServidor.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btnIniciarServidor.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnIniciarServidor.addActionListener(e -> iniciarServidor());
        p.add(btnIniciarServidor);

        btnDetenerServidor = new JButton("Detener servidor");
        btnDetenerServidor.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnDetenerServidor.setBackground(new Color(183, 28, 28));
        btnDetenerServidor.setForeground(Color.BLACK);
        btnDetenerServidor.setFocusPainted(false);
        btnDetenerServidor.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        btnDetenerServidor.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnDetenerServidor.setEnabled(false);
        btnDetenerServidor.addActionListener(e -> detenerServidor());
        p.add(btnDetenerServidor);

        return p;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  LÓGICA
    // ═════════════════════════════════════════════════════════════════════════

    private void iniciarServidor() {
        try {
            ServidorFirmaPQC.iniciar();
            actualizarEstadoServidor(true);
            statusLabel.setText("Servidor iniciado correctamente en localhost:8080");
            statusLabel.setForeground(new Color(46, 125, 50));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "No se pudo iniciar el servidor:\n" + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void detenerServidor() {
        ServidorFirmaPQC.detener();
        actualizarEstadoServidor(false);
        statusLabel.setText("Servidor detenido.");
        statusLabel.setForeground(Color.GRAY);
    }

    private void actualizarEstadoServidor(boolean activo) {
        lblEstadoServidor.setText(activo ? " ACTIVO" : " INACTIVO");
        lblEstadoServidor.setForeground(activo ? new Color(46, 125, 50) : new Color(183, 28, 28));
        btnIniciarServidor.setEnabled(!activo);
        btnDetenerServidor.setEnabled(activo);
    }

    private void verificarEstadoServidor() {
        new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(URL_ESTADO))
                    .timeout(Duration.ofSeconds(2))
                    .GET().build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    SwingUtilities.invokeLater(() -> actualizarEstadoServidor(true));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void seleccionarPdf() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".pdf");
            }
            public String getDescription() { return "Archivos PDF (*.pdf)"; }
        });
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            archivoSeleccionado = fc.getSelectedFile();
            pdfFileField.setText(archivoSeleccionado.getAbsolutePath());
        }
    }

    private void firmarRemotamente() {
        if (archivoSeleccionado == null) {
            showError("Por favor selecciona un archivo PDF");
            return;
        }
        if (dniField.getText().trim().isEmpty()) {
            showError("Por favor ingresa el DNI del firmante");
            return;
        }
        if (!ServidorFirmaPQC.estaActivo()) {
            showError("El servidor no está activo.\nPrimero pulsa 'Iniciar servidor'.");
            return;
        }

        String dni    = dniField.getText().trim();
        String motivo = comboMotivo.getSelectedIndex() == 0 ? "FIRMA_AUTOR" : "VISTO_BUENO";

        btnFirmar.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        statusLabel.setText("Enviando solicitud al servidor...");
        statusLabel.setForeground(Color.GRAY);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            String estadoFinal = "ERROR";
            String mensajeFinal = "";
            long   tamanoRespuesta = 0;

            @Override
            protected Void doInBackground() throws Exception {
                // Leer PDF y codificar en base64
                byte[] pdfBytes  = Files.readAllBytes(archivoSeleccionado.toPath());
                String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);

                // Construir JSON de solicitud
                String jsonBody = "{" +
                    "\"motivo\":\"" + motivo + "\"," +
                    "\"dni\":\"" + dni + "\"," +
                    "\"pdf_base64\":\"" + pdfBase64 + "\"" +
                    "}";

                // Enviar al servidor
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL_SERVIDOR))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                String respBody = response.body();
                tamanoRespuesta = respBody.length();

                if (response.statusCode() == 200 && respBody.contains("\"estado\":\"OK\"")) {
                    // Extraer PDF firmado
                    String pdfFirmadoB64 = extraerCampo(respBody, "pdf_base64");
                    if (pdfFirmadoB64 != null && !pdfFirmadoB64.isEmpty()) {
                        byte[] pdfFirmado = Base64.getDecoder().decode(pdfFirmadoB64);

                        // Guardar PDF firmado
                        String rutaSalida = generarRutaSalida(archivoSeleccionado.getAbsolutePath(), motivo);
                        try (FileOutputStream fos = new FileOutputStream(rutaSalida)) {
                            fos.write(pdfFirmado);
                        }

                        estadoFinal   = "OK";
                        mensajeFinal  = rutaSalida;
                        tamanoRespuesta = pdfFirmado.length;

                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Firma aplicada exitosamente");
                            statusLabel.setForeground(new Color(46, 125, 50));
                            JOptionPane.showMessageDialog(PanelFirmaRemota.this,
                                "Firma PQC aplicada remotamente\n\n" +
                                "Archivo: " + new File(rutaSalida).getName() + "\n" +
                                "Motivo : " + motivo + "\n" +
                                "DNI    : " + dni + "\n" +
                                "Algoritmo: Dilithium3 (ML-DSA-65)",
                                "Firma Remota Exitosa",
                                JOptionPane.INFORMATION_MESSAGE);
                        });
                    }
                } else {
                    String msg = extraerCampo(respBody, "mensaje");
                    estadoFinal  = "ERROR";
                    mensajeFinal = msg != null ? msg : "Error en el servidor";
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error: " + mensajeFinal);
                        statusLabel.setForeground(new Color(183, 28, 28));
                        showError("El servidor devolvió un error:\n\n" + mensajeFinal);
                    });
                }
                return null;
            }

            @Override
            protected void done() {
                btnFirmar.setEnabled(true);
                progressBar.setVisible(false);

                // Registrar en la tabla de historial
                String hora = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
                modeloLog.insertRow(0, new Object[]{
                    hora, dni, motivo,
                    archivoSeleccionado.getName(),
                    estadoFinal,
                    tamanoRespuesta > 0 ? (tamanoRespuesta / 1024) + " KB" : "—"
                });
            }
        };

        worker.execute();
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private String generarRutaSalida(String ruta, String motivo) {
        int dot = ruta.lastIndexOf('.');
        String base = ruta.substring(0, dot);
        String sufijo = "FIRMA_AUTOR".equals(motivo) ? "_pqc_autor" : "_pqc_vb";
        return base + sufijo + ".pdf";
    }

    private String extraerCampo(String json, String campo) {
        String buscar = "\"" + campo + "\"";
        int idx = json.indexOf(buscar);
        if (idx < 0) return null;
        int inicio = json.indexOf("\"", idx + buscar.length() + 1);
        if (inicio < 0) return null;
        int fin = json.indexOf("\"", inicio + 1);
        if (fin < 0) return null;
        return json.substring(inicio + 1, fin);
    }

    private void styleButton(JButton b, Color bg) {
        b.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        b.setFocusPainted(false);
        b.setBackground(bg);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(8, 20, 8, 20)));
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    // ── Renderer de color para columna Estado ─────────────────────────────────

    private static class EstadoRenderer extends javax.swing.table.DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            if (!sel && col == 4) {
                String e = String.valueOf(v);
                if ("OK".equals(e))    { setBackground(new Color(232,245,233)); setForeground(new Color(27,94,32)); }
                else if ("ERROR".equals(e)) { setBackground(new Color(255,235,238)); setForeground(new Color(183,28,28)); }
                else                   { setBackground(Color.WHITE); setForeground(Color.BLACK); }
            } else if (!sel) {
                setBackground(Color.WHITE);
                setForeground(Color.BLACK);
            }
            return this;
        }
    }
}
