package com.crediticio.evaluationquery.historial.ports.output;

import com.crediticio.evaluationquery.historial.domain.EvaluacionHistorica;
import com.crediticio.evaluationquery.historial.domain.FiltroHistorial;
import com.crediticio.evaluationquery.historial.domain.Paginacion;

import java.util.List;

/**
 * US 5.1.2: búsqueda en el historial de todas las evaluaciones. Ambas operaciones aplican exactamente los mismos
 * filtros y, como el historial de HU15, excluyen las evaluaciones PENDIENTE de la saga (CA08).
 */
public interface BusquedaHistorialPort {

    /** Página de evaluaciones, de la más reciente a la más antigua y, a igual fecha, por id descendente. */
    List<EvaluacionHistorica> buscar(FiltroHistorial filtro, Paginacion paginacion);

    /** Total de evaluaciones que cumplen el filtro, en todas las páginas. */
    long contar(FiltroHistorial filtro);
}
