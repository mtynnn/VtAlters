<div align="center">

# vAltars

### Altares rituales seguros para Paper y MythicMobs

[![Version](https://img.shields.io/badge/version-1.1.0-FFD166?style=for-the-badge)](https://github.com/ValerinSMP/vAltars)
[![Paper](https://img.shields.io/badge/Paper-1.21.11%2B-222222?style=for-the-badge)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21-E76F00?style=for-the-badge&logo=openjdk&logoColor=white)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-00B894?style=for-the-badge)](LICENSE)

Convierte estructuras del mundo en rituales configurables con pedestales, objetos y jefes personalizados.

</div>

## ⭐ Características

- ⭐ **Rituales seguros:** los objetos se confirman solo cuando MythicMobs devuelve un jefe vivo; un spawn fallido activa su devolución.
- ⭐ **Pedestales ordenados:** cada pedestal exige su propio objeto y cantidad, incluso cuando varios requisitos usan el mismo material.
- ⭐ **Objetos Bukkit y Nexo:** admite stacks, objetos centrales con cantidad y nombres visibles de Nexo en los hologramas.
- ⭐ **Recuperación automática:** los aportes abandonados o inactivos se devuelven, incluso si el jugador está desconectado o sin espacio.
- ⭐ **Administración en juego:** varita de selección, comandos con autocompletado y una GUI para consultar y visitar altares.
- ⭐ **Migración compatible:** importa instalaciones de VtAlters sin sobrescribir archivos modernos.

## Compatibilidad

| Componente | Versión | Uso |
| --- | --- | --- |
| Paper | 1.21.11 o superior | Plataforma requerida |
| Java | 21 | Runtime y compilación |
| MythicMobs | 5.11.2 | Dependencia requerida |
| Nexo | 1.8.0 | Opcional, solo para objetos Nexo |

vAltars está diseñado para Paper. No ofrece soporte oficial para Folia, Bukkit, Spigot, Arclight ni recargas mediante PlugMan.

## Setup

1. Instala MythicMobs y, si usarás objetos personalizados, Nexo.
2. Copia `vAltars-1.1.0.jar` a la carpeta `plugins/`.
3. Inicia el servidor; vAltars creará sus archivos en `plugins/vAltars/`.
4. Crea y configura tu primer altar siguiendo el [tutorial paso a paso](TUTORIAL-ALTAR.md).

Usa `/valtarsadmin reload` para validar y recargar `config.yml` y el idioma sin reiniciar el plugin. Los rituales activos conservan la configuración con la que comenzaron.

### Migración desde VtAlters

Al iniciar, vAltars copia únicamente los archivos faltantes desde `plugins/VtAlters/`, guarda un respaldo en `plugins/vAltars/backups/legacy-v1/` y nunca sobrescribe datos modernos. La migración es idempotente.

## Comandos

| Comando | Descripción | Permiso |
| --- | --- | --- |
| `/valtars help [página]` | Ayuda pública paginada | Ninguno |
| `/valtars about` | Versión, plataforma e integraciones | Ninguno |
| `/valtarsadmin help [página]` | Ayuda administrativa | Según cada subcomando |
| `/valtarsadmin create <nombre>` | Crear un altar | `valtars.command.create` |
| `/valtarsadmin delete <nombre>` | Eliminar un altar libre | `valtars.command.delete` |
| `/valtarsadmin list` | Listar altares | `valtars.command.list` |
| `/valtarsadmin gui` | Abrir el navegador y teletransporte | `valtars.command.teleport` |
| `/valtarsadmin wand` | Obtener la varita de selección | `valtars.command.wand` |
| `/valtarsadmin edit ...` | Configurar centro, jefe, pedestales y objetos | `valtars.command.edit` |
| `/valtarsadmin reload` | Recargar configuración e idioma | `valtars.command.reload` |

`valtars.admin` concede todos los permisos administrativos y es `op` por defecto. Los permisos legacy `vtalters.*` y los aliases `/altar`, `/vta` y `/vtalters` siguen disponibles.

La sintaxis completa de creación, cantidades centrales, stacks por pedestal y eliminación está en [TUTORIAL-ALTAR.md](TUTORIAL-ALTAR.md).

## Configuración

`config.yml` controla idioma, protección contra robo, radio de pedestales, expiración, anuncios, partículas, alturas y sonidos. Los mensajes MiniMessage se incluyen en español, inglés y vietnamita bajo `language/`.

Por defecto, un altar incompleto devuelve sus objetos tras 45 segundos sin aportes o cuando ningún contribuyente conectado permanece a menos de 16 bloques. Una configuración inválida no reemplaza el último estado válido.

## Desarrollo

Requiere Java 21 y el Gradle Wrapper incluido:

```powershell
.\gradlew.bat clean test build --no-daemon --max-workers=1 --console=plain
```

El JAR se genera en `build/libs/vAltars-1.1.0.jar`.

## Licencia y enlaces

- [Repositorio](https://github.com/ValerinSMP/vAltars)
- [Tutorial para crear altares](TUTORIAL-ALTAR.md)
- [Licencia MIT](LICENSE)

Se conserva la atribución original a **thangks** incluida en la licencia.
