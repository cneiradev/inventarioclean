-- =====================================================================
-- V1: Esquema inicial del sistema de bodega / inventario.
--
-- Convenciones:
--   - "TIMESTAMP WITH TIME ZONE" en vez del alias "TIMESTAMPTZ": es la forma
--     estandar y la unica que H2 (perfil local) tambien entiende.
--   - Soft delete via columna "activo": nunca se borra fisicamente una fila
--     que tenga movimientos historicos asociados.
--   - Enums como VARCHAR + CHECK, no como tipo ENUM de Postgres: anadir un
--     valor despues con ALTER TYPE es fragil; un CHECK se altera facil.
--   - BIGSERIAL para las claves, que ambos motores convierten a identidad.
--
-- Ojo: esta migracion debe ser portable entre H2 (local) y PostgreSQL
-- (Supabase). Si se anade algo especifico de Postgres, aislarlo en una
-- migracion pendiente del perfil, no en esta.
-- =====================================================================

-- ---------------------------------------------------------------------
-- usuario
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(100)  NOT NULL,
    apellido        VARCHAR(100)  NOT NULL,
    email           VARCHAR(150)  NOT NULL,
    -- Formato FPEREZ: inicial del primer nombre + primer apellido.
    -- Unico y siempre en mayusculas, validado en el backend.
    username        VARCHAR(50)   NOT NULL,
    -- BCrypt. Nunca texto plano ni SHA.
    password_hash   VARCHAR(100)  NOT NULL,
    rol             VARCHAR(20)   NOT NULL,
    activo          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_usuario_username UNIQUE (username),
    CONSTRAINT uq_usuario_email    UNIQUE (email),
    CONSTRAINT ck_usuario_rol      CHECK (rol IN ('ADMINISTRADOR', 'OPERADOR'))
);

CREATE INDEX idx_usuario_rol   ON usuario (rol);
CREATE INDEX idx_usuario_activo ON usuario (activo);

-- ---------------------------------------------------------------------
-- producto
-- ---------------------------------------------------------------------
CREATE TABLE producto (
    id                  BIGSERIAL PRIMARY KEY,
    -- Codigo de producto. Es la clave de busqueda usada por el operador y
    -- por el admin (modificar/eliminar "por SKU").
    sku                 VARCHAR(50)    NOT NULL,
    nombre              VARCHAR(200)   NOT NULL,
    -- Texto libre por ahora: la reunion del 24/09 dejo "medidas" sin
    -- definir. Si mas adelante se normaliza, se migra a columnas.
    medidas             VARCHAR(200),
    -- Cantidad fisica en bodega (no se toca al rentar/vender).
    cantidad_total      INTEGER        NOT NULL DEFAULT 0,
    -- Cantidad que se puede rentar/vender HOY = total - reservado.
    -- NUNCA se edita a mano desde la UI: se deriva de los movimientos.
    cantidad_disponible INTEGER        NOT NULL DEFAULT 0,
    -- DECIMAL y no FLOAT: el dinero no es binario.
    precio              DECIMAL(12, 2) NOT NULL DEFAULT 0,
    tipo_comercio       VARCHAR(10)    NOT NULL,
    estado              VARCHAR(20)    NOT NULL DEFAULT 'EN_BODEGA',
    -- Umbral del dashboard de "stock bajo". Configurable por producto.
    stock_minimo        INTEGER        NOT NULL DEFAULT 0,
    -- Soft delete: un producto con movimientos jamas se borra fisicamente.
    activo              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_producto_sku UNIQUE (sku),
    CONSTRAINT ck_producto_tipo_comercio CHECK (tipo_comercio IN ('VENTA', 'RENTA')),
    CONSTRAINT ck_producto_estado         CHECK (estado IN ('EN_BODEGA', 'ARRENDADO')),
    CONSTRAINT ck_producto_cantidades     CHECK (cantidad_total >= 0 AND cantidad_disponible >= 0),
    -- Invariante central del sistema: nunca se puede rentar/vender mas de
    -- lo que hay disponible. Se refuerza tambien en el servicio, dentro de
    -- la transaccion, para cubrir el acceso concurrente.
    CONSTRAINT ck_producto_disponible     CHECK (cantidad_disponible <= cantidad_total)
);

CREATE INDEX idx_producto_tipo   ON producto (tipo_comercio);
CREATE INDEX idx_producto_activo ON producto (activo);
CREATE INDEX idx_producto_nombre ON producto (nombre);
-- Soporte del dashboard de stock bajo (consulta frecuente: productos de
-- venta activos ordenados por disponible). Indice compuesto en vez de
-- parcial porque un indice parcial (CREATE INDEX ... WHERE) no es portable:
-- H2, que se usa en el perfil local, no lo soporta. En PostgreSQL el indice
-- compuesto cumple la misma funcion.
CREATE INDEX idx_producto_stock_bajo
    ON producto (tipo_comercio, activo, cantidad_disponible);

-- ---------------------------------------------------------------------
-- movimiento (append-only: la fuente de verdad del stock)
-- ---------------------------------------------------------------------
-- Toda variacion de cantidad_disponible pasa por aqui. Es append-only:
-- nunca se UPDATEa ni se DELETEa una fila. Si algo se registro mal, se
-- corrige con un movimiento de AJUSTE, no editando el historico.
--
-- El signo de "cantidad" codifica la direccion del stock:
--   cantidad > 0  -> sale de bodega  (VENTA, AJUSTE a la baja)
--   cantidad < 0  -> vuelve a bodega (ENTRADA_MANUAL)
CREATE TABLE movimiento (
    id             BIGSERIAL PRIMARY KEY,
    tipo           VARCHAR(20)   NOT NULL,
    producto_id    BIGINT        NOT NULL,
    cantidad       INTEGER       NOT NULL,
    -- Quien ejecuto el movimiento.
    usuario_id     BIGINT        NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_movimiento_tipo     CHECK (tipo IN ('VENTA', 'ENTRADA_MANUAL', 'AJUSTE')),
    CONSTRAINT ck_movimiento_cantidad CHECK (cantidad <> 0),
    CONSTRAINT fk_movimiento_producto FOREIGN KEY (producto_id) REFERENCES producto (id),
    CONSTRAINT fk_movimiento_usuario  FOREIGN KEY (usuario_id)  REFERENCES usuario (id)
);

CREATE INDEX idx_movimiento_producto ON movimiento (producto_id, created_at DESC);
CREATE INDEX idx_movimiento_usuario  ON movimiento (usuario_id);
CREATE INDEX idx_movimiento_fecha    ON movimiento (created_at DESC);

-- ---------------------------------------------------------------------
-- auditoria (append-only: evidencia de cada accion, requisito del cliente)
-- ---------------------------------------------------------------------
-- El documento pide registrar "quien lo hizo" y "cuando". username se
-- desnormaliza a proposito: si el usuario se elimina, la evidencia de sus
-- acciones debe seguir siendo legible.
CREATE TABLE auditoria (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT,
    username       VARCHAR(50)   NOT NULL,
    -- Que hizo: CREAR_USUARIO, ELIMINAR_PRODUCTO, AJUSTAR_STOCK...
    accion         VARCHAR(50)   NOT NULL,
    -- Sobre que: 'usuario', 'producto'...
    entidad        VARCHAR(50)   NOT NULL,
    entidad_id     BIGINT,
    -- Detalle libre: valores antes/despues, codigos, etc.
    detalle        VARCHAR(4000),
    -- IP y user-agent para descartar acciones de origen desconocido.
    ip             VARCHAR(45),
    created_at     TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE SET NULL
);

CREATE INDEX idx_auditoria_usuario ON auditoria (usuario_id, created_at DESC);
CREATE INDEX idx_auditoria_fecha   ON auditoria (created_at DESC);
CREATE INDEX idx_auditoria_accion  ON auditoria (accion);

