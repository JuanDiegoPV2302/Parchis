package parchis.modelo;

/**
 * Lo que el CONTROLADOR puede pedirle al modelo (segregación de interfaces).
 * El controlador modifica el estado a través de estas operaciones; la vista
 * nunca recibe esta interfaz, así que el compilador hace cumplir la separación.
 */
public interface IModeloDado {

    /** Lanza el dado del jugador en turno (si procede). */
    void lanzar();

    /** Pasa al siguiente jugador y deja el dado listo: valor y 6 seguidos en cero. */
    void reiniciarTurno();

    /** Mueve una ficha elegible del jugador en turno con el resultado actual. */
    void moverFicha(int idFicha);

    /** Consultas que el controlador necesita para validar el turno antes de pedir algo. */
    boolean esTurnoDe(int jugador);

    boolean esFichaElegible(int idFicha);
}
