# vAltars

Altares rituales para Paper que invocan jefes de MythicMobs. Admite objetos Bukkit y, de forma opcional, objetos personalizados de Nexo.

Repositorio oficial: [ValerinSMP/vAltars](https://github.com/ValerinSMP/vAltars).

## Requisitos

- Paper 1.21.11 o superior.
- Java 21.
- MythicMobs 5.11.2 o compatible. Es una dependencia obligatoria.
- Nexo 1.8 o compatible solo si los altares usan objetos Nexo.

## Instalación

1. Copia `vAltars-1.1.0.jar` a `plugins/`.
2. Instala el JAR real de MythicMobs y, si corresponde, Nexo.
3. Inicia el servidor. Los datos se guardan en `plugins/vAltars/`.
4. Configura los altares con `/valtarsadmin` o uno de sus alias.

No se recomienda descargar o recargar plugins en caliente. Usa `/valtarsadmin reload` para recargar solamente `config.yml` y el archivo de idioma.

## Migración desde VtAlters

Al iniciar, vAltars busca `plugins/VtAlters/`. Si existe, crea una copia de respaldo en `plugins/vAltars/backups/legacy-v1/` y copia únicamente los archivos que todavía no existen en la carpeta nueva.

La migración es idempotente: nunca sobrescribe datos modernos y puede ejecutarse más de una vez sin duplicar ni perder configuración. Se siguen leyendo `central-item-nexo-id` y el mapa legacy `required-items-nexo`; al guardar, los requisitos Bukkit y Nexo usan la lista ordenada `required-items`.

## Comandos

Los comandos públicos no requieren permisos:

| Comando | Descripción |
| --- | --- |
| `/valtars help [página]` | Muestra ayuda paginada e interactiva. |
| `/valtars about` | Muestra versión, plataforma e integraciones. |

Administración:

| Comando | Permiso |
| --- | --- |
| `/valtarsadmin reload` | `valtars.command.reload` |
| `/valtarsadmin create <nombre>` | `valtars.command.create` |
| `/valtarsadmin delete <nombre>` | `valtars.command.delete` |
| `/valtarsadmin list` | `valtars.command.list` |
| `/valtarsadmin gui` | `valtars.command.teleport` |
| `/valtarsadmin wand` | `valtars.command.wand` |
| `/valtarsadmin edit <altar> set center` | `valtars.command.edit` |
| `/valtarsadmin edit <altar> set mob <mob>` | `valtars.command.edit` |
| `/valtarsadmin edit <altar> add itemcenter [cantidad]` | `valtars.command.edit` |
| `/valtarsadmin edit <altar> add pedestal` | `valtars.command.edit` |
| `/valtarsadmin edit <altar> add item <cantidad>` | `valtars.command.edit` |
| `/valtarsadmin edit <altar> remove pedestal [all]` | `valtars.command.edit` |
| `/valtarsadmin edit <altar> remove item [all]` | `valtars.command.edit` |

`valtars.admin` incluye todos los permisos administrativos y es `op` por defecto. Se siguen aceptando `vtalters.admin` y `vtalters.command.*` para instalaciones existentes. Los alias administrativos son `/altar`, `/vta` y `/vtalters`.

## Crear un altar

Para una explicación completa con un ejemplo listo para copiar, consulta [TUTORIAL-ALTAR.md](TUTORIAL-ALTAR.md).

1. Ejecuta `/valtarsadmin create <nombre>`.
2. Obtén la varita con `/valtarsadmin wand` y selecciona el bloque central.
3. Ejecuta `/valtarsadmin edit <altar> set center`.
4. Define el jefe con `/valtarsadmin edit <altar> set mob <MythicMob>`.
5. Selecciona y añade cada pedestal con `/valtarsadmin edit <altar> add pedestal`.
6. Sostén el objeto de activación y usa `/valtarsadmin edit <altar> add itemcenter [cantidad]`.
7. Sostén cada objeto requerido y usa `/valtarsadmin edit <altar> add item <cantidad>`.

Los pedestales y requisitos se emparejan por orden. Cada ejecución de `add item` crea una ranura independiente para el siguiente pedestal, incluso si repites el mismo objeto.

La cantidad del objeto central es opcional y vale `1` si se omite. Por ejemplo, `/valtarsadmin edit altar_dragon add itemcenter 4` exige y consume cuatro unidades iguales al iniciar el ritual. Los altares guardados antes de esta opción conservan cantidad `1`.

`/valtarsadmin gui` abre una vista paginada de los altares, con jefe, coordenadas, número de pedestales y estado. Un clic teletransporta al bloque situado sobre el centro; la GUI es administrativa y requiere `valtars.command.teleport`.

Un altar con objetos colocados o un ritual activo no puede editarse ni eliminarse. Otros altares siguen funcionando de forma independiente.

## Configuración e idiomas

`config.yml` controla idioma, protección contra robo, radio máximo, expiración de objetos, anuncio global, partículas, alturas y sonidos. Se incluyen mensajes MiniMessage en español, inglés y vietnamita bajo `language/`.

Por defecto, un altar incompleto devuelve sus objetos después de 45 segundos sin un nuevo aporte. También los devuelve antes si ningún contribuyente sigue conectado a menos de 16 bloques. Puedes ajustar ambos valores en `altar.placement-expiry.idle-seconds` y `altar.placement-expiry.max-player-distance`.

Una recarga valida primero toda la configuración. Si algo es inválido, la configuración anterior sigue activa. Los rituales que ya comenzaron conservan su snapshot; los cambios se aplican solo a sesiones nuevas y no vuelven a registrar listeners.

## Seguridad de objetos

Cada ritual conserva snapshots propios de los objetos y pedestales. Los displays son solo una proyección visual. El consumo queda confirmado únicamente después de que MythicMobs devuelva una entidad viva y válida; si el spawn falla, los objetos se devuelven.

La animación usa `ItemDisplay` temporales e interpolados en lugar de entidades de objeto soltado, evitando su giro, rebote y física propios. Los displays se eliminan al terminar o cancelar y nunca sustituyen los snapshots de la sesión.

Cada pedestal acepta únicamente el objeto y la cantidad de su requisito asignado. Un único holograma señala la primera ranura incompleta, muestra la cantidad restante y avanza al siguiente pedestal al completarla. Para objetos Nexo utiliza el nombre visible del `ItemStack` construido por su API, no el ID interno. Un pedestal puede reunir varias unidades y el mismo dueño puede completarlo con clics posteriores. Los requisitos repetidos permanecen separados, por lo que otro jugador puede contribuir en otra ranura sin mezclar propiedad. El stack puede retirarlo su dueño, completa el ritual si llegan los demás aportes o se devuelve automáticamente por inactividad/abandono.

Si el jugador está desconectado o su inventario está lleno, la devolución se guarda en `pending-refunds.yml`. Los leftovers permanecen pendientes y se reintentan al ingresar. La entrega usa reclamación durable y un tag temporal; el tag se elimina tras confirmar y el objeto vuelve a apilar normalmente.

El inventario de Bukkit y el archivo YAML no forman una transacción atómica única. El protocolo está diseñado para recuperación sin una ruta reproducible de pérdida o duplicación bajo las garantías normales de `Player#saveData`, pero no promete “exactamente una vez” ante fallos del sistema de archivos o almacenamiento que contradigan una escritura confirmada.

## Compilar

```powershell
.\gradlew.bat clean test build --no-daemon --max-workers=1 --console=plain
```

El artefacto se genera en `build/libs/vAltars-1.1.0.jar`.

Último artefacto verificado: 123535 bytes, SHA-256 `D8FB0A6022723BF800046EB7A7404D80BD0ED70805AA94B44B9BAAD7452ED5B8` (26 pruebas superadas).

## Licencia

MIT. Se conserva el copyright original de thangks en [LICENSE](LICENSE).
