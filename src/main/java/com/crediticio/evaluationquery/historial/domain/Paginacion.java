package com.crediticio.evaluationquery.historial.domain;

/** US 5.1.2: página pedida del historial filtrado. La primera página es la 0; el tamaño va de 1 a 100. */
public record Paginacion(int pagina, int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    public Paginacion {
        if (pagina < 0) {
            throw new FiltroHistorialInvalidoException("El parámetro 'pagina' debe ser mayor o igual a 0");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new FiltroHistorialInvalidoException(
                    "El parámetro 'tamano' debe estar entre 1 y " + TAMANO_MAXIMO);
        }
    }

    /** Registros que se saltan antes de la página (long para no desbordar con páginas muy altas). */
    public long desplazamiento() {
        return (long) pagina * tamano;
    }

    public int totalPaginas(long totalRegistros) {
        return (int) Math.min(Integer.MAX_VALUE, (totalRegistros + tamano - 1) / tamano);
    }
}
