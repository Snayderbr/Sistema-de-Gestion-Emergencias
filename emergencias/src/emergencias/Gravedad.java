package emergencias;

/** Niveles de gravedad, ordenados de menor a mayor. */
public enum Gravedad {
    LEVE(1, 500),
    MODERADO(1, 800),
    GRAVE(1, 1200),
    CRITICO(2, 1500);

    private final int medicosRequeridos;
    private final long atencionMs;

    Gravedad(int medicosRequeridos, long atencionMs) {
        this.medicosRequeridos = medicosRequeridos;
        this.atencionMs = atencionMs;
    }

    public int peso() { return ordinal() + 1; }

    public int medicosRequeridos() { return medicosRequeridos; }

    public long atencionMs() { return atencionMs; }

    public Gravedad escalada() {
        return this == CRITICO ? this : values()[ordinal() + 1];
    }
}
