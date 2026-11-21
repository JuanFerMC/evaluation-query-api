package com.crediticio.evaluationquery.reportes.application;

import com.crediticio.evaluationquery.reportes.application.dto.DistribucionRiesgoResponse;
import com.crediticio.evaluationquery.reportes.ports.input.ConsultarDistribucionRiesgoUseCase;
import com.crediticio.evaluationquery.reportes.ports.output.DistribucionRiesgoPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * US 7.1.1: cada consulta cuenta las evaluaciones persistidas en ese momento (sin caché), por lo que una evaluación
 * recién registrada aparece en la consulta siguiente. Solo lectura.
 */
@Service
public class ConsultarDistribucionRiesgoService implements ConsultarDistribucionRiesgoUseCase {

    private final DistribucionRiesgoPort distribucionRiesgoPort;
    private final DistribucionRiesgoMapper mapper;

    public ConsultarDistribucionRiesgoService(DistribucionRiesgoPort distribucionRiesgoPort,
            DistribucionRiesgoMapper mapper) {
        this.distribucionRiesgoPort = distribucionRiesgoPort;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public DistribucionRiesgoResponse consultar() {
        return mapper.aResponse(distribucionRiesgoPort.contarPorCategoria());
    }
}
