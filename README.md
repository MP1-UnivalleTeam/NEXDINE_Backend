# NexDine Backend

Sistema de Gestión de Restaurantes - Backend

## Tecnologías

- Java 21
- Spring Boot 3.3.0
- Spring Web
- Spring Data JPA
- H2 Database
- Lombok

## Requisitos

- Java 21
- Maven 3.6+

## Instalación

```bash
cd backend
mvn clean install
```

## Ejecución

```bash
mvn spring-boot:run
```

El backend estará disponible en `http://localhost:8080`

## Variables de Entorno

Crea un archivo `.env` en la carpeta `backend/` basándote en `.env.example`:

| Variable | Descripción | Valor por defecto |
|----------|-------------|-------------------|
| `SERVER_PORT` | Puerto del servidor | 8080 |
| `SPR_DATASOURCE_URL` | URL de la base de datos H2 | jdbc:h2:mem:nextdinedb |
| `SPRING_DATASOURCE_USERNAME` | Usuario de la base de datos | sa |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de la base de datos | (vacío) |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para CORS | http://localhost:5173 |

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| GET | `/` | Redirige a login o dashboard |
| GET | `/login` | Muestra formulario de login |
| POST | `/login` | Autentica usuario |
| GET | `/logout` | Cierra sesión |
| GET | `/dashboard` | Muestra panel principal |
| GET | `/users` | Lista usuarios |
| GET | `/users/search` | Busca usuario por ID |
| POST | `/users/create` | Crea nuevo usuario |
| POST | `/users/edit` | Edita usuario existente |
| POST | `/users/delete` | Elimina usuario |
| POST | `/users/toggle` | Activa/desactiva usuario |

## Estructura

```
backend/
├── src/main/java/com/restaurant/app/
│   ├── controller/
│   ├── model/
│   ├── repository/
│   └── services/
├── src/main/resources/
│   ├── templates/          # Plantillas Thymeleaf (respaldo)
│   └── application.properties
├── src/test/
├── pom.xml
└── .env.example
```

## Frontend

El frontend React se encuentra en la carpeta `../frontend/`
