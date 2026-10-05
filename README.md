# NEXDINE Backend

Backend del sistema de gestión de restaurantes NEXDINE.

El proyecto contiene únicamente la lógica del servidor, API REST, acceso a datos y modelos de negocio. La interfaz de usuario se encuentra en un repositorio frontend separado.

## Tecnologías

- Java 21
- Spring Boot 3.3.0
- Spring Web
- Spring Data JPA
- PostgreSQL / Supabase
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

## Variables de entorno

El archivo `.env.example` contiene la estructura esperada. El archivo `.env` real no debe subirse al repositorio.

Variables principales:

| Variable | Descripción |
|---|---|
| `SUPABASE_DB_URL` | URL JDBC de PostgreSQL/Supabase |
| `SUPABASE_DB_USER` | Usuario de PostgreSQL/Supabase |
| `SUPABASE_DB_PASSWORD` | Contraseña de PostgreSQL/Supabase |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para consumir la API |

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

## Estructura

```text
NEXDINE_Backend/
├── src/
│   └── main/
│       ├── java/com/restaurant/app/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── model/
│       │   ├── repository/
│       │   ├── services/
│       │   └── RestaurantApplication.java
│       └── resources/
│           └── application.properties
├── .env.example
├── .gitignore
├── DockerFile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

No se incluyen plantillas HTML, Thymeleaf ni archivos del frontend en este repositorio.
