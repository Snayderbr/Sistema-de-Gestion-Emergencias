package emergencias;

public record Contexto(Config config, BusEventos bus, Estadisticas stats, ColaEmergencias cola,
                       CentroRecursos centro, RedHospitales hospitales) { }
