package emergencias;

import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** las camas pertenecen solo al hilo del hospital (confinamiento a un hilo)
 y los demás le piden camas por mensajes. Por eso no necesita locks.
 */
public class Hospital implements Runnable {
    public record Solicitud(int emergenciaId, CompletableFuture<Boolean> respuesta) { }

    private final String nombre;
    private final double x;
    private final double y;
    private final int camas;
    private final long estanciaMs;
    private final BusEventos bus;
    private final BlockingQueue<Solicitud> inbox = new LinkedBlockingQueue<>();
    private final PriorityQueue<Long> altas = new PriorityQueue<>();
    private volatile int camasOcupadas;       // un solo escritor (este hilo), varios lectores
    private volatile boolean desconectado;    // simula la caída de la conexión

    public Hospital(String nombre, double x, double y, int camas, long estanciaMs, BusEventos bus) {
        this.nombre = nombre;
        this.x = x;
        this.y = y;
        this.camas = camas;
        this.estanciaMs = estanciaMs;
        this.bus = bus;
    }

    public CompletableFuture<Boolean> solicitarCama(int emergenciaId) {
        CompletableFuture<Boolean> respuesta = new CompletableFuture<>();
        inbox.add(new Solicitud(emergenciaId, respuesta));
        return respuesta;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                long ahora = System.currentTimeMillis();
                while (!altas.isEmpty() && altas.peek() <= ahora) { altas.poll(); camasOcupadas--; }

                Solicitud s = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (s == null || desconectado) continue;   // desconectado: el mensaje se pierde sin respuesta

                if (camasOcupadas < camas) {
                    camasOcupadas++;
                    altas.add(System.currentTimeMillis() + estanciaMs);
                    s.respuesta().complete(true);
                } else {
                    s.respuesta().complete(false);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void setDesconectado(boolean d) {
        this.desconectado = d;
        bus.publicar(nombre, d ? "CONEXIÓN CAÍDA" : "conexión restablecida");
    }

    public String nombre() { return nombre; }
    public double x() { return x; }
    public double y() { return y; }
    public int camas() { return camas; }
    public int camasOcupadas() { return camasOcupadas; }
    public boolean desconectado() { return desconectado; }

    @Override
    public String toString() { return nombre; }
}
