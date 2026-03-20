package models

import kotlinx.serialization.Serializable

/**
 * Representa un track de audio con sus metadatos.
 *
 * @param id Identificador único del track (nombre del archivo)
 * @param title Título de la canción extraído de los metadatos ID3
 * @param author Artista de la canción extraído de los metadatos
 * @param cover Carátula del álbum en formato base64 (data URI) o null si no existe
 */
@Serializable
data class Track(
    val id: String,
    val title: String,
    val author: String,
    val cover: String?
)