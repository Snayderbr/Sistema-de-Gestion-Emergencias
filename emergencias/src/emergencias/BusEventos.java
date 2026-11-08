package emergencias;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Patrón Observer: los componentes publican eventos y quien quiera se suscribe. */
public class BusEventos {
    private final List<Consumer<Evento>> suscriptores = new CopyOnWriteArrayList<>();
    private final long inicioMs = System.currentTimeMillis();

    public void suscribir(Consumer<Evento> suscriptor) { suscriptores.add(suscriptor); }

    public void publicar(String origen, String mensaje) {
        Evento ev = new Evento(System.currentTimeMillis() - inicioMs, origen, mensaje);
        for (Consumer<Evento> s : suscriptores) s.accept(ev);
    }
}
