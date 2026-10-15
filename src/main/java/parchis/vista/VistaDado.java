package parchis.vista;

import parchis.controlador.ControlDado;
import parchis.modelo.IModeloDadoLectura;
import parchis.modelo.ModeloDado;
import parchis.modelo.ObservadorDado;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import static parchis.modelo.IModeloDadoLectura.AMARILLO;
import static parchis.modelo.IModeloDadoLectura.AZUL;
import static parchis.modelo.IModeloDadoLectura.FICHAS_POR_JUGADOR;
import static parchis.modelo.IModeloDadoLectura.PASOS_BASE;
import static parchis.modelo.IModeloDadoLectura.PASOS_EN_ANILLO;
import static parchis.modelo.IModeloDadoLectura.PASOS_META;
import static parchis.modelo.IModeloDadoLectura.ROJO;
import static parchis.modelo.IModeloDadoLectura.TOTAL_CASILLAS;
import static parchis.modelo.IModeloDadoLectura.VERDE;

/**
 * Vista gráfica (Swing) del dado y del tablero. Observa al modelo y se redibuja
 * cuando este avisa. Solo ve IModeloDadoLectura: no puede lanzar el dado ni mover
 * fichas, solo le envía las acciones del usuario al controlador.
 *
 * Todo el dibujo (tablero en cruz, fichas, etiquetas y el dado con sus puntos)
 * se hace con Graphics en paintComponent. Cada ventana representa a un jugador
 * local; las demás ventanas lo ven como observadores.
 */
public class VistaDado extends JPanel implements ObservadorDado, MouseListener {

    // ---- Geometría del tablero en cruz: rejilla de 19x19 casillas ----
    private static final int N = 19;
    private static final double ALTO_VIRTUAL = 23;   // 19 del tablero + 2 arriba y 2 abajo para las etiquetas
    private static final Point[] ANILLO = construirAnillo();

    private final IModeloDadoLectura modelo;
    private final ControlDado control;
    private final int jugadorLocal;

    private final JPanel norte = new JPanel(new GridLayout(2, 1));
    private final JPanel controles = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
    private final JLabel reservaDado = new JLabel();   // hueco donde se dibuja el dado
    private final JLabel lblTurno = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel lblMensaje = new JLabel(" ", SwingConstants.CENTER);
    private final JButton btnLanzar = new JButton("Lanzar dado");
    private final JButton btnMover = new JButton("Mover ficha");
    private final JButton btnNuevoTurno = new JButton("Nuevo turno");

    private Integer fichaSeleccionada;   // foco local: no se comparte con las demás vistas

    // Escala y origen del tablero (se recalculan en cada dibujo y en cada clic)
    private double escala;
    private double origenX;
    private double origenY;

    public VistaDado(IModeloDadoLectura modelo, ControlDado control, int jugadorLocal) {
        super(new BorderLayout());
        this.modelo = modelo;
        this.control = control;
        this.jugadorLocal = jugadorLocal;
        construirInterfaz();
        addMouseListener(this);
        modelo.agregarObservador(this);
        refrescar();
    }

    // ================= Construcción de la interfaz =================
    private void construirInterfaz() {
        setBackground(new Color(245, 245, 245));
        setPreferredSize(new Dimension(580, 800));

        lblTurno.setFont(lblTurno.getFont().deriveFont(Font.BOLD, 16f));
        lblMensaje.setFont(lblMensaje.getFont().deriveFont(13f));
        norte.setOpaque(false);
        norte.add(lblTurno);
        norte.add(lblMensaje);

        reservaDado.setPreferredSize(new Dimension(60, 56));
        controles.setOpaque(false);
        controles.add(reservaDado);
        controles.add(btnLanzar);
        controles.add(btnMover);
        controles.add(btnNuevoTurno);

        btnLanzar.addActionListener(e -> control.lanzar(jugadorLocal));
        btnMover.addActionListener(e -> clicMoverFicha());
        btnNuevoTurno.addActionListener(e -> control.reiniciarTurno(jugadorLocal));

        add(norte, BorderLayout.NORTH);
        add(controles, BorderLayout.SOUTH);
    }

    public JFrame mostrarEnVentana(int x, int y) {
        JFrame f = new JFrame("Parchís - vista de " + modelo.getNombre(jugadorLocal));
        f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        f.setContentPane(this);
        f.pack();
        f.setLocation(x, y);
        f.setVisible(true);
        return f;
    }

    // ================= Observer =================
    @Override
    public void actualizar(IModeloDadoLectura m) {
        // Swing solo se modifica desde el hilo de eventos (EDT). Los avisos de
        // la red llegarán desde otro hilo, por eso se usa invokeLater.
        if (SwingUtilities.isEventDispatchThread()) {
            refrescar();
        } else {
            SwingUtilities.invokeLater(this::refrescar);
        }
    }

    // ================= Estado de los controles =================
    public boolean lanzarDadoHabilitado() {
        return modelo.puedeTirar() && modelo.esTurnoDe(jugadorLocal);
    }

    public boolean moverFichaHabilitado() {
        return fichaSeleccionada != null && modelo.esFichaElegible(jugadorLocal, fichaSeleccionada);
    }

    public boolean nuevoTurnoHabilitado() {
        return modelo.turnoTerminado() && modelo.esTurnoDe(jugadorLocal);
    }

    private void refrescar() {
        if (fichaSeleccionada != null && !modelo.esFichaElegible(jugadorLocal, fichaSeleccionada)) {
            fichaSeleccionada = null;
        }
        btnLanzar.setEnabled(lanzarDadoHabilitado());
        btnMover.setEnabled(moverFichaHabilitado());
        btnNuevoTurno.setEnabled(nuevoTurnoHabilitado());
        if (modelo.partidaTerminada()) {
            lblTurno.setText("Fin de la partida: ganó " + modelo.getNombre(modelo.getGanador()));
        } else {
            lblTurno.setText("Turno de " + modelo.getNombre(modelo.getTurnoActual())
                    + (modelo.esTurnoDe(jugadorLocal) ? "  (es tu turno)" : "  (solo observas)"));
        }
        lblMensaje.setText(modelo.getMensaje().isEmpty() ? " " : modelo.getMensaje());
        reservaDado.setCursor(Cursor.getPredefinedCursor(lanzarDadoHabilitado() ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    // ================= Acciones del usuario =================
    /** Mensaje temporal de ayuda; se reemplaza en la siguiente actualización del modelo. */
    private void avisar(String texto) {
        lblMensaje.setText(texto);
    }

    /** Clic sobre el dado: equivale a pulsar «Lanzar dado». */
    private void clicDado() {
        if (lanzarDadoHabilitado()) {
            control.lanzar(jugadorLocal);
        } else if (modelo.partidaTerminada()) {
            avisar("La partida terminó.");
        } else if (!modelo.esTurnoDe(jugadorLocal)) {
            avisar("No es tu turno, espera a " + modelo.getNombre(modelo.getTurnoActual()) + ".");
        } else if (modelo.turnoTerminado()) {
            avisar("Tu turno terminó: pulsa \"Nuevo turno\".");
        } else {
            avisar("Ya lanzaste: elige una ficha y pulsa \"Mover ficha\".");
        }
    }

    /** Selección local de una ficha (clic sobre la ficha en el tablero). */
    private void seleccionarFicha(int idFicha) {
        if (fichaSeleccionada != null && fichaSeleccionada == idFicha) {   // segundo clic: quita la selección
            fichaSeleccionada = null;
            refrescar();
            return;
        }
        if (!control.seleccionarFicha(jugadorLocal, idFicha)) {
            return;   // no elegible
        }
        fichaSeleccionada = idFicha;   // solo cambia el foco local; el tablero no se modifica
        refrescar();
    }

    /** «Mover ficha»: confirmación previa; el tablero no cambia hasta que el modelo responde. */
    private void clicMoverFicha() {
        if (!moverFichaHabilitado()) {
            return;
        }
        int id = fichaSeleccionada;
        int r = JOptionPane.showConfirmDialog(this, "¿Mover la ficha #" + id + "?",
                "Confirmar movimiento", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            control.moverFicha(jugadorLocal, id);
        }
    }

    private void clicTablero(Point p) {
        Point2D.Double[][] pos = posiciones();
        int tocada = -1;
        for (int id = 1; id <= FICHAS_POR_JUGADOR; id++) {
            Point2D.Double c = pos[jugadorLocal][id - 1];
            double r = Math.max(radio(modelo.getPasos(jugadorLocal, id)), 0.3) * escala;
            if (Point2D.distance(px(c.x), py(c.y), p.x, p.y) <= r) {
                tocada = id;
            }
        }
        if (tocada < 0 || modelo.partidaTerminada()) {
            return;
        }
        if (!modelo.esTurnoDe(jugadorLocal)) {
            avisar("No es tu turno, espera a " + modelo.getNombre(modelo.getTurnoActual()) + ".");
        } else if (modelo.puedeTirar()) {
            avisar("Primero lanza el dado (clic en el dado o en \"Lanzar dado\").");
        } else if (modelo.turnoTerminado()) {
            avisar("Tu turno terminó: pulsa \"Nuevo turno\".");
        } else if (!control.seleccionarFicha(jugadorLocal, tocada)) {
            avisar("La ficha #" + tocada + " no puede moverse con este resultado.");
        } else {
            seleccionarFicha(tocada);
        }
    }

    // ================= MouseListener (la vista misma escucha los clics) =================
    @Override
    public void mousePressed(MouseEvent e) {
        calcularEscala();
        if (rectDado().contains(e.getPoint())) {
            clicDado();
        } else {
            clicTablero(e.getPoint());
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    // ================= Geometría del tablero =================
    private static Point[] construirAnillo() {
        Point[] a = new Point[TOTAL_CASILLAS];
        int i = 0;
        // Brazo superior (amarillo)
        for (int r = 7; r >= 0; r--) a[i++] = new Point(10, r);
        a[i++] = new Point(9, 0);
        for (int r = 0; r <= 7; r++) a[i++] = new Point(8, r);
        // Brazo izquierdo (verde)
        for (int c = 7; c >= 0; c--) a[i++] = new Point(c, 8);
        a[i++] = new Point(0, 9);
        for (int c = 0; c <= 7; c++) a[i++] = new Point(c, 10);
        // Brazo inferior (azul)
        for (int r = 11; r <= 18; r++) a[i++] = new Point(8, r);
        a[i++] = new Point(9, 18);
        for (int r = 18; r >= 11; r--) a[i++] = new Point(10, r);
        // Brazo derecho (rojo)
        for (int c = 11; c <= 18; c++) a[i++] = new Point(c, 10);
        a[i++] = new Point(18, 9);
        for (int c = 18; c >= 11; c--) a[i++] = new Point(c, 8);
        return a;
    }

    /** Casilla del carril de meta: k = 0 es la casilla final del anillo, k = 1..7 el pasillo. */
    private static Point pasillo(int color, int k) {
        return switch (color) {
            case AMARILLO -> new Point(9, k);
            case VERDE -> new Point(k, 9);
            case AZUL -> new Point(9, 18 - k);
            default -> new Point(18 - k, 9);
        };
    }

    private static Point origenBase(int color) {
        return switch (color) {
            case AMARILLO -> new Point(0, 0);
            case ROJO -> new Point(11, 0);
            case VERDE -> new Point(0, 11);
            default -> new Point(11, 11);
        };
    }

    /** Centro del hueco de una ficha en su base (la ficha 2 queda arriba a la derecha). */
    private static Point2D.Double slotBase(int color, int idFicha) {
        Point o = origenBase(color);
        double dx = (idFicha == 2 || idFicha == 4) ? 5.5 : 2.5;
        double dy = (idFicha == 3 || idFicha == 4) ? 5.5 : 2.5;
        return new Point2D.Double(o.x + dx, o.y + dy);
    }

    /** Posición dentro del triángulo central del color. */
    private static Point2D.Double slotMeta(int color, int idFicha) {
        double off = (idFicha - 2.5) * 0.22;
        return switch (color) {
            case AMARILLO -> new Point2D.Double(9.5 + off, 8.85);
            case VERDE -> new Point2D.Double(8.85, 9.5 + off);
            case AZUL -> new Point2D.Double(9.5 + off, 10.15);
            default -> new Point2D.Double(10.15, 9.5 + off);
        };
    }

    /** Centro (en casillas) de una posición expresada en pasos (-1 = base, 71 = meta). */
    private Point2D.Double centroPara(int color, int idFicha, int pasos) {
        if (pasos < 0) {
            return slotBase(color, idFicha);
        }
        if (pasos >= PASOS_META) {
            return slotMeta(color, idFicha);
        }
        Point c = pasos <= PASOS_EN_ANILLO
                ? ANILLO[(modelo.getCasillaSalida(color) + pasos) % TOTAL_CASILLAS]
                : pasillo(color, pasos - PASOS_EN_ANILLO);
        return new Point2D.Double(c.x + 0.5, c.y + 0.5);
    }

    private double radio(int pasos) {
        return pasos >= PASOS_META ? 0.2 : 0.38;
    }

    /** Centros de todas las fichas, separando las que comparten casilla. */
    private Point2D.Double[][] posiciones() {
        Point2D.Double[][] res = new Point2D.Double[modelo.getNumeroJugadores()][FICHAS_POR_JUGADOR];
        Map<String, Integer> ocupacion = new HashMap<>();
        for (int j = 0; j < modelo.getNumeroJugadores(); j++) {
            for (int id = 1; id <= FICHAS_POR_JUGADOR; id++) {
                int pasos = modelo.getPasos(j, id);
                Point2D.Double c = centroPara(modelo.getColor(j), id, pasos);
                if (pasos > PASOS_BASE && pasos < PASOS_META) {
                    int n = ocupacion.merge(c.x + "," + c.y, 1, Integer::sum) - 1;
                    c = new Point2D.Double(c.x + n * 0.22, c.y - n * 0.22);
                }
                res[j][id - 1] = c;
            }
        }
        return res;
    }

    // ================= Escala y conversión de coordenadas =================
    /** El tablero ocupa el espacio entre las etiquetas de arriba y los controles de abajo. */
    private void calcularEscala() {
        int y0 = norte.getY() + norte.getHeight();
        int alto = controles.getY() - y0;
        escala = Math.min(getWidth() / (double) N, alto / ALTO_VIRTUAL);
        origenX = (getWidth() - N * escala) / 2;
        origenY = y0 + (alto - ALTO_VIRTUAL * escala) / 2 + 2 * escala;
    }

    private double px(double x) {
        return origenX + x * escala;
    }

    private double py(double y) {
        return origenY + y * escala;
    }

    private Rectangle2D celda(double x, double y, double w, double h) {
        return new Rectangle2D.Double(px(x), py(y), w * escala, h * escala);
    }

    private Rectangle rectDado() {
        return SwingUtilities.convertRectangle(controles, reservaDado.getBounds(), this);
    }

    // ================= Utilidades de color =================
    private static Color awt(int color) {
        return switch (color) {
            case VERDE -> new Color(0, 214, 0);
            case ROJO -> new Color(230, 20, 20);
            case AZUL -> new Color(20, 50, 230);
            default -> new Color(255, 200, 0);
        };
    }

    private static Color alfa(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    // ================= Dibujo con Graphics =================
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        calcularEscala();
        if (escala <= 0) {
            return;   // todavía no se ha hecho el layout
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        dibujarTablero(g);
        dibujarVistaPrevia(g);
        dibujarFichas(g);
        for (int j = 0; j < modelo.getNumeroJugadores(); j++) {
            dibujarEtiqueta(g, j);
        }
        Rectangle r = rectDado();
        dibujarDado(g, r.x + 4, r.y + 4, 48, modelo.getValor());
        g.dispose();
    }

    private void dibujarTablero(Graphics2D g) {
        double s = escala;

        // Marco del tablero
        g.setColor(new Color(217, 217, 217));
        g.fill(new RoundRectangle2D.Double(px(-0.3), py(-0.3), 19.6 * s, 19.6 * s, s, s));
        g.setColor(new Color(120, 120, 120));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(px(-0.3), py(-0.3), 19.6 * s, 19.6 * s, s, s));

        boolean[] activo = new boolean[4];
        for (int j = 0; j < modelo.getNumeroJugadores(); j++) {
            activo[modelo.getColor(j)] = true;
        }

        // Bases (los colores sin jugador quedan inactivos y sin fichas)
        for (int c = 0; c < 4; c++) {
            Point o = origenBase(c);
            Color col = awt(c);
            RoundRectangle2D rr = new RoundRectangle2D.Double(px(o.x + 0.2), py(o.y + 0.2), 7.6 * s, 7.6 * s, s, s);
            g.setColor(alfa(col, activo[c] ? 90 : 55));
            g.fill(rr);
            g.setColor(activo[c] ? col : alfa(col, 140));
            g.setStroke(new BasicStroke(activo[c] ? 3f : 2f));
            g.draw(rr);
            if (activo[c]) {
                for (int id = 1; id <= FICHAS_POR_JUGADOR; id++) {   // huecos de la base
                    Point2D.Double h = slotBase(c, id);
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new Ellipse2D.Double(px(h.x) - 0.5 * s, py(h.y) - 0.5 * s, s, s));
                }
            }
        }

        // Casillas de los brazos
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                boolean ejeV = c >= 8 && c <= 10;
                boolean ejeH = r >= 8 && r <= 10;
                if ((ejeV || ejeH) && !(ejeV && ejeH)) {
                    g.setColor(new Color(240, 240, 240));
                    g.fill(celda(c, r, 1, 1));
                    g.setColor(new Color(150, 150, 150));
                    g.setStroke(new BasicStroke(1f));
                    g.draw(celda(c, r, 1, 1));
                }
            }
        }

        // Carriles de meta y casillas de salida
        for (int c = 0; c < 4; c++) {
            for (int k = 0; k <= 7; k++) {
                Point p = pasillo(c, k);
                g.setColor(awt(c));
                g.fill(celda(p.x, p.y, 1, 1));
                g.setColor(new Color(110, 110, 110));
                g.draw(celda(p.x, p.y, 1, 1));
            }
            Point sal = ANILLO[modelo.getCasillaSalida(c)];
            g.setColor(awt(c));
            g.fill(celda(sal.x, sal.y, 1, 1));
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (s * 0.6)));
            g.drawString("★", (float) (px(sal.x) + 0.2 * s), (float) (py(sal.y) + 0.75 * s));
        }

        // Centro: cuatro triángulos
        triangulo(g, awt(AMARILLO), 8, 8, 11, 8);
        triangulo(g, awt(ROJO), 11, 8, 11, 11);
        triangulo(g, awt(AZUL), 11, 11, 8, 11);
        triangulo(g, awt(VERDE), 8, 11, 8, 8);
    }

    private void triangulo(Graphics2D g, Color col, double x1, double y1, double x2, double y2) {
        Path2D t = new Path2D.Double();
        t.moveTo(px(x1), py(y1));
        t.lineTo(px(x2), py(y2));
        t.lineTo(px(9.5), py(9.5));
        t.closePath();
        g.setColor(col);
        g.fill(t);
    }

    /** Vista previa del destino de la ficha seleccionada (solo en la vista local). */
    private void dibujarVistaPrevia(Graphics2D g) {
        if (fichaSeleccionada == null) {
            return;
        }
        OptionalInt destino = modelo.getPasosDestino(jugadorLocal, fichaSeleccionada);
        if (destino.isEmpty()) {
            return;
        }
        Color col = awt(modelo.getColor(jugadorLocal));
        Point2D.Double d = centroPara(modelo.getColor(jugadorLocal), fichaSeleccionada, destino.getAsInt());
        double rd = 0.5 * escala;
        g.setColor(alfa(col, 110));
        g.fill(new Ellipse2D.Double(px(d.x) - rd, py(d.y) - rd, 2 * rd, 2 * rd));
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
        g.draw(new Ellipse2D.Double(px(d.x) - rd, py(d.y) - rd, 2 * rd, 2 * rd));
    }

    private void dibujarFichas(Graphics2D g) {
        Point2D.Double[][] pos = posiciones();
        boolean activoLocal = modelo.esTurnoDe(jugadorLocal);
        for (int j = 0; j < modelo.getNumeroJugadores(); j++) {
            for (int id = 1; id <= FICHAS_POR_JUGADOR; id++) {
                dibujarFicha(g, j, id, pos[j][id - 1], activoLocal && j == jugadorLocal);
            }
        }
    }

    private void dibujarFicha(Graphics2D g, int jugador, int id, Point2D.Double c, boolean propiaYActiva) {
        int color = modelo.getColor(jugador);
        int pasos = modelo.getPasos(jugador, id);
        double r = radio(pasos) * escala;
        double x = px(c.x);
        double y = py(c.y);
        Ellipse2D e = new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r);
        boolean elegible = propiaYActiva && modelo.getFichasElegibles().contains(id);
        boolean seleccionada = jugador == jugadorLocal && fichaSeleccionada != null && fichaSeleccionada == id;
        if (elegible) {   // halo: solo lo ve el jugador activo
            g.setColor(alfa(Color.BLACK, 70));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
            g.draw(new Ellipse2D.Double(x - r - 4, y - r - 4, 2 * r + 8, 2 * r + 8));
        }
        g.setColor(awt(color));
        g.fill(e);
        g.setColor(seleccionada ? Color.WHITE : awt(color).darker().darker());
        g.setStroke(new BasicStroke(seleccionada ? 4f : 2f));
        g.draw(e);
        if (pasos < PASOS_META) {   // identificador visible sobre la ficha
            g.setColor(color == AMARILLO ? Color.BLACK : Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(9, (int) (r * 1.1))));
            String t = String.valueOf(id);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(t, (float) (x - fm.stringWidth(t) / 2.0), (float) (y + fm.getAscent() / 2.0 - 2));
        }
    }

    /** Etiqueta con avatar, nombre, color, fichas en meta y última tirada. Resalta al jugador con turno. */
    private void dibujarEtiqueta(Graphics2D g, int j) {
        double s = escala;
        int c = modelo.getColor(j);
        boolean arriba = c == AMARILLO || c == ROJO;
        boolean izquierda = c == AMARILLO || c == VERDE;
        double w = 9.2;
        double h = 1.7;
        double x = izquierda ? 0 : 19 - w;
        double y = arriba ? -2.0 : 19.4;
        boolean turno = modelo.esTurnoDe(j) && !modelo.partidaTerminada();
        RoundRectangle2D caja = new RoundRectangle2D.Double(px(x), py(y), w * s, h * s, 0.6 * s, 0.6 * s);
        g.setColor(turno ? alfa(awt(c), 70) : Color.WHITE);
        g.fill(caja);
        g.setColor(awt(c));
        g.setStroke(new BasicStroke(turno ? 5f : 2f));
        g.draw(caja);

        // Avatar (inicial del nombre)
        double d = 1.3 * s;
        double ax = px(x + 0.2);
        double ay = py(y + 0.2);
        g.setColor(awt(c).darker());
        g.fill(new Ellipse2D.Double(ax, ay, d, d));
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (d * 0.6)));
        String ini = modelo.getNombre(j).substring(0, 1).toUpperCase();
        FontMetrics fm = g.getFontMetrics();
        g.drawString(ini, (float) (ax + (d - fm.stringWidth(ini)) / 2), (float) (ay + d / 2 + fm.getAscent() / 2.0 - 3));

        // Textos
        double tx = px(x + 1.7);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, (int) (s * 0.6)));
        g.drawString(modelo.getNombre(j) + (turno ? "  ▶ TURNO" : ""), (float) tx, (float) py(y + 0.65));
        g.setFont(new Font("SansSerif", Font.PLAIN, (int) (s * 0.45)));
        g.drawString(modelo.getEtiquetaColor(c) + "  |  Meta: " + modelo.getFichasEnMeta(j) + "/" + FICHAS_POR_JUGADOR,
                (float) tx, (float) py(y + 1.1));
        int ultima = modelo.getUltimaTirada(j);
        g.drawString("Última tirada: " + (ultima == 0 ? "-" : String.valueOf(ultima)), (float) tx, (float) py(y + 1.5));
    }

    /** El dado: un cuadrado redondeado con los puntos del valor (0 = dado en blanco). */
    private void dibujarDado(Graphics2D g, int x, int y, int t, int valor) {
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(x, y, t, t, 10, 10));
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(x, y, t, t, 10, 10));
        double[] p = {0.25, 0.5, 0.75};
        int[][] puntos = switch (valor) {
            case 1 -> new int[][]{{1, 1}};
            case 2 -> new int[][]{{0, 0}, {2, 2}};
            case 3 -> new int[][]{{0, 0}, {1, 1}, {2, 2}};
            case 4 -> new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}};
            case 5 -> new int[][]{{0, 0}, {2, 0}, {1, 1}, {0, 2}, {2, 2}};
            case 6 -> new int[][]{{0, 0}, {2, 0}, {0, 1}, {2, 1}, {0, 2}, {2, 2}};
            default -> new int[0][];
        };
        for (int[] q : puntos) {
            double cx = x + p[q[0]] * t;
            double cy = y + p[q[1]] * t;
            g.fill(new Ellipse2D.Double(cx - 4.5, cy - 4.5, 9, 9));
        }
    }

    // ================= Arranque =================
    /**
     * Fernando y Sofía observan el mismo ModeloDado, y VistaHistorial es una
     * tercera vista del mismo modelo. Con --demo, la primera tirada sale 5.
     */
    public static void main(String[] args) {
        boolean demo = args.length > 0 && args[0].equals("--demo");
        // La GUI se crea en el hilo de eventos de Swing
        SwingUtilities.invokeLater(() -> {
            ModeloDado modelo = new ModeloDado();
            ControlDado control = new ControlDado(modelo);
            if (demo) {
                modelo.programarTirada(5);
            }
            VistaDado vistaFernando = new VistaDado(modelo, control, 0);
            VistaDado vistaSofia = new VistaDado(modelo, control, 1);
            VistaHistorial historial = new VistaHistorial(modelo);

            vistaFernando.mostrarEnVentana(30, 20);
            vistaSofia.mostrarEnVentana(640, 20);
            Point pos = enPantalla(1250, 20, historial.getPreferredSize());
            historial.mostrarEnVentana(pos.x, pos.y);
            mostrarPanelPruebas(modelo);

            modelo.iniciarPartida(0);   // Fernando comienza mientras Sofía solo observa
        });
    }

    private static Point enPantalla(int x, int y, Dimension tam) {
        Dimension pantalla = Toolkit.getDefaultToolkit().getScreenSize();
        return new Point(Math.max(0, Math.min(x, pantalla.width - tam.width)),
                Math.max(0, Math.min(y, pantalla.height - tam.height - 60)));
    }

    /** Ventana de ayuda para pruebas: permite elegir el valor de la siguiente tirada. */
    private static void mostrarPanelPruebas(ModeloDado modelo) {
        JFrame f = new JFrame("Panel de pruebas");
        f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JSpinner valor = new JSpinner(new SpinnerNumberModel(5, 1, 6, 1));
        JLabel estado = new JLabel("El dado es aleatorio", SwingConstants.CENTER);
        JButton programar = new JButton("Programar próxima tirada");
        JButton quitar = new JButton("Volver a aleatorio");
        programar.addActionListener(e -> {
            modelo.limpiarTiradasProgramadas();
            modelo.programarTirada((Integer) valor.getValue());
            estado.setText("Próxima tirada: " + valor.getValue());
        });
        quitar.addActionListener(e -> {
            modelo.limpiarTiradasProgramadas();
            estado.setText("El dado es aleatorio");
        });

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        fila.add(new JLabel("Dado:"));
        fila.add(valor);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        botones.add(programar);
        botones.add(quitar);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.add(fila, BorderLayout.NORTH);
        raiz.add(botones, BorderLayout.CENTER);
        raiz.add(estado, BorderLayout.SOUTH);
        f.setContentPane(raiz);
        f.pack();
        Dimension pantalla = Toolkit.getDefaultToolkit().getScreenSize();
        f.setLocation(Math.max(0, Math.min(1250, pantalla.width - f.getWidth())),
                Math.max(0, pantalla.height - f.getHeight() - 60));
        f.setVisible(true);
    }
}
