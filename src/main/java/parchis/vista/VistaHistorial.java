package parchis.vista;

import parchis.modelo.IModeloDadoLectura;
import parchis.modelo.ObservadorDado;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

/**
 * Segunda vista del mismo modelo: lista los tiros y movimientos en orden.
 * Solo lee (IModeloDadoLectura) y se redibuja cuando el modelo avisa.
 */
public class VistaHistorial extends JPanel implements ObservadorDado {

    private final JTextArea area = new JTextArea();
    private final JLabel lblResumen = new JLabel(" ", SwingConstants.CENTER);

    public VistaHistorial(IModeloDadoLectura modelo) {
        super(new BorderLayout());
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setMargin(new java.awt.Insets(6, 8, 6, 8));

        JLabel titulo = new JLabel("Historial de tiros", SwingConstants.CENTER);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        JPanel norte = new JPanel(new java.awt.GridLayout(2, 1));
        norte.add(titulo);
        norte.add(lblResumen);

        add(norte, BorderLayout.NORTH);
        add(new JScrollPane(area), BorderLayout.CENTER);
        setPreferredSize(new Dimension(380, 520));

        modelo.agregarObservador(this);
        refrescar(modelo);
    }

    public JFrame mostrarEnVentana(int x, int y) {
        JFrame f = new JFrame("Parchís - historial de tiros");
        f.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        f.setContentPane(this);
        f.pack();
        f.setLocation(x, y);
        f.setVisible(true);
        return f;
    }

    @Override
    public void actualizar(IModeloDadoLectura modelo) {
        // Los avisos pueden llegar desde otro hilo (la red, en la Unidad II):
        // Swing solo se toca desde el hilo de eventos.
        if (SwingUtilities.isEventDispatchThread()) {
            refrescar(modelo);
        } else {
            SwingUtilities.invokeLater(() -> refrescar(modelo));
        }
    }

    private void refrescar(IModeloDadoLectura modelo) {
        List<String> lineas = modelo.getHistorial();
        area.setText(String.join("\n", lineas));
        area.setCaretPosition(area.getDocument().getLength());
        lblResumen.setText(modelo.partidaTerminada()
                ? "Ganó " + modelo.getNombre(modelo.getGanador())
                : "Turno de " + modelo.getNombre(modelo.getTurnoActual()) + "  |  seises seguidos: " + modelo.getSeisSeguidos());
    }
}
