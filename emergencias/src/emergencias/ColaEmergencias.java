package emergencias;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cola de espera thread-safe con prioridad DINÁMICA.
 */
public class ColaEmergencias {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hayElementos = lock.newCondition();
    private final List<Emergencia> items = new ArrayList<>();

    public void agregar(Emergencia e) {
        lock.lock();
        try {
            items.add(e);
            hayElementos.signal();
        } finally {
            lock.unlock();
        }
    }

    /** Bloquea hasta timeoutMs esperando una emergencia; devuelve la más urgente o null. */
    public Emergencia tomarMasUrgente(long timeoutMs) throws InterruptedException {
        long nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        lock.lockInterruptibly();
        try {
            while (items.isEmpty()) {
                if (nanos <= 0) return null;
                nanos = hayElementos.awaitNanos(nanos);
            }
            long ahora = System.currentTimeMillis();
            Emergencia mejor = items.get(0);
            for (Emergencia e : items) {
                if (e.prioridad(ahora) > mejor.prioridad(ahora)) mejor = e;
            }
            items.remove(mejor);
            return mejor;
        } finally {
            lock.unlock();
        }
    }

    /** Modificación de prioridad: sube un nivel la gravedad de un caso pendiente al azar. */
    public Emergencia escalarAlguna(Random rnd) {
        lock.lock();
        try {
            List<Emergencia> candidatas = new ArrayList<>();
            for (Emergencia e : items) if (e.gravedad() != Gravedad.CRITICO) candidatas.add(e);
            if (candidatas.isEmpty()) return null;
            Emergencia e = candidatas.get(rnd.nextInt(candidatas.size()));
            e.reclasificar(e.gravedad().escalada());
            return e;
        } finally {
            lock.unlock();
        }
    }

    /** Copia ordenada por prioridad actual, para que el monitor lea sin bloquear a los demás. */
    public List<Emergencia> instantanea() {
        List<Emergencia> copia;
        lock.lock();
        try {
            copia = new ArrayList<>(items);
        } finally {
            lock.unlock();
        }
        long ahora = System.currentTimeMillis();
        copia.sort(Comparator.comparingDouble((Emergencia e) -> e.prioridad(ahora)).reversed());
        return copia;
    }

    public int tamano() {
        lock.lock();
        try {
            return items.size();
        } finally {
            lock.unlock();
        }
    }
}
