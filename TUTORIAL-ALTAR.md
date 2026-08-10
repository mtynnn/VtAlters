# Minitutorial: crear un altar en vAltars

Esta guía crea un altar llamado `altar_dragon`. Necesitas ser operador o tener los permisos administrativos de vAltars. MythicMobs debe estar instalado; Nexo es opcional.

## 1. Crear el altar

```text
/valtarsadmin create altar_dragon
```

El nombre puede contener letras, números, guion y guion bajo.

## 2. Obtener la varita y elegir el centro

```text
/valtarsadmin wand
```

Con la varita en la mano, haz clic izquierdo o derecho en el bloque que será el centro. Después ejecuta:

```text
/valtarsadmin edit altar_dragon set center
```

El centro es el bloque que los jugadores pulsarán para iniciar el ritual.

## 3. Elegir el jefe

Usa el ID interno exacto del mob definido en MythicMobs:

```text
/valtarsadmin edit altar_dragon set mob DragonOscuro
```

Para una prueba rápida puedes usar `DefaultBoss`, que invoca un zombi:

```text
/valtarsadmin edit altar_dragon set mob DefaultBoss
```

## 4. Añadir los pedestales

Selecciona un bloque con la varita y añádelo:

```text
/valtarsadmin edit altar_dragon add pedestal
```

Repite la selección y el comando por cada pedestal. No uses el bloque central y mantén los pedestales dentro del radio configurado, 10 bloques por defecto. Recuerda el orden: el primer requisito se asignará al primer pedestal añadido, el segundo al segundo y así sucesivamente.

Cada requisito necesita su propio pedestal. Los requisitos repetidos no se fusionan: dos stacks de 16 perlas requieren dos pedestales y dos comandos separados.

## 5. Definir el objeto de activación

Sostén en la mano el objeto que se usará como llave y ejecuta, indicando opcionalmente la cantidad:

```text
/valtarsadmin edit altar_dragon add itemcenter

# Para exigir 4 unidades iguales en el centro:
/valtarsadmin edit altar_dragon add itemcenter 4
```

Si omites la cantidad, se exige una unidad. El jugador debe llevar al menos el total configurado al hacer clic derecho en el centro; vAltars captura esa cantidad completa para el ritual y la devuelve completa si el spawn falla. Si es un objeto Nexo, vAltars guarda su ID automáticamente.

## 6. Añadir los objetos requeridos

Sostén el objeto requerido y especifica cuántas unidades necesita el siguiente pedestal:

```text
# Primer pedestal: sostén perlas
/valtarsadmin edit altar_dragon add item 16

# Segundo pedestal: continúa sosteniendo perlas
/valtarsadmin edit altar_dragon add item 16
```

Ejemplo para asignar además un diamante al tercer pedestal:

```text
# Sostén un diamante
/valtarsadmin edit altar_dragon add item 1
```

Ejecuta un comando por pedestal y en el mismo orden en que los añadiste. Cada cantidad debe caber en el stack natural del objeto. Los objetos Bukkit se comparan por tipo y metadatos; los objetos Nexo se comparan por su ID.

## 7. Probar el ritual

1. Un holograma muestra la cantidad y el nombre visible del objeto requerido sobre el primer pedestal pendiente.
2. Un jugador hace clic derecho en ese pedestal sosteniendo el objeto indicado.
3. vAltars transfiere de una vez las unidades que todavía falten, sin superar el máximo natural del stack; un objeto incorrecto se rechaza.
4. Si no llevaba suficiente, el mismo dueño puede completar ese stack con más clics. El holograma muestra la cantidad restante.
5. Al completar el pedestal, el holograma avanza al siguiente. En objetos Nexo usa el nombre visible configurado por Nexo, nunca su ID interno mientras el objeto exista.
6. Repite hasta completar todos los requisitos, sostén el objeto de activación y haz clic derecho en el bloque central.
7. El ritual comienza y el jefe aparece al finalizar la animación.

El consumo se confirma únicamente si MythicMobs devuelve un jefe vivo y válido. Si el spawn falla, los objetos se devuelven mediante el sistema de devoluciones pendientes.

## Expiración y abandono

Un altar incompleto no puede quedar bloqueado indefinidamente:

- Cada objeto válido reinicia el plazo de ese altar.
- Tras 45 segundos sin un nuevo aporte, todos sus objetos se devuelven.
- Si ningún contribuyente permanece conectado a menos de 16 bloques, se devuelven antes.
- Si el inventario está lleno o el dueño está desconectado, la devolución queda guardada para el próximo ingreso.

Los valores se pueden cambiar en `config.yml`:

```yaml
altar:
  placement-expiry:
    idle-seconds: 45
    max-player-distance: 16.0
```

## Comandos útiles

```text
/valtarsadmin list
/valtarsadmin gui
/valtarsadmin help
/valtars about
```

`/valtarsadmin gui` muestra los altares registrados y permite teletransportarse con un clic al bloque situado sobre su centro. Requiere `valtars.command.teleport` o `valtars.admin`.

Para corregir una configuración antes de colocar objetos:

```text
# Selecciona el pedestal con la varita
/valtarsadmin edit altar_dragon remove pedestal

# Sostén el tipo de objeto que quieres retirar
/valtarsadmin edit altar_dragon remove item

/valtarsadmin edit altar_dragon remove pedestal all
/valtarsadmin edit altar_dragon remove item all
```

Un altar con objetos colocados o con un ritual activo no se puede editar ni eliminar. Retira los objetos o espera su devolución automática antes de modificarlo.

Los aliases administrativos `/altar`, `/vta` y `/vtalters` funcionan igual que `/valtarsadmin`.
