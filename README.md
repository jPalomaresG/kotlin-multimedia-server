# Kotlin Multimedia Server

Servidor TCP desarrollado en Kotlin para servir archivos de audio y metadatos. Diseñado específicamente para trabajar con el cliente Android Audio Client.

## Descripción

Este servidor permite a aplicaciones cliente:

- Obtener lista de canciones disponibles con metadatos completos  
- Descargar o hacer streaming de archivos de audio  
- Acceder a carátulas de álbumes extraídas de los metadatos  

## Características

- Protocolo binario eficiente con prefijo de longitud  
- Extracción de metadatos ID3 (título, artista, carátula) mediante JAudioTagger  
- Soporte multi-formato: MP3, WAV, AAC, OGG, FLAC  
- Manejo concurrente de clientes con corutinas  
- Validación de seguridad contra *path traversal*  
- Respuestas JSON estructuradas  

## Requisitos

- Java 21 o superior  
- Gradle (incluido wrapper)  

## Configuración

1. Clonar el repositorio  
2. Añadir archivos de audio a la carpeta `audio_files/`  
3. Ejecutar el servidor  

```bash
git clone https://github.com/jPalomaresG/kotlin-multimedia-server
cd kotlin-multimedia-server
./gradlew run
```

El servidor iniciará en `localhost:8080`.

## Protocolo de comunicación

### Obtener lista de canciones

**Request:**
```json
{
  "action": "get_track_list"
}
```

**Response:**
```json
{
  "action": "track_list",
  "tracks": [
    {
      "id": "cancion.mp3",
      "title": "Nombre Canción",
      "artist": "Artista",
      "cover": "data:image/jpeg;base64,..."
    }
  ]
}
```

### Descargar audio

**Request:**
```json
{
  "action": "get_track",
  "track_id": "cancion.mp3"
}
```

**Response (binario):**
```
[2 bytes metadata length][metadata JSON][8 bytes audio size][audio bytes]
```

## Tecnologías

- Kotlin 2.1.0  
- Corutinas para concurrencia  
- kotlinx.serialization para JSON  
- JAudioTagger para metadatos de audio  
- Gradle Kotlin DSL  

## Relación con el cliente

Este servidor está diseñado específicamente para trabajar con:

https://github.com/jPalomaresG/android-audio-client

El cliente Android se conecta a este servidor para obtener la lista de canciones y reproducir el audio.

## Autor

Josué Javier Palomares García

## Licencia

Todos los derechos reservados © 2025 Josué Javier Palomares García