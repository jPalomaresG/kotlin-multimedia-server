import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import models.Request
import models.TrackListResponse
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Servidor TCP multimedia que sirve archivos de audio y sus metadatos.
 *
 * El servidor utiliza un protocolo binario simple:
 * - Las peticiones JSON van precedidas de 2 bytes con la longitud
 * - Las respuestas de audio incluyen metadatos JSON + tamaño + datos binarios
 *
 * @param port Puerto en el que escuchará el servidor
 * @param audioDirectory Directorio donde se encuentran los archivos de audio
 */
class Server(private val port: Int, private val audioDirectory: String) {

    private val logger: Logger = Logger.getLogger(Server::class.java.name)
    private val audioManager = AudioTrackManager(audioDirectory)
    private val json = Json { ignoreUnknownKeys = true }  // Ignorar campos extra en JSON

    /**
     * Inicia el servidor y comienza a aceptar conexiones de clientes.
     * Este método es bloqueante y se ejecuta indefinidamente.
     */
    fun start() {
        ServerSocket(port).use { serverSocket ->
            logger.info("Multimedia server started")
            logger.info("Listening on port: $port")
            logger.info("Audio directory: ${File(audioDirectory).absolutePath}")

            // Bucle principal de aceptación de conexiones
            while (true) {
                try {
                    val clientSocket = serverSocket.accept()
                    logger.info("Client connected from ${clientSocket.inetAddress.hostAddress}")

                    // Lanzar cada cliente en una corutina independiente
                    // Esto permite manejar múltiples clientes concurrentemente
                    CoroutineScope(Dispatchers.IO).launch {
                        handleClient(clientSocket)
                    }
                } catch (e: Exception) {
                    logger.log(Level.SEVERE, "Error accepting client connection", e)
                }
            }
        }
    }

    /**
     * Maneja la comunicación con un cliente específico.
     *
     * @param socket Socket conectado al cliente
     */
    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use { client ->
            try {
                val input = client.getInputStream()
                val output = client.getOutputStream()

                // Leer petición del cliente (formato: [2 bytes longitud][JSON])
                val requestJson = readUtfWithLength(input)
                logger.info("Request received: $requestJson")

                // Parsear JSON a objeto Request
                val request = json.decodeFromString<Request>(requestJson)

                // Procesar según la acción solicitada
                when (request.action) {
                    "get_track_list" -> sendTrackList(output)
                    "get_track" -> sendAudioTrack(output, request.track_id)
                    else -> logger.warning("Unknown action requested: ${request.action}")
                }

                output.flush()

            } catch (e: Exception) {
                logger.log(Level.SEVERE, "Error handling client request", e)
            }
        }
        logger.info("Client connection closed")
    }

    /**
     * Lee un mensaje UTF-8 prefijado con su longitud (2 bytes big-endian).
     *
     * Formato: [2 bytes longitud][mensaje UTF-8]
     *
     * @param input InputStream del cliente
     * @return String con el mensaje recibido
     */
    private fun readUtfWithLength(input: InputStream): String {
        // Leer los 2 bytes de longitud
        val lengthBytes = ByteArray(2)
        input.readNBytes(lengthBytes, 0, 2)

        // Convertir bytes a entero (big-endian)
        val length = ((lengthBytes[0].toInt() and 0xFF) shl 8) or (lengthBytes[1].toInt() and 0xFF)

        // Leer el mensaje con la longitud especificada
        val messageBytes = ByteArray(length)
        input.readNBytes(messageBytes, 0, length)

        return String(messageBytes, Charsets.UTF_8)
    }

    /**
     * Escribe un mensaje UTF-8 prefijado con su longitud (2 bytes big-endian).
     *
     * Formato: [2 bytes longitud][mensaje UTF-8]
     *
     * @param output OutputStream del cliente
     * @param message Mensaje a enviar
     */
    private fun writeUtfWithLength(output: OutputStream, message: String) {
        val utf8Bytes = message.toByteArray(Charsets.UTF_8)
        val length = utf8Bytes.size

        // Escribir longitud en big-endian
        output.write((length shr 8) and 0xFF)
        output.write(length and 0xFF)

        // Escribir mensaje
        output.write(utf8Bytes)
        output.flush()
    }

    /**
     * Envía la lista de tracks disponibles al cliente.
     *
     * @param output OutputStream del cliente
     */
    private fun sendTrackList(output: OutputStream) {
        val tracks = audioManager.getTracksFromDirectory()
        val response = TrackListResponse("track_list", tracks)
        val jsonResponse = json.encodeToString(response)

        writeUtfWithLength(output, jsonResponse)
        logger.info("Track list transmitted successfully. Total tracks: ${tracks.size}")
    }

    /**
     * Envía un archivo de audio al cliente con sus metadatos.
     *
     * Formato de respuesta:
     * - 2 bytes: longitud del metadata JSON
     * - N bytes: metadata JSON
     * - 8 bytes: tamaño del audio (long, big-endian)
     * - M bytes: datos binarios del audio
     *
     * @param output OutputStream del cliente
     * @param trackId Identificador del track a enviar
     */
    private fun sendAudioTrack(output: OutputStream, trackId: String?) {
        if (trackId == null) {
            logger.warning("Track ID was not provided in request")
            return
        }

        val file = audioManager.getValidatedTrackFile(trackId) ?: return

        try {
            val audioBytes = Files.readAllBytes(file.toPath())

            // Metadata en JSON que precede al audio
            val metadata = "{\"action\":\"track_audio\",\"track_id\":\"$trackId\",\"size\":${audioBytes.size}}"
            val metadataUtf8 = metadata.toByteArray(Charsets.UTF_8)
            val metadataLength = metadataUtf8.size

            // Logging de depuración
            logger.fine("Metadata length: $metadataLength bytes")
            logger.fine("Metadata: $metadata")
            logger.fine("Audio size: ${audioBytes.size} bytes")

            // Enviar metadata: [2 bytes longitud][JSON]
            output.write((metadataLength shr 8) and 0xFF)
            output.write(metadataLength and 0xFF)
            output.write(metadataUtf8)

            // Enviar tamaño del audio como long de 8 bytes (big-endian)
            var size = audioBytes.size.toLong()
            for (i in 7 downTo 0) {
                output.write(((size shr (i * 8)) and 0xFF).toInt())
            }

            // Enviar datos binarios del audio
            output.write(audioBytes)
            output.flush()

            // Pequeña pausa para asegurar que el cliente procesa la cabecera
            // antes de continuar con la siguiente operación
            Thread.sleep(50)

            val totalSent = 2 + metadataLength + 8 + audioBytes.size
            logger.info("Audio track transmitted successfully: $trackId")
            logger.info("Total bytes sent: $totalSent (headerSize(2) + metadataSize($metadataLength) + audioSizeHeader(8) + audioDataSize(${audioBytes.size}))")

        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Error transmitting audio track: $trackId", e)
        }
    }
}

/**
 * Punto de entrada principal de la aplicación.
 *
 * Inicializa el directorio de audio, verifica que existan archivos
 * y arranca el servidor en el puerto 8080.
 */
fun main() {
    val audioDirectory = "./audio_files"
    File(audioDirectory).mkdirs()

    val logger = Logger.getLogger("Main")
    val files = File(audioDirectory).listFiles()

    // Verificar si hay archivos de audio disponibles
    if (files.isNullOrEmpty()) {
        logger.warning("No audio files found in directory: $audioDirectory")
    } else {
        logger.info("Audio files detected:")
        files.forEach {
            logger.info(" - ${it.name} (${it.length()} bytes)")
        }
    }

    // Iniciar servidor
    val server = Server(8080, audioDirectory)
    server.start()
}