package com.crediticio.evaluationquery.recomendaciones.application;

import com.crediticio.evaluationquery.recomendaciones.application.dto.ResultadoGeneracionResponse;
import com.crediticio.evaluationquery.recomendaciones.domain.CatalogoNoDisponibleException;
import com.crediticio.evaluationquery.recomendaciones.domain.CatalogoRecomendaciones;
import com.crediticio.evaluationquery.recomendaciones.domain.EntradaCatalogo;
import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionInexistenteException;
import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionNoPendienteException;
import com.crediticio.evaluationquery.recomendaciones.domain.EvaluacionParaRecomendar;
import com.crediticio.evaluationquery.recomendaciones.domain.FactorEvaluado;
import com.crediticio.evaluationquery.recomendaciones.domain.GeneracionRecomendaciones;
import com.crediticio.evaluationquery.recomendaciones.domain.RecomendacionGenerada;
import com.crediticio.evaluationquery.recomendaciones.domain.TipoActivacion;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU14 CA02–CA06, CA09, CA14 y CA15 con puertos simulados. */
class GenerarRecomendacionesServiceTest {

    private static final List<EntradaCatalogo> ENTRADAS = List.of(
            new EntradaCatalogo("REC-CAT-MEDIO-01", "Validar factores", TipoActivacion.CATEGORIA, "MEDIO", null),
            new EntradaCatalogo("REC-MORAS-01", "Revisar moras", TipoActivacion.FACTOR_DESFAVORABLE, null,
                    "NUMERO_MORAS"));

    private final Map<Long, EvaluacionParaRecomendar> evaluaciones = new HashMap<>();
    private final RecomendacionesEnMemoria repositorio = new RecomendacionesEnMemoria();
    private CatalogoRecomendaciones catalogo = new CatalogoRecomendaciones("V1", true, ENTRADAS);

    private GenerarRecomendacionesService servicio() {
        GeneracionRecomendacionesTransaccional transaccional = new GeneracionRecomendacionesTransaccional(
                id -> Optional.ofNullable(evaluaciones.get(id)), repositorio, () -> catalogo, snapshot -> { });
        return new GenerarRecomendacionesService(transaccional, new RecomendacionesMapper());
    }

    private void pendiente(long id) {
        evaluaciones.put(id, new EvaluacionParaRecomendar(id, "PENDIENTE", "MEDIO", List.of(
                new FactorEvaluado("NUMERO_MORAS", true, -20),
                new FactorEvaluado("INGRESOS_MENSUALES", false, 0))));
    }

    @Test
    void debeGenerarYGuardarLasRecomendacionesDeUnaEvaluacionPendiente() {
        pendiente(7);

        ResultadoGeneracionResponse resultado = servicio().generar(7);

        assertThat(resultado).isEqualTo(new ResultadoGeneracionResponse(7, "V1", 2, true));
        GeneracionRecomendaciones guardada = repositorio.guardadas.get(7L);
        assertThat(guardada.categoriaAplicada()).isEqualTo("MEDIO");
        assertThat(guardada.recomendaciones()).extracting(RecomendacionGenerada::codigo)
                .containsExactly("REC-CAT-MEDIO-01", "REC-MORAS-01");
    }

    @Test
    void sinRecomendacionesAplicablesDebeGuardarUnaGeneracionVacia() {
        evaluaciones.put(8L, new EvaluacionParaRecomendar(8, "PENDIENTE", "BAJO", List.of()));

        assertThat(servicio().generar(8)).isEqualTo(new ResultadoGeneracionResponse(8, "V1", 0, true));
        assertThat(repositorio.guardadas).containsKey(8L);
    }

    @Test
    void unReintentoDebeDevolverLaGeneracionExistenteSinRegenerarla() {
        pendiente(7);
        servicio().generar(7);
        // El catálogo cambia de versión y la evaluación ya pasó a COMPLETADA (CA06, CA15)
        catalogo = new CatalogoRecomendaciones("V2", true, List.of());
        evaluaciones.put(7L, new EvaluacionParaRecomendar(7, "COMPLETADA", "MEDIO", List.of()));

        ResultadoGeneracionResponse reintento = servicio().generar(7);

        assertThat(reintento).isEqualTo(new ResultadoGeneracionResponse(7, "V1", 2, false));
        assertThat(repositorio.inserciones).isEqualTo(1);
    }

    @Test
    void siOtraPeticionGuardaPrimeroDebeDevolverLaGuardada() {
        pendiente(7);
        GeneracionRecomendaciones ajena = new GeneracionRecomendaciones(7, "V1", "MEDIO", RecomendacionesEnMemoria.FECHA,
                List.of());
        repositorio.antesDeGuardar = () -> repositorio.guardadas.put(7L, ajena);

        ResultadoGeneracionResponse resultado = servicio().generar(7);

        assertThat(resultado).isEqualTo(new ResultadoGeneracionResponse(7, "V1", 0, false));
        assertThat(repositorio.inserciones).isZero();
    }

    @Test
    void unaEvaluacionInexistenteDebeRechazarse() {
        assertThatThrownBy(() -> servicio().generar(99)).isInstanceOf(EvaluacionInexistenteException.class);
    }

    @Test
    void unaEvaluacionAnteriorALaSagaOCompletadaSinGeneracionDebeRechazarse() {
        evaluaciones.put(3L, new EvaluacionParaRecomendar(3, null, "MEDIO", List.of()));
        evaluaciones.put(4L, new EvaluacionParaRecomendar(4, "COMPLETADA", "MEDIO", List.of()));

        assertThatThrownBy(() -> servicio().generar(3)).isInstanceOf(EvaluacionNoPendienteException.class);
        assertThatThrownBy(() -> servicio().generar(4)).isInstanceOf(EvaluacionNoPendienteException.class);
        assertThat(repositorio.guardadas).isEmpty();
    }

    @Test
    void conUnCatalogoNoAprobadoNoDebeGenerarNada() {
        pendiente(7);
        catalogo = new CatalogoRecomendaciones("BORRADOR-1", false, ENTRADAS);

        assertThatThrownBy(() -> servicio().generar(7)).isInstanceOf(CatalogoNoDisponibleException.class)
                .hasMessageContaining("BORRADOR-1");
        assertThat(repositorio.guardadas).isEmpty();
    }
}
