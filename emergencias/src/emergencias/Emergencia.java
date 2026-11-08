package emergencias;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Un incidente. Los campos que cambian entre hilos son volatile o atómicos. */
public class Emergencia {
    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private static final double PESO_ESPERA = 10.0;     // puntos por segundo de espera
    private static final double PESO_DISTANCIA = 2.0;   // puntos que se restan por km al centro

    private final int id = SECUENCIA.incrementAndGet();
    private final double x;
    private final double y;
    private final long creadaMs = System.currentTimeMillis();
    private final AtomicLong llegadaMs = new AtomicLong(-1);
    private volatile Gravedad gravedad;
    private volatile EstadoEmergencia estado = EstadoEmergencia.PENDIENTE;

    public Emergencia(Gravedad gravedad, double x, double y) {
        this.gravedad = gravedad;
        this.x = x;
        this.y = y;
    }

    /** Prioridad dinámica: gravedad + envejecimiento por espera - penalización por distancia. */
    public double prioridad(long ahoraMs) {
        double esperaSeg = (ahoraMs - creadaMs) / 1000.0;
        double dist = Ciudad.distancia(x, y, Ciudad.CENTRO_X, Ciudad.CENTRO_Y);
        return gravedad.peso() * 100.0 + esperaSeg * PESO_ESPERA - dist * PESO_DISTANCIA;
    }

    public void reclasificar(Gravedad nueva) { this.gravedad = nueva; }

    /** Devuelve true solo la primera vez, para contar el tiempo de respuesta una única vez. */
    public boolean registrarLlegada() {
        return llegadaMs.compareAndSet(-1, System.currentTimeMillis());
    }

    public int id() { return id; }
    public double x() { return x; }
    public double y() { return y; }
    public long creadaMs() { return creadaMs; }
    public long llegadaMs() { return llegadaMs.get(); }
    public Gravedad gravedad() { return gravedad; }
    public EstadoEmergencia estado() { return estado; }
    public void setEstado(EstadoEmergencia e) { this.estado = e; }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "E#%d[%s]", id, gravedad);
    }
}
