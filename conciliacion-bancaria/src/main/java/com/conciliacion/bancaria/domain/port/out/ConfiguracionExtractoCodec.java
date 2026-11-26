package com.conciliacion.bancaria.domain.port.out;

import com.conciliacion.bancaria.domain.model.extractoconfig.ConfiguracionExtractoDetalle;

// Puerto de serialización del detalle de configuración de extracto.
// Reemplaza el parseo ad-hoc a Map<String,Object> que antes bypaseaba el DTO web:
// ahora hay un único schema tipado (ConfiguracionExtractoDetalle) tanto para
// guardar como para leer.
public interface ConfiguracionExtractoCodec {

    ConfiguracionExtractoDetalle leer(String json);

    String escribir(ConfiguracionExtractoDetalle detalle);
}
