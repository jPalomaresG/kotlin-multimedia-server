import models.Track
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.Base64
import java.util.logging.Logger

/**
 * Gestiona la lectura de archivos de audio y extracción de metadatos.
 *
 * Esta clase se encarga de:
 * - Escanear el directorio de audio en busca de archivos soportados
 * - Extraer metadatos ID3 (título, artista, carátula) de los archivos
 * - Validar solicitudes de archivos para prevenir ataques de path traversal
 *
 * @param audioDirectory Ruta al directorio donde se almacenan los archivos de audio
 */
class AudioTrackManager(private val audioDirectory: String) {

    private val logger: Logger = Logger.getLogger(AudioTrackManager::class.java.name)

    /**
     * Escanea el directorio de audio y extrae metadatos de todos los archivos soportados.
     *
     * Formatos soportados: mp3, wav, aac, ogg, flac
     *
     * @return Lista de objetos Track con los metadatos extraídos.
     *         Los archivos que no puedan ser procesados se omiten silenciosamente.
     */
    fun getTracksFromDirectory(): List<Track> {
        // Filtrar solo archivos con extensiones de audio soportadas
        val audioFiles = File(audioDirectory).listFiles { file ->
            file.isFile && file.extension.lowercase() in listOf("mp3", "wav", "aac", "ogg", "flac")
        } ?: return emptyList()

        return audioFiles.mapNotNull { file ->
            try {
                // Leer metadatos del archivo de audio
                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tag

                // Extraer título: usar metadato o nombre de archivo como fallback
                val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotEmpty() }
                    ?: file.nameWithoutExtension

                // Extraer artista: usar metadato o valor por defecto
                val author = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotEmpty() }
                    ?: "Unknown Artist"

                // Extraer carátula si existe y codificarla a base64 para enviar al cliente
                var coverBase64: String? = null
                val artwork = tag?.firstArtwork

                if (artwork != null) {
                    val imageBytes = artwork.binaryData
                    val base64Image = Base64.getEncoder().encodeToString(imageBytes)
                    // Formato data URI para que el cliente pueda mostrarla directamente
                    coverBase64 = "data:image/jpeg;base64,$base64Image"
                }

                Track(
                    id = file.name,      // Usar nombre de archivo como identificador único
                    title = title,
                    author = author,
                    cover = coverBase64
                )

            } catch (e: Exception) {
                // Registrar error pero continuar con el siguiente archivo
                logger.warning("Metadata extraction failed for file ${file.name}: ${e.message}")
                null
            }
        }
    }

    /**
     * Valida que un track solicitado sea seguro y exista en el sistema.
     *
     * Esta función previene ataques de path traversal donde un cliente malicioso
     * podría intentar acceder a archivos fuera del directorio permitido usando
     * secuencias como "../".
     *
     * @param trackId Nombre del archivo solicitado (del campo id en Track)
     * @return Objeto File válido si pasa todas las validaciones, null en caso contrario
     */
    fun getValidatedTrackFile(trackId: String): File? {
        // Obtener rutas canónicas para resolver symlinks y normalizar path
        val baseDir = File(audioDirectory).canonicalFile
        val requestedFile = File(baseDir, trackId).canonicalFile

        // Verificar que el archivo solicitado está dentro del directorio permitido
        // Esto previene que alguien solicite "../../etc/passwd" o rutas similares
        if (!requestedFile.path.startsWith(baseDir.path)) {
            logger.warning("Path traversal attempt detected for track_id: $trackId")
            return null
        }

        // Verificar que el archivo realmente existe
        if (!requestedFile.exists()) {
            logger.warning("Requested track does not exist: $trackId")
            return null
        }

        return requestedFile
    }
}