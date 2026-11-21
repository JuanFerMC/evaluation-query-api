package com.crediticio.evaluationquery.historial.ports.input;

import com.crediticio.evaluationquery.historial.application.dto.PaginaHistorialResponse;
import com.crediticio.evaluationquery.historial.domain.FiltroHistorial;
import com.crediticio.evaluationquery.historial.domain.Paginacion;

/** US 5.1.2: historial de evaluaciones filtrado y paginado, con el total de coincidencias. */
public interface FiltrarHistorialUseCase {

    PaginaHistorialResponse filtrar(FiltroHistorial filtro, Paginacion paginacion);
}
