package parchis.modelo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import parchis.controlador.ControlDado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del modelo sin abrir ninguna ventana: el modelo no conoce Swing. */
class ModeloDadoTest {

    private ModeloDado modelo;

    @BeforeEach
    void preparar() {
        modelo = new ModeloDado();      // Fernando (verde, turno 0) y Sofía (rojo)
        modelo.iniciarPartida(0);
    }

    @Test
    void elDadoSoloDaValoresDelUnoAlSeis() {
        for (int i = 0; i < 500; i++) {
            ModeloDado m = new ModeloDado();
            m.iniciarPartida(0);
            m.lanzar();
            assertTrue(m.getValor() >= 1 && m.getValor() <= 6);
        }
    }

    @Test
    void conSeisSeVuelveATirarMaximoTresVeces() {
        modelo.programarTirada(6);
        modelo.programarTirada(6);
        modelo.programarTirada(6);

        modelo.lanzar();                       // 1.er seis
        assertTrue(modelo.puedeTirar());
        modelo.lanzar();                       // 2.º seis
        assertTrue(modelo.puedeTirar());
        modelo.lanzar();                       // 3.er seis: se acabó
        assertEquals(3, modelo.getSeisSeguidos());
        assertFalse(modelo.puedeTirar());
        assertTrue(modelo.turnoTerminado());
    }

    @Test
    void unNumeroDistintoDeSeisTerminaElTurno() {
        modelo.programarTirada(6);
        modelo.programarTirada(3);
        modelo.lanzar();
        modelo.lanzar();
        assertEquals(0, modelo.getSeisSeguidos());
        assertFalse(modelo.puedeTirar());
        assertTrue(modelo.turnoTerminado());
    }

    @Test
    void conSeisHayQueMoverAntesDeVolverATirar() {
        modelo.colocarFicha(0, 1, 10);
        modelo.programarTirada(6);
        modelo.lanzar();
        assertEquals(java.util.List.of(1), modelo.getFichasElegibles());
        assertFalse(modelo.puedeTirar());      // primero debe mover
        modelo.moverFicha(1);
        assertEquals(16, modelo.getPasos(0, 1));
        assertTrue(modelo.puedeTirar());       // y ahora sí vuelve a tirar
        assertEquals(1, modelo.getSeisSeguidos());
    }

    @Test
    void soloSaleDeLaBaseConCinco() {
        modelo.programarTirada(4);
        modelo.lanzar();
        assertTrue(modelo.getFichasElegibles().isEmpty());

        ModeloDado otro = new ModeloDado();
        otro.iniciarPartida(0);
        otro.programarTirada(5);
        otro.lanzar();
        assertEquals(java.util.List.of(1, 2, 3, 4), otro.getFichasElegibles());
        otro.moverFicha(2);
        assertEquals(0, otro.getPasos(0, 2));  // en la casilla de salida
    }

    @Test
    void caerSobreUnaRivalLaRegresaASuBase() {
        modelo.colocarFicha(0, 1, 1);          // verde: salida 30 + 1 = casilla 31
        modelo.colocarFicha(1, 1, 38);         // rojo: (64 + 38) % 68 = casilla 34
        modelo.programarTirada(3);             // verde avanza a la casilla 34
        modelo.lanzar();
        modelo.moverFicha(1);
        assertEquals(4, modelo.getPasos(0, 1));
        assertEquals(-1, modelo.getPasos(1, 1));
    }

    @Test
    void aLaMetaSeEntraConElNumeroExacto() {
        modelo.colocarFicha(0, 1, 68);
        modelo.programarTirada(4);             // 68 + 4 = 72 > 71: no puede
        modelo.lanzar();
        assertTrue(modelo.getFichasElegibles().isEmpty());

        ModeloDado otro = new ModeloDado();
        otro.iniciarPartida(0);
        otro.colocarFicha(0, 1, 68);
        otro.programarTirada(3);               // 68 + 3 = 71: meta exacta
        otro.lanzar();
        otro.moverFicha(1);
        assertEquals(1, otro.getFichasEnMeta(0));
    }

    @Test
    void ganaQuienMeteLasCuatroFichas() {
        modelo.colocarFicha(0, 1, 71);
        modelo.colocarFicha(0, 2, 71);
        modelo.colocarFicha(0, 3, 71);
        modelo.colocarFicha(0, 4, 68);
        modelo.programarTirada(3);
        modelo.lanzar();
        modelo.moverFicha(4);
        assertTrue(modelo.partidaTerminada());
        assertEquals(0, modelo.getGanador());
        assertFalse(modelo.puedeTirar());
    }

    @Test
    void reiniciarTurnoPasaAlSiguienteYDejaElDadoListo() {
        modelo.programarTirada(3);
        modelo.lanzar();                       // sin fichas en juego: el turno termina
        assertTrue(modelo.turnoTerminado());
        modelo.reiniciarTurno();
        assertEquals(1, modelo.getTurnoActual());
        assertEquals(0, modelo.getValor());
        assertEquals(0, modelo.getSeisSeguidos());
        assertTrue(modelo.puedeTirar());
    }

    @Test
    void reiniciarTurnoNoSaltaUnTurnoQueAunNoTermina() {
        modelo.reiniciarTurno();               // todavía no lanzó
        assertEquals(0, modelo.getTurnoActual());
    }

    @Test
    void elControladorValidaDeQuienEsElTurno() {
        ControlDado control = new ControlDado(modelo);
        control.lanzar(1);                     // Sofía fuera de turno
        assertEquals(0, modelo.getValor());
        control.lanzar(0);                     // Fernando sí
        assertTrue(modelo.getValor() >= 1);
    }

    @Test
    void elModeloAvisaATodosLosObservadores() {
        int[] avisos = new int[2];
        modelo.agregarObservador(m -> avisos[0]++);
        modelo.agregarObservador(m -> avisos[1]++);
        modelo.lanzar();
        assertEquals(1, avisos[0]);
        assertEquals(1, avisos[1]);
    }
}
