package emergencias;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Productor: recibe llamadas y las deja en la cola de espera. */
public class Operador implements Runnable {
    private final int id;
    private final Contexto ctx;

    public Operador(int id, Contexto ctx) {
        this.id = id;
        this.ctx = ctx;
    }

    @Override
    public void run() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String nombre = "OPERADOR-" + id;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep((long) (ctx.config().intervaloMs() * (0.5 + rnd.nextDouble())));

                Emergencia e = new Emergencia(gravedadAleatoria(rnd),
                        rnd.nextDouble() * Ciudad.TAM, rnd.nextDouble() * Ciudad.TAM);
                ctx.stats().registrarCreada();
                ctx.cola().agregar(e);
                ctx.bus().publicar(nombre, String.format(Locale.ROOT, "recibe llamada %s en (%.1f, %.1f)",
                        e, e.x(), e.y()));

                if (rnd.nextInt(100) < 10) {
                    Emergencia empeora = ctx.cola().escalarAlguna(rnd);
                    if (empeora != null) {
                        ctx.bus().publicar(nombre, "el paciente empeora: " + empeora + " reclasificada");
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static Gravedad gravedadAleatoria(ThreadLocalRandom rnd) {
        int n = rnd.nextInt(100);
        if (n < 35) return Gravedad.LEVE;
        if (n < 70) return Gravedad.MODERADO;
        if (n < 90) return Gravedad.GRAVE;
        return Gravedad.CRITICO;
    }
}
