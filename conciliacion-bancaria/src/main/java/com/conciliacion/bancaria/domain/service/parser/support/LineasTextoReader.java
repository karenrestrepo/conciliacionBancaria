package com.conciliacion.bancaria.domain.service.parser.support;

import com.conciliacion.bancaria.domain.exception.CsvValidationException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Decodifica los bytes de un archivo de texto (TXT/CSV) a líneas, con encoding configurable. */
public class LineasTextoReader {

    public List<String> leer(byte[] contenido, String encoding) {
        Charset charset = resolverCharset(encoding);
        List<String> lineas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(contenido), charset))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                lineas.add(linea);
            }
        } catch (IOException e) {
            throw new CsvValidationException("Error al leer el extracto: " + e.getMessage());
        }
        return lineas;
    }

    private Charset resolverCharset(String encoding) {
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.ISO_8859_1;
        }
    }
}
