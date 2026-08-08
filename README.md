# EmiProtecciones

Mod independiente de protecciones para **Cobbleverse / Fabric 1.21.1**.

## 0.1.0-alpha.1

Primera build jugable del bloque de protección:

- bloque: `emiprotecciones:protection_core`
- modelo 3D GeckoLib recreado a partir del OBJ aportado
- diseño rojo / negro / blanco tipo Poké Ball cuadrada
- botón frontal con una animación idle sutil
- guarda UUID del propietario al colocarlo
- otro jugador no puede romperlo
- propietario o creativo sí puede retirarlo
- muestra durante unos segundos el perímetro visual de **21×21**
- **todavía no crea el claim real de Flan**; esta alpha sirve para validar el modelo, escala, posición y flujo base

### Cómo probar

```mcfunction
/give @s emiprotecciones:protection_core
```

Instala el JAR tanto en cliente como en servidor, junto con Fabric API y GeckoLib.

Cuando el aspecto quede validado en juego, el siguiente paso será conectar esta Pokébola con Flan y LuckPerms para crear el claim real y aplicar límites por rango.
