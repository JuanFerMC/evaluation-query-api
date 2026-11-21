package com.crediticio.evaluationquery.historial.application;

import com.crediticio.evaluationquery.historial.application.dto.EvaluacionHistorialResponse;
import com.crediticio.evaluationquery.historial.application.dto.PaginaHistorialResponse;
import com.crediticio.evaluationquery.historial.domain.FiltroHistorial;
import com.crediticio.evaluationquery.historial.domain.Paginacion;
import com.crediticio.evaluationquery.historial.ports.input.FiltrarHistorialUseCase;
import com.crediticio.evaluationquery.historial.ports.output.BusquedaHistorialPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * US 5.1.2: cuenta las coincidencias y lee solo la página pedida, con los mismos filtros. REPEATABLE_READ hace que
 * el conteo y la página vean la misma instantánea. Una búsqueda sin coincidencias no es un error: devuelve una
 * página vacía con total 0. Solo lectura.
 */
@Service
public class FiltrarHistorialService implements FiltrarHistorialUseCase {

    private final BusquedaHistorialPort busquedaHistorialPort;
    private final HistorialMapper mapper;

    public FiltrarHistorialService(BusquedaHistorialPort busquedaHistorialPort, HistorialMapper mapper) {
        this.busquedaHistorialPort = busquedaHistorialPort;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaginaHistorialResponse filtrar(FiltroHistorial filtro, Paginacion paginacion) {
        long total = busquedaHistorialPort.contar(filtro);
        // Sin coincidencias, o con una página posterior a la última, no hace falta leer filas
        List<EvaluacionHistorialResponse> evaluaciones = total <= paginacion.desplazamiento()
                ? List.of()
                : busquedaHistorialPort.buscar(filtro, paginacion).stream().map(mapper::aResponse).toList();
        return new PaginaHistorialResponse(evaluaciones, paginacion.pagina(), paginacion.tamano(), total,
                paginacion.totalPaginas(total));
    }
}
