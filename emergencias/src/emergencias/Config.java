package emergencias;

import java.util.HashMap;
import java.util.Map;

/** Parámetros de la simulación.  */
public record Config(int duracionSeg, int operadores, int intervaloMs, int despachadores,
                     int ambulancias, int medicos, int equipos, int msPorKm,
                     double escalaAtencion, int refrescoMs, boolean fallas) {

    public static final long DURACION_FALLA_MS = 2500;

    public static Config desdeArgumentos(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (String a : args) {
            int i = a.indexOf('=');
            if (i > 0) m.put(a.substring(0, i), a.substring(i + 1));
        }
        return new Config(
                entero(m, "duracion", 30), entero(m, "operadores", 2), entero(m, "intervalo", 1500),
                entero(m, "despachadores", 2), entero(m, "ambulancias", 6), entero(m, "medicos", 8),
                entero(m, "equipos", 6), entero(m, "msPorKm", 120),
                Double.parseDouble(m.getOrDefault("escala", "1.0")),
                entero(m, "refresco", 2000), !"false".equals(m.get("fallas")));
    }

    private static int entero(Map<String, String> m, String clave, int defecto) {
        return Integer.parseInt(m.getOrDefault(clave, String.valueOf(defecto)));
    }
}
