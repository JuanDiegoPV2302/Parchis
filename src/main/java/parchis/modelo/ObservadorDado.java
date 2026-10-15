package parchis.modelo;

/**
 * Patrón Observer: las vistas se suscriben al modelo mediante esta interfaz.
 * El modelo solo conoce a ObservadorDado, nunca a una vista concreta, y le
 * entrega la interfaz de SOLO LECTURA.
 */
public interface ObservadorDado {

    void actualizar(IModeloDadoLectura modelo);
}
