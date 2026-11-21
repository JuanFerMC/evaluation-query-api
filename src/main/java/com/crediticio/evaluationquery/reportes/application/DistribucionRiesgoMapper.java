package com.crediticio.evaluationquery.reportes.application;

import com.crediticio.evaluationquery.reportes.application.dto.DistribucionRiesgoResponse;
import com.crediticio.evaluationquery.reportes.application.dto.NivelRiesgoResponse;
import com.crediticio.evaluationquery.reportes.domain.DistribucionRiesgo;
import com.crediticio.evaluationquery.shared.domain.CategoriaRiesgo;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class DistribucionRiesgoMapper {

    public DistribucionRiesgoResponse aResponse(DistribucionRiesgo distribucion) {
        return new DistribucionRiesgoResponse(
                distribucion.total(),
                Arrays.stream(CategoriaRiesgo.values())
                        .map(categoria -> new NivelRiesgoResponse(categoria.name(), distribucion.cantidad(categoria),
                                distribucion.porcentaje(categoria)))
                        .toList(),
                distribucion.getSinClasificar());
    }
}
