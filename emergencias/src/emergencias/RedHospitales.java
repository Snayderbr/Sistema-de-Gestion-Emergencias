package emergencias;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Fachada para hablar con los hospitales: prueba el más cercano y, si no responde o está lleno, el siguiente. */
public class RedHospitales {
    private static final long TIMEOUT_MS = 800;

    private final List<Hospital> hospitales;
    private final BusEventos bus;
    private final Estadisticas stats;

    public RedHospitales(List<Hospital> hospitales, BusEventos bus, Estadisticas stats) {
        this.hospitales = List.copyOf(hospitales);
        this.bus = bus;
        this.stats = stats;
    }

    public Hospital reservarCama(Emergencia em, double x, double y) throws InterruptedException {
        for (Hospital h : ordenadosPorDistancia(x, y)) {
            CompletableFuture<Boolean> f = h.solicitarCama(em.id());
            try {
                if (f.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)) return h;
            } catch (TimeoutException ex) {
                f.cancel(true);
                stats.registrarHospitalSinRespuesta();
                bus.publicar("RED", h + " no responde, se prueba el siguiente");
            } catch (ExecutionException ex) {
                // la respuesta nunca se completa con error; se ignora
            }
        }
        return null;
    }

    public Hospital masCercano(double x, double y) { return ordenadosPorDistancia(x, y).get(0); }

    public List<Hospital> todos() { return hospitales; }

    private List<Hospital> ordenadosPorDistancia(double x, double y) {
        List<Hospital> orden = new ArrayList<>(hospitales);
        orden.sort(Comparator.comparingDouble(h -> Ciudad.distancia(x, y, h.x(), h.y())));
        return orden;
    }
}
