package models

import kotlinx.serialization.Serializable

/**
 * Respuesta del servidor para la acción "get_track_list".
 *
 * @param action Siempre "track_list" para identificar el tipo de respuesta
 * @param tracks Lista de tracks disponibles en el servidor
 */
@Serializable
data class TrackListResponse(
    val action: String,
    val tracks: List<Track>
)