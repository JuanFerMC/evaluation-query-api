package com.crediticio.evaluationquery.recomendaciones.domain;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Snapshot persistido de Scoring recibido antes de generar recomendaciones (sin datos del solicitante). */
public record SnapshotEvaluacion(long idEvaluacion, long idSolicitante, LocalDateTime fechaEvaluacion,
        int scoreTotal, String categoria, int versionPolitica, String estado, List<Factor> factores) {

    private static final Set<String> CATEGORIAS = Set.of("BAJO", "MEDIO", "ALTO");
    private static final Set<String> OPERADORES = Set.of("=", ">", ">=", "<", "<=");

    public SnapshotEvaluacion {
        if (idEvaluacion <= 0 || idSolicitante <= 0 || versionPolitica <= 0) {
            throw new SnapshotEvaluacionInvalidoException("Los identificadores y la versión de política deben ser positivos");
        }
        if (fechaEvaluacion == null) {
            throw new SnapshotEvaluacionInvalidoException("fechaEvaluacion es obligatoria");
        }
        if (categoria == null || !CATEGORIAS.contains(categoria) || !"PENDIENTE".equals(estado)) {
            throw new SnapshotEvaluacionInvalidoException("La evaluación debe estar clasificada y PENDIENTE");
        }
        if (factores == null) {
            throw new SnapshotEvaluacionInvalidoException("factores es obligatorio");
        }
        for (SnapshotEvaluacion.Factor factor : factores) {
            if (factor == null) {
                throw new SnapshotEvaluacionInvalidoException("El snapshot no puede incluir factores nulos");
            }
        }
        factores = List.copyOf(factores);
        Set<Integer> ordenes = new HashSet<>();
        Set<Long> detalles = new HashSet<>();
        long suma = 0;
        for (int indice = 0; indice < factores.size(); indice++) {
            Factor factor = factores.get(indice);
            if (factor.orden() != indice + 1) {
                throw new SnapshotEvaluacionInvalidoException("Los factores deben llegar ordenados sin saltos");
            }
            if (!ordenes.add(factor.orden()) || !detalles.add(factor.idDetalleOrigen())) {
                throw new SnapshotEvaluacionInvalidoException("El snapshot contiene factores duplicados");
            }
            suma += factor.contribucion();
        }
        if (suma != scoreTotal) {
            throw new SnapshotEvaluacionInvalidoException("El puntaje no coincide con la suma de los factores");
        }
    }

    public record Factor(int orden, long idDetalleOrigen, long idRegla, String variable, String operadorAplicado,
            String valorCondicionAplicado, int puntajeReglaAplicado, boolean condicionCumplida, int contribucion) {

        public Factor {
            if (orden <= 0 || idDetalleOrigen <= 0 || idRegla <= 0
                    || variable == null || variable.isBlank() || variable.length() > 30
                    || operadorAplicado == null || !OPERADORES.contains(operadorAplicado)
                    || valorCondicionAplicado == null || valorCondicionAplicado.isBlank()
                    || valorCondicionAplicado.length() > 255) {
                throw new SnapshotEvaluacionInvalidoException("Un factor del snapshot no cumple el contrato");
            }
            int esperado = condicionCumplida ? puntajeReglaAplicado : 0;
            if (contribucion != esperado) {
                throw new SnapshotEvaluacionInvalidoException("La contribución no coincide con el factor aplicado");
            }
        }
    }
}
