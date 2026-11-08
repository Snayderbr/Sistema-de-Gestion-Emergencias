package emergencias;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Hilo de monitoreo: lee instantáneas y pinta el tablero. Los eventos llegan por el bus (Observer). */
public class Monitor implements Runnable {
    private static final int EVENTOS_VISIBLES = 8;
    private static final int PENDIENTES_VISIBLES = 8;

    private final Contexto ctx;
    private final long inicioMs = System.currentTimeMillis();
    private final ConcurrentLinkedDeque<Evento> recientes = new ConcurrentLinkedDeque<>();

    public Monitor(Contexto ctx) {
        this.ctx = ctx;
        ctx.bus().suscribir(this::recibir);
    }

    private void recibir(Evento ev) {          // corre en el hilo que publica: debe ser barato y sin bloqueos
        recientes.addLast(ev);
        while (recientes.size() > EVENTOS_VISIBLES) recientes.pollFirst();
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(ctx.config().refrescoMs());
                System.out.print(tablero());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String tablero() {
        StringBuilder sb = new StringBuilder();
        double t = (System.currentTimeMillis() - inicioMs) / 1000.0;
        sb.append(String.format(Locale.ROOT, "%n==================== MONITOR  t=%.1fs ====================%n", t));

        List<Emergencia> pendientes = ctx.cola().instantanea();
        long ahora = System.currentTimeMillis();
        sb.append(String.format("EMERGENCIAS PENDIENTES (%d)%n", pendientes.size()));
        for (int i = 0; i < Math.min(PENDIENTES_VISIBLES, pendientes.size()); i++) {
            Emergencia e = pendientes.get(i);
            sb.append(String.format(Locale.ROOT, "  %-14s %-9s prioridad=%6.1f  espera=%4.1fs%n",
                    e, e.gravedad(), e.prioridad(ahora), (ahora - e.creadaMs()) / 1000.0));
        }
        if (pendientes.size() > PENDIENTES_VISIBLES) {
            sb.append(String.format("  ... y %d más%n", pendientes.size() - PENDIENTES_VISIBLES));
        }

        CentroRecursos.Resumen r = ctx.centro().resumen();
        sb.append(String.format("RECURSOS%n"));
        sb.append("  Ambulancias: ").append(String.join(" | ", r.ambulancias())).append(System.lineSeparator());
        sb.append(String.format("  Disponibles=%d  En camino=%d  Ocupadas=%d  Fuera de servicio=%d%n",
                r.conteo().getOrDefault(Ambulancia.Estado.DISPONIBLE, 0),
                r.conteo().getOrDefault(Ambulancia.Estado.EN_CAMINO, 0),
                r.conteo().getOrDefault(Ambulancia.Estado.OCUPADA, 0),
                r.conteo().getOrDefault(Ambulancia.Estado.FUERA_DE_SERVICIO, 0)));
        sb.append(String.format("  Médicos libres %d/%d | Equipos libres %d/%d%n",
                r.medicosLibres(), r.medicosTotal(), r.equiposLibres(), r.equiposTotal()));
        sb.append("  Hospitales: ");
        for (Hospital h : ctx.hospitales().todos()) {
            sb.append(String.format("%s %d/%d camas%s  ", h, h.camasOcupadas(), h.camas(),
                    h.desconectado() ? " (SIN CONEXIÓN)" : ""));
        }
        sb.append(System.lineSeparator());

        sb.append(resumenEstadisticas());
        sb.append(String.format("ÚLTIMOS EVENTOS%n"));
        for (Evento ev : recientes) {
            sb.append(String.format(Locale.ROOT, "  [%5.1fs] %-11s %s%n", ev.tiempoMs() / 1000.0, ev.origen(), ev.mensaje()));
        }
        return sb.toString();
    }

    public String resumenEstadisticas() {
        Estadisticas s = ctx.stats();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("ESTADÍSTICAS%n"));
        sb.append(String.format("  Creadas=%d  Finalizadas=%d  Reasignadas por avería=%d  Sin cama=%d  Hospital sin respuesta=%d%n",
                s.creadas(), s.finalizadas(), s.reasignadas(), s.sinCama(), s.hospitalSinRespuesta()));
        sb.append(String.format(Locale.ROOT, "  Respuesta media=%s  máxima=%.1fs%n", seg(s.respuestaMediaSeg()), s.respuestaMaxSeg()));
        sb.append("  Por gravedad:");
        for (Gravedad g : Gravedad.values()) {
            sb.append(String.format(Locale.ROOT, "  %s=%s", g, seg(s.respuestaMediaSeg(g))));
        }
        sb.append(System.lineSeparator());
        return sb.toString();
    }

    private static String seg(double v) {
        return Double.isNaN(v) ? "--" : String.format(Locale.ROOT, "%.2fs", v);
    }
}
