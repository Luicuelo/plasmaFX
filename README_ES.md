# PlasmaFX - Interactive Plasma Fractal Generator

Una aplicación JavaFX que genera y anima fractales de plasma interactivos en tiempo real.

## Características

- **Generación fractal**: Utiliza el algoritmo diamond-square para crear patrones de plasma únicos
- **Animación fluida**: Renderizado a 60 FPS con efectos de morphing continuo
- **Interactividad**: El efecto se intensifica alrededor del cursor del ratón
- **Rugosidad ajustable**: Un deslizador deforma el patrón en vivo manteniendo su paleta, desde un
  gradiente liso hasta un ruido grueso
- **Reinicio dinámico**: Genera nuevos patrones sin reiniciar la aplicación

## Arquitectura

### Clases principales

- **`PlasmaFX`**: Aplicación principal JavaFX, maneja la UI, eventos de ratón y ciclo de vida de la animación
- **`PlasmaRenderer`**: Motor de renderizado que implementa la lógica del plasma y el algoritmo diamond-square

### Algoritmo

1. **Generación inicial**: El algoritmo diamond-square crea el patrón base subdividiendo la rejilla y desplazando aleatoriamente el centro de cada celda
2. **Paleta de colores**: 256 colores generados proceduralmente y empaquetados como valores ARGB
3. **Animación**: Copia aleatoria de regiones de píxeles a una posición ligeramente desplazada para crear movimiento
4. **Interactividad**: Intensificación del efecto en un radio de 50 px alrededor del ratón
5. **Rugosidad**: El factor que escala cada desplazamiento aleatorio se puede cambiar en
   caliente; el patrón se redibuja con la misma semilla, así que se deforma en lugar de sustituirse

## Requisitos

- Java 11 o superior
- JavaFX 11 o superior

## Uso

Compilar y ejecutar con el SDK de JavaFX en el module path:

```bash
javac --module-path "C:/ruta/javafx-sdk-XX/lib" --add-modules javafx.controls,javafx.graphics \
      -d bin src/principal/*.java

java --module-path "C:/ruta/javafx-sdk-XX/lib" --add-modules javafx.controls,javafx.graphics \
     -cp bin principal.PlasmaFX
```

El tamaño del lienzo se puede indicar en píxeles, entre 64 y 3840; cualquier otro valor se
descarta con un aviso y se usa el tamaño por defecto:

```bash
java ... -cp bin principal.PlasmaFX --width=1024 --height=768
```

### Controles

- **Movimiento del ratón**: Intensifica el efecto plasma en esa zona
- **Deslizador Roughness** (0 a 30, con marcas cada 5): Deforma el patrón actual en vivo; 0 da
  un gradiente liso y 30 uno muy rugoso
- **Botón Restart**: Genera un nuevo patrón de plasma

## Configuración

Las constantes al principio de `PlasmaRenderer` permiten ajustar:

- `BASE_COPY_COUNT` (300) / `BASE_COPY_SIZE` (30 px): intensidad del efecto base y tamaño de los bloques
- `MOUSE_COPY_COUNT` (50) / `MOUSE_COPY_SIZE` (20 px): efecto adicional alrededor del cursor
- `MOUSE_RADIUS` (50 px): radio de la zona afectada por el cursor
- `COPY_JITTER` (1 px): desplazamiento máximo de un bloque copiado
- `PALETTE_SIZE` (256) / `MAX_CHANNEL` (254): número de colores y techo de cada canal
- `DEFAULT_DISPLACEMENT_FACTOR` (5) / `MIN`-`MAX_DISPLACEMENT_FACTOR` (0-30): rugosidad inicial
  y rango que ofrece el deslizador

## Rendimiento

- Los píxeles viven en un único `int[]`; el `WritableImage` es sólo un espejo de él
- La rejilla se subdivide con aritmética entera (primera mitad y resto), que cubre la imagen
  sin huecos y es unas 1,8 veces más rápida que con geometría en coma flotante
- Las copias de región son llamadas a `System.arraycopy` sobre ese array: sin `PixelReader` ni reservas de memoria
- El fractal se sube a la imagen con una sola llamada masiva a `setPixels`
- Los números aleatorios provienen de `ThreadLocalRandom` en lugar de `Math.random()`
- Una sola llamada `drawImage()` por frame, y el trabajo por frame no depende de la resolución
  salvo en ese repintado final
