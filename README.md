# VtAlters

Plugin de Minecraft (Paper/Spigot) para invocar jefes de **MythicMobs** mediante altares rituales configurables.
Soporta items personalizados de **Nexo** de forma opcional.

---

## Índice

1. [Requisitos](#1-requisitos)
2. [Instalación](#2-instalación)
3. [Compatibilidad con PlugMan](#3-compatibilidad-con-plugman)
4. [Configuración general](#4-configuración-general-configyml)
5. [Idiomas disponibles](#5-idiomas-disponibles)
6. [Comandos y permisos](#6-comandos-y-permisos)
7. [Flujo completo para crear un altar](#7-flujo-completo-para-crear-un-altar)
   - 7.1 [Crear el altar](#71-crear-el-altar)
   - 7.2 [Obtener la varita](#72-obtener-la-varita)
   - 7.3 [Establecer el bloque central](#73-establecer-el-bloque-central)
   - 7.4 [Establecer el jefe](#74-establecer-el-jefe)
   - 7.5 [Añadir pedestales](#75-añadir-pedestales)
   - 7.6 [Establecer el item de activación](#76-establecer-el-item-de-activación)
   - 7.7 [Añadir items requeridos](#77-añadir-items-requeridos)
   - 7.8 [Probar el altar](#78-probar-el-altar)
8. [Integración con Nexo](#8-integración-con-nexo)
9. [Estructura de altars.yml](#9-estructura-de-altarsyml)
10. [Efectos visuales y sonidos](#10-efectos-visuales-y-sonidos)
11. [Protección de bloques](#11-protección-de-bloques)
12. [Preguntas frecuentes](#12-preguntas-frecuentes)

---

## 1. Requisitos

| Requisito | Versión mínima | Notas |
|-----------|---------------|-------|
| Paper / Spigot | 1.17+ | Se recomienda Paper |
| Java | 17 | Requerido para compilar y ejecutar |
| MythicMobs | 5.x | Dependencia **obligatoria** |
| Nexo | 1.8+ | Dependencia **opcional** |

---

## 2. Instalación

1. Coloca `VtAlters.jar` en la carpeta `plugins/` de tu servidor.
2. Asegúrate de que **MythicMobs** ya está instalado.
3. *(Opcional)* Si usas Nexo, instálalo también antes de arrancar.
4. Arranca o recarga el servidor.
5. Los archivos de configuración se generarán en `plugins/VtAlters/`.

---

## 3. Compatibilidad con PlugMan

VtAlters es 100 % compatible con PlugMan:

```
plugman load VtAlters       → Carga el plugin desde cero
plugman reload VtAlters     → Equivale a /vta reload
plugman unload VtAlters     → Cancela tareas y elimina items flotantes
```

El método `reloadPlugin()` cancela todas las tareas activas, elimina entidades de display,
desregistra listeners, recarga la configuración y reinicializa todos los managers —
no quedan estados colgados entre recargas.

> `/vta reload` hace exactamente lo mismo que `plugman reload VtAlters`.

---

## 4. Configuración general (`config.yml`)

```yaml
language: es          # Idioma: es | en | vi

altar:
  prevent-item-theft: true     # Solo quien colocó un item puede recuperarlo
  broadcast-summon:
    enabled: true              # Anuncio global al invocar un jefe
  max-pedestal-radius: 10.0    # Radio máximo bloque central ↔ pedestal (bloques)

nexo:
  enabled: true                # Activar/desactivar soporte de Nexo

effects:
  heights:
    pedestal: 1.2              # Altura del item flotando sobre el pedestal
    ready-particle: 1.2        # Altura de la partícula "altar listo"
    ritual-ring-offset: 0.0    # Offset vertical de los anillos rituales
  particles:
    altar-ready: "SOUL_FIRE_FLAME"
    ritual-ring: "SOUL_FIRE_FLAME"
    pedestal-ready: "END_ROD"
    animation-trail: "ENCHANTMENT_TABLE"
    animation-trail-secondary: "END_ROD"
    convergence-burst: "END_ROD"
  sounds:
    # Formato: "NOMBRE_ENUM,Volumen,Tono"
    ritual-start: "BLOCK_BEACON_ACTIVATE,1.5,0.8"
    ritual-ambient-loop: "BLOCK_CONDUIT_AMBIENT_SHORT,1.0,1.2"
    ritual-items-fly: "ENTITY_PHANTOM_SWOOP,0.7,1.5"
    ritual-converge: "ENTITY_GENERIC_EXPLODE,2.0,1.2"
    summon-spawn: "ENTITY_WITHER_SPAWN,2.0,1.0"
```

Pon `"none"` en cualquier partícula para desactivarla.

---

## 5. Idiomas disponibles

| Código | Archivo | Idioma |
|--------|---------|--------|
| `es` | `messages_es.yml` | Español *(por defecto)* |
| `en` | `messages_en.yml` | English |
| `vi` | `messages_vi.yml` | Tiếng Việt |

Para cambiar el idioma: edita `config.yml` → `language: en` → ejecuta `/vta reload`.

Puedes crear `messages_XX.yml` en `plugins/VtAlters/language/` para añadir tu propio idioma.

---

## 6. Comandos y permisos

Aliases disponibles: `/vta` y `/altar`.

| Comando | Permiso | Descripción |
|---------|---------|-------------|
| `/vta create <nombre>` | `vtalters.command.create` | Crea un altar vacío |
| `/vta delete <nombre>` | `vtalters.command.delete` | Elimina un altar |
| `/vta list` | `vtalters.command.list` | Lista todos los altares |
| `/vta wand` | `vtalters.command.wand` | Recibe la varita de configuración |
| `/vta reload` | `vtalters.command.reload` | Recarga config sin reiniciar |
| `/vta edit <nombre> set center` | `vtalters.command.edit` | Establece el bloque central (varita) |
| `/vta edit <nombre> set mob <mob>` | `vtalters.command.edit` | Establece el jefe de MythicMobs |
| `/vta edit <nombre> add itemcenter` | `vtalters.command.edit` | Item de activación (mano) |
| `/vta edit <nombre> add pedestal` | `vtalters.command.edit` | Añade pedestal (varita) |
| `/vta edit <nombre> add item <cantidad>` | `vtalters.command.edit` | Añade item requerido (mano) |
| `/vta edit <nombre> remove pedestal [all]` | `vtalters.command.edit` | Elimina pedestal(es) |
| `/vta edit <nombre> remove item [all]` | `vtalters.command.edit` | Elimina item(s) requerido(s) |

`vtalters.admin` engloba todos los permisos y está activo para OPs por defecto.

---

## 7. Flujo completo para crear un altar

### 7.1 Crear el altar

```
/vta create mi_altar
```

Registra un altar llamado `mi_altar` en `altars.yml` con todos los campos vacíos.
No estará activo hasta completar la configuración.

---

### 7.2 Obtener la varita

```
/vta wand
```

Recibirás una **Varita de Configuración** (Blaze Rod con brillo).
Haz clic izquierdo o derecho sobre cualquier bloque para seleccionarlo.
El chat confirmará las coordenadas del bloque elegido.

---

### 7.3 Establecer el bloque central

El bloque central es donde los jugadores harán clic para activar el ritual.

```
# 1. Con la varita, haz clic en el bloque deseado.
# 2. Ejecuta:
/vta edit mi_altar set center
```

Los pedestales deben estar dentro del radio definido en `altar.max-pedestal-radius`
(10 bloques por defecto) desde este bloque.

---

### 7.4 Establecer el jefe

```
/vta edit mi_altar set mob NombreDelJefe
```

El nombre debe coincidir exactamente con el definido en MythicMobs (case-sensitive).

```
/vta edit mi_altar set mob DragonOscuro
```

> Usa `DefaultBoss` para invocar un Zombie de prueba.

---

### 7.5 Añadir pedestales

Los pedestales son los bloques donde se colocan los items. Puedes añadir tantos como quieras.

```
# Para cada pedestal:
# 1. Con la varita, haz clic en el bloque que será pedestal.
# 2. Ejecuta:
/vta edit mi_altar add pedestal
```

> El número de pedestales limita cuántos items requeridos puedes configurar.

---

### 7.6 Establecer el item de activación

Item que el jugador debe **sostener en la mano** al hacer clic en el bloque central.

```
# 1. Sostén el item en la mano principal.
# 2. Ejecuta:
/vta edit mi_altar add itemcenter
```

Si el item es de **Nexo**, se guardará por su ID — no por NBT.

---

### 7.7 Añadir items requeridos

Items que deben colocarse en los pedestales antes de poder activar el ritual.

```
# 1. Sostén el item en la mano principal.
# 2. Ejecuta:
/vta edit mi_altar add item <cantidad>
```

```
/vta edit mi_altar add item 3    → requiere 3 unidades del item que sostienes
/vta edit mi_altar add item 1    → requiere 1 unidad
```

La suma de todas las cantidades no puede superar el número de pedestales.
Si el item es de **Nexo**, se guarda por ID automáticamente.

---

### 7.8 Probar el altar

Flujo de uso para jugadores:

```
1. COLOCAR ITEMS
   Clic derecho en un pedestal con el item requerido en la mano.
   → El item flota sobre el bloque con partículas orbitales.

2. ALTAR LISTO
   Cuando todos los pedestales tienen sus items:
   → Aparecen partículas SOUL_FIRE_FLAME sobre el bloque central.

3. ACTIVAR
   Sostén el item de activación y haz clic derecho en el bloque central.
   → Se consume 1 unidad del item de activación.

4. ANIMACIÓN DEL RITUAL
   Fase 0 (~2s)  → Anillos de partículas + sonido de preparación
   Fase 1 (~0.7s)→ Items vuelan en espiral hacia el punto orbital
   Fase 2 (~3s)  → Items orbitan el centro rotando 540°
   Fase 3 (~0.3s)→ Items convergen en el punto final
   Final         → Explosión de partículas + el jefe aparece

5. RECUPERAR ITEMS (si no se activó el ritual)
   Clic derecho en el pedestal para recuperar el item.
   Con prevent-item-theft: true → solo quien lo colocó puede tomarlo.
```

---

## 8. Integración con Nexo

VtAlters detecta Nexo automáticamente al cargarse. No requiere configuración adicional.

### Item de activación de Nexo

```
# Sostén el item de Nexo y ejecuta:
/vta edit mi_altar add itemcenter
# Se guarda como: central-item-nexo-id: mi_item_nexo
```

### Items requeridos de Nexo

```
# Sostén el item de Nexo y ejecuta:
/vta edit mi_altar add item 2
# Se guarda en required-items-nexo:
#   mi_item_nexo: 2
```

### Lógica de comparación

| Caso | Comportamiento |
|------|---------------|
| Ambos son items de Nexo | Compara por ID de Nexo |
| Solo uno es de Nexo | No coinciden |
| Ninguno es de Nexo | Usa `isSimilar()` de Bukkit |

### Nexo no instalado

Si Nexo no está presente, los altares con items de Nexo mostrarán avisos en consola
al cargar, pero el resto del plugin funciona con normalidad.

---

## 9. Estructura de `altars.yml`

```yaml
altars:
  mi_altar:
    boss-name: DragonOscuro
    center: "world,10,64,20"

    # Item de activación — usa UNO de los dos campos:
    central-item:               # Item Bukkit serializado (null si usas Nexo)
      ==: org.bukkit.inventory.ItemStack
      v: 2730
      type: DIAMOND
    central-item-nexo-id: null  # ID de Nexo (tiene prioridad sobre central-item)

    # Items requeridos en pedestales:
    required-items:             # Items Bukkit serializados
      - item:
          ==: org.bukkit.inventory.ItemStack
          v: 2730
          type: ENDER_PEARL
        amount: 2
    required-items-nexo:        # Items de Nexo → ID: cantidad
      esencia_oscura: 3

    pedestal-locations:
      - "world,11,64,20"
      - "world,9,64,20"
      - "world,10,64,21"
      - "world,10,64,19"
      - "world,12,64,20"
```

---

## 10. Efectos visuales y sonidos

| Fase | Duración | Partícula | Sonido |
|------|----------|-----------|--------|
| Altar listo | Continua (1s) | `altar-ready` | — |
| Item en pedestal | Continua | `pedestal-ready` | `END_PORTAL_FRAME_FILL` |
| Preparación | 40 ticks | `ritual-ring` | `EVOKER_PREPARE_SUMMON` |
| Vuelo a órbita | 14 ticks | `animation-trail` + secondary | `ritual-items-fly` |
| Órbita | 60 ticks | trails | `ritual-ambient-loop` (c/25t) |
| Convergencia | 5 ticks | trails | `ritual-converge` |
| Invocación | — | 150× `convergence-burst` | `summon-spawn` |

---

## 11. Protección de bloques

Los bloques registrados como centro o pedestal están protegidos contra:
- Rotura por jugadores
- Explosiones de TNT y creepers
- Explosiones de Wither y Wind Charge

Para desproteger un altar, elimínalo con `/vta delete <nombre>`.

---

## 12. Preguntas frecuentes

**¿Puedo mezclar items Nexo y Bukkit en el mismo altar?**
Sí. Cada item requerido se trata independientemente.

**¿Qué pasa si recargo con un ritual en curso?**
El ritual se cancela y las entidades flotantes se eliminan. Espera a que termine antes de recargar.

**¿Cómo muevo un altar?**
Elimínalo con `/vta delete` y recréalo en la nueva posición.

**¿Puedo tener varios altares con el mismo jefe?**
Sí, sin límite.

**¿Los pedestales pueden ser de cualquier bloque?**
Sí, el tipo de bloque es puramente decorativo.

---

## Licencia

MIT License — Copyright (c) 2025 thangks
Consulta el archivo `LICENSE` para más detalles.
