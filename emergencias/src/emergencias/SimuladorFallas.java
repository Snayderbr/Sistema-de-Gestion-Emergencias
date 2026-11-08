package emergencias;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Inyecta fallos: tumba ambulancias (libres u ocupadas) y desconecta hospitales por unos segundos. */
public class SimuladorFallas implements Runnable {
    private final Contexto ctx;

    public SimuladorFallas(Contexto ctx) { this.ctx = ctx; }

    @Override
    public void run() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(3000 + rnd.nextInt(3000));
                if (rnd.nextInt(100) < 70) {
                    List<Ambulancia> flota = ctx.centro().ambulancias();
                    Ambulancia a = flota.get(rnd.nextInt(flota.size()));
                    if (ctx.centro().averiar(a)) {
                        ctx.bus().publicar("FALLAS", a + " fuera de servicio (estaba libre)");
                        Thread.sleep(Config.DURACION_FALLA_MS);
                        ctx.centro().recuperar(a);
                        ctx.bus().publicar("FALLAS", a + " recuperada");
                    }
                } else {
                    List<Hospital> hs = ctx.hospitales().todos();
                    Hospital h = hs.get(rnd.nextInt(hs.size()));
                    h.setDesconectado(true);
                    Thread.sleep(Config.DURACION_FALLA_MS);
                    h.setDesconectado(false);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
