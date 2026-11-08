package emergencias;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Hilo que espera asignaciones en su buzón, viaja, atiende, traslada al hospital y vuelve a quedar libre. */
public class Ambulancia implements Runnable {
    public enum Estado { DISPONIBLE, EN_CAMINO, OCUPADA, FUERA_DE_SERVICIO }

    private final int id;
    private final Contexto ctx;
    private final BlockingQueue<Asignacion> buzon = new LinkedBlockingQueue<>();
    private volatile Estado estado = Estado.DISPONIBLE;
    private volatile double x;
    private volatile double y;
    private volatile boolean falla;

    public Ambulancia(int id, double x, double y, Contexto ctx) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.ctx = ctx;
    }

    /** Llamado por un despachador: entrega el trabajo sin esperar a que la ambulancia lo procese. */
    public void asignar(Asignacion a) { buzon.add(a); }

    public Estado estado() { return estado; }
    public double x() { return x; }
    public double y() { return y; }

    // Estos tres solo se invocan desde CentroRecursos, con su lock tomado.
    void setEstado(Estado e) { this.estado = e; }
    void marcarEnCamino() { this.falla = false; this.estado = Estado.EN_CAMINO; }
    void provocarFalla() { this.falla = true; }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Asignacion a = buzon.take();
                try {
                    atender(a);
                } catch (RuntimeException ex) {
                    recuperarDeError(a, ex);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void atender(Asignacion a) throws InterruptedException {
        Emergencia em = a.emergencia();
        em.setEstado(EstadoEmergencia.EN_CAMINO);
        ctx.bus().publicar(this.toString(), "sale hacia " + em);

        if (!avanzar((long) (Ciudad.distancia(x, y, em.x(), em.y()) * ctx.config().msPorKm()))) {
            abortar(a);
            return;
        }
        x = em.x();
        y = em.y();
        if (em.registrarLlegada()) ctx.stats().registrarRespuesta(em);
        estado = Estado.OCUPADA;
        em.setEstado(EstadoEmergencia.ATENDIENDO);
        ctx.bus().publicar(this.toString(), "llegó a " + em + ", atendiendo");

        if (!avanzar((long) (em.gravedad().atencionMs() * ctx.config().escalaAtencion()))) {
            abortar(a);
            return;
        }
        trasladarAHospital(em);

        em.setEstado(EstadoEmergencia.FINALIZADA);
        ctx.stats().registrarFinalizada();
        falla = false;
        ctx.centro().liberar(a);
    }

    private void trasladarAHospital(Emergencia em) throws InterruptedException {
        Hospital h = ctx.hospitales().reservarCama(em, x, y);
        if (h == null) {
            ctx.stats().registrarSinCama();
            h = ctx.hospitales().masCercano(x, y);
            ctx.bus().publicar(this.toString(), "ningún hospital con cama para " + em + ", deriva a " + h);
        }
        Thread.sleep((long) (Ciudad.distancia(x, y, h.x(), h.y()) * ctx.config().msPorKm()));
        x = h.x();
        y = h.y();
        ctx.bus().publicar(this.toString(), "entregó " + em + " en " + h);
    }

    /** Avería: el caso vuelve a la cola conservando su antigüedad, así que reaparece con prioridad alta. */
    private void abortar(Asignacion a) throws InterruptedException {
        Emergencia em = a.emergencia();
        estado = Estado.FUERA_DE_SERVICIO;
        falla = false;
        ctx.stats().registrarAveria();
        ctx.stats().registrarReasignacion();
        ctx.bus().publicar(this.toString(), "AVERÍA: " + em + " vuelve a la cola");
        em.setEstado(EstadoEmergencia.PENDIENTE);
        ctx.centro().liberarPersonal(a);
        ctx.cola().agregar(em);
        Thread.sleep(Config.DURACION_FALLA_MS);
        ctx.centro().recuperar(this);
        ctx.bus().publicar(this.toString(), "recuperada");
    }

    /** Error inesperado: se devuelve todo y el caso vuelve a la cola; la ambulancia sigue viva. */
    private void recuperarDeError(Asignacion a, RuntimeException ex) {
        ctx.bus().publicar(this.toString(), "ERROR INESPERADO (" + ex + "): " + a.emergencia() + " vuelve a la cola");
        a.emergencia().setEstado(EstadoEmergencia.PENDIENTE);
        ctx.centro().liberarPersonal(a);
        ctx.cola().agregar(a.emergencia());
        ctx.centro().recuperar(this);
    }

    /** Duerme en tramos cortos para poder reaccionar a una avería. Devuelve false si falló. */
    private boolean avanzar(long ms) throws InterruptedException {
        long restante = ms;
        while (restante > 0) {
            if (falla) return false;
            long tramo = Math.min(50, restante);
            Thread.sleep(tramo);
            restante -= tramo;
        }
        return !falla;
    }

    @Override
    public String toString() { return "AMB-" + id; }
}
