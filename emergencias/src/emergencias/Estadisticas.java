package emergencias;

import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/** Contadores sin bloqueos: LongAdder escala mejor que AtomicLong bajo mucha contención. */
public class Estadisticas {
    private final LongAdder creadas = new LongAdder();
    private final LongAdder finalizadas = new LongAdder();
    private final LongAdder reasignadas = new LongAdder();
    private final LongAdder averias = new LongAdder();
    private final LongAdder sinCama = new LongAdder();
    private final LongAdder hospitalSinRespuesta = new LongAdder();
    private final LongAdder[] respuestaSuma = nuevos();
    private final LongAdder[] respuestaCuenta = nuevos();
    private final LongAccumulator respuestaMax = new LongAccumulator(Math::max, 0);

    private static LongAdder[] nuevos() {
        LongAdder[] a = new LongAdder[Gravedad.values().length];
        for (int i = 0; i < a.length; i++) a[i] = new LongAdder();
        return a;
    }

    public void registrarCreada() { creadas.increment(); }
    public void registrarFinalizada() { finalizadas.increment(); }
    public void registrarReasignacion() { reasignadas.increment(); }
    public void registrarAveria() { averias.increment(); }
    public void registrarSinCama() { sinCama.increment(); }
    public void registrarHospitalSinRespuesta() { hospitalSinRespuesta.increment(); }

    public void registrarRespuesta(Emergencia e) {
        long ms = e.llegadaMs() - e.creadaMs();
        int i = e.gravedad().ordinal();
        respuestaSuma[i].add(ms);
        respuestaCuenta[i].increment();
        respuestaMax.accumulate(ms);
    }

    public long creadas() { return creadas.sum(); }
    public long finalizadas() { return finalizadas.sum(); }
    public long reasignadas() { return reasignadas.sum(); }
    public long averias() { return averias.sum(); }
    public long sinCama() { return sinCama.sum(); }
    public long hospitalSinRespuesta() { return hospitalSinRespuesta.sum(); }
    public double respuestaMaxSeg() { return respuestaMax.get() / 1000.0; }

    /** Tiempo medio de respuesta en segundos para una gravedad (NaN si aún no hay datos). */
    public double respuestaMediaSeg(Gravedad g) {
        long n = respuestaCuenta[g.ordinal()].sum();
        return n == 0 ? Double.NaN : respuestaSuma[g.ordinal()].sum() / 1000.0 / n;
    }

    public double respuestaMediaSeg() {
        long n = 0, s = 0;
        for (int i = 0; i < respuestaSuma.length; i++) { n += respuestaCuenta[i].sum(); s += respuestaSuma[i].sum(); }
        return n == 0 ? Double.NaN : s / 1000.0 / n;
    }
}
