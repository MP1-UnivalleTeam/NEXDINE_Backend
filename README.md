# NEXDINE Backend

Backend del sistema de gestión de restaurantes NEXDINE.

El proyecto contiene únicamente la lógica del servidor, API REST, acceso a datos y modelos de negocio. La interfaz de usuario se encuentra en un repositorio frontend separado.

## Tecnologías

- Java 21
- Spring Boot 3.3.0
- Spring Web
- Spring Data JPA
- PostgreSQL / Supabase
- Cloudinary Java SDK 2.5.0
- Lombok

## Requisitos

- Java 21
- Maven 3.6+ (o Maven Wrapper incluido)

## Ejecución

En Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

En Linux/macOS:

```bash
./mvnw spring-boot:run
```

Por defecto, el backend queda disponible en:

```text
http://localhost:8080
```

En Render, Spring utiliza automáticamente la variable `PORT` proporcionada por la plataforma.

## Pruebas

```powershell
.\mvnw.cmd test
```

No requieren base de datos ni credenciales de Cloudinary: validan el
contenido de los archivos, las reglas de imagen, la compensación de errores
y los códigos HTTP.

## Variables de entorno

El archivo `.env.example` contiene la estructura esperada. El archivo `.env` real no debe subirse al repositorio.

Variables principales:

| Variable | Descripción |
|---|---|
| `SUPABASE_DB_URL` | URL JDBC de PostgreSQL/Supabase |
| `SUPABASE_DB_USER` | Usuario de PostgreSQL/Supabase |
| `SUPABASE_DB_PASSWORD` | Contraseña de PostgreSQL/Supabase |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para consumir la API |
| `CLOUDINARY_CLOUD_NAME` | Nombre del cloud de Cloudinary |
| `CLOUDINARY_API_KEY` | Clave pública de Cloudinary |
| `CLOUDINARY_API_SECRET` | Secreto de Cloudinary (nunca se expone) |

Opcionales:

| Variable | Por defecto | Descripción |
|---|---|---|
| `CLOUDINARY_FOLDER` | `nexdine/productos` | Carpeta destino de las imágenes |
| `APP_IMAGEN_MAX_BYTES` | `5242880` | Tamaño máximo de imagen (5 MB) |
| `APP_PEDIDOS_TASA_IMPUESTO` | `0` | Tasa porcentual de impuestos aplicada al subtotal después del descuento (0–100) |

Si faltan las variables de Cloudinary, la aplicación **arranca con normalidad**:
la subida de imágenes por archivo responde `503` con un mensaje que indica el
nombre de las variables faltantes. El resto del sistema, incluido el campo
`imagen` como URL en texto, sigue funcionando.

## Imágenes de productos (Cloudinary)

Las imágenes se validan por su **contenido real** (magic bytes), no por la
extensión ni por el `Content-Type` declarado por el cliente.

| Aspecto | Valor |
|---|---|
| Formatos | JPEG, PNG, WebP |
| Tamaño máximo | 5 MB |
| Carpeta | `nexdine/productos` |
| Identificador | `producto_<uuid>` (único, no sobrescribe) |
| URL persistida | `secure_url` (siempre HTTPS) |

En PostgreSQL se guardan **solo la URL y el `public_id`**; nunca binarios.

### Endpoints de productos

| Método | Ruta | Content-Type | Descripción |
|---|---|---|---|
| POST | `/productos/create` | urlencoded | Crear producto (contrato actual) |
| POST | `/productos/create` | multipart/form-data | Crear producto con imagen |
| PUT | `/productos/{id}` | urlencoded | Editar producto (contrato actual) |
| PUT | `/productos/{id}` | multipart/form-data | Editar producto con imagen |
| DELETE | `/productos/{id}` | — | Eliminar producto y limpiar su imagen |
| GET | `/productos/imagenes/config` | — | Estado de la integración (sin secretos) |

Spring distingue las variantes `urlencoded` y `multipart` de la misma ruta por
el `Content-Type`, por lo que el contrato existente **no se rompe**.

Códigos de error relevantes:

| Código | Causa |
|---|---|
| `400` | Archivo inválido o campos obligatorios incorrectos |
| `413` | Archivo mayor al límite permitido |
| `502` | Fallo de comunicación con Cloudinary |
| `503` | Faltan variables de entorno de Cloudinary |
| `409` | Producto duplicado (mismo nombre normalizado en el restaurante) |

## SKU de productos

Cada producto recibe un identificador comercial **único en toda la base**:

```
INICIALES-CODIGO_RESTAURANTE-CONSECUTIVO     ->  EB-R001-000001
```

| Segmento | Origen | Ejemplo |
|---|---|---|
| Iniciales | Nombre del restaurante, congeladas al asignarlas | `EB` |
| Código | Derivado del id (inmutable), permanente y único | `R001` |
| Consecutivo | Contador transaccional por restaurante, mínimo 6 dígitos | `000001` |

Características:

- El **SKU no reemplaza la llave primaria**: `id` sigue autogenerado por la BD.
- El **frontend nunca elige** el SKU: el controller no lo acepta como parámetro.
- El consecutivo **nunca se reutiliza**, ni siquiera al borrar productos.
- Renombrar el restaurante **no altera** los SKU ya emitidos.
- Si supera 999999 crece sin truncar (`1000000`).

### Prevención de duplicados

Dos productos del mismo restaurante se consideran equivalentes si sus nombres
se normalizan igual (sin tildes, minúsculas, sin símbolos, espacios colapsados).

```
"Hamburguesa Clasica"  ==  " hamburguesa clasica "  ==  "HAMBURGUESA CLASICA"
```

- `POST /productos/create` y `PUT /productos/{id}` responden **409 Conflict**
  si el nombre normalizado ya existe en ese restaurante.
- Restaurantes distintos sí pueden tener productos homónimos.
- Al editar se excluye el producto actual de la comprobación.
- La garantía ante concurrencia la aporta el índice UNIQUE de PostgreSQL,
  no la comprobación previa en Java.

### Estado de la asignación

```http
GET /productos/sku/estado     (solo SUPERADMIN)
```

```json
{
  "restaurantesSinCodigo": 0,
  "restaurantesSinIniciales": 0,
  "migracionCompleta": true,
  "productosSinSku": 0,
  "formato": "GEN-R000-000000"
}
```

La migración de base de datos está en `archivos IA/migracion_sku_duplicados.sql`.

## API

### Autenticación

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/login` | Autentica un usuario y crea la sesión |
| GET | `/logout` | Cierra la sesión actual |
| GET | `/api/auth/check` | Comprueba si existe una sesión activa |
| GET | `/api/auth/me` | Obtiene el usuario autenticado |

### Usuarios

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/users` | Lista usuarios autenticados |
| GET | `/api/users/search?id={id}` | Busca un usuario por ID |
| GET | `/users` | Lista usuarios |
| GET | `/users/search?id={id}` | Busca un usuario por ID |
| POST | `/users/create` | Crea un usuario |
| POST | `/users/edit` | Actualiza un usuario |
| POST | `/users/delete` | Elimina un usuario |
| POST | `/users/toggle` | Activa o suspende un usuario |

Las operaciones de administración de usuarios requieren una sesión iniciada con rol `ADMINISTRADOR`.

### Pedidos, mesas y favoritos

Flyway aplica automáticamente las migraciones de `src/main/resources/db/migration/`
al arrancar, antes de que Hibernate valide el esquema. En bases existentes sin
historial de Flyway, se crea una línea base en la versión 1 y se aplican las
migraciones posteriores. Hibernate conserva `spring.jpa.hibernate.ddl-auto=validate`:
el esquema se actualiza únicamente mediante migraciones versionadas.

La tasa de impuestos se configura en porcentaje mediante
`APP_PEDIDOS_TASA_IMPUESTO`; por defecto es `0`. Los impuestos se calculan y
redondean a dos decimales sobre `max(subtotal - descuento, 0)`. El resumen
incluye `subtotal`, `descuento`, `impuestos`, `tasaImpuesto`, `propina`,
`cargos` y `total`.

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| GET | `/api/clientes/favoritos` | Cliente autenticado | Lista productos favoritos del perfil |
| POST | `/api/clientes/favoritos/{productoId}` | Cliente autenticado | Guarda un producto disponible como favorito |
| DELETE | `/api/clientes/favoritos/{productoId}` | Cliente autenticado | Quita un favorito |
| GET | `/mesas` | Administrador, superadmin o mesero | Lista mesas disponibles para elegir |
| POST | `/api/pedidos/manual` | Personal autenticado | Registra un pedido para una mesa y sus productos |
| POST | `/api/pedidos/invitado?sucursalId={uuid}` | Público | Asocia un pedido en línea a la sucursal elegida (parámetro opcional) |
| GET | `/api/pedidos/activos` | Personal autenticado | Lista pedidos activos de la sucursal/restaurante |
| GET | `/api/pedidos/mesas/{mesaId}/pedidos` | Personal autenticado | Lista pedidos activos de una mesa |
| PUT | `/api/pedidos/{pedidoId}/estado?estado=EN_PREPARACION` | Personal autenticado | Actualiza el estado del pedido |
| GET | `/api/pedidos/activos/eventos` | Personal autenticado | Flujo SSE `pedido-actualizado` con cambios en tiempo real |

El cuerpo de `POST /api/pedidos/manual` usa el formato:

```json
{
  "mesaId": "uuid-de-mesa",
  "nombre": "Cliente de mesa",
  "telefono": "5551234567",
  "observaciones": "Sin hielo",
  "items": [
    { "productoId": "uuid-de-producto", "cantidad": 2, "observaciones": "" }
  ]
}
```

Los estados aceptados son `PENDIENTE`, `CONFIRMADO`, `EN_PREPARACION`,
`LISTO`, `ENTREGADO` y `CANCELADO`. Pedidos `ENTREGADO` y `CANCELADO` dejan
de aparecer en la lista de activos.

## Estructura

```text
NEXDINE_Backend/
├── src/
│   ├── main/
│   │   ├── java/com/restaurant/app/
│   │   │   ├── config/
│   │   │   │   ├── CloudinaryConfig.java
│   │   │   │   ├── CloudinaryProperties.java
│   │   │   │   ├── CorsConfig.java
│   │   │   │   └── SesionScheduler.java
│   │   │   ├── controller/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   ├── services/
│   │   │   │   ├── CloudinaryStorageService.java
│   │   │   │   ├── ImagenProductoValidador.java
│   │   │   │   ├── ProductoImagenService.java
│   │   │   │   ├── SkuService.java
│   │   │   │   ├── SkuUtil.java
│   │   │   │   └── ...
│   │   │   └── RestaurantApplication.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/restaurant/app/
├── .env.example
├── .gitignore
├── DockerFile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

No se incluyen plantillas HTML, Thymeleaf ni archivos del frontend en este repositorio.
