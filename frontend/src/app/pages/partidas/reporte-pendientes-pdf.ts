import jsPDF from 'jspdf';
import { autoTable } from 'jspdf-autotable';
import { Conciliacion, Partida } from '../../core/models';
import { filasParaPdf, nombreArchivoReporte, tituloReporte } from './reporte-pendientes.util';

/** #5b21b6 -- morado primario usado en el resto de la app (ver partida-card, botones). */
const COLOR_ENCABEZADO_TABLA: [number, number, number] = [91, 33, 182];

/**
 * Genera el PDF del reporte de pendientes y dispara la descarga en el navegador
 * (doc.save(...)). No hay vista previa en pantalla: el resultado final es el archivo
 * descargado. Lanza si la conciliación no tiene banco o período cargado -- el llamador
 * (PartidasComponent) captura el error y lo muestra en un MatSnackBar en vez de generar un
 * PDF vacío o corrupto.
 */
export function generarPdfPendientes(conciliacion: Conciliacion, partidasFiltradas: Partida[]): void {
  const titulo = tituloReporte(conciliacion);
  const nombreArchivo = nombreArchivoReporte(conciliacion);

  const doc = new jsPDF();
  doc.setFontSize(14);
  doc.text(titulo, 14, 18);

  autoTable(doc, {
    startY: 26,
    head: [['Fecha', 'Descripción', 'Valor', 'Origen']],
    body: filasParaPdf(partidasFiltradas),
    headStyles: { fillColor: COLOR_ENCABEZADO_TABLA },
  });

  doc.save(nombreArchivo);
}
