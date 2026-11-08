package emergencias;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Monitor de recursos compartidos (ambulancias, médicos, equipos).
 */
public class CentroRecursos {
    public record Resumen(int medicosLibres, int medicosTotal, int equiposLibres, int equiposTotal,
                          List<String> ambulancias, Map<Ambulancia.Estado, Integer> conteo) { }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hayCambio = lock.newCondition();
    private final List<Ambulancia> ambulancias = new ArrayList<>();
    private final int medicosTotal;
    private final int equiposTotal;
    private int medicosLibres;
    private int equiposLibres;
    /** Emergencia más urgente que no pudo servirse: los casos menos urgentes no pueden quitarle sus recursos. */
    private Emergencia bloqueada;

    public CentroRecursos(int medicos, int equipos) {
        this.medicosTotal = medicos;
        this.equiposTotal = equipos;
        this.medicosLibres = medicos;
        this.equiposLibres = equipos;
    }

    public void registrar(Ambulancia a) {
        lock.lock();
        try {
            ambulancias.add(a);
        } finally {
            lock.unlock();
        }
    }

    public Optional<Asignacion> intentarReservar(Emergencia e) {
        lock.lock();
        try {
            long ahora = System.currentTimeMillis();
            if (bloqueada != null && bloqueada.estado() != EstadoEmergencia.PENDIENTE) bloqueada = null;

            int medicos = e.gravedad().medicosRequeridos();
            boolean alcanza = medicosLibres >= medicos && equiposLibres >= 1;
            Ambulancia mejor = alcanza ? ambulanciaMasCercana(e) : null;

            if (mejor != null && bloqueada != null && bloqueada != e
                    && e.prioridad(ahora) < bloqueada.prioridad(ahora)) {
                boolean sigueAlcanzando = medicosLibres - medicos >= bloqueada.gravedad().medicosRequeridos()
                        && equiposLibres - 1 >= 1 && ambulanciasLibres() - 1 >= 1;
                if (!sigueAlcanzando) mejor = null;   // se aparta para no robarle los recursos
            }

            if (mejor == null) {
                if (bloqueada == null || e.prioridad(ahora) > bloqueada.prioridad(ahora)) bloqueada = e;
                return Optional.empty();
            }
            if (bloqueada == e) bloqueada = null;
            mejor.marcarEnCamino();
            medicosLibres -= medicos;
            equiposLibres -= 1;
            return Optional.of(new Asignacion(e, mejor, medicos, 1));
        } finally {
            lock.unlock();
        }
    }

    private Ambulancia ambulanciaMasCercana(Emergencia e) {
        Ambulancia mejor = null;
        double mejorDist = Double.MAX_VALUE;
        for (Ambulancia a : ambulancias) {
            if (a.estado() != Ambulancia.Estado.DISPONIBLE) continue;
            double d = Ciudad.distancia(a.x(), a.y(), e.x(), e.y());
            if (d < mejorDist) { mejorDist = d; mejor = a; }
        }
        return mejor;
    }

    private int ambulanciasLibres() {
        int n = 0;
        for (Ambulancia a : ambulancias) if (a.estado() == Ambulancia.Estado.DISPONIBLE) n++;
        return n;
    }

    /** Espera con timeout a que se libere algo */
    public void esperarCambio(long ms) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            hayCambio.await(ms, TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }

    /** Fin normal de la atención: devuelve personal, equipo y la ambulancia. */
    public void liberar(Asignacion a) {
        lock.lock();
        try {
            medicosLibres += a.medicos();
            equiposLibres += a.equipos();
            a.ambulancia().setEstado(Ambulancia.Estado.DISPONIBLE);
            hayCambio.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Fin por avería: se devuelve el personal pero la ambulancia sigue fuera de servicio. */
    public void liberarPersonal(Asignacion a) {
        lock.lock();
        try {
            medicosLibres += a.medicos();
            equiposLibres += a.equipos();
            hayCambio.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void recuperar(Ambulancia a) {
        lock.lock();
        try {
            a.setEstado(Ambulancia.Estado.DISPONIBLE);
            hayCambio.signalAll();
        } finally {
            lock.unlock();
        }
    }

    
    public boolean averiar(Ambulancia a) {
        lock.lock();
        try {
            switch (a.estado()) {
                case DISPONIBLE -> { a.setEstado(Ambulancia.Estado.FUERA_DE_SERVICIO); return true; }
                case EN_CAMINO, OCUPADA -> a.provocarFalla();
                default -> { }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

   
    public Resumen resumen() {
        lock.lock();
        try {
            Map<Ambulancia.Estado, Integer> conteo = new EnumMap<>(Ambulancia.Estado.class);
            List<String> detalle = new ArrayList<>();
            for (Ambulancia a : ambulancias) {
                conteo.merge(a.estado(), 1, Integer::sum);
                detalle.add(a + " " + a.estado());
            }
            return new Resumen(medicosLibres, medicosTotal, equiposLibres, equiposTotal, detalle, conteo);
        } finally {
            lock.unlock();
        }
    }

    public List<Ambulancia> ambulancias() {
        lock.lock();
        try {
            return new ArrayList<>(ambulancias);
        } finally {
            lock.unlock();
        }
    }
}
