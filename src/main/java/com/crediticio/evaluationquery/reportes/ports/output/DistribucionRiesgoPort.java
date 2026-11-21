package com.crediticio.evaluationquery.reportes.ports.output;

import com.crediticio.evaluationquery.reportes.domain.DistribucionRiesgo;

/** US 7.1.1: conteo de las evaluaciones persistidas por categoría, en una sola lectura consistente. */
public interface DistribucionRiesgoPort {

    DistribucionRiesgo contarPorCategoria();
}
