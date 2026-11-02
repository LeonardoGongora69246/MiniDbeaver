
package minidbeaver.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import minidbeaver.model.RemoteDbDao.ColumnaInfo;
import minidbeaver.model.RemoteDbDao.ResultadoTabla;

public class SqliteManager {

    private static final String URL =
            "jdbc:sqlite:datos/minidbeaver.db";

    // 1. CONEXIÓN A SQLITE
    public Connection conectar() throws SQLException {
        try {
            Files.createDirectories(Path.of("datos"));
        } catch (IOException e) {
            throw new SQLException(
                    "No se pudo crear la carpeta datos", e);
        }

        return DriverManager.getConnection(URL);
    }

    // 2. CONVERTIR TIPOS DE MARIADB A SQLITE
    private String convertirTipo(String tipoMariaDB) {

        String tipo = tipoMariaDB.toUpperCase();

        if (tipo.contains("BLOB") ||
                tipo.contains("BINARY")) {
            return "BLOB";
        }

        if (tipo.contains("INT") ||
                tipo.equals("BIT") ||
                tipo.equals("BOOLEAN")) {
            return "INTEGER";
        }

        if (tipo.contains("CHAR") ||
                tipo.contains("TEXT") ||
                tipo.contains("ENUM") ||
                tipo.contains("SET") ||
                tipo.contains("JSON")) {
            return "TEXT";
        }

        if (tipo.contains("FLOAT") ||
                tipo.contains("DOUBLE")) {
            return "REAL";
        }

        if (tipo.contains("DECIMAL") ||
                tipo.contains("NUMERIC")) {
            return "NUMERIC";
        }

        return "TEXT";
    }

    // 3. PROTEGER NOMBRES SQL
    private String citarNombre(String nombre)
            throws SQLException {

        if (nombre == null || nombre.isBlank()) {
            throw new SQLException(
                    "Nombre de tabla o columna vacío");
        }

        return "\"" + nombre.replace("\"", "\"\"") + "\"";
    }

    // 4. COMPROBAR SI UNA TABLA YA EXISTE
    public boolean tablaExiste(String nombreTabla)
            throws SQLException {

        try (Connection conexion = conectar()) {
            return tablaExiste(conexion, nombreTabla);
        }
    }

    private boolean tablaExiste(
            Connection conexion,
            String nombreTabla) throws SQLException {

        String sql = """
                SELECT COUNT(*)
                FROM sqlite_master
                WHERE type = 'table'
                AND name = ? COLLATE NOCASE
                """;

        try (PreparedStatement ps =
                     conexion.prepareStatement(sql)) {

            ps.setString(1, nombreTabla);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // 5. CREAR TABLA DINÁMICAMENTE
    private void crearTabla(
            Connection conexion,
            String nombre,
            List<ColumnaInfo> columnas)
            throws SQLException {

        if (columnas == null || columnas.isEmpty()) {
            throw new SQLException(
                    "La tabla no tiene columnas");
        }

        StringBuilder sql = new StringBuilder();

        sql.append("CREATE TABLE ")
                .append(citarNombre(nombre))
                .append(" (");

        List<String> clavesPrimarias = new ArrayList<>();

        for (int i = 0; i < columnas.size(); i++) {

            ColumnaInfo columna = columnas.get(i);

            sql.append(citarNombre(columna.getNombre()))
                    .append(" ")
                    .append(convertirTipo(columna.getTipo()));

            if (columna.isPrimaryKey()) {
                clavesPrimarias.add(
                        citarNombre(columna.getNombre()));
            }

            if (!columna.isNulo() &&
                    !columna.isPrimaryKey()) {
                sql.append(" NOT NULL");
            }

            if (i < columnas.size() - 1) {
                sql.append(", ");
            }
        }

        // Conserva claves primarias simples y compuestas
        if (!clavesPrimarias.isEmpty()) {
            sql.append(", PRIMARY KEY (")
                    .append(String.join(", ", clavesPrimarias))
                    .append(")");
        }

        sql.append(")");

        try (Statement stmt =
                     conexion.createStatement()) {

            stmt.executeUpdate(sql.toString());

            System.out.println("Tabla creada: " + nombre);
        }
    }

    // 6. INSERTAR REGISTROS EN LOTES
    private void insertarRegistros(
            Connection conexion,
            String nombreTabla,
            List<ColumnaInfo> columnas,
            List<List<Object>> registros)
            throws SQLException {

        if (registros == null || registros.isEmpty()) {
            System.out.println("La tabla está vacía");
            return;
        }

        StringBuilder sql = new StringBuilder();

        sql.append("INSERT INTO ")
                .append(citarNombre(nombreTabla))
                .append(" (");

        for (int i = 0; i < columnas.size(); i++) {

            sql.append(
                    citarNombre(columnas.get(i).getNombre()));

            if (i < columnas.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(") VALUES (");

        for (int i = 0; i < columnas.size(); i++) {

            sql.append("?");

            if (i < columnas.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(")");

        try (PreparedStatement ps =
                     conexion.prepareStatement(sql.toString())) {

            int contador = 0;

            for (List<Object> registro : registros) {

                if (registro == null ||
                        registro.size() != columnas.size()) {
                    throw new SQLException(
                            "Los valores no coinciden con las columnas");
                }

                for (int i = 0; i < columnas.size(); i++) {
                    ps.setObject(i + 1, registro.get(i));
                }

                ps.addBatch();
                contador++;

                if (contador % 500 == 0) {
                    ps.executeBatch();
                    ps.clearBatch();
                }
            }

            ps.executeBatch();

            System.out.println(
                    "Registros preparados: " + contador);
        }
    }

    // 7. ELIMINAR TABLA LOCAL
    private void eliminarTabla(
            Connection conexion,
            String nombreTabla)
            throws SQLException {

        String sql = "DROP TABLE IF EXISTS "
                + citarNombre(nombreTabla);

        try (Statement stmt =
                     conexion.createStatement()) {

            stmt.executeUpdate(sql);
        }
    }

    // 8. DESCARGAR TABLA COMPLETA
    public void guardarTabla(String nombreTabla)
            throws SQLException {

        guardarTabla(nombreTabla, false);
    }

    // Sobrescribir solo cuando el usuario lo autorice
    public void guardarTabla(
            String nombreTabla,
            boolean sobrescribir)
            throws SQLException {

        RemoteDbDao remoto = new RemoteDbDao();

        // Obtener información de MariaDB
        List<ColumnaInfo> columnas =
                remoto.obtenerColumnas(nombreTabla);

        ResultadoTabla resultado =
                remoto.obtenerRegistros(nombreTabla);

        if (columnas == null || columnas.isEmpty()) {
            throw new SQLException(
                    "No se encontraron columnas");
        }

        if (resultado.getColumnas().size()
                != columnas.size()) {
            throw new SQLException(
                    "La cantidad de columnas no coincide");
        }

        // Comprobar que coincida el orden
        for (int i = 0; i < columnas.size(); i++) {

            String nombreOrigen =
                    columnas.get(i).getNombre();

            String nombreResultado =
                    resultado.getColumnas().get(i);

            if (!nombreOrigen.equalsIgnoreCase(
                    nombreResultado)) {

                throw new SQLException(
                        "El orden de columnas no coincide");
            }
        }

        // Una sola conexión para toda la operación
        try (Connection conexion = conectar()) {

            if (tablaExiste(conexion, nombreTabla)
                    && !sobrescribir) {

                throw new SQLException(
                        "La tabla ya existe en SQLite: "
                                + nombreTabla);
            }

            conexion.setAutoCommit(false);

            try {

                // Reemplazar si fue autorizado
                if (sobrescribir &&
                        tablaExiste(conexion, nombreTabla)) {

                    eliminarTabla(conexion, nombreTabla);
                }

                // Crear estructura local
                crearTabla(conexion, nombreTabla, columnas);

                // Copiar registros
                insertarRegistros(
                        conexion,
                        nombreTabla,
                        columnas,
                        resultado.getFilas());

                // Confirmar toda la operación
                conexion.commit();

                System.out.println(
                        "Descarga completada: " + nombreTabla);

            } catch (Exception e) {

                try {
                    conexion.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                throw new SQLException(
                        "Error al descargar la tabla: "
                                + nombreTabla, e);
            }
        }
    }
}
