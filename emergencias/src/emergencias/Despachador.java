package emergencias;

import java.util.Optional;

/** Consumidor: toma la emergencia más urgente y le asigna recursos; si no hay, la devuelve a la cola. */
public class Despachador implements Runnable {
    private final int id;
    private final Contexto ctx;

    public Despachador(int id, Contexto ctx) {
        this.id = id;
        this.ctx = ctx;
    }

    @Override
    public void run() {
        String nombre = "DESPACHO-" + id;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Emergencia e = ctx.cola().tomarMasUrgente(300);
                if (e == null) continue;

                Optional<Asignacion> asignacion;
                try {
                    asignacion = ctx.centro().intentarReservar(e);
                } catch (RuntimeException ex) {
                    ctx.bus().publicar(nombre, "ERROR al reservar " + e + ": " + ex + " (se reencola)");
                    ctx.cola().agregar(e);
                    continue;
                }
                if (asignacion.isPresent()) {
                    Asignacion a = asignacion.get();
                    e.setEstado(EstadoEmergencia.ASIGNADA);
                    ctx.bus().publicar(nombre, "asigna " + a.ambulancia() + " a " + e);
                    a.ambulancia().asignar(a);
                } else {
                    ctx.cola().agregar(e);                 // conserva su antigüedad, sigue envejeciendo
                    ctx.centro().esperarCambio(150);       // duerme hasta que se libere algo
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
