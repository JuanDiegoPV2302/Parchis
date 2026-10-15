package parchis.controlador;

import parchis.modelo.IModeloDado;

/**
 * Controlador: traduce cada acción del usuario en una operación del modelo.
 * Valida de quién es el turno antes de pedirle algo al modelo. No dibuja, no
 * tiene componentes gráficos y solo conoce la interfaz IModeloDado.
 */
public class ControlDado {

    private final IModeloDado modelo;

    public ControlDado(IModeloDado modelo) {
        this.modelo = modelo;
    }

    /** Clic en «Lanzar» o sobre el dado. */
    public void lanzar(int solicitante) {
        if (modelo.esTurnoDe(solicitante)) {
            modelo.lanzar();
        }
    }

    /** Selección local de una ficha: solo valida, no cambia el modelo. */
    public boolean seleccionarFicha(int solicitante, int idFicha) {
        return modelo.esTurnoDe(solicitante) && modelo.esFichaElegible(idFicha);
    }

    /** Confirmación de «Mover ficha». */
    public void moverFicha(int solicitante, int idFicha) {
        if (modelo.esTurnoDe(solicitante)) {
            modelo.moverFicha(idFicha);
        }
    }

    /** Clic en «Nuevo turno». */
    public void reiniciarTurno(int solicitante) {
        if (modelo.esTurnoDe(solicitante)) {
            modelo.reiniciarTurno();
        }
    }
}
