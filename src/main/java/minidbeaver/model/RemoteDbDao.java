package minidbeaver.model;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoteDbDao {

    static {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // Si el driver no está, la conexión lanzará un error más claro.
        }
    }

    /**
     * Abre conexión JDBC contra MariaDB usando el túnel SSH local.
     * El túnel redirige 127.0.0.1:3307 -> servidorMariaDB:3306.
     */


    /**
     * INTEGRANTE 2:
     * Abre una conexión JDBC con MariaDB mediante el túnel SSH
     * configurado durante el inicio de sesión.
     *
     * MODIFICACIONES DEL INTEGRANTE 3:
     *
     * 1. Validación del puerto local del túnel SSH.
     * 2. Configuración de un tiempo máximo de conexión.
     * 3. Mensajes DEBUG para identificar el puerto utilizado.
     * 4. Registro de errores SQL y sus causas.
     *
     * Motivo:
     * Durante las pruebas de descarga de distintas tablas
     * se presentaron errores intermitentes de conexión.
     *
     * Estos cambios permiten identificar posibles fallas
     * sin modificar los métodos de consulta del integrante 2.
     */
    public Connection abrirConexion() throws SQLException {

        // INTEGRANTE 2:
        // Recuperar la configuración almacenada en la sesión.
        ConexionConfig cfg = SesionApp.get().getConfig();

        if (cfg == null) {
            throw new SQLException(
                    "No hay configuración de sesión. Inicia sesión primero.");
        }

        // INTEGRANTE 2:
        // Recuperar el puerto local del túnel SSH.
        int puertoLocal = SesionApp.get().getPuertoLocal();

        // INTEGRANTE 3:
        // Verificar que el puerto almacenado sea válido.
        if (puertoLocal <= 0 || puertoLocal > 65535) {
            throw new SQLException(
                    "Puerto local del túnel SSH inválido: " + puertoLocal);
        }

        // INTEGRANTE 3:
        // Mostrar el puerto utilizado para facilitar el diagnóstico.
        System.out.println("[DEBUG] Puerto local SSH: " + puertoLocal);

        // INTEGRANTE 2:
        // Construir la dirección de conexión JDBC.
        //
        // INTEGRANTE 3:
        // Se agregó connectTimeout=10000 para establecer
        // un límite de 10 segundos al intentar conectar.
        String url = "jdbc:mariadb://127.0.0.1:"
                + puertoLocal + "/" + cfg.getDbNombre()
                + "?connectTimeout=10000";

        System.out.println("[DEBUG] Conectando a: " + url);

        try {

            // INTEGRANTE 2:
            // Establecer conexión con MariaDB.
            Connection conexion = DriverManager.getConnection(
                    url,
                    cfg.getDbUsuario(),
                    cfg.getDbPassword()
            );

            // INTEGRANTE 3:
            // Confirmar en consola que la conexión fue exitosa.
            System.out.println("[DEBUG] Conexión MariaDB exitosa");

            return conexion;

        } catch (SQLException e) {

            // INTEGRANTE 3:
            // Registrar información del error para facilitar
            // la identificación de problemas del túnel SSH.
            System.err.println("[ERROR] Falló la conexión MariaDB");
            System.err.println("[ERROR] Puerto: " + puertoLocal);
            System.err.println("[ERROR] SQLState: " + e.getSQLState());
            System.err.println("[ERROR] Mensaje: " + e.getMessage());

            // Mostrar las causas internas de la excepción.
            Throwable causa = e.getCause();

            while (causa != null) {

                System.err.println(
                        "[ERROR] Causa: "
                                + causa.getClass().getSimpleName()
                                + ": " + causa.getMessage()
                );

                causa = causa.getCause();
            }

            // Mantener el comportamiento original:
            // propagar el error para que el controlador lo maneje.
            throw e;
        }
    }


    /**
     * Contrato: RemoteDbDao.listarTablas() devuelve List<String>.
     */
    public List<String> listarTablas() throws SQLException {
        List<String> tablas = new ArrayList<>();

        try (Connection con = abrirConexion();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SHOW TABLES")) {

            while (rs.next()) {
                tablas.add(rs.getString(1));
            }
        }

        return tablas;
    }

    /**
     * Obtiene estructura de columnas de una tabla.
     * Lo usará el Integrante 3 para generar el CREATE TABLE en SQLite.
     */
    public List<ColumnaInfo> obtenerColumnas(String tabla) throws SQLException {
        List<ColumnaInfo> columnas = new ArrayList<>();

        try (Connection con = abrirConexion()) {
            DatabaseMetaData meta = con.getMetaData();

            String catalogo = con.getCatalog();
            if (catalogo == null || catalogo.isBlank()) {
                catalogo = SesionApp.get().getConfig().getDbNombre();
            }

            Set<String> primaryKeys = new HashSet<>();

            try (ResultSet rsPk = meta.getPrimaryKeys(catalogo, null, tabla)) {
                while (rsPk.next()) {
                    primaryKeys.add(rsPk.getString("COLUMN_NAME"));
                }
            }

            try (ResultSet rs = meta.getColumns(catalogo, null, tabla, "%")) {
                while (rs.next()) {
                    String nombre = rs.getString("COLUMN_NAME");
                    String tipo = rs.getString("TYPE_NAME");
                    int tamano = rs.getInt("COLUMN_SIZE");
                    int decimales = rs.getInt("DECIMAL_DIGITS");
                    boolean nulo = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                    String porDefecto = rs.getString("COLUMN_DEF");
                    boolean autoIncrement = "YES".equalsIgnoreCase(safeGet(rs, "IS_AUTOINCREMENT"));

                    columnas.add(new ColumnaInfo(
                            nombre,
                            tipo,
                            tamano,
                            decimales,
                            nulo,
                            autoIncrement,
                            primaryKeys.contains(nombre),
                            porDefecto
                    ));
                }
            }
        }

        return columnas;
    }

    /**
     * Contrato: RemoteDbDao.obtenerRegistros(tabla) devuelve columnas y filas.
     */
    public ResultadoTabla obtenerRegistros(String tabla) throws SQLException {
        String sql = "SELECT * FROM " + citarIdentificador(tabla);

        List<String> columnas = new ArrayList<>();
        List<List<Object>> filas = new ArrayList<>();

        try (Connection con = abrirConexion();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int totalColumnas = meta.getColumnCount();

            for (int i = 1; i <= totalColumnas; i++) {
                columnas.add(meta.getColumnLabel(i));
            }

            while (rs.next()) {
                List<Object> fila = new ArrayList<>(totalColumnas);

                for (int i = 1; i <= totalColumnas; i++) {
                    fila.add(rs.getObject(i));
                }

                filas.add(fila);
            }
        }

        return new ResultadoTabla(columnas, filas);
    }

    private String citarIdentificador(String nombre) throws SQLException {
        if (nombre == null || nombre.isBlank()) {
            throw new SQLException("El nombre de la tabla está vacío.");
        }

        return "`" + nombre.replace("`", "``") + "`";
    }

    private String safeGet(ResultSet rs, String columna) {
        try {
            return rs.getString(columna);
        } catch (SQLException e) {
            return null;
        }
    }

    public static class ColumnaInfo {
        private final String nombre;
        private final String tipo;
        private final int tamano;
        private final int decimales;
        private final boolean nulo;
        private final boolean autoIncrement;
        private final boolean primaryKey;
        private final String porDefecto;

        public ColumnaInfo(String nombre, String tipo, int tamano, int decimales,
                           boolean nulo, boolean autoIncrement, boolean primaryKey,
                           String porDefecto) {
            this.nombre = nombre;
            this.tipo = tipo;
            this.tamano = tamano;
            this.decimales = decimales;
            this.nulo = nulo;
            this.autoIncrement = autoIncrement;
            this.primaryKey = primaryKey;
            this.porDefecto = porDefecto;
        }

        public String getNombre() {
            return nombre;
        }

        public String getTipo() {
            return tipo;
        }

        public int getTamano() {
            return tamano;
        }

        public int getDecimales() {
            return decimales;
        }

        public boolean isNulo() {
            return nulo;
        }

        public boolean isAutoIncrement() {
            return autoIncrement;
        }

        public boolean isPrimaryKey() {
            return primaryKey;
        }

        public String getPorDefecto() {
            return porDefecto;
        }
    }

    public static class ResultadoTabla {
        private final List<String> columnas;
        private final List<List<Object>> filas;

        public ResultadoTabla(List<String> columnas, List<List<Object>> filas) {
            this.columnas = columnas;
            this.filas = filas;
        }

        public List<String> getColumnas() {
            return columnas;
        }

        public List<List<Object>> getFilas() {
            return filas;
        }
    }
}