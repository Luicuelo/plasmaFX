# PlasmaFX - Interactive Plasma Fractal Generator

PlasmaFX is a JavaFX application that generates and animates interactive plasma fractals in real time.

## 🚀 Features

- **Fractal Generation**: Uses the diamond-square algorithm to create unique plasma patterns  
- **Smooth Animation**: Renders at 60 FPS with continuous morphing effects  
- **Interactivity**: Effect intensifies around the mouse cursor  
- **Dynamic Restart**: Generates new patterns without restarting the application  

## 🧩 Architecture

### Main Classes

- `PlasmaFX`: Main JavaFX application. Handles UI, mouse events, and animation lifecycle  
- `PlasmaRenderer`: Rendering engine implementing plasma logic and the diamond-square algorithm  

### Algorithm Overview

1. **Initial Generation**: Diamond-square algorithm creates the base pattern  
2. **Color Palette**: 256 procedurally generated colors  
3. **Animation**: Random copying of pixel regions to simulate motion  
4. **Interactivity**: Effect intensifies within a 100px radius around the mouse  

## 📦 Requirements

- Java 11 or higher  
- JavaFX 11 or higher  

## ▶️ Usage

Run the application with:

```bash
java --module-path "C:/path/to/javafx-sdk-XX/lib" --add-modules javafx.controls,javafx.graphics -cp . principal.PlasmaFX 
