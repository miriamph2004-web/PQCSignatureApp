import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PanelFirmaMasiva extends JPanel {

    private JSpinner          spFechaInicial;
    private JSpinner          spFechaFinal;
    private JButton           btnEjecutar;
    private JProgressBar      barraProgreso;
    private JLabel            lblEstado;
    private JTable            tablaResultados;
    private DefaultTableModel modeloTabla;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelado = false;
    private final FirmaMasivaService servicio;

    public PanelFirmaMasiva() {
        this.servicio = new FirmaMasivaService();
        buildUI();
    }

    // ── UI ────────────────────────────────────────────────────────────────────

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
        JLabel titleLabel = new JLabel("Firma Masiva Post-Cu\u00e1ntica");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.BLACK);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        gbc.anchor = GridBagConstraints.NORTH;
        mainPanel.add(titleLabel, gbc);

        // ── Subtítulo ─────────────────────────────────────────────────────────
        JLabel subtitleLabel = new JLabel(
            "Aplica firma Dilithium (ML-DSA-65) a documentos PDF almacenados en la base de datos");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(Color.GRAY);
        gbc.gridy = 1;
        mainPanel.add(subtitleLabel, gbc);

        // ── Separador ─────────────────────────────────────────────────────────
        gbc.gridy = 2; gbc.insets = new Insets(15, 8, 15, 8);
        mainPanel.add(new JSeparator(), gbc);
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;

        // ── Fecha inicial ─────────────────────────────────────────────────────
        gbc.gridy = 3; gbc.gridx = 0;
        JLabel lFechaIni = new JLabel("Fecha emisi\u00f3n inicial:");
        lFechaIni.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainPanel.add(lFechaIni, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        spFechaInicial = makeSpinner(LocalDate.now().withDayOfMonth(1));
        mainPanel.add(spFechaInicial, gbc);

        // ── Fecha final ───────────────────────────────────────────────────────
        gbc.gridy = 4; gbc.gridx = 0; gbc.weightx = 0;
        JLabel lFechaFin = new JLabel("Fecha emisi\u00f3n final:");
        lFechaFin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainPanel.add(lFechaFin, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        spFechaFinal = makeSpinner(LocalDate.now());
        mainPanel.add(spFechaFinal, gbc);

        // ── Botón Ejecutar ────────────────────────────────────────────────────
        gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 3;
        gbc.insets = new Insets(20, 8, 8, 8);
        gbc.anchor = GridBagConstraints.NORTH;
        btnEjecutar = new JButton("Firma Masiva PQC");
        btnEjecutar.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btnEjecutar.setBackground(new Color(76, 175, 80));
        btnEjecutar.setForeground(Color.BLACK);
        btnEjecutar.setFocusPainted(false);
        btnEjecutar.setBorder(BorderFactory.createEmptyBorder(12, 30, 12, 30));
        btnEjecutar.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEjecutar.addActionListener(this::onEjecutar);
        btnEjecutar.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btnEjecutar.setBackground(new Color(67, 160, 71)); }
            public void mouseExited(MouseEvent e)  { btnEjecutar.setBackground(new Color(76, 175, 80)); }
        });
        mainPanel.add(btnEjecutar, gbc);

        // ── Barra de progreso ─────────────────────────────────────────────────
        gbc.gridy = 7; gbc.insets = new Insets(15, 50, 5, 50);
        barraProgreso = new JProgressBar();
        barraProgreso.setStringPainted(true);
        barraProgreso.setVisible(false);
        mainPanel.add(barraProgreso, gbc);

        // ── Estado ────────────────────────────────────────────────────────────
        gbc.gridy = 8; gbc.insets = new Insets(5, 8, 15, 8);
        lblEstado = new JLabel("");
        lblEstado.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblEstado.setForeground(Color.GRAY);
        lblEstado.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(lblEstado, gbc);

        // ── Tabla de resultados ───────────────────────────────────────────────
        gbc.gridy = 9; gbc.insets = new Insets(10, 8, 8, 8);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        String[] columnas = {
            "A\u00f1o", "N\u00famero Emisi\u00f3n", "Fecha Registro",
            "Estado", "Mensaje"
        };
        modeloTabla = new DefaultTableModel(columnas, 0) {
            public boolean isCellEditable(int row, int col) { return false; }
        };

        tablaResultados = new JTable(modeloTabla);
        tablaResultados.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tablaResultados.setRowHeight(25);
        tablaResultados.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));
        tablaResultados.getTableHeader().setBackground(new Color(240, 240, 240));
        tablaResultados.setSelectionBackground(new Color(220, 237, 254));
        tablaResultados.setAutoCreateRowSorter(true);
        tablaResultados.setDefaultRenderer(Object.class, new EstadoRenderer());

        tablaResultados.getColumnModel().getColumn(0).setPreferredWidth(65);
        tablaResultados.getColumnModel().getColumn(0).setMaxWidth(90);
        tablaResultados.getColumnModel().getColumn(1).setPreferredWidth(110);
        tablaResultados.getColumnModel().getColumn(1).setMaxWidth(140);
        tablaResultados.getColumnModel().getColumn(2).setPreferredWidth(110);
        tablaResultados.getColumnModel().getColumn(2).setMaxWidth(140);
        tablaResultados.getColumnModel().getColumn(3).setPreferredWidth(90);
        tablaResultados.getColumnModel().getColumn(3).setMaxWidth(110);

        JScrollPane scrollTabla = new JScrollPane(tablaResultados);
        scrollTabla.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            "Resultados del Proceso",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 12)
        ));
        mainPanel.add(scrollTabla, gbc);
        add(mainPanel, BorderLayout.CENTER);
    }

    // ── LÓGICA ────────────────────────────────────────────────────────────────

    private void onEjecutar(ActionEvent e) {
        LocalDate desde = toDate(spFechaInicial);
        LocalDate hasta = toDate(spFechaFinal);

        if (hasta.isBefore(desde)) {
            JOptionPane.showMessageDialog(this,
                "La fecha final debe ser igual o posterior a la fecha inicial.",
                "Rango inv\u00e1lido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int ok = JOptionPane.showConfirmDialog(this,
            "Se aplicar\u00e1 firma PQC a los documentos con Fecha de Emisión entre:\n\n"
            + " Desde: " + desde + " Hasta: " + hasta + "\n\n"
            + "\u00bfDesea continuar?",
            "Confirmar Firma Masiva",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.YES_OPTION) return;

        modeloTabla.setRowCount(0);
        lblEstado.setText("Procesando documentos...");
        lblEstado.setForeground(Color.GRAY);
        btnEjecutar.setEnabled(false);
        barraProgreso.setVisible(true);
        barraProgreso.setIndeterminate(true);
        barraProgreso.setString("Consultando base de datos...");
        cancelado = false;
        executor.submit(() -> ejecutar(desde, hasta));
    }

    private void ejecutar(LocalDate desde, LocalDate hasta) {
        int  total = 0, firmados = 0, errores = 0, omitidos = 0;

        // Acumuladores de tiempos (solo docs firmados)
        long sumaCriptoMs      = 0; // T1: firma criptográfica pura (SHA-512+Dilithium+CMS)
        long sumaOverheadPdfMs = 0; // T2: overhead iText/PdfSigner
        long sumaIoMs          = 0; // T3: I/O BD + disco
        long sumaTotalDocMs    = 0; // T4: tiempo real total por documento

        try {
            List<String[]> ids = servicio.obtenerIds(desde, hasta);
            total = ids.size();

            if (total == 0) {
                SwingUtilities.invokeLater(() -> {
                    barraProgreso.setIndeterminate(false);
                    barraProgreso.setValue(0);
                    barraProgreso.setString("Sin resultados");
                    lblEstado.setText("No se encontraron documentos en el rango indicado");
                    lblEstado.setForeground(Color.GRAY);
                    btnEjecutar.setEnabled(true);
                });
                return;
            }

            final int t = total;
            SwingUtilities.invokeLater(() -> {
                barraProgreso.setIndeterminate(false);
                barraProgreso.setMaximum(t);
                barraProgreso.setValue(0);
            });

            // ── Cronómetro total del lote ─────────────────────────────────────
            long inicioLote = System.nanoTime();

            for (int i = 0; i < ids.size(); i++) {
                if (cancelado) break;

                String[] cl  = ids.get(i);
                String   ann = cl[0];
                String   emi = cl[1];

                FirmaMasivaService.Resultado res = servicio.procesarDocumento(ann, emi);

                switch (res.estado) {
                    case FIRMADO:
                        firmados++;
                        sumaCriptoMs      += res.tiempoFirmaCriptoMs;
                        sumaOverheadPdfMs += res.tiempoOverheadPdfMs;
                        sumaIoMs          += res.tiempoIoMs;
                        sumaTotalDocMs    += res.tiempoTotalDocMs;
                        break;
                    case ERROR:   errores++;  break;
                    case OMITIDO: omitidos++; break;
                }

                final int fi = firmados, er = errores, om = omitidos, idx = i + 1;
                final FirmaMasivaService.Resultado rf = res;
                final String annF = ann, emiF = emi;

                SwingUtilities.invokeLater(() -> {
                    modeloTabla.addRow(new Object[]{
                        annF, emiF,
                        rf.fechaModif != null ? rf.fechaModif.toString() : "-",
                        rf.estado.name(),
                        rf.mensaje
                    });
                    int ult = modeloTabla.getRowCount() - 1;
                    tablaResultados.scrollRectToVisible(tablaResultados.getCellRect(ult, 0, true));
                    barraProgreso.setValue(idx);
                    barraProgreso.setString(idx + " / " + t);
                    lblEstado.setText("Procesando... OK: " + fi + "  Errores: " + er + "  Omitidos: " + om);
                });
            }

            // ── Calcular estadísticas finales ─────────────────────────────────
            long tiempoTotalLoteMs = (System.nanoTime() - inicioLote) / 1_000_000;

            // Promedios calculados solo sobre documentos firmados exitosamente
            long promCriptoMs      = firmados > 0 ? sumaCriptoMs      / firmados : 0;
            long promOverheadPdfMs = firmados > 0 ? sumaOverheadPdfMs / firmados : 0;
            long promIoMs          = firmados > 0 ? sumaIoMs          / firmados : 0;
            long promTotalDocMs    = firmados > 0 ? sumaTotalDocMs     / firmados : 0;
            
            System.out.println("═════════════════════════════════════════════════════");
            System.out.println("         RESUMEN FIRMA MASIVA PQC                    ");
            System.out.println("═════════════════════════════════════════════════════");
            System.out.printf( "  Total documentos        : %-24d%n", total);
            System.out.printf( "     Firmados             : %-24d%n", firmados);
            System.out.printf( "     Errores              : %-24d%n", errores);
            System.out.printf( "     Omitidos             : %-24d%n", omitidos);
            System.out.printf( "  Tiempo total del lote   : %d ms%n", tiempoTotalLoteMs);
            System.out.println("══════════════════════════════════════════════════════");
            System.out.println("  PROMEDIOS POR DOCUMENTO   ");
            System.out.printf( "     Firma criptográfica    (SHA-512 + Dilithium + CMS, sin I/O)         : %d ms%n", promCriptoMs);
            System.out.printf( "     Overhead iText/PDF     (PdfSigner: rangos, embed CMS, escritura PDF): %d ms%n", promOverheadPdfMs);
            System.out.printf( "     I/O  (BD + disco)      (leer Blob + tmpIn + tmpOut + actualizarBlob) : %d ms%n", promIoMs);
            long promOverheadConexMs = promTotalDocMs - (promCriptoMs + promOverheadPdfMs + promIoMs);
            System.out.printf( "     Overhead conexión/misc (getConexion + leerFechaModif + delete + JVM)  : %d ms%n", promOverheadConexMs);
            System.out.printf( "  TOTAL POR DOCUMENTO       (suma de los 4 componentes anteriores)         : %d ms%n", promTotalDocMs);
            System.out.println("═════════════════════════════════════════════════════");
            System.out.println("  Verificación de cierre                              ");
            System.out.println("══════════════════════════════════════════════════════");
            long sumaTotalDocLote  = promTotalDocMs * firmados;
            long overheadEntreDocs = tiempoTotalLoteMs - sumaTotalDocLote;
            System.out.printf( "     Cripto+PDF+I/O+misc = %d ms%n",
                promCriptoMs + promOverheadPdfMs + promIoMs + promOverheadConexMs, promTotalDocMs);
            System.out.printf( "     Overhead entre docs (UI Swing + bucle + conexiones secuenciales) : %d ms%n", overheadEntreDocs);
            System.out.printf( "  Tiempo total lote     : %d ms%n", tiempoTotalLoteMs);
       
            // ─────────────────────────────────────────────────────────────────

            final int   tF  = total,      fiF = firmados, erF = errores, omF = omitidos;
            final long  ttL = tiempoTotalLoteMs;
            final long  pC  = promCriptoMs, pO = promOverheadPdfMs, pI = promIoMs, pT = promTotalDocMs;

            SwingUtilities.invokeLater(() -> {
                barraProgreso.setValue(tF);
                barraProgreso.setString("Completado");

                String resumen = "Completado \u2014 Total: " + tF
                    + "  |  Firmados: " + fiF
                    + "  |  Errores: "  + erF
                    + "  |  Omitidos: " + omF;
                lblEstado.setText(resumen);
                lblEstado.setForeground(erF > 0 ? new Color(183, 28, 28) : new Color(46, 125, 50));

                JOptionPane.showMessageDialog(PanelFirmaMasiva.this,
                    "Proceso de firma masiva completado\n\n"
                    + "Total procesados : " + tF  + "\n"
                    + "Firmados OK      : " + fiF + "\n"
                    + "Con error        : " + erF + "\n"
                    + "Omitidos         : " + omF + "\n\n"
                    + "Tiempo total     : " + ttL + " ms\n\n"
                    + "Consulte la consola para el desglose\n"
                    + "detallado de tiempos por documento.",
                    "Resultado Final",
                    erF > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
            });

        } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                barraProgreso.setIndeterminate(false);
                barraProgreso.setString("Error");
                lblEstado.setText("Error: " + ex.getMessage());
                lblEstado.setForeground(new Color(183, 28, 28));
                JOptionPane.showMessageDialog(PanelFirmaMasiva.this,
                    "Error durante el proceso:\n\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            });
        } finally {
            SwingUtilities.invokeLater(() -> btnEjecutar.setEnabled(true));
        }
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private JSpinner makeSpinner(LocalDate ld) {
        SpinnerDateModel m = new SpinnerDateModel(
            Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant()),
            null, null, java.util.Calendar.DAY_OF_MONTH);
        JSpinner s = new JSpinner(m);
        s.setEditor(new JSpinner.DateEditor(s, "dd/MM/yyyy"));
        s.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        s.setBackground(new Color(245, 245, 245));
        s.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        s.setPreferredSize(new Dimension(160, 34));
        return s;
    }

    private LocalDate toDate(JSpinner sp) {
        return ((Date) sp.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ── Renderer de colores para la columna Estado ────────────────────────────
    private static class EstadoRenderer extends javax.swing.table.DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            if (!sel) {
                String estado = String.valueOf(t.getValueAt(row, 3));
                if ("FIRMADO".equals(estado)) {
                    setBackground(new Color(232, 245, 233));
                    setForeground(new Color(27, 94, 32));
                } else if ("ERROR".equals(estado)) {
                    setBackground(new Color(255, 235, 238));
                    setForeground(new Color(183, 28, 28));
                } else if ("OMITIDO".equals(estado)) {
                    setBackground(new Color(255, 248, 225));
                    setForeground(new Color(180, 90, 0));
                } else {
                    setBackground(Color.WHITE);
                    setForeground(Color.BLACK);
                }
            }
            return this;
        }
    }
}
