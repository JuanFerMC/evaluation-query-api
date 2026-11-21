package com.crediticio.evaluationquery.historial.infrastructure.output;

import com.crediticio.evaluationquery.historial.domain.FiltroHistorial;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusquedaHistorialJdbcAdapterTest {

    @Test
    void contarDebeDevolverCeroCuandoLaConsultaNoRetornaUnValor() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(null);
        BusquedaHistorialJdbcAdapter adapter = new BusquedaHistorialJdbcAdapter(jdbcTemplate);

        long total = adapter.contar(FiltroHistorial.sinFiltros());

        assertThat(total).isZero();
        verify(jdbcTemplate).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }
}
