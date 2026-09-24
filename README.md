# Sistema de Pedidos — PA1

Evaluación individual del curso **Desarrollo de Aplicaciones Empresariales Avanzado** (ISIL, 2026-20). Amplía el proyecto base `sistema-pedidos` (Jakarta EE) agregando **edición** y **eliminación** de pedidos sobre la arquitectura ya existente.

## Arquitectura

```
Navegador (HTML generado por el Servlet)
        │  HTTP (GET / POST)
        ▼
   PedidoServlet          ← Web Tier (controlador)
        │  @EJB
        ▼
   PedidoService (@Stateless) ← Business Tier (reglas de negocio, transacciones)
        │  JPA / EntityManager
        ▼
   Pedido / Producto (entidades)
        │
        ▼
   Base de datos H2        ← EIS Tier
```

El Servlet **no** contiene lógica de negocio: solo recibe la solicitud HTTP, delega al EJB `PedidoService`, y renderiza el HTML de respuesta.

## Tecnologías

- Java 17+ / Jakarta EE (Servlet, EJB, JPA)
- Hibernate como proveedor JPA
- H2 (base de datos embebida)
- Maven
- WildFly como servidor de aplicaciones

## Funcionalidades

- **Registrar** un pedido (cliente, producto, cantidad) — descuenta stock.
- **Editar** un pedido:
  - Cambiar solo el cliente → no afecta stock.
  - Cambiar la cantidad → repone la cantidad anterior y descuenta la nueva.
  - Cambiar de producto → repone stock del producto anterior y descuenta del nuevo.
  - Datos inválidos (stock insuficiente, ID inexistente) → se rechaza sin dejar cambios parciales (rollback transaccional automático del contenedor EJB).
- **Eliminar** un pedido → repone el stock del producto asociado antes de borrar el registro.

## Métodos HTTP implementados

| Acción      | Método HTTP | Endpoint                              |
|-------------|-------------|----------------------------------------|
| Listar      | GET         | `/pedidos`                             |
| Ver formulario de edición | GET | `/pedidos?action=editar&id={id}` |
| Registrar   | POST        | `/pedidos` (`action=registrar`)        |
| Actualizar  | POST        | `/pedidos` (`action=actualizar`)       |
| Eliminar    | POST        | `/pedidos` (`action=eliminar`)         |

> Solución base: todas las operaciones de escritura usan **POST** con un parámetro `action` que el Servlet interpreta con un `switch`. *(Si se implementó el reto avanzado, actualizar esta tabla indicando qué operaciones usan PUT/DELETE reales.)*

## Manejo de errores

| Situación                          | Código HTTP |
|-------------------------------------|-------------|
| Datos inválidos (cantidad ≤ 0, campos vacíos, stock insuficiente) | 400 |
| Pedido o producto inexistente       | 404 |
| Error inesperado                    | 500 |

Las excepciones de negocio (`IllegalArgumentException`, `IllegalStateException`, `NoSuchElementException`) son lanzadas desde `PedidoService`. Como el contenedor EJB las envuelve en `EJBException`, el Servlet las "desenvuelve" (`getCause()`) para devolver el código HTTP correcto.

## Cómo ejecutar el proyecto

1. Clonar el repositorio:
   ```bash
   git clone https://github.com/GabrielOVQ-dev/sistema-pedidos-pa1.git
   cd sistema-pedidos-pa1
   ```
2. Compilar con Maven:
   ```bash
   mvn clean package
   ```
   Esto genera `target/sistema-pedidos.war`.
3. Copiar el `.war` a la carpeta `deployments` de WildFly:
   ```bash
   copy target\sistema-pedidos.war C:\wildfly\standalone\deployments\
   ```
4. Iniciar WildFly (ajustar el puerto si el 8080 ya está en uso, como en mi caso que ya lo tiene ocupado otro programa):
   ```bash
   C:\wildfly\bin\standalone.bat -Djboss.socket.binding.port-offset=100
   ```
5. Abrir en el navegador:
   ```
   http://localhost:8180/sistema-pedidos/pedidos
   ```

## Datos de prueba

Al iniciar por primera vez, el sistema crea automáticamente 3 productos de ejemplo: Laptop (S/2500, stock 5), Monitor (S/850, stock 8) y Teclado (S/120, stock 15).

## Autor

Gabriel — ISIL, Desarrollo de Software, 2026-20.
