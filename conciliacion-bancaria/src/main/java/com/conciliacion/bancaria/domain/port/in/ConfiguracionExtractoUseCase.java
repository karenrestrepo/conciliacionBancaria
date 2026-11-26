package com.conciliacion.bancaria.domain.port.in;

import com.conciliacion.bancaria.domain.model.ConfiguracionExtracto;
import com.conciliacion.bancaria.domain.model.ResultadoPruebaConfiguracion;
import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;

import java.util.List;

public interface ConfiguracionExtractoUseCase {

    ConfiguracionExtracto crear(ConfiguracionExtracto c);

    ConfiguracionExtracto actualizar(Long id, ConfiguracionExtracto c);

    ConfiguracionExtracto obtenerPorId(Long id);

    List<ConfiguracionExtracto> listarPorBanco(Long idBanco);

    void eliminar(Long id);

    /**
     * Prueba una configuración (aún no guardada) contra un archivo de muestra:
     * parsea con el motor genérico y valida el cuadre, sin persistir nada.
     */
    ResultadoPruebaConfiguracion probarConfiguracion(byte[] archivo, ConfiguracionExtractoDetalle detalle, String periodo);
}
