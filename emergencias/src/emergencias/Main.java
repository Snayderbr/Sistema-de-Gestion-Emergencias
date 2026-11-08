package emergencias;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        Config cfg = Config.desdeArgumentos(args);

        BusEventos bus = new BusEventos();
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> bus.publicar("SISTEMA", "el hilo " + t.getName() + " murió: " + ex));
        Estadisticas stats = new Estadisticas();
        ColaEmergencias cola = new ColaEmergencias();
        CentroRecursos centro = new CentroRecursos(cfg.medicos(), cfg.equipos());

        List<Hospital> hospitales = List.of(
                new Hospital("H-NORTE", 4, 3, 4, 3000, bus),
                new Hospital("H-CENTRO", 10, 10, 5, 3000, bus),
                new Hospital("H-SUR", 16, 17, 4, 3000, bus));
        RedHospitales red = new RedHospitales(hospitales, bus, stats);
        Contexto ctx = new Contexto(cfg, bus, stats, cola, centro, red);

        List<Thread> hilosDeTrabajo = new ArrayList<>();
        for (Hospital h : hospitales) hilosDeTrabajo.add(new Thread(h, h.nombre()));
        for (int i = 1; i <= cfg.ambulancias(); i++) {
            Ambulancia a = new Ambulancia(i, 2 + (i * 7) % 16, 2 + (i * 11) % 16, ctx);
            centro.registrar(a);
            hilosDeTrabajo.add(new Thread(a, "AMB-" + i));
        }
        for (int i = 1; i <= cfg.despachadores(); i++) hilosDeTrabajo.add(new Thread(new Despachador(i, ctx), "DESPACHO-" + i));
        Monitor monitor = new Monitor(ctx);
        hilosDeTrabajo.add(new Thread(monitor, "MONITOR"));
        if (cfg.fallas()) hilosDeTrabajo.add(new Thread(new SimuladorFallas(ctx), "FALLAS"));

        List<Thread> operadores = new ArrayList<>();
        for (int i = 1; i <= cfg.operadores(); i++) operadores.add(new Thread(new Operador(i, ctx), "OPERADOR-" + i));

        System.out.println("Iniciando simulación: " + cfg);
        long inicio = System.currentTimeMillis();
        hilosDeTrabajo.forEach(Thread::start);
        operadores.forEach(Thread::start);

        Thread.sleep(cfg.duracionSeg() * 1000L);

        // Cierre ordenado: se dejan de recibir llamadas y se espera a que se atiendan las pendientes.
        operadores.forEach(Thread::interrupt);
        for (Thread t : operadores) t.join();
        long limite = System.currentTimeMillis() + 40_000;
        while (stats.finalizadas() < stats.creadas() && System.currentTimeMillis() < limite) Thread.sleep(200);

        hilosDeTrabajo.forEach(Thread::interrupt);
        for (Thread t : hilosDeTrabajo) t.join(2000);

        double total = (System.currentTimeMillis() - inicio) / 1000.0;
        System.out.println();
        System.out.println("==================== INFORME FINAL ====================");
        System.out.print(monitor.resumenEstadisticas());
        System.out.println(String.format(Locale.ROOT, "  Duración total=%.1fs  Rendimiento=%.2f emergencias/s",
                total, stats.finalizadas() / total));
    }
}
