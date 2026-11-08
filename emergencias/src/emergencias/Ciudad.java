package emergencias;

/** Mapa simplificado: una cuadrícula de 20 x 20 km con el centro de despacho en el medio. */
public final class Ciudad {
    public static final double TAM = 20.0;
    public static final double CENTRO_X = 10.0;
    public static final double CENTRO_Y = 10.0;

    private Ciudad() { }

    public static double distancia(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }
}
