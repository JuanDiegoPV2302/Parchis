package parchis.modelo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Modelo MVC y sujeto observable. Aquí viven TODAS las reglas: el dado (1 a 6),
 * la regla del 6 (se vuelve a tirar, máximo tres veces seguidas), la salida de
 * la base con 5, las capturas, la entrada exacta a la meta y los turnos.
 *
 * No importa nada de javax.swing ni de java.awt: se prueba con JUnit sin abrir
 * ventanas. Las fichas y el tablero se representan con arreglos de enteros:
 * pasos[jugador][ficha - 1] = -1 en la base, 0..63 en el anillo, 64..70 en el
 * pasillo y 71 en la meta.
 */
public class ModeloDado implements IModeloDado, IModeloDadoLectura {

    // ---- Reglas del juego ----
    public static final int MAX_TIRADAS_CONSECUTIVAS = 3;
    public static final int VALOR_REPETIR = 6;
    public static final int VALOR_SALIR_DE_BASE = 5;

    /** Si es true, el turno pasa solo al terminar; si es false, se espera a «Nuevo turno». */
    public static final boolean TURNO_AUTOMATICO = false;

    // Constantes del recorrido y de los colores: ver IModeloDadoLectura
    // (PASOS_BASE, PASOS_EN_ANILLO, PASOS_META, TOTAL_CASILLAS, AMARILLO, VERDE, AZUL, ROJO).

    private static final String[] ETIQUETAS = {"Amarillo", "Verde", "Azul", "Rojo"};
    private static final int[] CASILLA_SALIDA = {13, 30, 47, 64};

    private final Random random = new Random();
    private final Deque<Integer> tiradasProgramadas = new ArrayDeque<>();
    private final List<ObservadorDado> observadores = new CopyOnWriteArrayList<>();

    private final String[] nombres;
    private final int[] colores;
    private final int[][] pasos;
    private final int[] ultimaTirada;
    private final List<String> historial = new ArrayList<>();

    private int turno;
    private int valor;                 // 0 = sin tirada en este turno
    private int seisSeguidos;
    private boolean puedeTirar = true;
    private boolean repetirTrasMover;  // sacó 6 y aún debe mover antes de volver a tirar
    private List<Integer> elegibles = List.of();
    private int ganador = -1;
    private boolean iniciada;
    private int tiros;
    private String mensaje = "";

    /** Partida de ejemplo: Fernando (verde) contra Sofía (rojo). */
    public ModeloDado() {
        this(new String[]{"Fernando", "Sofía"}, new int[]{VERDE, ROJO});
    }

    public ModeloDado(String[] nombres, int[] colores) {
        if (nombres.length != colores.length || nombres.length < 2 || nombres.length > 4) {
            throw new IllegalArgumentException("Se necesitan de 2 a 4 jugadores, cada uno con su color");
        }
        this.nombres = nombres.clone();
        this.colores = colores.clone();
        this.pasos = new int[nombres.length][FICHAS_POR_JUGADOR];
        this.ultimaTirada = new int[nombres.length];
        for (int[] fila : pasos) {
            java.util.Arrays.fill(fila, PASOS_BASE);
        }
    }

    // ================= Observer =================
    @Override
    public void agregarObservador(ObservadorDado o) {
        observadores.add(o);
    }

    @Override
    public void eliminarObservador(ObservadorDado o) {
        observadores.remove(o);
    }

    protected void notificar() {
        for (ObservadorDado o : observadores) {
            o.actualizar(this);
        }
    }

    // ================= Operaciones del controlador (IModeloDado) =================
    public void iniciarPartida(int indiceJugadorInicial) {
        turno = indiceJugadorInicial;
        iniciada = true;
        puedeTirar = true;
        mensaje = "La partida inició. Turno de " + nombres[turno] + ".";
        notificar();
    }

    public void iniciarPartida() {
        iniciarPartida(random.nextInt(nombres.length));
    }

    @Override
    public void lanzar() {
        if (!iniciada || ganador >= 0 || !puedeTirar) {
            return;
        }
        valor = siguienteValor();
        ultimaTirada[turno] = valor;
        tiros++;
        seisSeguidos = (valor == VALOR_REPETIR) ? seisSeguidos + 1 : 0;
        boolean repite = valor == VALOR_REPETIR && seisSeguidos < MAX_TIRADAS_CONSECUTIVAS;
        elegibles = List.copyOf(calcularElegibles());
        puedeTirar = false;
        historial.add(tiros + ". " + nombres[turno] + " sacó " + valor
                + (valor == VALOR_REPETIR ? "  (seis seguidos: " + seisSeguidos + ")" : ""));

        if (elegibles.isEmpty()) {
            mensaje = nombres[turno] + " sacó " + valor + " y no tiene movimientos posibles.";
            if (repite) {
                puedeTirar = true;   // el 6 da otra tirada aunque no pueda mover
                mensaje += " Sacó 6: vuelve a tirar.";
            } else {
                finDeTurno();
            }
        } else {
            repetirTrasMover = repite;
            mensaje = nombres[turno] + " sacó " + valor + ". Fichas elegibles: " + elegibles;
        }
        notificar();
    }

    @Override
    public void moverFicha(int idFicha) {
        if (ganador >= 0 || !elegibles.contains(idFicha)) {
            return;
        }
        int j = turno;
        int i = idFicha - 1;
        pasos[j][i] = (pasos[j][i] == PASOS_BASE) ? valor - VALOR_SALIR_DE_BASE : pasos[j][i] + valor;
        List<Integer> afectados = capturar(j, i);
        boolean llegoAMeta = pasos[j][i] >= PASOS_META;

        String detalle = nombres[j] + " movió la ficha #" + idFicha + "."
                + (afectados.isEmpty() ? "" : " ¡Capturó una ficha de " + nombres[afectados.get(0)] + "!")
                + (llegoAMeta ? " ¡Una ficha llegó a la meta!" : "");
        mensaje = detalle;
        historial.add("      " + detalle);
        elegibles = List.of();
        boolean repite = repetirTrasMover;
        repetirTrasMover = false;

        if (getFichasEnMeta(j) == FICHAS_POR_JUGADOR) {
            ganador = j;
            puedeTirar = false;
            mensaje += " " + nombres[j] + " gana la partida.";
            historial.add("      " + nombres[j] + " gana la partida.");
        } else if (repite) {
            puedeTirar = true;   // sacó 6: otra tirada, máx. 3 seguidas
            mensaje += " Sacó 6: " + nombres[j] + " vuelve a tirar.";
        } else {
            finDeTurno();
        }
        notificar();
    }

    @Override
    public void reiniciarTurno() {
        if (!turnoTerminado()) {
            return;
        }
        avanzarTurno("");
        notificar();
    }

    // ================= Reglas internas =================
    private void finDeTurno() {
        puedeTirar = false;
        elegibles = List.of();
        if (TURNO_AUTOMATICO) {
            avanzarTurno(mensaje + " ");
        } else {
            mensaje += " Pulsa «Nuevo turno».";
        }
    }

    private void avanzarTurno(String prefijo) {
        seisSeguidos = 0;
        valor = 0;
        repetirTrasMover = false;
        elegibles = List.of();
        turno = (turno + 1) % nombres.length;
        puedeTirar = true;
        mensaje = prefijo + "Turno de " + nombres[turno] + ".";
    }

    private int siguienteValor() {
        Integer programado = tiradasProgramadas.poll();
        return programado != null ? programado : random.nextInt(6) + 1;
    }

    /** Fichas del jugador en turno que pueden moverse con el valor actual. */
    private List<Integer> calcularElegibles() {
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < FICHAS_POR_JUGADOR; i++) {
            int p = pasos[turno][i];
            if (p == PASOS_BASE) {
                if (valor == VALOR_SALIR_DE_BASE) {
                    r.add(i + 1);
                }
            } else if (p < PASOS_META && p + valor <= PASOS_META) {
                r.add(i + 1);   // a la meta se entra con el número exacto
            }
        }
        return r;
    }

    /** Casilla absoluta del anillo (0..67) o -1 si la ficha está en base, pasillo o meta. */
    private int casillaAbsoluta(int jugador, int ficha) {
        int p = pasos[jugador][ficha];
        if (p < 0 || p > PASOS_EN_ANILLO) {
            return -1;
        }
        return (CASILLA_SALIDA[colores[jugador]] + p) % TOTAL_CASILLAS;
    }

    /** Regresa a su base a las fichas rivales que estén en la casilla de la ficha movida. */
    private List<Integer> capturar(int jugador, int ficha) {
        List<Integer> afectados = new ArrayList<>();
        int casilla = casillaAbsoluta(jugador, ficha);
        if (casilla < 0) {
            return afectados;
        }
        for (int k = 0; k < nombres.length; k++) {
            if (k == jugador) {
                continue;
            }
            for (int f = 0; f < FICHAS_POR_JUGADOR; f++) {
                if (casillaAbsoluta(k, f) == casilla) {
                    pasos[k][f] = PASOS_BASE;
                    afectados.add(k);
                }
            }
        }
        return afectados;
    }

    // ================= Apoyo para pruebas =================
    /** Programa el valor de la siguiente tirada (útil para probar la regla del 6). */
    public void programarTirada(int valorDado) {
        if (valorDado < 1 || valorDado > 6) {
            throw new IllegalArgumentException("El dado solo tiene valores del 1 al 6");
        }
        tiradasProgramadas.add(valorDado);
    }

    public void limpiarTiradasProgramadas() {
        tiradasProgramadas.clear();
    }

    /** Coloca una ficha en una posición concreta (para pruebas de capturas y meta). */
    public void colocarFicha(int jugador, int idFicha, int pasosFicha) {
        pasos[jugador][idFicha - 1] = pasosFicha;
    }

    // ================= Consultas (IModeloDadoLectura) =================
    @Override
    public int getValor() {
        return valor;
    }

    @Override
    public int getSeisSeguidos() {
        return seisSeguidos;
    }

    @Override
    public boolean puedeTirar() {
        return iniciada && ganador < 0 && puedeTirar;
    }

    @Override
    public int getTurnoActual() {
        return turno;
    }

    @Override
    public boolean esTurnoDe(int jugador) {
        return jugador == turno;
    }

    @Override
    public boolean turnoTerminado() {
        return iniciada && ganador < 0 && !puedeTirar && elegibles.isEmpty();
    }

    @Override
    public boolean partidaTerminada() {
        return ganador >= 0;
    }

    @Override
    public int getGanador() {
        return ganador;
    }

    @Override
    public String getMensaje() {
        return mensaje;
    }

    @Override
    public int getNumeroJugadores() {
        return nombres.length;
    }

    @Override
    public String getNombre(int jugador) {
        return nombres[jugador];
    }

    @Override
    public int getColor(int jugador) {
        return colores[jugador];
    }

    @Override
    public String getEtiquetaColor(int color) {
        return ETIQUETAS[color];
    }

    @Override
    public int getCasillaSalida(int color) {
        return CASILLA_SALIDA[color];
    }

    @Override
    public int getPasos(int jugador, int idFicha) {
        return pasos[jugador][idFicha - 1];
    }

    @Override
    public int getFichasEnMeta(int jugador) {
        int n = 0;
        for (int p : pasos[jugador]) {
            if (p >= PASOS_META) {
                n++;
            }
        }
        return n;
    }

    @Override
    public int getUltimaTirada(int jugador) {
        return ultimaTirada[jugador];
    }

    @Override
    public List<Integer> getFichasElegibles() {
        return elegibles;
    }

    @Override
    public boolean esFichaElegible(int jugador, int idFicha) {
        return jugador == turno && elegibles.contains(idFicha);
    }

    @Override
    public boolean esFichaElegible(int idFicha) {
        return elegibles.contains(idFicha);
    }

    @Override
    public OptionalInt getPasosDestino(int jugador, int idFicha) {
        if (!esFichaElegible(jugador, idFicha) || valor == 0) {
            return OptionalInt.empty();
        }
        int p = pasos[jugador][idFicha - 1];
        return OptionalInt.of(p == PASOS_BASE ? valor - VALOR_SALIR_DE_BASE : p + valor);
    }

    @Override
    public List<String> getHistorial() {
        return List.copyOf(historial);
    }
}
