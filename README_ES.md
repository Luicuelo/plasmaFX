# PlasmaFX - Interactive Plasma Fractal Generator

Una aplicación JavaFX que genera y anima fractales de plasma interactivos en tiempo real.

## Características

- **Generación fractal**: Utiliza el algoritmo diamond-square para crear patrones de plasma únicos
- **Animación fluida**: Renderizado a 60 FPS con efectos de morphing continuo
- **Interactividad**: El efecto se intensifica alrededor del cursor del ratón
- **Reinicio dinámico**: Genera nuevos patrones sin reiniciar la aplicación

## Arquitectura

### Clases principales

- **`PlasmaFX`**: Aplicación principal JavaFX, maneja la UI, eventos de ratón y ciclo de vida de la animación
- **`PlasmaRenderer`**: Motor de renderizado que implementa la lógica del plasma y algoritmo diamond-square

### Algoritmo

1. **Generación inicial**: Diamond-square algorithm crea el patrón base
2. **Paleta de colores**: 256 colores generados proceduralmente
3. **Animación**: Copia aleatoria de regiones de píxeles para crear movimiento
4. **Interactividad**: Intensificación del efecto en un radio de 100px alrededor del ratón

## Requisitos

- Java 11 o superior
- JavaFX 11 o superior

## Uso

```bash
java -cp . principal.PlasmaFX
```

### Controles

- **Movimiento del ratón**: Intensifica el efecto plasma en esa zona
- **Botón Restart**: Genera un nuevo patrón de plasma

## Configuración

Las constantes en `PlasmaRenderer` permiten ajustar:

- `BASE_EFFECT_ITERATIONS`: Intensidad base del efecto (300)
- `MOUSE_EFFECT_ITERATIONS`: Intensidad adicional con ratón (500)  
- `MOUSE_EFFECT_RADIUS`: Radio de influencia del ratón (100px)
- `BASE_COPY_SIZE` / `MOUSE_COPY_SIZE`: Tamaño de las regiones copiadas

## Rendimiento

- Utiliza `WritableImage` con `PixelWriter`/`PixelReader` para operaciones eficientes
- Una sola llamada `drawImage()` por frame
- Manejo de excepciones en el bucle de animación