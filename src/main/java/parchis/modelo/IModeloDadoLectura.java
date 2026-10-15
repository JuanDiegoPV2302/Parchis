package parchis.modelo;

import java.util.List;
import java.util.OptionalInt;

/**
 * Lo que la VISTA puede consultar del modelo (segregación de interfaces).
 * La vista solo lee: no puede lanzar el dado ni mover fichas por su cuenta,
 * esas acciones se las pide al controlador. Suscribirse no cambia el estado,
 * por eso agregarObservador() vive aquí.
 *
 * Los colores se identifican con enteros (ModeloDado.AMARILLO, VERDE, AZUL,
 * ROJO) para que el modelo no dependa de java.awt ni de javax.swing.
 */
public interface IModeloDadoLectura {

    // ---- Constantes del recorrido (la vista las usa para dibujar) ----
    int FICHAS_POR_JUGADOR = 4;
    int PASOS_BASE = -1;
    int PASOS_EN_ANILLO = 63;
    int PASOS_META = 71;
    int TOTAL_CASILLAS = 68;

    // ---- Colores (el orden coincide con los brazos del tablero) ----
    int AMARILLO = 0;
    int VERDE = 1;
    int AZUL = 2;
    int ROJO = 3;

    // ---- Dado ----
    /** Valor de la última tirada del turno (0 si todavía no se ha tirado). */
    int getValor();

    /** Cuántos 6 seguidos lleva el jugador en turno. */
    int getSeisSeguidos();

    /** true si el jugador en turno tiene derecho a lanzar el dado ahora. */
    boolean puedeTirar();

    // ---- Turno y partida ----
    int getTurnoActual();

    boolean esTurnoDe(int jugador);

    /** true si el turno ya terminó y solo falta pulsar «Nuevo turno». */
    boolean turnoTerminado();

    boolean partidaTerminada();

    /** Índice del ganador, o -1 si la partida sigue. */
    int getGanador();

    String getMensaje();

    // ---- Jugadores y fichas ----
    int getNumeroJugadores();

    String getNombre(int jugador);

    int getColor(int jugador);

    String getEtiquetaColor(int color);

    /** Casilla del anillo (0..67) donde sale cada color. */
    int getCasillaSalida(int color);

    /** Pasos de una ficha: -1 = base, 0 = salida, 1..63 = anillo, 64..70 = pasillo, 71 = meta. */
    int getPasos(int jugador, int idFicha);

    int getFichasEnMeta(int jugador);

    /** Última tirada de un jugador (0 si aún no ha tirado). */
    int getUltimaTirada(int jugador);

    List<Integer> getFichasElegibles();

    boolean esFichaElegible(int jugador, int idFicha);

    /** Posición destino de una ficha con la tirada actual (vista previa). */
    OptionalInt getPasosDestino(int jugador, int idFicha);

    /** Historial de tiros y movimientos, en orden cronológico. */
    List<String> getHistorial();

    // ---- Observer ----
    void agregarObservador(ObservadorDado o);

    void eliminarObservador(ObservadorDado o);
}
