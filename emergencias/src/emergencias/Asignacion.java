package emergencias;

/** Lo que el despachador reservó para una emergencia: una ambulancia, médicos y equipos. */
public record Asignacion(Emergencia emergencia, Ambulancia ambulancia, int medicos, int equipos) { }
