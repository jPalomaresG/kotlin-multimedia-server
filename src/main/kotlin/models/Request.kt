package models

import kotlinx.serialization.Serializable

/**
 * Representa una petición del cliente al servidor.
 *
 * @param action Tipo de operación solicitada. Valores posibles:
 *               - "get_track_list": Obtener lista de canciones disponibles
 *               - "get_track": Descargar un archivo de audio específico
 * @param track_id Identificador del track solicitado. Solo es obligatorio
 *                 cuando action = "get_track".
 */
@Serializable
data class Request(
    val action: String,
    val track_id: String? = null
)