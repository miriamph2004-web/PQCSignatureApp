import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import com.itextpdf.kernel.pdf.*;
import java.awt.image.BufferedImage;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;

public class PQCSignatureApp extends JFrame {
    
    // ── OIDs Dilithium / ML-DSA ──────────────────────────────────────────────
    // [NUEVO] Constantes de OID para identificar firmas post-cuánticas
    private static final String OID_DILITHIUM2   = "1.3.6.1.4.1.2.267.7.4.4";
    private static final String OID_DILITHIUM3   = "1.3.6.1.4.1.2.267.7.6.5";
    private static final String OID_DILITHIUM5   = "1.3.6.1.4.1.2.267.7.8.7";
    private static final String OID_MLDSA44      = "2.16.840.1.101.3.4.3.17";
    private static final String OID_MLDSA65      = "2.16.840.1.101.3.4.3.18";
    private static final String OID_MLDSA87      = "2.16.840.1.101.3.4.3.19";
    private static final String OID_RSA_PREFIX   = "1.2.840.113549.1.1";
    private static final String OID_ECDSA_PREFIX = "1.2.840.10045.4.3";

    // Componentes para FIRMAR
    private JTextField pdfFileField;
    private JTextField keystoreField;
    private JPasswordField keystorePasswordField;
    private JButton selectPdfButton;
    private JButton selectKeystoreButton;
    private JButton signButton;
    private JProgressBar signProgressBar;
    private JLabel signStatusLabel;
    
    // Componentes para VERIFICAR
    private JTextField verifyPdfField;
    private JButton selectVerifyPdfButton;
    private JButton verifyButton;
    private JProgressBar verifyProgressBar;
    private JLabel verifyStatusLabel;
    private JTable resultsTable;
    private DefaultTableModel tableModel;
    
    private File selectedPdfFile;
    private File selectedKeystore;
    private File selectedVerifyPdf;
    
    public PQCSignatureApp() {
        initializeUI();
    }
    
    // Mostrar todos los paneles
    private void initializeUI() {
        setTitle("Sistema de Firma Post-Cuántica - Dilithium (ML-DSA-65)");
        setSize(1100, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        setIconImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JPanel signPanel = createSignPanel();
        tabbedPane.addTab("Firmar Documento", signPanel);
        
        JPanel verifyPanel = createVerifyPanel();
        tabbedPane.addTab("Verificar Firmas", verifyPanel);
        
        PanelFirmaMasiva massivePanel = new PanelFirmaMasiva();
        tabbedPane.addTab("Firma Masiva PQC", massivePanel);

        PanelFirmaRemota remotePanel = new PanelFirmaRemota();
        tabbedPane.addTab("Firma Remota PQC", remotePanel);
        
        add(tabbedPane, BorderLayout.CENTER);
        setLocationRelativeTo(null);
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // Panel FIRMAR UI
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel createSignPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JLabel titleLabel = new JLabel("Añadir Firma Post-Cuántica");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.BLACK);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.NORTH;
        mainPanel.add(titleLabel, gbc);  
      
        gbc.gridy = 2;
        gbc.insets = new Insets(15, 8, 15, 8);
        mainPanel.add(new JSeparator(), gbc);
        
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        
        // PDF
        gbc.gridy = 3; gbc.gridx = 0;
        JLabel pdfLabel = new JLabel("Documento PDF:");
        pdfLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainPanel.add(pdfLabel, gbc);
        
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
        selectPdfButton.addActionListener(e -> selectPdfFile());
        mainPanel.add(selectPdfButton, gbc);
        
        // Keystore
        gbc.gridy = 4; gbc.gridx = 0;
        JLabel keystoreLabel = new JLabel("Keystore PQC:");
        keystoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainPanel.add(keystoreLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        keystoreField = new JTextField(30);
        keystoreField.setEditable(false);
        keystoreField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        keystoreField.setBackground(new Color(245, 245, 245));
        keystoreField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        mainPanel.add(keystoreField, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0;
        selectKeystoreButton = new JButton("Seleccionar");
        styleButton(selectKeystoreButton, new Color(240, 240, 240));
        selectKeystoreButton.addActionListener(e -> selectKeystore());
        mainPanel.add(selectKeystoreButton, gbc);
        
        // Password
        gbc.gridy = 5; gbc.gridx = 0;
        JLabel passwordLabel = new JLabel("Contraseña:");
        passwordLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainPanel.add(passwordLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        keystorePasswordField = new JPasswordField(30);
        keystorePasswordField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        keystorePasswordField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        mainPanel.add(keystorePasswordField, gbc);
        
        // Botón firmar
        gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 3;
        gbc.insets = new Insets(20, 8, 8, 8);
        gbc.anchor = GridBagConstraints.NORTH;
        signButton = new JButton("Añadir Firma PQC");
        signButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        signButton.setBackground(new Color(76, 175, 80));
        signButton.setForeground(Color.BLACK);
        signButton.setFocusPainted(false);
        signButton.setBorder(BorderFactory.createEmptyBorder(12, 30, 12, 30));
        signButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        signButton.addActionListener(e -> addPQCSignature());
        signButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { signButton.setBackground(new Color(67, 160, 71)); }
            public void mouseExited(MouseEvent e)  { signButton.setBackground(new Color(76, 175, 80)); }
        });
        mainPanel.add(signButton, gbc);
        
        gbc.gridy = 7; gbc.insets = new Insets(15, 50, 5, 50);
        signProgressBar = new JProgressBar();
        signProgressBar.setStringPainted(true);
        signProgressBar.setVisible(false);
        mainPanel.add(signProgressBar, gbc);
        
        gbc.gridy = 8; gbc.insets = new Insets(5, 8, 8, 8);
        signStatusLabel = new JLabel("");
        signStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        signStatusLabel.setForeground(Color.GRAY);
        signStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(signStatusLabel, gbc);
        
        gbc.gridy = 99; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.VERTICAL;
        JPanel filler = new JPanel(); filler.setBackground(Color.WHITE);
        mainPanel.add(filler, gbc);
        
        panel.add(mainPanel, BorderLayout.CENTER);
        return panel;
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // Panel VERIFICAR UI
    // ══════════════════════════════════════════════════════════════════════════
    private JPanel createVerifyPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        JLabel titleLabel = new JLabel("Verificar Firmas Digitales");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.BLACK);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.NORTH;
        mainPanel.add(titleLabel, gbc);
        
        JLabel subtitleLabel = new JLabel("Analiza y verifica si un documento contiene firmas post-cuánticas");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        gbc.gridy = 1;
        mainPanel.add(subtitleLabel, gbc);
        
        gbc.gridy = 2; gbc.insets = new Insets(15, 8, 15, 8);
        mainPanel.add(new JSeparator(), gbc);
        
        gbc.insets = new Insets(8, 8, 8, 8); gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        
        // PDF a verificar
        gbc.gridy = 3; gbc.gridx = 0;
        JLabel pdfLabel = new JLabel("Documento PDF:");
        pdfLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainPanel.add(pdfLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        verifyPdfField = new JTextField(30);
        verifyPdfField.setEditable(false);
        verifyPdfField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        verifyPdfField.setBackground(new Color(245, 245, 245));
        verifyPdfField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        mainPanel.add(verifyPdfField, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0;
        selectVerifyPdfButton = new JButton("Seleccionar");
        styleButton(selectVerifyPdfButton, new Color(240, 240, 240));
        selectVerifyPdfButton.addActionListener(e -> selectVerifyPdf());
        mainPanel.add(selectVerifyPdfButton, gbc);
        
        // Botón verificar
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 3;
        gbc.insets = new Insets(20, 8, 8, 8); gbc.anchor = GridBagConstraints.NORTH;
        verifyButton = new JButton("Analizar Firmas");
        verifyButton.setFont(new Font("Segoe UI", Font.BOLD, 15));
        verifyButton.setBackground(new Color(33, 150, 243));
        verifyButton.setForeground(Color.BLACK);
        verifyButton.setFocusPainted(false);
        verifyButton.setBorder(BorderFactory.createEmptyBorder(12, 30, 12, 30));
        verifyButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        verifyButton.addActionListener(e -> verifySignatures());
        verifyButton.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { verifyButton.setBackground(new Color(30, 136, 229)); }
            public void mouseExited(MouseEvent e)  { verifyButton.setBackground(new Color(33, 150, 243)); }
        });
        mainPanel.add(verifyButton, gbc);
        
        gbc.gridy = 5; gbc.insets = new Insets(15, 50, 5, 50);
        verifyProgressBar = new JProgressBar();
        verifyProgressBar.setStringPainted(true);
        verifyProgressBar.setVisible(false);
        mainPanel.add(verifyProgressBar, gbc);
        
        gbc.gridy = 6; gbc.insets = new Insets(5, 8, 15, 8);
        verifyStatusLabel = new JLabel("");
        verifyStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        verifyStatusLabel.setForeground(Color.GRAY);
        verifyStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(verifyStatusLabel, gbc);
        
        // Tabla de resultados
        gbc.gridy = 7; gbc.insets = new Insets(10, 8, 8, 8);
        gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0;
        
        String[] columnNames = {"N° Firma", "Firmante", "Razón", "Ubicación", "Tamaño Real", "Fecha", "Tipo de Firma"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        
        resultsTable = new JTable(tableModel);
        resultsTable.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        resultsTable.setRowHeight(25);
        resultsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        resultsTable.getTableHeader().setBackground(new Color(240, 240, 240));
        resultsTable.setSelectionBackground(new Color(220, 237, 254));

        resultsTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                String texto = value != null ? value.toString() : "";
                if (texto.contains("PQC") || texto.contains("Dilithium") || texto.contains("ML-DSA")) {
                    // Verde para firmas post-cuánticas
                    c.setForeground(isSelected ? Color.WHITE : new Color(27, 130, 50));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    c.setForeground(isSelected ? Color.WHITE : Color.DARK_GRAY);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                return c;
            }
        });

        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(90);
        resultsTable.getColumnModel().getColumn(5).setPreferredWidth(150);
        resultsTable.getColumnModel().getColumn(6).setPreferredWidth(200);
        
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            "Resultados del Análisis",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 12)));
        mainPanel.add(tableScrollPane, gbc);
        
        panel.add(mainPanel, BorderLayout.CENTER);
        return panel;
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // Lógica de FIRMAR
    // ══════════════════════════════════════════════════════════════════════════
    private void styleButton(JButton button, Color bgColor) {
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.setBackground(bgColor);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(8, 20, 8, 20)));
    }
    
    private void selectPdfFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) { return f.isDirectory() || f.getName().toLowerCase().endsWith(".pdf"); }
            public String getDescription() { return "Archivos PDF (*.pdf)"; }
        });
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedPdfFile = fc.getSelectedFile();
            pdfFileField.setText(selectedPdfFile.getAbsolutePath());
        }
    }
    
    private void selectKeystore() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".p12")
                       || f.getName().toLowerCase().endsWith(".jks");
            }
            public String getDescription() { return "Keystore (*.p12, *.jks)"; }
        });
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedKeystore = fc.getSelectedFile();
            keystoreField.setText(selectedKeystore.getAbsolutePath());
        }
    }
    
    private void selectVerifyPdf() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) { return f.isDirectory() || f.getName().toLowerCase().endsWith(".pdf"); }
            public String getDescription() { return "Archivos PDF (*.pdf)"; }
        });
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedVerifyPdf = fc.getSelectedFile();
            verifyPdfField.setText(selectedVerifyPdf.getAbsolutePath());
        }
    }
    
    private void addPQCSignature() {
        if (selectedPdfFile == null)  { showError("Por favor selecciona un archivo PDF");     return; }
        if (selectedKeystore == null) { showError("Por favor selecciona un keystore PQC");    return; }
        char[] password = keystorePasswordField.getPassword();
        if (password.length == 0)    { showError("Por favor ingresa el password del keystore"); return; }
        
        signButton.setEnabled(false);
        signProgressBar.setVisible(true);
        signProgressBar.setIndeterminate(true);
        signStatusLabel.setText("Firmando documento...");
        
        SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    publish(20);
                    PQCSignatureService service = new PQCSignatureService();
                    publish(40);
                    service.loadKeystore(selectedKeystore.getAbsolutePath(), password);
                    publish(60);
                    String outputPath = generateOutputPath(selectedPdfFile.getAbsolutePath());
                    publish(80);
                    service.addPQCSignatureToPdf(selectedPdfFile.getAbsolutePath(), outputPath, "pqc-key");
                    publish(100);
                    SwingUtilities.invokeLater(() -> {
                        signStatusLabel.setText("Firma añadida exitosamente");
                        signStatusLabel.setForeground(new Color(76, 175, 80));
                        JOptionPane.showMessageDialog(PQCSignatureApp.this,
                            "Firma PQC añadida exitosamente\n\n" +
                            "Archivo: " + new File(outputPath).getName() + "\n" +
                            "Algoritmo: Dilithium3 (ML-DSA-65)\n" +
                            "Resistente a ataques cuánticos",
                            "Operación Exitosa", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> {
                        signStatusLabel.setText("Error al firmar");
                        signStatusLabel.setForeground(Color.RED);
                        String errorMsg = ex.getMessage();
                        String msg;
                        if (errorMsg != null && errorMsg.contains("Append mode requires a document without errors"))
                            msg = "El PDF tiene errores de formato.\n\nSoluciones:\n1. Abrirlo en Adobe Reader y guardarlo\n2. Imprimirlo a un nuevo PDF";
                        else if (errorMsg != null && errorMsg.contains("Cannot find"))
                            msg = "No se encontró el archivo o keystore.";
                        else if (errorMsg != null && errorMsg.contains("password"))
                            msg = "Contraseña incorrecta del keystore.";
                        else
                            msg = "Error al firmar:\n" + (errorMsg != null ? errorMsg : "Error desconocido");
                        showError(msg);
                    });
                }
                return null;
            }
            @Override
            protected void process(java.util.List<Integer> chunks) {
                int p = chunks.get(chunks.size() - 1);
                signProgressBar.setIndeterminate(false);
                signProgressBar.setValue(p);
                signProgressBar.setString(p + "%");
            }
            @Override
            protected void done() {
                signButton.setEnabled(true);
                keystorePasswordField.setText("");
                signProgressBar.setVisible(false);
                signProgressBar.setValue(0);
                signStatusLabel.setForeground(Color.GRAY);
            }
        };
        worker.execute();
    }
    
    // ══════════════════════════════════════════════════════════════════════════
    // Lógica de VERIFICAR
    // ══════════════════════════════════════════════════════════════════════════
    private void verifySignatures() {
        if (selectedVerifyPdf == null) { showError("Por favor selecciona un PDF para verificar"); return; }
        
        tableModel.setRowCount(0);
        verifyButton.setEnabled(false);
        verifyProgressBar.setVisible(true);
        verifyProgressBar.setIndeterminate(true);
        verifyStatusLabel.setText("Analizando firmas...");
        
        SwingWorker<Void, Object[]> worker = new SwingWorker<Void, Object[]>() {
            @Override
            protected Void doInBackground() throws Exception {
                try {
                    PdfReader reader   = new PdfReader(selectedVerifyPdf.getAbsolutePath());
                    PdfDocument pdfDoc = new PdfDocument(reader);
                    
                    PdfDictionary acroForm = pdfDoc.getCatalog()
                            .getPdfObject().getAsDictionary(PdfName.AcroForm);
                    
                    if (acroForm != null) {
                        PdfArray fields = acroForm.getAsArray(PdfName.Fields);
                        if (fields != null) {
                            for (int i = 0; i < fields.size(); i++) {
                                PdfDictionary field = fields.getAsDictionary(i);
                                if (field == null) continue;
                                PdfDictionary v = field.getAsDictionary(PdfName.V);
                                if (v == null) continue;
                                
                                String nroFirma  = String.valueOf(i + 1);
                                PdfString nameStr = v.getAsString(PdfName.Name);
                                String firmante   = nameStr != null ? nameStr.toUnicodeString() : "N/A";
                                PdfString reasonStr = v.getAsString(PdfName.Reason);
                                String razon      = reasonStr != null ? reasonStr.toUnicodeString() : "N/A";
                                PdfString locStr  = v.getAsString(PdfName.Location);
                                String ubicacion  = locStr != null ? locStr.toUnicodeString() : "N/A";
                                PdfString mStr    = v.getAsString(PdfName.M);
                                String fecha      = mStr != null ? mStr.toUnicodeString() : "N/A";
                                
                                String tamano = "N/A";
                                String tipo   = "Desconocido";
                                
                                PdfString contents = v.getAsString(PdfName.Contents);
                                if (contents != null) {
                                    byte[] sigBytes   = contents.getValueBytes();
                                    int tamanoReal    = calcularTamanoRealSinPadding(sigBytes);
                                    tamano = tamanoReal + " bytes";

                                    String oid = detectarOID(sigBytes);     
                                    tipo = determinarTipo(tamanoReal, oid);                                   
                             
                                }
                                
                                
                                publish(new Object[]{nroFirma, firmante, razon, ubicacion, tamano, fecha, tipo});
                            }
                        }
                    }
                    pdfDoc.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return null;
            }
            @Override
            protected void process(java.util.List<Object[]> chunks) {
                for (Object[] row : chunks) tableModel.addRow(row);
            }
            @Override
            protected void done() {
                verifyButton.setEnabled(true);
                verifyProgressBar.setVisible(false);
                int n = tableModel.getRowCount();
                verifyStatusLabel.setText("Análisis completado - " + n + " firma(s) encontrada(s)");
                verifyStatusLabel.setForeground(new Color(76, 175, 80));
            }
        };
        worker.execute();
    }
    
    private String detectarOID(byte[] signatureBytes) {
        try {
            int tamanoReal = calcularTamanoRealSinPadding(signatureBytes);
            byte[] datos = new byte[tamanoReal];
            System.arraycopy(signatureBytes, 0, datos, 0, tamanoReal);

            ASN1Primitive primitive = ASN1Primitive.fromByteArray(datos);

            return buscarOIDPQC(primitive);

        } catch (Exception e) {
            System.out.println("[detectarOID] Error al parsear ASN1: " + e.getMessage());
            return null;
        }
    }

  //── Recorre recursivamente la estructura ASN1 buscando OIDs de Dilithium/ML-DSA Prioriza los OIDs del SignerInfo (rama "Set" al final de SignedData)     */
    private String buscarOIDPQC(ASN1Primitive primitive) {
    	  //System.out.println("    [ASN1] tipo=" + primitive.getClass().getSimpleName());
        try {
            if (primitive instanceof ASN1Sequence) {
                ASN1Sequence seq = (ASN1Sequence) primitive;

               if (seq.size() >= 1 && seq.getObjectAt(0) instanceof ASN1ObjectIdentifier) {
                    String oid = ((ASN1ObjectIdentifier) seq.getObjectAt(0)).getId();
                    if (esPQC(oid)) return oid;
                }

                // Recorrer hijos
                for (int i = 0; i < seq.size(); i++) {
                    ASN1Encodable elem = seq.getObjectAt(i);
                    String resultado = buscarOIDPQC(elem.toASN1Primitive());
                    if (resultado != null) return resultado;
                }

            } else if (primitive instanceof ASN1Set) {
                ASN1Set set = (ASN1Set) primitive;
                for (int i = 0; i < set.size(); i++) {
                    String resultado = buscarOIDPQC(set.getObjectAt(i).toASN1Primitive());
                    if (resultado != null) return resultado;
                }

            } else if (primitive instanceof ASN1TaggedObject) {
                ASN1TaggedObject tagged = (ASN1TaggedObject) primitive;
                try {
                    String resultado = buscarOIDPQC(tagged.getBaseObject().toASN1Primitive());
                    if (resultado != null) return resultado;
                } catch (Exception ignored) {}

            } else if (primitive instanceof ASN1ObjectIdentifier) {
                String oid = ((ASN1ObjectIdentifier) primitive).getId();
                if (esPQC(oid)) return oid;
            }

        } catch (Exception ignored) {}

        return null;
    }

    private boolean esPQC(String oid) {
        switch (oid) {
            // Dilithium (familia .7.)
            case "1.3.6.1.4.1.2.267.7.4.4":  // Dilithium2
            case "1.3.6.1.4.1.2.267.7.6.5":  // Dilithium3  
            case "1.3.6.1.4.1.2.267.7.8.7":  // Dilithium5

            // Dilithium variantes (familia .12.)
            case "1.3.6.1.4.1.2.267.12.4.4": // Dilithium2 variante
            case "1.3.6.1.4.1.2.267.12.6.5": // Dilithium3 variante 
            case "1.3.6.1.4.1.2.267.12.8.7": // Dilithium5 variante

            // ML-DSA (NIST FIPS 204)
            case "2.16.840.1.101.3.4.3.17":   // ML-DSA-44
            case "2.16.840.1.101.3.4.3.18":   // ML-DSA-65
            case "2.16.840.1.101.3.4.3.19":   // ML-DSA-87
                return true;

            default:
                return false;
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // determinarTipo: OID tiene prioridad; tamaño es fallback
    // ══════════════════════════════════════════════════════════════════════════
    private String determinarTipo(int tamano, String oid) {
        if (oid != null) {
            switch (oid) {
                case "1.3.6.1.4.1.2.267.7.4.4":
                case "1.3.6.1.4.1.2.267.12.4.4":  // 
                    return "Dilithium2 / ML-DSA-44 (PQC)";

                case "1.3.6.1.4.1.2.267.7.6.5":
                case "1.3.6.1.4.1.2.267.12.6.5":  // 
                case "2.16.840.1.101.3.4.3.18":
                    return "Dilithium3 / ML-DSA-65 (PQC)";

                case "1.3.6.1.4.1.2.267.7.8.7":
                case "1.3.6.1.4.1.2.267.12.8.7":  //
                case "2.16.840.1.101.3.4.3.19":
                    return "Dilithium5 / ML-DSA-87 (PQC)";

                default:
                    if (oid.startsWith("1.2.840.113549.1.1")) return "RSA / PKCS#7 (OID detectado)";
                    if (oid.startsWith("1.2.840.10045.4.3"))  return "ECDSA / PKCS#7 (OID detectado)";
                    break;
            }
        }
        // heurística por tamaño (fallback)
        if (tamano >= 2000 && tamano <= 2900)  return "Dilithium2 (PQC) ~probable";
        if (tamano >= 2900 && tamano <= 3700)  return "Dilithium3 (PQC) ~probable";
        if (tamano >= 3700 && tamano <= 5000)  return "Dilithium5 (PQC) ~probable";
        if (tamano >= 1500 && tamano <= 20000) return "RSA/ECDSA (PKCS#7)";
        if (tamano >= 200  && tamano <= 650)   return "RSA";
        if (tamano >= 50   && tamano <= 200)   return "ECDSA";
        return "Desconocido (" + tamano + " bytes)";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Utilidades
    // ══════════════════════════════════════════════════════════════════════════
    private int calcularTamanoRealSinPadding(byte[] data) {
        for (int i = data.length - 1; i >= 0; i--) {
            if (data[i] != 0x00) return i + 1;
        }
        return 0;
    }
    
    private String generateOutputPath(String originalPath) {
        int dotIndex = originalPath.lastIndexOf('.');
        return originalPath.substring(0, dotIndex) + "_pqc_signed" + originalPath.substring(dotIndex);
    }
    
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            PQCSignatureApp app = new PQCSignatureApp();
            app.setVisible(true);
        });
    }
}
